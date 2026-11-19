package com.nuvio.tv.balootv.reproductor

import org.junit.Assert.assertEquals
import org.junit.Test

class RetrasoSubtitulosTest {

    @Test
    fun `el texto lleva coma decimal y signo`() {
        assertEquals("0,0 s", RetrasoSubtitulos.texto(0))
        assertEquals("+0,5 s", RetrasoSubtitulos.texto(500))
        assertEquals("−1,2 s", RetrasoSubtitulos.texto(-1200))
        assertEquals("+10,0 s", RetrasoSubtitulos.texto(10_000))
        // Valores guardados que no son múltiplo del paso se redondean a la décima.
        assertEquals("+0,3 s", RetrasoSubtitulos.texto(260))
        assertEquals("0,0 s", RetrasoSubtitulos.texto(-40))
    }

    @Test
    fun `cada pulsacion mueve una decima`() {
        assertEquals(100, RetrasoSubtitulos.paso(0, 1))
        assertEquals(-100, RetrasoSubtitulos.paso(0, -1))
        assertEquals(400, RetrasoSubtitulos.paso(500, -1))
        assertEquals(-1300, RetrasoSubtitulos.paso(-1200, -1))
        // Un valor suelto vuelve a la rejilla de décimas.
        assertEquals(400, RetrasoSubtitulos.paso(260, 1))
    }

    @Test
    fun `no pasa de los limites`() {
        assertEquals(RetrasoSubtitulos.MAX_MS, RetrasoSubtitulos.paso(RetrasoSubtitulos.MAX_MS, 1))
        assertEquals(-RetrasoSubtitulos.MAX_MS, RetrasoSubtitulos.paso(-RetrasoSubtitulos.MAX_MS, -1))
        assertEquals(RetrasoSubtitulos.MAX_MS, RetrasoSubtitulos.paso(60_000, 1))
    }
}
