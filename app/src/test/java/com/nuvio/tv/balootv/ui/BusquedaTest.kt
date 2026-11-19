package com.nuvio.tv.balootv.ui

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

class BusquedaTest {

    @Test
    fun `el teclado tiene las letras con ñ, espacio, borrar y cambio a numeros`() {
        val letras = TecladoLineal.teclas(numeros = false)
        assertEquals("a", letras.first())
        assertEquals(listOf("ñ", "o"), letras.subList(14, 16))
        assertEquals(listOf(TecladoLineal.ESPACIO, TecladoLineal.BORRAR, TecladoLineal.NUMEROS), letras.takeLast(3))
        val numeros = TecladoLineal.teclas(numeros = true)
        assertEquals((0..9).map { it.toString() }, numeros.take(10))
        assertEquals(TecladoLineal.LETRAS, numeros.last())
    }

    @Test
    fun `pulsar teclas escribe, deja espacios sin duplicar y borra`() {
        assertEquals("fr", TecladoLineal.pulsar("f", "r"))
        assertEquals("frieren ", TecladoLineal.pulsar("frieren", TecladoLineal.ESPACIO))
        assertEquals("frieren ", TecladoLineal.pulsar("frieren ", TecladoLineal.ESPACIO))
        assertEquals("", TecladoLineal.pulsar("", TecladoLineal.ESPACIO))
        assertEquals("frier", TecladoLineal.pulsar("friere", TecladoLineal.BORRAR))
        assertEquals("", TecladoLineal.pulsar("", TecladoLineal.BORRAR))
        assertEquals("abc", TecladoLineal.pulsar("abc", TecladoLineal.NUMEROS))
    }

    private fun m(id: String, tipo: ContentType, generos: List<String> = emptyList(), idioma: String? = null, nombre: String = id) = MetaPreview(
        id = id, type = tipo, name = nombre, poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = generos, language = idioma,
    )

    @Test
    fun `los resultados se agrupan en peliculas, series y anime, y los grupos vacios no salen`() {
        val grupos = Resultados.agrupar(
            peliculas = listOf(m("p1", ContentType.MOVIE), m("pa", ContentType.MOVIE, listOf("Animación"), "ja")),
            series = listOf(m("s1", ContentType.SERIES), m("sa", ContentType.SERIES, listOf("Animación"), "ja"), m("sw", ContentType.SERIES, listOf("Animación"), "en")),
        )
        assertEquals(listOf("Películas", "Series", "Anime"), grupos.map { it.first })
        assertEquals(listOf("p1"), grupos[0].second.map { it.id })
        assertEquals(listOf("s1", "sw"), grupos[1].second.map { it.id })
        assertEquals(listOf("sa", "pa"), grupos[2].second.map { it.id })
        assertEquals(listOf("Películas"), Resultados.agrupar(listOf(m("p1", ContentType.MOVIE)), emptyList()).map { it.first })
    }

    @Test
    fun `sube el grupo cuyo titulo empieza por lo escrito`() {
        val grupos = Resultados.agrupar(
            peliculas = listOf(m("p1", ContentType.MOVIE, nombre = "Doctor Strange"), m("p2", ContentType.MOVIE, nombre = "Flow")),
            series = listOf(m("s1", ContentType.SERIES, nombre = "Stranger Things")),
            texto = "stra",
        )
        assertEquals(listOf("Series", "Películas"), grupos.map { it.first })
        val anime = Resultados.agrupar(
            peliculas = listOf(m("p1", ContentType.MOVIE, nombre = "Frío")),
            series = listOf(m("sa", ContentType.SERIES, listOf("Animación"), "ja", nombre = "Frieren")),
            texto = "Frie",
        )
        assertEquals(listOf("Anime", "Películas"), anime.map { it.first })
    }
}
