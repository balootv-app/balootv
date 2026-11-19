package com.nuvio.tv.balootv.reproductor

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TiemposReproductorTest {

    @Test
    fun `tiempo transcurrido con y sin horas`() {
        assertEquals("44:05", TiemposReproductor.transcurrido(2_645_000))
        assertEquals("1:02:05", TiemposReproductor.transcurrido(3_725_000))
        assertEquals("0:00", TiemposReproductor.transcurrido(-5))
    }

    @Test
    fun `tiempo que queda y hora a la que termina`() {
        assertEquals("−1:11:55 · termina a las 23:18", TiemposReproductor.restante(2_645_000, 6_960_000, LocalTime.of(22, 6, 5)))
        assertEquals("−9:30 · termina a las 0:05", TiemposReproductor.restante(0, 570_000, LocalTime.of(23, 55, 30)))
        assertEquals("", TiemposReproductor.restante(1_000, 0, LocalTime.NOON))
    }

    @Test
    fun `en pausa se dice lo que queda en palabras`() {
        assertEquals("Quedan 1 h 12 min · termina a las 23:18", TiemposReproductor.enPausa(2_645_000, 6_960_000, LocalTime.of(22, 6, 5)))
        assertEquals("Quedan 10 min · termina a las 0:05", TiemposReproductor.enPausa(0, 570_000, LocalTime.of(23, 55, 30)))
        assertEquals("Queda menos de un minuto", TiemposReproductor.enPausa(6_940_000, 6_960_000, LocalTime.NOON))
        assertEquals("", TiemposReproductor.enPausa(1_000, 0, LocalTime.NOON))
    }
}
