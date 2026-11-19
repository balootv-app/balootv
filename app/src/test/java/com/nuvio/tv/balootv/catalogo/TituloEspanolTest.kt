package com.nuvio.tv.balootv.catalogo

import org.junit.Assert.assertEquals
import org.junit.Test

class TituloEspanolTest {

    @Test
    fun conTituloEspanolSeUsaEse() {
        assertEquals("Rápido", TituloEspanol.elegir("Rápido", "Faster"))
    }

    @Test
    fun siTmdbSoloLoTieneEnJaponesSeUsaElIngles() {
        assertEquals("Frieren: Beyond Journey's End", TituloEspanol.elegir("葬送のフリーレン", "Frieren: Beyond Journey's End"))
    }

    @Test
    fun siElInglesTambienEstaEnJaponesSeQuedaComoViene() {
        assertEquals("葬送のフリーレン", TituloEspanol.elegir("葬送のフリーレン", "葬送のフリーレン"))
        assertEquals("葬送のフリーレン", TituloEspanol.elegir("葬送のフリーレン", null))
    }
}
