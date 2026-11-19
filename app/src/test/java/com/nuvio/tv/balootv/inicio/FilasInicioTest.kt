package com.nuvio.tv.balootv.inicio

import com.nuvio.tv.data.remote.dto.trakt.TraktIdsDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowEpisodeProgressDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowProgressResponseDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowSeasonProgressDto
import com.nuvio.tv.domain.model.ContentType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FilasInicioTest {

    private val hoy = LocalDate.parse("2026-10-31")

    private fun peli(n: Int, imdb: String? = null) =
        Titulo(Tipo.PELICULA, Ids(n, imdb, 1000 + n), "Peli $n", 2020, "c$n", "f$n", "s$n")

    private fun serie(n: Int) = Titulo(Tipo.SERIE, Ids(n, "tts$n", 2000 + n), "Serie $n", 2021)

    private fun guardado(
        paraTi: List<Titulo> = emptyList(),
        series: List<SerieEstrenos> = emptyList(),
        enLaFicha: Map<String, EpisodioFicha> = emptyMap(),
    ) = InicioGuardado(0, entrada = EntradaIdeas(paraTi, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), series = series, enLaFicha = enLaFicha)

    @Test
    fun `cada fila con su nombre y sus carteles con el id de TMDB`() {
        val filas = FilasInicio.de(guardado(paraTi = (1..5).map { peli(it) }), emptyList(), emptyList(), hoy)
        assertEquals(listOf("Para ti"), filas.map { it.titulo })
        val primero = filas[0].items[0]
        assertEquals("tmdb:1", primero.id)
        assertEquals(ContentType.MOVIE, primero.type)
        assertEquals("Peli 1", primero.name)
        assertEquals("c1", primero.poster)
        assertEquals("f1", primero.background)
        assertEquals("2020", primero.releaseInfo)
    }

    @Test
    fun `lo que esta en Seguir viendo con id de IMDb no sale`() {
        val paraTi = (1..5).map { peli(it, "tt$it") }
        val filas = FilasInicio.de(guardado(paraTi = paraTi), emptyList(), listOf(EnCurso("tt2", serie = false)), hoy)
        assertEquals(listOf("tmdb:1", "tmdb:3", "tmdb:4", "tmdb:5"), filas[0].items.map { it.id })
    }

    @Test
    fun `lo de Mi lista con id de TMDB no sale`() {
        val paraTi = (1..5).map { peli(it) }
        val filas = FilasInicio.de(guardado(paraTi = paraTi), listOf(Marca(Tipo.PELICULA, Ids(5))), emptyList(), hoy)
        assertEquals(4, filas[0].items.size)
    }

    @Test
    fun `la temporada nueva dice cual es con la numeracion de la ficha`() {
        val s = serie(7)
        val estrenos = listOf(SerieEstrenos(s, 1, mapOf(2 to hoy.minusDays(3))))
        val enFicha = mapOf(InicioGuardado.claveTemporada(s, 2) to EpisodioFicha(1, 13))
        val fila = FilasInicio.de(guardado(series = estrenos, enLaFicha = enFicha), emptyList(), emptyList(), hoy).single()
        assertEquals("Ya está aquí la temporada nueva", fila.titulo)
        assertEquals("Temporada 1", fila.items.single().releaseInfo)
        assertEquals(mapOf("tmdb:7" to EpisodioFicha(1, 13)), fila.temporadas)
    }

    @Test
    fun `sin saber donde empieza en la ficha, la temporada de TMDB y su episodio 1`() {
        val estrenos = listOf(SerieEstrenos(serie(7), 1, mapOf(2 to hoy)))
        val fila = FilasInicio.de(guardado(series = estrenos), emptyList(), emptyList(), hoy).single()
        assertEquals("Temporada 2", fila.items.single().releaseInfo)
        assertEquals(EpisodioFicha(2, 1), fila.temporadas["tmdb:7"])
    }

    @Test
    fun `el salvapantallas saca lo de las filas de hoy con fondo y sin repetir`() {
        val sinFondo = peli(9).copy(fondo = null)
        val paraTi = (1..5).map { peli(it) } + sinFondo
        val g = guardado(paraTi = paraTi).let {
            it.copy(entrada = it.entrada.copy(estaSemana = listOf(peli(1), peli(6), peli(7), peli(8), peli(10))))
        }
        val ids = FilasInicio.salvapantallas(g, hoy).map { it.id }
        assertEquals(listOf("tmdb:1", "tmdb:2", "tmdb:3", "tmdb:4", "tmdb:5", "tmdb:6", "tmdb:7", "tmdb:8", "tmdb:10"), ids)
    }

    @Test
    fun `un titulo sin id de TMDB ni de IMDb no se puede abrir y no sale`() {
        assertNull(FilasInicio.meta(Titulo(Tipo.PELICULA, Ids(null, null, 9), "Sin ficha")))
        assertEquals("tt9", FilasInicio.meta(Titulo(Tipo.PELICULA, Ids(null, "tt9", 9), "Solo IMDb"))!!.id)
    }

    @Test
    fun `lo guardado se lee igual que se escribio`() {
        val g = guardado(paraTi = listOf(peli(1)), series = listOf(SerieEstrenos(serie(2), 1, mapOf(2 to hoy))),
            enLaFicha = mapOf("k" to EpisodioFicha(1, 2)))
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(g, json.decodeFromString(InicioGuardado.serializer(), json.encodeToString(InicioGuardado.serializer(), g)))
    }

    @Test
    fun `de Trakt, la ultima temporada con algo completado y si la serie sigue`() {
        val progreso = TraktShowProgressResponseDto(seasons = listOf(
            TraktShowSeasonProgressDto(1, listOf(TraktShowEpisodeProgressDto(1, true))),
            TraktShowSeasonProgressDto(2, listOf(TraktShowEpisodeProgressDto(1, true), TraktShowEpisodeProgressDto(2, false))),
            TraktShowSeasonProgressDto(3, listOf(TraktShowEpisodeProgressDto(1, false))),
        ))
        assertEquals(2, DeTrakt.ultimaTemporadaVista(progreso))
        assertEquals(0, DeTrakt.ultimaTemporadaVista(null))
        val ids = TraktIdsDto(trakt = 1, tmdb = 2)
        assertTrue(DeTrakt.puedeEstrenar(TraktShowDto(ids = ids, status = "returning series")))
        assertFalse(DeTrakt.puedeEstrenar(TraktShowDto(ids = ids, status = "ended")))
        assertFalse(DeTrakt.puedeEstrenar(TraktShowDto(ids = ids, status = "Canceled")))
        assertFalse(DeTrakt.puedeEstrenar(TraktShowDto(ids = TraktIdsDto(trakt = 1), status = "returning series")))
    }
}
