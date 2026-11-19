package com.nuvio.tv.balootv.ui

import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.NextUpInfo
import com.nuvio.tv.balootv.iconos.leidas
import org.junit.Assert.assertEquals
import org.junit.Test

class TarjetaSeguirTest {

    private fun progreso(tipo: String, pos: Long, dur: Long, temporada: Int? = null, episodio: Int? = null, porcentaje: Float? = null) = WatchProgress(
        contentId = "tt1", contentType = tipo, name = "Dan Da Dan", poster = "p", backdrop = "fondo", logo = null,
        videoId = "tt1:2:7", season = temporada, episode = episodio, episodeTitle = null,
        position = pos, duration = dur, lastWatched = 0L, progressPercent = porcentaje,
    )

    @Test
    fun `episodio a medias con temporada, episodio y lo que queda`() {
        val t = TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("series", 13 * 60_000L, 24 * 60_000L, 2, 7), episodeThumbnail = "miniatura"))
        assertEquals("Dan Da Dan", t.titulo)
        assertEquals("T2 · E7 · Quedan 11 min", t.detalle)
        // En pantalla, sin «Quedan»: lo dice la barra (como el móvil).
        assertEquals("T2 · E7 · 11 min", t.corto.leidas())
        assertEquals(false, t.siguiente)
        assertEquals("miniatura", t.imagen)
        assertEquals(13f / 24f, t.progreso, 0.001f)
    }

    @Test
    fun `con menos de un minuto, como el movil`() {
        val t = TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("series", 24 * 60_000L - 30_000L, 24 * 60_000L, 2, 7)))
        assertEquals("T2 · E7 · menos de 1 min", t.corto.leidas())
    }

    @Test
    fun `pelicula con horas y fondo como imagen`() {
        val t = TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("movie", 18 * 60_000L, (2 * 60 + 49) * 60_000L)))
        assertEquals("Quedan 2 h 31 min", t.detalle)
        assertEquals("2 h 31 min", t.corto.leidas())
        assertEquals("fondo", t.imagen)
    }

    @Test
    fun `sin duracion usa el porcentaje de Trakt`() {
        val t = TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("movie", 0, 0, porcentaje = 54f)))
        assertEquals(0.54f, t.progreso, 0.001f)
        assertEquals("", t.detalle)
        assertEquals("", t.corto.leidas())
    }

    @Test
    fun `siguiente episodio`() {
        val info = NextUpInfo(
            contentId = "tt2", contentType = "series", name = "SPY×FAMILY", poster = null, backdrop = "f", logo = null,
            videoId = "tt2:3:2", season = 3, episode = 2, episodeTitle = null, thumbnail = null, lastWatched = 0L, sortTimestamp = 0L,
        )
        val t = TarjetaSeguir.de(ContinueWatchingItem.NextUp(info))
        assertEquals("T3 · E2 · Siguiente episodio", t.detalle)
        // El icono de siguiente en lugar de la palabra, y sin barra vacía.
        assertEquals("<SIGUIENTE> T3 · E2", t.corto.leidas())
        assertEquals(true, t.siguiente)
        assertEquals(0f, t.progreso, 0f)
        assertEquals("f", t.imagen)
    }

    @Test
    fun `el siguiente episodio no se ve todavia, su imagen tampoco`() {
        val info = NextUpInfo(
            contentId = "tt2", contentType = "series", name = "SPY×FAMILY", poster = "cartel", backdrop = null, logo = null,
            videoId = "tt2:3:2", season = 3, episode = 2, episodeTitle = null, thumbnail = "miniatura", lastWatched = 0L, sortTimestamp = 0L,
        )
        val t = TarjetaSeguir.de(ContinueWatchingItem.NextUp(info))
        assertEquals(listOf("cartel"), t.imagenes)
    }

    @Test
    fun `sin fondo ni miniatura usa la imagen apaisada o el cartel enriquecidos`() {
        val sinImagenes = progreso("series", 1, 2, 2, 7).copy(backdrop = null, poster = null)
        assertEquals("apaisada", TarjetaSeguir.de(ContinueWatchingItem.InProgress(sinImagenes, customLandscapePoster = "apaisada", originalPoster = "cartel")).imagen)
        assertEquals("cartel", TarjetaSeguir.de(ContinueWatchingItem.InProgress(sinImagenes, originalPoster = "cartel")).imagen)
    }

    @Test
    fun `una miniatura vacia no tapa el fondo y las candidatas siguen en orden sin repetirse`() {
        val p = progreso("series", 1, 2, 1, 3).copy(backdrop = "fondo", poster = "cartel")
        val t = TarjetaSeguir.de(ContinueWatchingItem.InProgress(p, episodeThumbnail = "  ", originalPoster = "cartel"))
        assertEquals("fondo", t.imagen)
        assertEquals(listOf("fondo", "cartel"), t.imagenes)
    }

    @Test
    fun `el fondo del Inicio es la imagen de la serie o la pelicula, nunca la del episodio ni el cartel`() {
        val aMedias = TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("series", 1, 2, 2, 7), episodeThumbnail = "miniatura"))
        assertEquals("fondo", aMedias.fondo)
        val siguiente = TarjetaSeguir.de(ContinueWatchingItem.NextUp(NextUpInfo(
            contentId = "tt2", contentType = "series", name = "SPY×FAMILY", poster = "cartel", backdrop = "f", logo = null,
            videoId = "tt2:1:3", season = 1, episode = 3, episodeTitle = null, thumbnail = "miniatura", lastWatched = 0L, sortTimestamp = 0L,
        )))
        assertEquals("f", siguiente.fondo)
        val sinFondo = TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("movie", 1, 2).copy(backdrop = null), customLandscapePoster = "apaisada"))
        assertEquals("apaisada", sinFondo.fondo)
        assertEquals(null, TarjetaSeguir.de(ContinueWatchingItem.InProgress(progreso("movie", 1, 2).copy(backdrop = " "))).fondo)
    }
}
