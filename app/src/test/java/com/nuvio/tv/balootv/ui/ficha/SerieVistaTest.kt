package com.nuvio.tv.balootv.ui.ficha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerieVistaTest {
    private val serie = listOf(1 to 1, 1 to 2, 2 to 1)

    @Test
    fun conTodoVistoEsEntera() {
        assertTrue(SerieVista.entera(serie, setOf(1 to 1, 1 to 2, 2 to 1)))
    }

    @Test
    fun conUnEpisodioSinVerNoEsEntera() {
        assertFalse(SerieVista.entera(serie, setOf(1 to 1, 2 to 1)))
    }

    @Test
    fun sinProgresoNoEsEntera() {
        assertFalse(SerieVista.entera(serie, emptySet()))
    }

    @Test
    fun losEspecialesSinVerNoImpiden() {
        assertTrue(SerieVista.entera(listOf(0 to 1) + serie, setOf(1 to 1, 1 to 2, 2 to 1)))
    }

    @Test
    fun sinEpisodiosNoEsEntera() {
        assertFalse(SerieVista.entera(emptyList(), setOf(1 to 1)))
    }
}
