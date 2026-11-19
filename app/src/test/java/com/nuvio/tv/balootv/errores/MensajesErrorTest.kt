package com.nuvio.tv.balootv.errores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MensajesErrorTest {

    @Test
    fun `sin red manda sobre todo lo demas`() {
        assertEquals(MensajesError.SIN_RED, MensajesError.sinFuentes(hayRed = false, conError = 0, total = 4))
        assertEquals(MensajesError.SIN_RED, MensajesError.ficha(hayRed = false))
        assertEquals(MensajesError.SIN_RED, MensajesError.inicio(hayRed = false))
    }

    @Test
    fun `todas las fuentes con error no es lo mismo que no haber castellano`() {
        assertEquals("Las fuentes no responden", MensajesError.sinFuentes(true, conError = 4, total = 4).titulo)
        assertEquals("No hay fuentes en castellano", MensajesError.sinFuentes(true, conError = 1, total = 4).titulo)
        assertEquals("No hay fuentes en castellano", MensajesError.sinFuentes(true, conError = 0, total = 0).titulo)
    }

    @Test
    fun `nota de fuentes caidas con sus nombres`() {
        assertNull(MensajesError.fuentesCaidas(emptyList()))
        assertEquals("Fuente A no ha respondido; puede que falte alguna fuente.", MensajesError.fuentesCaidas(listOf("Fuente A")))
        assertEquals(
            "Fuente A, Fuente B y Fuente C no han respondido; puede que falten fuentes.",
            MensajesError.fuentesCaidas(listOf("Fuente A", "Fuente B", "Fuente C")),
        )
    }
}
