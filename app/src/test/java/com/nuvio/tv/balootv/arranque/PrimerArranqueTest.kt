package com.nuvio.tv.balootv.arranque

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimerArranqueTest {

    @Test
    fun `solo la edicion publica sin terminar pasa por el primer arranque`() {
        assertTrue(AlmacenPrimerArranque.pendiente(publica = true, hecho = false))
        assertFalse(AlmacenPrimerArranque.pendiente(publica = true, hecho = true))
        assertFalse(AlmacenPrimerArranque.pendiente(publica = false, hecho = false))
    }

    @Test
    fun `los pasos van bienvenida, TorBox, Trakt y fuentes`() {
        assertEquals(PasoPrimerArranque.TORBOX, AlmacenPrimerArranque.siguiente(PasoPrimerArranque.BIENVENIDA))
        assertEquals(PasoPrimerArranque.TRAKT, AlmacenPrimerArranque.siguiente(PasoPrimerArranque.TORBOX))
        assertEquals(PasoPrimerArranque.FUENTES, AlmacenPrimerArranque.siguiente(PasoPrimerArranque.TRAKT))
        assertNull(AlmacenPrimerArranque.siguiente(PasoPrimerArranque.FUENTES))
        assertNull(AlmacenPrimerArranque.anterior(PasoPrimerArranque.BIENVENIDA))
    }
}
