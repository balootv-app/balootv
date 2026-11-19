package com.nuvio.tv.balootv.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionPlataformasTest {

    private val netflix = ConfigCatalogo.plataformas[0]
    private val prime = ConfigCatalogo.plataformas[1]

    private fun real(nombre: String): List<Titulo> {
        val cuerpo = javaClass.getResource("/balootv/catalogo/$nombre.json")!!.readText()
        return TmdbCatalogoJson.titulos(cuerpo, TipoFila.PELICULA)
    }

    private fun t(id: Int, popularidad: Double, fecha: String = "2026-01-01", nota: Double = 7.0) =
        Titulo(id, TipoFila.PELICULA, "T$id", null, null, null, fecha, nota, 100, popularidad)

    @Test
    fun `lee las respuestas reales de discover con titulo en castellano`() {
        val netflixReal = real("populares_netflix")
        assertEquals(20, netflixReal.size)
        val caballero = real("populares_prime").first { it.tmdbId == 155 }
        assertEquals("El caballero oscuro", caballero.nombre)
        assertTrue(caballero.poster!!.startsWith("/"))
        assertEquals("2008-07-16", caballero.fecha)
        assertTrue(53 in caballero.generos) // Thriller, según TMDB
        assertEquals("en", caballero.idiomaOriginal)
    }

    @Test
    fun `un titulo en dos plataformas sale una vez y con la primera de la lista`() {
        val r = FusionPlataformas.fusionar(listOf(netflix to real("populares_netflix"), prime to real("populares_prime")), Orden.POPULARIDAD)
        assertEquals(1, r.count { it.titulo.tmdbId == 155 })
        assertEquals(netflix, r.first { it.titulo.tmdbId == 155 }.plataforma)
        assertEquals(37, r.size) // 20 + 20 - 3 comunes
        assertTrue(r.any { it.plataforma == prime })
    }

    @Test
    fun `ordena por popularidad, fecha o nota y corta en el maximo`() {
        val a = listOf(t(1, 10.0, "2026-01-01", 6.0), t(2, 30.0, "2025-01-01", 9.0))
        val b = listOf(t(3, 20.0, "2026-06-01", 7.0))
        val entrada = listOf(netflix to a, prime to b)
        assertEquals(listOf(2, 3, 1), FusionPlataformas.fusionar(entrada, Orden.POPULARIDAD).map { it.titulo.tmdbId })
        assertEquals(listOf(3, 1, 2), FusionPlataformas.fusionar(entrada, Orden.FECHA).map { it.titulo.tmdbId })
        assertEquals(listOf(2, 3, 1), FusionPlataformas.fusionar(entrada, Orden.VALORACION).map { it.titulo.tmdbId })
        assertEquals(2, FusionPlataformas.fusionar(entrada, Orden.POPULARIDAD, maximo = 2).size)
    }

    @Test
    fun `una plataforma vacia no estorba`() {
        val r = FusionPlataformas.fusionar(listOf(netflix to emptyList(), prime to listOf(t(5, 1.0))), Orden.POPULARIDAD)
        assertEquals(listOf(prime), r.map { it.plataforma })
    }
}
