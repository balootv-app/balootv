package com.nuvio.tv.balootv.catalogo

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClienteTmdbCatalogoTest {

    private val server = MockWebServer().apply { start() }
    private val cliente = ClienteTmdbCatalogo(OkHttpClient(), "clave-de-prueba", server.url("/3").toString().trimEnd('/'))

    @After
    fun cerrar() = server.shutdown()

    @Test
    fun `descubrir llama a discover tv con los parametros y traduce la serie`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"page":1,"results":[{"id":71446,"name":"La casa de papel","poster_path":"/p.jpg","backdrop_path":"/f.jpg",
                "overview":"Atraco","first_air_date":"2017-05-02","vote_average":8.2,"vote_count":19000,"popularity":120.5}]}"""
            )
        )
        val r = cliente.descubrir(TipoFila.SERIE, mapOf("with_watch_providers" to "8", "language" to "es-ES"))!!
        val peticion = server.takeRequest()
        assertEquals("/3/discover/tv", peticion.requestUrl!!.encodedPath)
        assertEquals("8", peticion.requestUrl!!.queryParameter("with_watch_providers"))
        assertEquals("clave-de-prueba", peticion.requestUrl!!.queryParameter("api_key"))
        val serie = r.single()
        assertEquals("La casa de papel", serie.nombre)
        assertEquals("2017-05-02", serie.fecha)
        assertEquals(8.2, serie.nota!!, 0.001)
    }

    @Test
    fun `un error de TMDB da null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(cliente.descubrir(TipoFila.PELICULA, emptyMap()))
    }

    @Test
    fun `buscar usa search movie en castellano`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[{"id":155,"title":"El caballero oscuro"}]}"""))
        val r = cliente.buscar(TipoFila.PELICULA, "caballero oscuro", 1)!!
        val url = server.takeRequest().requestUrl!!
        assertEquals("/3/search/movie", url.encodedPath)
        assertEquals("caballero oscuro", url.queryParameter("query"))
        assertEquals("es-ES", url.queryParameter("language"))
        assertEquals("El caballero oscuro", r.single().nombre)
    }

    @Test
    fun `buscar con texto en blanco no pregunta nada`() = runBlocking {
        assertTrue(cliente.buscar(TipoFila.SERIE, "  ", 1)!!.isEmpty())
        assertEquals(0, server.requestCount)
    }
}
