package com.nuvio.tv.balootv.fuentes

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TitulosTmdbRepositorioTest {

    private val servidor = MockWebServer()

    @Before fun arrancar() = servidor.start()
    @After fun parar() = servidor.close()

    private fun repo() = TitulosTmdbRepositorio(OkHttpClient(), apiKey = "k", base = servidor.url("/3").toString().trimEnd('/'))

    @Test
    fun `pelicula trae titulos de Espana y Mexico, original y duracion`() = runTest {
        servidor.enqueue(MockResponse.Builder().body("""{"movie_results":[{"id":105}],"tv_results":[]}""").build())
        servidor.enqueue(MockResponse.Builder().body(
            """{"original_title":"Back to the Future","runtime":116,"translations":{"translations":[
               {"iso_639_1":"es","iso_3166_1":"ES","data":{"title":"Regreso al futuro"}},
               {"iso_639_1":"es","iso_3166_1":"MX","data":{"title":"Volver al Futuro"}}]}}"""
        ).build())
        val p = repo().peticion("tt0088763", esSerie = false, anio = 1985, temporada = null, episodio = null)
        assertEquals(TitulosTmdb(es = "Regreso al futuro", mx = "Volver al Futuro", original = "Back to the Future"), p.titulos)
        assertEquals(116, p.duracionMin)
        assertEquals(1985, p.anio)
    }

    @Test
    fun `serie usa name y la duracion del episodio`() = runTest {
        servidor.enqueue(MockResponse.Builder().body("""{"movie_results":[],"tv_results":[{"id":100088}]}""").build())
        servidor.enqueue(MockResponse.Builder().body(
            """{"original_name":"The Last of Us","episode_run_time":[57],"translations":{"translations":[
               {"iso_639_1":"es","iso_3166_1":"ES","data":{"name":""}}]}}"""
        ).build())
        val p = repo().peticion("tt3581920", esSerie = true, anio = 2023, temporada = 2, episodio = 1)
        assertEquals("The Last of Us", p.titulos.original)
        assertNull("un título vacío cuenta como ausente", p.titulos.es)
        assertEquals(57, p.duracionMin)
        assertEquals(2, p.temporada)
    }

    @Test
    fun `si TMDB falla devuelve la peticion sin titulos`() = runTest {
        servidor.enqueue(MockResponse.Builder().code(500).build())
        val p = repo().peticion("tt0000001", esSerie = false, anio = 2000, temporada = null, episodio = null)
        assertEquals(TitulosTmdb(), p.titulos)
        assertNull(p.duracionMin)
    }

    @Test
    fun `una serie de animacion japonesa es anime`() = runTest {
        servidor.enqueue(MockResponse.Builder().body("""{"movie_results":[],"tv_results":[{"id":209867}]}""").build())
        servidor.enqueue(MockResponse.Builder().body(
            """{"original_name":"葬送のフリーレン","original_language":"ja","genres":[{"id":16,"name":"Animación"},{"id":10765}],
               "episode_run_time":[24],"translations":{"translations":[]}}"""
        ).build())
        val p = repo().peticion("tt22248376", esSerie = true, anio = null, temporada = 1, episodio = 1)
        assertEquals(true, p.anime)
    }

    // --- Encargo 002 ---

    private fun hoy() = java.time.LocalDate.of(2026, 11, 19)

    private fun repoConDespachador(rutas: Map<String, String>) = TitulosTmdbRepositorio(
        OkHttpClient(), apiKey = "k", base = servidor.url("/3").toString().trimEnd('/'), hoy = ::hoy,
    ).also {
        servidor.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val ruta = request.url.encodedPath
                val cuerpo = rutas.entries.firstOrNull { ruta.endsWith(it.key) }?.value
                return if (cuerpo == null) MockResponse.Builder().code(404).build() else MockResponse.Builder().body(cuerpo).build()
            }
        }
    }

    private val odisea = """{"title":"The Odyssey","original_title":"The Odyssey","release_date":"2026-07-15","runtime":173,
        "translations":{"translations":[{"iso_639_1":"es","iso_3166_1":"ES","data":{"title":"La Odisea"}}]},
        "release_dates":{"results":[{"iso_3166_1":"ES","release_dates":[{"type":3,"release_date":"2026-07-17T00:00:00.000Z"}]}]}}"""

    @Test
    fun `un id tmdb va directo a la ficha y no deja la validacion a ciegas`() = runTest {
        val p = repoConDespachador(mapOf("/movie/1368337" to odisea)).peticion("tmdb:1368337", esSerie = false, anio = 2026, temporada = null, episodio = null)
        assertEquals("La Odisea", p.titulos.es)
        assertEquals("The Odyssey", p.titulos.en)
        assertEquals(173, p.duracionMin)
    }

    @Test
    fun `la fecha de salida en casa de TMDB decide si ya ha salido`() = runTest {
        val conDigital = odisea.replace("""{"type":3,"release_date":"2026-07-17T00:00:00.000Z"}""",
            """{"type":3,"release_date":"2026-07-17T00:00:00.000Z"},{"type":4,"release_date":"2026-11-17T00:00:00.000Z"}""")
        val p = repoConDespachador(mapOf("/movie/1368337" to conDigital)).peticion("tmdb:1368337", esSerie = false, anio = 2026, temporada = null, episodio = null)
        assertEquals(true, p.estrenadaEnCasa)
        val conDigitalFutura = conDigital.replace("2026-11-17", "2026-12-17")
        val q = repoConDespachador(mapOf("/movie/1368337" to conDigitalFutura)).peticion("tmdb:1368337", esSerie = false, anio = 2026, temporada = null, episodio = null)
        assertEquals(false, q.estrenadaEnCasa)
    }

    @Test
    fun `otra pelicula conocida con el mismo titulo y otro anio marca homonimos`() = runTest {
        val halloween = """{"title":"Halloween","original_title":"Halloween","release_date":"2018-10-18","runtime":106,
            "translations":{"translations":[{"iso_639_1":"es","iso_3166_1":"ES","data":{"title":"La noche de Halloween"}}]}}"""
        val busqueda = """{"results":[{"id":424139,"title":"La noche de Halloween","original_title":"Halloween","release_date":"2018-10-18","vote_count":5000},
            {"id":948,"title":"La noche de Halloween","original_title":"Halloween","release_date":"1978-10-25","vote_count":6000}]}"""
        val p = repoConDespachador(mapOf("/movie/424139" to halloween, "/search/movie" to busqueda))
            .peticion("tmdb:424139", esSerie = false, anio = 2018, temporada = null, episodio = null)
        assertEquals(true, p.homonimos)
        val sinOtra = busqueda.replace("1978-10-25", "2019-01-01")
        val q = repoConDespachador(mapOf("/movie/424139" to halloween, "/search/movie" to sinOtra))
            .peticion("tmdb:424139", esSerie = false, anio = 2018, temporada = null, episodio = null)
        assertEquals(false, q.homonimos)
    }

    @Test
    fun `si la busqueda de homonimos no contesta, ante la duda los hay`() = runTest {
        val halloween = """{"title":"Halloween","original_title":"Halloween","release_date":"2018-10-18","translations":{"translations":[]}}"""
        val p = repoConDespachador(mapOf("/movie/424139" to halloween))
            .peticion("tmdb:424139", esSerie = false, anio = 2018, temporada = null, episodio = null)
        assertEquals(true, p.homonimos)
    }

    @Test
    fun `una pelicula poco conocida con el mismo titulo no cuenta como homonimo`() = runTest {
        val halloween = """{"title":"Halloween","original_title":"Halloween","release_date":"2018-10-18","translations":{"translations":[]}}"""
        val busqueda = """{"results":[{"id":1,"title":"Halloween","original_title":"Halloween","release_date":"2001-01-01","vote_count":3}]}"""
        val p = repoConDespachador(mapOf("/movie/424139" to halloween, "/search/movie" to busqueda))
            .peticion("tmdb:424139", esSerie = false, anio = 2018, temporada = null, episodio = null)
        assertEquals(false, p.homonimos)
    }

    @Test
    fun `el id de la ficha se reduce a tt o tmdb sin temporada ni episodio`() {
        assertEquals("tt0903747", TitulosTmdbRepositorio.idDe("tt0903747:2:3"))
        assertEquals("tmdb:1396", TitulosTmdbRepositorio.idDe("tmdb:1396:2:3"))
        assertEquals("tmdb:603", TitulosTmdbRepositorio.idDe("tmdb:603"))
    }

    @Test
    fun `estreno en casa segun las fechas`() {
        val hoy = hoy()
        assertEquals(true, EstrenoEnCasa.yaSalio(listOf("2026-11-17"), "2026-07-06", hoy))
        assertEquals(false, EstrenoEnCasa.yaSalio(emptyList(), "2026-10-01", hoy))
        assertEquals(false, EstrenoEnCasa.yaSalio(listOf("2026-11-25"), "2026-03-01", hoy))
        // Reedición en 4K de una película de 1980: la fecha futura de físico no la hace «aún en cines».
        assertEquals(true, EstrenoEnCasa.yaSalio(listOf("2026-12-15"), "1980-05-01", hoy))
        assertEquals(false, EstrenoEnCasa.yaSalio(listOf("2026-12-01"), null, hoy))
        assertEquals(true, EstrenoEnCasa.yaSalio(emptyList(), "2026-03-13", hoy))
        assertEquals(true, EstrenoEnCasa.yaSalio(emptyList(), "2026-07-22", hoy))
        assertEquals(null, EstrenoEnCasa.yaSalio(emptyList(), null, hoy))
    }
}
