package com.nuvio.tv.balootv.ui.componentes

import org.junit.Assert.assertEquals
import org.junit.Test

class TipografiaTest {

    @Test
    fun `el espaciado es el de la tabla trak de SF Pro en sus puntos`() {
        assertEquals(-0.0254f, Tipografia.espaciadoEm(17f), 0.0005f)
        assertEquals(0.0137f, Tipografia.espaciadoEm(28f), 0.0005f)
        assertEquals(0.0068f, Tipografia.espaciadoEm(50f), 0.0005f)
        assertEquals(0f, Tipografia.espaciadoEm(80f), 0.0001f)
    }

    @Test
    fun `entre dos puntos de la tabla se interpola y fuera de ella se usa el extremo`() {
        // 26 px está a medio camino entre 24 (+2,9) y 28 (+13,7).
        assertEquals(0.0083f, Tipografia.espaciadoEm(26f), 0.0005f)
        assertEquals(0f, Tipografia.espaciadoEm(120f), 0.0001f)
        assertEquals(0.0400f, Tipografia.espaciadoEm(4f), 0.0005f)
    }

    @Test
    fun `el tamano optico sigue al tamano en pixeles, por tramos, entre Text y Display`() {
        assertEquals(17, Tipografia.tamanoOptico(14f))
        assertEquals(17, Tipografia.tamanoOptico(18f))
        assertEquals(20, Tipografia.tamanoOptico(20f))
        assertEquals(22, Tipografia.tamanoOptico(23f))
        assertEquals(24, Tipografia.tamanoOptico(25f))
        assertEquals(26, Tipografia.tamanoOptico(27f))
        assertEquals(28, Tipografia.tamanoOptico(29f))
        assertEquals(28, Tipografia.tamanoOptico(92f))
    }

    @Test
    fun `cada tamano de la escala de tvOS lleva su interlineado y los demas ninguno`() {
        assertEquals(36f, EscalaTv.interlineado(29f))
        assertEquals(30f, EscalaTv.interlineado(23f))
        assertEquals(96f, EscalaTv.interlineado(76f))
        assertEquals(null, EscalaTv.interlineado(92f))
    }
}
