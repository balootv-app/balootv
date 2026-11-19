package com.nuvio.tv.balootv.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConfigCatalogoTest {

    private val hoy = LocalDate.of(2026, 10, 23)
    private val netflix = ConfigCatalogo.plataformas.first()
    private fun fila(id: String) = ConfigCatalogo.filas.first { it.id == id }

    @Test
    fun `plataformas en orden de preferencia con los ids de TMDB para España`() {
        assertEquals(listOf(8, 119, 337, 1899, 350, 2241, 1773, 63), ConfigCatalogo.plataformas.map { it.id })
    }

    @Test
    fun `filas de cada portada en su orden y con id unico`() {
        val ids = ConfigCatalogo.filas.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(
            listOf("novedades-peliculas", "peliculas-populares", "cine-espanol", "thriller", "comedia", "animacion", "ciencia-ficcion", "mejor-valoradas"),
            ConfigCatalogo.filas(Portada.PELICULAS).map { it.id },
        )
        assertEquals(
            listOf("series-en-emision", "series-populares", "series-espanolas", "series-mejor-valoradas", "series-comedia", "series-crimen"),
            ConfigCatalogo.filas(Portada.SERIES).map { it.id },
        )
        assertEquals(
            listOf("anime-en-emision", "anime-populares", "anime-mejor-valorados", "anime-peliculas"),
            ConfigCatalogo.filas(Portada.ANIME).map { it.id },
        )
    }

    @Test
    fun `el anime es animacion japonesa sin filtro de plataforma`() {
        val p = ConfigCatalogo.parametros(fila("anime-populares"), null, 1, hoy)
        assertEquals("16", p["with_genres"])
        assertEquals("ja", p["with_original_language"])
        assertEquals("300", p["vote_count.gte"]) // fuera las series infantiles antiguas con pocos votos
        assertFalse(p.containsKey("with_watch_providers"))
        assertFalse(p.containsKey("watch_region"))
        assertEquals(TipoFila.SERIE, fila("anime-populares").tipo)
        assertEquals(TipoFila.PELICULA, fila("anime-peliculas").tipo)
        assertTrue(ConfigCatalogo.filas(Portada.ANIME).all { it.sinPlataformas })
    }

    @Test
    fun `series populares pide solo suscripcion en España y sin tertulias ni realities`() {
        val p = ConfigCatalogo.parametros(fila("series-populares"), netflix, 2, hoy)
        assertEquals("8", p["with_watch_providers"])
        assertEquals("ES", p["watch_region"])
        assertEquals("flatrate", p["with_watch_monetization_types"])
        assertEquals("es-ES", p["language"])
        assertEquals("popularity.desc", p["sort_by"])
        assertEquals("10763,10764,10767", p["without_genres"])
        assertEquals("2", p["page"])
    }

    @Test
    fun `novedades en peliculas por fecha de estreno hasta hoy y con algo de votos`() {
        val p = ConfigCatalogo.parametros(fila("novedades-peliculas"), netflix, 1, hoy)
        assertEquals("primary_release_date.desc", p["sort_by"])
        assertEquals("2026-10-23", p["primary_release_date.lte"])
        assertEquals("20", p["vote_count.gte"])
        assertFalse(p.containsKey("without_genres"))
    }

    @Test
    fun `series en emision son las que han emitido algo en los ultimos 30 dias`() {
        val p = ConfigCatalogo.parametros(fila("series-en-emision"), netflix, 1, hoy)
        assertEquals("2026-09-23", p["air_date.gte"])
        assertEquals("2026-10-23", p["air_date.lte"])
    }

    @Test
    fun `cine español filtra por pais de origen`() {
        assertEquals("ES", ConfigCatalogo.parametros(fila("cine-espanol"), netflix, 1, hoy)["with_origin_country"])
        assertEquals(TipoFila.SERIE, fila("series-espanolas").tipo)
    }
}
