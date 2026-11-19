package com.nuvio.tv.balootv.ficha

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DisponibleEnTest {

    private val respuestas = mutableMapOf<String, MockResponse>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                respuestas[request.requestUrl!!.encodedPath] ?: MockResponse().setResponseCode(404)
        }
        start()
    }
    private val disponible = DisponibleEn(OkHttpClient(), "clave", server.url("/tmdb").toString().trimEnd('/'))

    @After
    fun cerrar() = server.shutdown()

    @Test
    fun `plataformas de Espana de la lista, en su orden`() = runBlocking {
        respuestas["/tmdb/find/tt0088763"] = MockResponse().setBody("""{"movie_results":[{"id":105}],"tv_results":[]}""")
        respuestas["/tmdb/movie/105/watch/providers"] = MockResponse().setBody(
            """{"results":{"ES":{"flatrate":[{"provider_id":9999,"provider_name":"Otra"},{"provider_id":119},{"provider_id":8}]},
               "US":{"flatrate":[{"provider_id":337}]}}}"""
        )
        assertEquals(listOf("Netflix", "Prime Video"), disponible.de("tt0088763", esSerie = false).map { it.nombre })
    }

    @Test
    fun `serie por id de TMDB`() = runBlocking {
        respuestas["/tmdb/tv/100088/watch/providers"] = MockResponse().setBody("""{"results":{"ES":{"flatrate":[{"provider_id":1899}]}}}""")
        assertEquals(listOf("HBO Max"), disponible.de("tmdb:100088", esSerie = true).map { it.nombre })
    }

    @Test
    fun `sin Espana o con error no hay plataformas`() = runBlocking {
        respuestas["/tmdb/tv/1/watch/providers"] = MockResponse().setBody("""{"results":{"US":{"flatrate":[{"provider_id":8}]}}}""")
        assertEquals(emptyList<Any>(), disponible.de("tmdb:1", esSerie = true))
        respuestas["/tmdb/tv/2/watch/providers"] = MockResponse().setResponseCode(500)
        assertEquals(emptyList<Any>(), disponible.de("tmdb:2", esSerie = true))
        assertEquals(emptyList<Any>(), disponible.de("loquesea", esSerie = true))
    }
}
