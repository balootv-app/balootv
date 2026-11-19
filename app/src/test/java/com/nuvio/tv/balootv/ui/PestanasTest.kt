package com.nuvio.tv.balootv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PestanasTest {

    @Test
    fun `cinco pestanas en el orden aprobado`() {
        assertEquals(listOf("Inicio", "Películas", "Series", "Anime", "Mi lista"), Pestana.entries.map { it.titulo })
    }

    @Test
    fun `cada ruta raiz tiene su pestana y las demas ninguna`() {
        Pestana.entries.forEach { assertEquals(it, Pestanas.deRuta(it.ruta)) }
        assertNull(Pestanas.deRuta("detail/tt1/movie"))
        assertNull(Pestanas.deRuta(null))
        assertEquals(Pestana.MI_LISTA, Pestanas.deRuta("library"))
    }

    @Test
    fun `atras en una portada sube a la barra y con la barra enfocada sale`() {
        assertEquals(AccionAtras.SUBIR_A_BARRA, Pestanas.alPulsarAtras(enRaiz = true, focoEnBarra = false))
        assertEquals(AccionAtras.SALIR, Pestanas.alPulsarAtras(enRaiz = true, focoEnBarra = true))
        assertEquals(AccionAtras.NADA, Pestanas.alPulsarAtras(enRaiz = false, focoEnBarra = false))
    }
}
