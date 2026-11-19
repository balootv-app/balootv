package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.core.tmdb.TmdbEpisodeEnrichment
import com.nuvio.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class AccionesContextualesTest {

    @Test
    fun `el episodio se nombra como en las fichas, T1 E2 y su titulo`() {
        assertEquals("T1 · E2 · El camino real", AccionesContextuales.episodio(1, 2, "El camino real"))
        assertEquals("T3 · E10", AccionesContextuales.episodio(3, 10, " "))
    }

    @Test
    fun `cada tramo saltable tiene su texto en castellano`() {
        assertEquals("Saltar introducción", AccionesContextuales.etiquetaSalto("intro", false))
        assertEquals("Saltar introducción", AccionesContextuales.etiquetaSalto("op", false))
        assertEquals("Saltar resumen", AccionesContextuales.etiquetaSalto("recap", false))
        assertEquals("Saltar créditos", AccionesContextuales.etiquetaSalto("outro", false))
        assertEquals("Ir a la escena poscréditos", AccionesContextuales.etiquetaSalto("ed", true))
        assertEquals("Saltar", AccionesContextuales.etiquetaSalto(null, false))
    }

    @Test
    fun `la cuenta atras se lee en segundos y en fraccion para la barra`() {
        assertEquals("Empieza en 7 s", AccionesContextuales.estado(buscando = false, cuentaAtras = 7))
        assertEquals("Buscando en castellano…", AccionesContextuales.estado(buscando = true, cuentaAtras = null))
        assertEquals(null, AccionesContextuales.estado(buscando = false, cuentaAtras = null))
        assertEquals(0.3f, AccionesContextuales.fraccionCuentaAtras(3), 0.001f)
    }

    @Test
    fun `los episodios toman de TMDB el titulo, la sinopsis y la imagen en castellano`() {
        val videos = listOf(
            Video(id = "tt0944947:1:1", title = "Winter Is Coming", released = null, thumbnail = "a.jpg", season = 1, episode = 1, overview = "EN"),
            Video(id = "tt0944947:1:2", title = "The Kingsroad", released = null, thumbnail = null, season = 1, episode = 2, overview = null),
            Video(id = "tt0944947:1:3", title = "Lord Snow", released = null, thumbnail = "c.jpg", season = 1, episode = 3, overview = "EN"),
        )
        val tmdb = mapOf(
            (1 to 1) to TmdbEpisodeEnrichment(title = "Se acerca el invierno", overview = "ES", thumbnail = null, airDate = null, runtimeMinutes = null),
            (1 to 2) to TmdbEpisodeEnrichment(title = "El camino real", overview = "ES", thumbnail = "b.jpg", airDate = null, runtimeMinutes = null),
            (1 to 3) to TmdbEpisodeEnrichment(title = " ", overview = null, thumbnail = null, airDate = null, runtimeMinutes = null),
        )
        val r = EpisodiosTmdb.localizar(videos, tmdb)
        assertEquals(listOf("Se acerca el invierno", "El camino real", "Lord Snow"), r.map { it.title })
        assertEquals(listOf("ES", "ES", "EN"), r.map { it.overview })
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), r.map { it.thumbnail })
        assertEquals(videos, EpisodiosTmdb.localizar(videos, emptyMap()))
    }
}
