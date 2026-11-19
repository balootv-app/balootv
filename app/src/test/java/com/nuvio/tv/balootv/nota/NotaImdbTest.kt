package com.nuvio.tv.balootv.nota

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotaImdbTest {

    /** TMDB y Cinemeta falsos en el mismo servidor, con respuestas por ruta. */
    private val respuestas = mutableMapOf<String, MockResponse>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                respuestas[request.requestUrl!!.encodedPath] ?: MockResponse().setResponseCode(404)
        }
        start()
    }
    private val nota = NotaImdb(OkHttpClient(), "clave", server.url("/tmdb").toString().trimEnd('/'), server.url("/cinemeta").toString().trimEnd('/'))

    @After
    fun cerrar() = server.shutdown()

    @Test
    fun `de tmdb a IMDb pasando por los ids externos y Cinemeta`() = runBlocking {
        respuestas["/tmdb/movie/155/external_ids"] = MockResponse().setBody("""{"id":155,"imdb_id":"tt0468569"}""")
        respuestas["/cinemeta/meta/movie/tt0468569.json"] = MockResponse().setBody("""{"meta":{"imdb_id":"tt0468569","imdbRating":"9.1"}}""")
        assertEquals(9.1f, nota.de("tmdb:155", "movie")!!, 0.001f)
    }

    @Test
    fun `una serie usa tv en TMDB y series en Cinemeta`() = runBlocking {
        respuestas["/tmdb/tv/209867/external_ids"] = MockResponse().setBody("""{"imdb_id":"tt22248376"}""")
        respuestas["/cinemeta/meta/series/tt22248376.json"] = MockResponse().setBody("""{"meta":{"imdbRating":"8.9"}}""")
        assertEquals(8.9f, nota.de("tmdb:209867", "series")!!, 0.001f)
    }

    @Test
    fun `un id de IMDb va directo a Cinemeta`() = runBlocking {
        respuestas["/cinemeta/meta/movie/tt0088763.json"] = MockResponse().setBody("""{"meta":{"imdbRating":"8.5"}}""")
        assertEquals(8.5f, nota.de("tt0088763", "movie")!!, 0.001f)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sin id de IMDb, sin nota o con error no hay nota`() = runBlocking {
        respuestas["/tmdb/movie/1/external_ids"] = MockResponse().setBody("""{"imdb_id":null}""")
        assertNull(nota.de("tmdb:1", "movie"))
        respuestas["/cinemeta/meta/movie/tt1.json"] = MockResponse().setBody("""{"meta":{}}""")
        assertNull(nota.de("tt1", "movie"))
        respuestas["/cinemeta/meta/movie/tt2.json"] = MockResponse().setResponseCode(500)
        assertNull(nota.de("tt2", "movie"))
        assertNull(nota.de("loquesea", "movie"))
    }

    @Test
    fun `la segunda vez no vuelve a preguntar`() = runBlocking {
        respuestas["/cinemeta/meta/movie/tt0088763.json"] = MockResponse().setBody("""{"meta":{"imdbRating":"8.5"}}""")
        nota.de("tt0088763", "movie")
        nota.de("tt0088763", "movie")
        assertEquals(1, server.requestCount)
    }
}
