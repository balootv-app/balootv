package com.nuvio.tv.balootv.fuentes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoSinEleccionTest {

    @Test
    fun `cada motivo con lista explica por que no ha empezado solo`() {
        listOf(Motivo.SOLO_DUDOSO, Motivo.SOLO_NO_CACHEADO, Motivo.SIN_CALIDAD).forEach { m ->
            val texto = TextoSinEleccion.de(m)
            assertTrue("$m", texto != null && "no he empezado solo" in texto)
        }
        assertTrue("flecha de descarga" in TextoSinEleccion.de(Motivo.SOLO_NO_CACHEADO)!!)
        assertTrue("Esta no está en castellano" in TextoSinEleccion.de(Motivo.SOLO_DUDOSO)!!)
    }

    @Test
    fun `sin castellano va al aviso propio y una eleccion normal no lleva texto`() {
        assertNull(TextoSinEleccion.de(Motivo.SIN_CASTELLANO))
        assertNull(TextoSinEleccion.de(null))
    }

    @Test
    fun `todo motivo tiene un porque corto para la ficha`() {
        Motivo.entries.forEach { m -> assertTrue("$m", TextoSinEleccion.corto(m).isNotBlank()) }
        assertEquals("Aún en cines", TextoSinEleccion.corto(Motivo.AUN_EN_CINES))
        assertEquals("Solo en latino", TextoSinEleccion.corto(Motivo.SOLO_LATINO))
        assertEquals("Sin castellano todavía", TextoSinEleccion.corto(Motivo.SIN_CASTELLANO))
        assertEquals("Castellano solo por debajo de 1080p", TextoSinEleccion.corto(Motivo.SIN_CALIDAD))
    }

    @Test
    fun `solo latino y solo sin confirmar no se quedan sin explicacion`() {
        assertTrue(TextoSinEleccion.sinCastellano(Motivo.SOLO_LATINO))
        assertTrue("título" in TextoSinEleccion.de(Motivo.SOLO_SIN_CONFIRMAR)!!)
        assertTrue("cines" in TextoSinEleccion.de(Motivo.AUN_EN_CINES)!!)
    }
}
