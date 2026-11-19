package com.nuvio.tv.balootv.catalogo

import com.nuvio.tv.domain.model.ContentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CatalogoBalooTVTest {

    /** TMDB falso: devuelve por plataforma lo que se le diga y apunta lo que le piden. */
    private class TmdbFalso(val porPlataforma: Map<String, List<Titulo>?> = emptyMap()) : FuenteTmdb {
        val pedidas = mutableListOf<Map<String, String>>()
        var busquedas = 0
        override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? {
            pedidas += parametros
            return porPlataforma[parametros["with_watch_providers"]] ?: emptyList()
        }
        override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? {
            busquedas++
            return if (texto.isBlank()) emptyList() else listOf(t(155, "El caballero oscuro"))
        }
    }

    companion object {
        fun t(id: Int, nombre: String = "T$id", popularidad: Double = 1.0) =
            Titulo(id, TipoFila.PELICULA, nombre, "/p$id.jpg", "/f$id.jpg", "Sinopsis", "2008-07-16", 8.5, 100, popularidad)
    }

    private fun catalogo(tmdb: FuenteTmdb) = CatalogoBalooTV(tmdb, hoy = { LocalDate.of(2026, 10, 23) })

    @Test
    fun `el addon no pone nada en el Inicio y ofrece dos busquedas`() {
        val addon = catalogo(TmdbFalso()).addon
        assertEquals(CatalogoBalooTV.BASE_URL, addon.baseUrl)
        assertTrue(addon.catalogs.none { it.showInHome })
        assertEquals(ContentType.SERIES, addon.catalogs.first { it.id == "series-populares" }.type)
        val busquedas = addon.catalogs.filter { c -> c.extra.any { it.name == "search" && it.isRequired } }
        assertEquals(listOf("movie", "series"), busquedas.map { it.apiType })
        assertTrue(CatalogoBalooTV.esPropio(addon.baseUrl))
        assertFalse(CatalogoBalooTV.esPropio("https://v3-cinemeta.strem.io"))
    }

    @Test
    fun `una fila mezcla plataformas con ids tmdb, imagenes al tamaño justo y logo de la primera`() = runBlocking {
        val tmdb = TmdbFalso(mapOf("8" to listOf(t(155, "El caballero oscuro", 50.0)), "119" to listOf(t(155, popularidad = 50.0), t(7, popularidad = 10.0))))
        val fila = catalogo(tmdb).fila("peliculas-populares", "movie", 0, emptyMap())
        assertEquals(listOf("tmdb:155", "tmdb:7"), fila.items.map { it.id })
        val caballero = fila.items.first()
        assertEquals("El caballero oscuro", caballero.name)
        assertEquals("https://image.tmdb.org/t/p/w342/p155.jpg", caballero.poster)
        assertEquals("https://image.tmdb.org/t/p/w1280/f155.jpg", caballero.background)
        assertEquals("2008", caballero.releaseInfo)
        assertEquals("Netflix", caballero.plataforma!!.nombre)
        assertEquals("https://image.tmdb.org/t/p/w92/rK1KljqmbvO9HQa1PBFLILWah72.png", caballero.plataforma!!.logo)
        assertEquals("Prime Video", fila.items[1].plataforma!!.nombre)
        assertNull(caballero.imdbRating) // la nota de TMDB nunca se hace pasar por IMDb
        assertEquals("Películas populares en España", fila.catalogName)
        assertEquals(ConfigCatalogo.plataformas.size, tmdb.pedidas.size)
        assertNull(caballero.sourceAddonBaseUrl)
    }

    @Test
    fun `con una plataforma elegida solo se le pregunta a ella`() = runBlocking {
        val tmdb = TmdbFalso(mapOf("119" to listOf(t(1))))
        val fila = catalogo(tmdb).fila("peliculas-populares", "movie", 0, mapOf("plataforma" to "119"))
        assertEquals(listOf("119"), tmdb.pedidas.map { it["with_watch_providers"] })
        assertEquals("Prime Video", fila.items.single().plataforma!!.nombre)
    }

    @Test
    fun `el anime se pide una vez, sin plataformas ni logo`() = runBlocking {
        val tmdb = object : FuenteTmdb {
            val pedidas = mutableListOf<Map<String, String>>()
            override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? { pedidas += parametros; return listOf(t(9)) }
            override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = emptyList()
        }
        val fila = catalogo(tmdb).fila("anime-populares", "series", 0, emptyMap())
        assertEquals(1, tmdb.pedidas.size)
        assertNull(tmdb.pedidas.single()["with_watch_providers"])
        assertNull(fila.items.single().plataforma)
    }

    @Test
    fun `si TMDB falla en todas la fila sale vacia y sin mas paginas`() = runBlocking {
        val todasFallan = ConfigCatalogo.plataformas.associate { it.id.toString() to null }
        val fila = catalogo(TmdbFalso(todasFallan)).fila("thriller", "movie", 0, emptyMap())
        assertTrue(fila.items.isEmpty())
        assertFalse(fila.hasMore)
    }

    @Test
    fun `paginacion pide la pagina siguiente y sigue mientras alguna plataforma llene la suya`() = runBlocking {
        val llena = (1..20).map { t(it) }
        val tmdb = TmdbFalso(mapOf("8" to llena))
        val primera = catalogo(tmdb).fila("comedia", "movie", 0, emptyMap())
        assertTrue(primera.hasMore)
        assertEquals(40, primera.nextSkip)
        tmdb.pedidas.clear()
        catalogo(tmdb).fila("comedia", "movie", 40, emptyMap())
        assertTrue(tmdb.pedidas.all { it["page"] == "2" })
        val corta = catalogo(TmdbFalso(mapOf("8" to listOf(t(1))))).fila("comedia", "movie", 0, emptyMap())
        assertFalse(corta.hasMore)
    }

    @Test
    fun `busqueda en castellano y vacia sin texto`() = runBlocking {
        val tmdb = TmdbFalso()
        val r = catalogo(tmdb).fila(CatalogoBalooTV.BUSCAR_PELICULAS, "movie", 0, mapOf("search" to "caballero"))
        assertEquals("El caballero oscuro", r.items.single().name)
        assertNull(r.items.single().plataforma)
        val vacia = catalogo(tmdb).fila(CatalogoBalooTV.BUSCAR_PELICULAS, "movie", 0, mapOf("search" to ""))
        assertTrue(vacia.items.isEmpty())
    }

    @Test
    fun `los resultados llevan idioma original y la animacion marcada, para separar el anime`() = runBlocking {
        val frieren = Titulo(209867, TipoFila.SERIE, "Frieren", "/p.jpg", null, null, "2023-09-29", 8.8, 900, 50.0, generos = listOf(16, 10765), idiomaOriginal = "ja")
        val tmdb = object : FuenteTmdb {
            override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? = emptyList()
            override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = listOf(frieren)
        }
        val r = catalogo(tmdb).fila(CatalogoBalooTV.BUSCAR_SERIES, "series", 0, mapOf("search" to "frier")).items.single()
        assertEquals("ja", r.language)
        assertEquals(listOf("Animación"), r.genres)
    }

    @Test
    fun `la busqueda pone primero lo mas popular y quita lo que no tiene cartel`() = runBlocking {
        val tmdb = object : FuenteTmdb {
            override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? = emptyList()
            override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = listOf(
                t(1, "Fri", popularidad = 0.5), t(2, "Fringe", popularidad = 60.0), t(3, "Sin cartel", popularidad = 90.0).copy(poster = null),
                t(4, "Frieren", popularidad = 80.0),
            )
        }
        val r = catalogo(tmdb).fila(CatalogoBalooTV.BUSCAR_SERIES, "series", 0, mapOf("search" to "fri")).items
        assertEquals(listOf("Frieren", "Fringe", "Fri"), r.map { it.name })
    }

    @Test
    fun `un anime sin titulo en castellano sale en ingles, no en japones`() = runBlocking {
        val tmdb = object : FuenteTmdb {
            val pedidas = mutableListOf<Map<String, String>>()
            override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? {
                pedidas += parametros
                return if (parametros["language"] == "en-US") listOf(t(1, "Frieren: Beyond Journey's End"), t(2, "Spy x Family"))
                else listOf(t(1, "葬送のフリーレン"), t(2, "Spy × Family"))
            }
            override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = emptyList()
        }
        val fila = catalogo(tmdb).fila("anime-populares", "series", 0, emptyMap())
        assertEquals(listOf("Frieren: Beyond Journey's End", "Spy × Family"), fila.items.map { it.name })
        assertEquals(2, tmdb.pedidas.size)
    }

    @Test
    fun `sin titulos en japones no se pide nada mas`() = runBlocking {
        val tmdb = object : FuenteTmdb {
            var pedidas = 0
            override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? { pedidas++; return listOf(t(2, "Spy × Family")) }
            override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = emptyList()
        }
        catalogo(tmdb).fila("anime-populares", "series", 0, emptyMap())
        assertEquals(1, tmdb.pedidas)
    }
}
