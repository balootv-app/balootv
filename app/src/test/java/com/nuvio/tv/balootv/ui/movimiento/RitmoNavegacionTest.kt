package com.nuvio.tv.balootv.ui.movimiento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RitmoNavegacionTest {

    /** Mantener la tecla como el mando: pulsación, 400 ms y una repetición cada 50 ms. Devuelve los instantes aceptados. */
    private fun mantener(r: RitmoNavegacion, desde: Long, ms: Long): List<Long> {
        val aceptadas = mutableListOf<Long>()
        if (r.pulsar(desde, 0)) aceptadas += desde
        var t = desde + 400
        var rep = 1
        while (t < desde + ms) {
            if (r.pulsar(t, rep)) aceptadas += t
            t += 50
            rep++
        }
        return aceptadas
    }

    @Test
    fun `pulsaciones sueltas pasan siempre y no son rafaga`() {
        val r = RitmoNavegacion()
        assertTrue(r.pulsar(0, 0))
        assertFalse(r.rafaga)
        assertTrue(r.pulsar(400, 0))
        assertFalse(r.rafaga)
    }

    @Test
    fun `pulsar muy seguido es rafaga pero no se descarta ninguna pulsacion`() {
        val r = RitmoNavegacion()
        assertTrue(r.pulsar(0, 0))
        assertTrue(r.pulsar(90, 0))
        assertTrue(r.rafaga)
        assertTrue(r.pulsar(170, 0))
    }

    @Test
    fun `la tecla mantenida avanza a 10 pasos por segundo`() {
        val r = RitmoNavegacion()
        val aceptadas = mantener(r, 0, 3_000)
        assertEquals(10, aceptadas.count { it in 400 until 1_400 })
        assertEquals(10, aceptadas.count { it in 2_000 until 3_000 })
        assertTrue(r.rafaga)
    }

    @Test
    fun `tras un rato sin teclas vuelve a reposo`() {
        val r = RitmoNavegacion()
        r.pulsar(0, 0)
        r.pulsar(80, 0)
        assertFalse(r.enReposo(200))
        assertTrue(r.enReposo(320))
    }
}
