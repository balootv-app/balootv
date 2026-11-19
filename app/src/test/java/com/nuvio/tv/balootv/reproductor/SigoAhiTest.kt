package com.nuvio.tv.balootv.reproductor

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SigoAhiTest {

    @Test
    fun `de dia se pregunta tras tres episodios seguidos sin tocar el mando`() {
        assertEquals(3, SigoAhi.umbral(LocalTime.of(22, 30)))
        assertEquals(3, SigoAhi.umbral(LocalTime.of(6, 0)))
        assertEquals(3, SigoAhi.umbral(LocalTime.of(0, 59)))
    }

    @Test
    fun `de madrugada, tras uno`() {
        assertEquals(1, SigoAhi.umbral(LocalTime.of(1, 0)))
        assertEquals(1, SigoAhi.umbral(LocalTime.of(5, 59)))
    }
}
