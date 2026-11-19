package com.nuvio.tv.balootv.ui.ficha

import com.nuvio.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodioDelEnlaceTest {

    private fun v(t: Int, e: Int, disponible: Boolean? = null) =
        Video(id = "tt30217403:$t:$e", title = "T$t E$e", released = null, thumbnail = null, season = t, episode = e, overview = null, available = disponible)

    // DAN DA DAN en Cinemeta: 12 + 12 + 1.
    private val danDaDan = (1..12).map { v(1, it) } + (1..12).map { v(2, it) } + v(3, 1)

    @Test
    fun elEpisodioPedidoSeReproduce() {
        val r = EpisodioDelEnlace.de(danDaDan, 2, 7)
        assertEquals("tt30217403:2:7", r.video?.id)
        assertEquals(2, r.temporada)
    }

    @Test
    fun siNoExisteSeAbreLaFichaEnEsaTemporadaSinReproducir() {
        val r = EpisodioDelEnlace.de(danDaDan, 2, 30)
        assertNull(r.video)
        assertEquals(2, r.temporada)
    }

    @Test
    fun siTampocoExisteLaTemporadaSeAbreEnLaQueToca() {
        val r = EpisodioDelEnlace.de(danDaDan, 9, 1)
        assertNull(r.video)
        assertNull(r.temporada)
    }

    @Test
    fun unEpisodioNoDisponibleNoSeReproduce() {
        val r = EpisodioDelEnlace.de(listOf(v(1, 1), v(1, 2, disponible = false)), 1, 2)
        assertNull(r.video)
        assertEquals(1, r.temporada)
    }
}
