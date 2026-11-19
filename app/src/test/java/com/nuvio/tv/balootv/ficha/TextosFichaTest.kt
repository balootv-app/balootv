package com.nuvio.tv.balootv.ficha

import com.nuvio.tv.balootv.fuentes.Formato
import com.nuvio.tv.balootv.fuentes.Hdr
import com.nuvio.tv.balootv.iconos.leidas
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TextosFichaTest {

    private fun progreso(posMin: Int, durMin: Int, temporada: Int? = null, episodio: Int? = null) = WatchProgress(
        contentId = "tt1", contentType = if (temporada == null) "movie" else "series", name = "x", poster = null, backdrop = null,
        logo = null, videoId = "tt1", season = temporada, episode = episodio, episodeTitle = null,
        position = posMin * 60_000L, duration = durMin * 60_000L, lastWatched = 0L
    )

    private fun episodio(t: Int, e: Int, minutos: Int? = null, estreno: String? = null) = Video(
        id = "tt1:$t:$e", title = "Episodio $e", released = estreno, thumbnail = null, season = t, episode = e, overview = null, runtime = minutos
    )

    private val ahora = LocalTime.of(22, 0)

    /** El botón como (título, frase de C4). */
    private fun TextoBoton.par() = titulo to detalle

    @Test
    fun `boton de pelicula`() {
        assertEquals("Reproducir" to null, TextosFicha.boton(null, null, null, null, ahora).par())
        assertEquals("Reproducir" to "1 h 56 min · termina a las 23:56", TextosFicha.boton(null, null, null, 116, ahora).par())
        assertEquals("Continuar" to "Quedan 34 min · termina a las 22:34", TextosFicha.boton(null, null, progreso(82, 116), 116, ahora).par())
    }

    @Test
    fun `boton de pelicula con el progreso de Trakt, que trae porcentaje y no posicion`() {
        // Regreso al futuro en la tele (22-10-2026): Trakt devolvió position=0, duration=116 min, 6,07 %. Quedan 108,96
        // min: se dicen 109 y el fin es ahora más esos 109 (contrato C4).
        val trakt = progreso(0, 116).copy(progressPercent = 6.06667f, source = WatchProgress.SOURCE_TRAKT_PLAYBACK)
        assertEquals("Continuar" to "Quedan 1 h 49 min · termina a las 23:49", TextosFicha.boton(null, null, trakt, 116, ahora).par())
    }

    @Test
    fun `solo se continua lo que el reproductor reanuda, entre el 2 y el 90 por ciento`() {
        // Al 95 % (quedan 6 min) el reproductor empieza de cero: Reproducir, con la duración entera.
        assertEquals("Reproducir" to "1 h 56 min · termina a las 23:56", TextosFicha.boton(null, null, progreso(110, 116), 116, ahora).par())
        // Con 1 min (no llega al 2 %), igual.
        assertEquals("Reproducir" to "1 h 56 min · termina a las 23:56", TextosFicha.boton(null, null, progreso(1, 116), 116, ahora).par())
        assertEquals("Episodio 4 · 53 min", TextosFicha.etiquetaEpisodio(episodio(2, 4, 53), visto = false, progreso = progreso(50, 53, 2, 4)).leidas())
    }

    @Test
    fun `boton de serie`() {
        assertEquals("Reproducir T1 · E1" to null, TextosFicha.boton(null, episodio(1, 1), null, null, ahora).par())
        val n = NextToWatch(progreso(19, 53, 2, 3), isResume = true, nextVideoId = "tt1:2:3", nextSeason = 2, nextEpisode = 3, displayText = "")
        assertEquals("Continuar T2 · E3" to "Quedan 34 min · termina a las 22:34", TextosFicha.boton(n, episodio(2, 3), progreso(19, 53, 2, 3), 53, ahora).par())
        val siguiente = NextToWatch(null, isResume = false, nextVideoId = "tt1:2:4", nextSeason = 2, nextEpisode = 4, displayText = "")
        assertEquals("Reproducir T2 · E4" to "53 min · termina a las 22:53", TextosFicha.boton(siguiente, episodio(2, 4), null, 53, ahora).par())
    }

    @Test
    fun `etiqueta de episodio, con el numero delante y sin mayusculas`() {
        // Decidido el 31-10-2026: «Episodio 1» y luego el ✓; lo que queda, sin «Quedan» (lo dice la barra).
        assertEquals("Episodio 1 · <VISTO>", TextosFicha.etiquetaEpisodio(episodio(2, 1, 50), visto = true, progreso = null).leidas())
        assertEquals("Episodio 3 · 34 min", TextosFicha.etiquetaEpisodio(episodio(2, 3, 53), visto = false, progreso = progreso(19, 53, 2, 3)).leidas())
        assertEquals("Episodio 4 · 53 min", TextosFicha.etiquetaEpisodio(episodio(2, 4, 53), visto = false, progreso = null).leidas())
        assertEquals("Episodio 6 · 1 h 5 min", TextosFicha.etiquetaEpisodio(episodio(2, 6, 65), visto = false, progreso = null).leidas())
        assertEquals("Episodio 5", TextosFicha.etiquetaEpisodio(episodio(2, 5), visto = false, progreso = null).leidas())
    }

    @Test
    fun `fecha de estreno del episodio`() {
        val hoy = LocalDate.of(2026, 10, 30)
        assertEquals("8 ene 2021", TextosFicha.fechaEpisodio("2021-01-08", hoy)?.leidas())
        assertEquals("5 sept 2023", TextosFicha.fechaEpisodio("2023-09-05T07:00:00.000Z", hoy)?.leidas())
        assertEquals("Hoy", TextosFicha.fechaEpisodio("2026-10-30", hoy)?.leidas())
        // Sin emitir: el calendario dice que sale ese día, como en el móvil.
        assertEquals("<SALE> 6 nov", TextosFicha.fechaEpisodio("2026-11-06", hoy)?.leidas())
        assertEquals("<SALE> 15 ene 2027", TextosFicha.fechaEpisodio("2027-01-15", hoy)?.leidas())
        assertNull(TextosFicha.fechaEpisodio(null, hoy))
        assertNull(TextosFicha.fechaEpisodio("pronto", hoy))
    }

    @Test
    fun `anio de cada temporada por su primer estreno, sin los especiales`() {
        val videos = listOf(
            episodio(1, 2, estreno = "2021-01-08"), episodio(1, 1, estreno = "2021-01-08"),
            episodio(2, 1, estreno = "2021-06-11"),
            episodio(3, 1, estreno = "2023-10-05"),
            episodio(4, 2, estreno = "2027-01-02"), episodio(4, 1, estreno = "2026-12-26"),
            episodio(5, 1),
            episodio(0, 1, estreno = "2020-12-01"),
        )
        assertEquals(mapOf(1 to "2021", 2 to "2021", 3 to "2023", 4 to "2026"), TextosFicha.aniosTemporada(videos))
    }

    @Test
    fun `linea de metadatos`() {
        assertEquals("1985 · Aventura, comedia", TextosFicha.meta("1985", listOf("Aventura", "Comedia"), null))
        assertEquals("2023 · Drama · 2 temporadas", TextosFicha.meta("2023", listOf("Drama"), 2))
        assertEquals("1 temporada", TextosFicha.meta(null, emptyList(), 1))
        assertEquals("2019 · Acción, aventura", TextosFicha.meta("2019", listOf("Acción", "Aventura", "Ciencia ficción"), null))
    }

    @Test
    fun `generos como en el movil y el salvapantallas`() {
        assertEquals("Acción, aventura", TextosFicha.generos(listOf("Acción", "Aventura", "Ciencia ficción")))
        // TMDB deja en inglés algunos géneros de series: se traducen (C9).
        assertEquals("Ciencia ficción y fantasía", TextosFicha.generos(listOf("Sci-Fi & Fantasy")))
        assertNull(TextosFicha.generos(emptyList()))
    }

    @Test
    fun `detalle con la bandera de la hora y la fuente elegida en insignias, sin el codec`() {
        val formato = Formato("4K", Hdr.HDR10, "Castellano", "AC-3")
        val boton = TextosFicha.boton(null, null, null, 129, LocalTime.of(17, 46))
        assertEquals("2 h 9 min · <TERMINA> 19:55 · [4K] [HDR10] · <AUDIO> Castellano", TextosFicha.detalle(boton, formato).leidas())
        val menosDeUno = TextoBoton("Continuar", TerminaALas.partesQuedan(0, ahora))
        assertEquals("Queda menos de un minuto · [4K] [HDR10] · <AUDIO> Castellano", TextosFicha.detalle(menosDeUno, formato).leidas())
        val dudosa = Formato(null, Hdr.SDR, "Idioma por confirmar", null)
        assertEquals("<AUDIO> Idioma por confirmar", TextosFicha.detalle(TextoBoton("Reproducir", null), dudosa).leidas())
        assertEquals("", TextosFicha.detalle(TextoBoton("Reproducir", null), null).leidas())
    }

    @Test
    fun `sin fuente elegida el detalle dice el porque`() {
        val boton = TextosFicha.boton(null, null, null, 173, LocalTime.of(17, 46))
        assertEquals("2 h 53 min · <TERMINA> 20:39 · Aún en cines",
            TextosFicha.detalle(boton, null, com.nuvio.tv.balootv.fuentes.Motivo.AUN_EN_CINES).leidas())
        // Con fuente elegida manda la fuente.
        val formato = Formato("4K", Hdr.HDR10, "Castellano", "AC-3")
        assertEquals("<AUDIO> Castellano".let { "[4K] [HDR10] · $it" },
            TextosFicha.detalle(TextoBoton("Reproducir", null), formato, com.nuvio.tv.balootv.fuentes.Motivo.SOLO_LATINO).leidas())
    }

    @Test
    fun `minutos desde el texto de duracion`() {
        assertEquals(116, TextosFicha.minutos("116 min"))
        assertEquals(116, TextosFicha.minutos("1h 56m"))
        assertEquals(120, TextosFicha.minutos("2h"))
        assertEquals(45, TextosFicha.minutos("45"))
        assertNull(TextosFicha.minutos(null))
        assertNull(TextosFicha.minutos("N/A"))
    }
}
