package com.nuvio.tv.balootv.ui.movimiento

import org.junit.Assert.assertEquals
import org.junit.Test

class AmortiguadorFocoTest {

    @Test
    fun `paseando la cabecera cambia al momento`() {
        var t = 0L
        val a = AmortiguadorFoco { t }
        assertEquals(0L, a.espera())
        t = 500
        assertEquals(0L, a.espera())
    }

    @Test
    fun `en rafaga espera a que se pare`() {
        var t = 0L
        val a = AmortiguadorFoco { t }
        a.espera()
        t = 100
        assertEquals(AmortiguadorFoco.ESPERA_MS, a.espera())
        t = 200
        assertEquals(AmortiguadorFoco.ESPERA_MS, a.espera())
        t = 900
        assertEquals(0L, a.espera())
    }
}
