package com.nuvio.tv.balootv.ficha

import org.junit.Assert.assertEquals
import org.junit.Test

class SinopsisSinAnuncioTest {

    private val buena = "Una peligrosa banda de ladrones planea el golpe de su vida en pleno centro de Madrid."

    @Test
    fun `quita el anuncio de una web antes del texto`() {
        assertEquals(buena, SinopsisSinAnuncio.limpiar("verpelis-ejemplo.net – Películas y series HD, Gratis  $buena"))
        assertEquals(buena, SinopsisSinAnuncio.limpiar("www.ejemplo.com | Ver estrenos online. $buena"))
        assertEquals(buena, SinopsisSinAnuncio.limpiar("ejemplo.to - Descargar películas completas\n$buena"))
    }

    @Test
    fun `tambien con caracteres invisibles dentro del nombre de la web, como llega de TMDB`() {
        val z = "\u200C"
        assertEquals(buena, SinopsisSinAnuncio.limpiar("v${z}e${z}rpelis-ejemplo.net – Películas y series HD, Gratis  $buena"))
    }

    @Test
    fun `sin anuncio la deja igual`() {
        assertEquals(buena, SinopsisSinAnuncio.limpiar(buena))
        assertEquals("", SinopsisSinAnuncio.limpiar(""))
    }

    @Test
    fun `una web que es parte de la historia no se toca`() {
        // Sin separador ni eslogan de anuncio: es la sinopsis de verdad.
        val documental = "Amazon.com nació en un garaje y acabó cambiando la forma de comprar de medio mundo."
        assertEquals(documental, SinopsisSinAnuncio.limpiar(documental))
        // Con separador pero sin palabras de anuncio.
        val otra = "Facebook.com: la red que lo cambió todo.  Un documental sobre sus primeros años en Harvard."
        assertEquals(otra, SinopsisSinAnuncio.limpiar(otra))
    }

    @Test
    fun `si tras el anuncio no queda texto suficiente, se deja como estaba`() {
        val corta = "ejemplo.net – Películas gratis  Ver ahora."
        assertEquals(corta, SinopsisSinAnuncio.limpiar(corta))
    }
}
