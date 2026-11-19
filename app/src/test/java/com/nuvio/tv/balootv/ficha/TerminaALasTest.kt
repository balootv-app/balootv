package com.nuvio.tv.balootv.ficha

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminaALasTest {

    @Test
    fun `duracion en horas y minutos, con la hora justa sin ceros`() {
        assertEquals("45 min", TerminaALas.duracion(45))
        assertEquals("1 h 55 min", TerminaALas.duracion(115))
        assertEquals("2 h", TerminaALas.duracion(120))
    }

    @Test
    fun `sin empezar, la duracion y la hora a la que acaba`() {
        assertEquals("1 h 56 min · termina a las 23:18", TerminaALas.sinEmpezar(116, LocalTime.of(21, 22, 40)))
        assertEquals("45 min · termina a las 0:10", TerminaALas.sinEmpezar(45, LocalTime.of(23, 25)))
        assertNull(TerminaALas.sinEmpezar(null, LocalTime.NOON))
        assertNull(TerminaALas.sinEmpezar(0, LocalTime.NOON))
    }

    @Test
    fun `empezado, lo que queda redondeado al minuto y la hora sin segundos`() {
        // Quedan 71 min 55 s: se dicen 72 y acaba a las 23:18:00.
        assertEquals("Quedan 1 h 12 min · termina a las 23:18", TerminaALas.empezado(2_645_000, 6_960_000, LocalTime.of(22, 6, 5)))
        // Quedan 9 min 30 s: 10 min; las 0:05:00.
        assertEquals("Quedan 10 min · termina a las 0:05", TerminaALas.empezado(0, 570_000, LocalTime.of(23, 55, 30)))
        assertEquals("Quedan 2 h · termina a las 23:00", TerminaALas.empezado(0, 7_200_000, LocalTime.of(21, 0)))
        assertEquals("Queda 1 min · termina a las 21:01", TerminaALas.empezado(0, 70_000, LocalTime.of(21, 0)))
    }

    @Test
    fun `con menos de un minuto no se da la hora`() {
        assertEquals("Queda menos de un minuto", TerminaALas.empezado(6_940_000, 6_960_000, LocalTime.NOON))
        assertNull(TerminaALas.empezado(1_000, 0, LocalTime.NOON))
    }
}
