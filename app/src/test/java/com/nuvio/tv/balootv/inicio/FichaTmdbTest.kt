package com.nuvio.tv.balootv.inicio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FichaTmdbTest {

    private val serie = """
        {"id": 94605, "name": "Arcane", "first_air_date": "2021-11-06", "poster_path": "/p.jpg", "backdrop_path": "/f.jpg",
         "overview": "Una serie.", "seasons": [
           {"season_number": 0, "air_date": "2021-01-01", "episode_count": 3},
           {"season_number": 1, "air_date": "2021-11-06", "episode_count": 9},
           {"season_number": 2, "air_date": "2024-12-01", "episode_count": 9},
           {"season_number": 3, "air_date": null, "episode_count": 0}],
         "next_episode_to_air": {"season_number": 3, "episode_number": 1, "air_date": "2026-11-20"}}
    """.trimIndent()

    @Test
    fun `una serie con su ficha en castellano y las imagenes completas`() {
        val ficha = FichaTmdb.de(serie, Tipo.SERIE)!!
        assertEquals(Titulo(Tipo.SERIE, Ids(94605), "Arcane", 2021, "https://image.tmdb.org/t/p/w342/p.jpg",
            "https://image.tmdb.org/t/p/w1280/f.jpg", "Una serie."), ficha.titulo)
    }

    @Test
    fun `los generos de TMDB en castellano y en su orden, para el salvapantallas`() {
        val conGeneros = serie.replace("\"overview\"", "\"genres\": [{\"id\": 16, \"name\": \"Animación\"}, {\"id\": 10765, \"name\": \"Sci-Fi & Fantasy\"}, {\"id\": 18, \"name\": \" \"}], \"overview\"")
        assertEquals(listOf("Animación", "Sci-Fi & Fantasy"), FichaTmdb.de(conGeneros, Tipo.SERIE)!!.titulo.generos)
        assertEquals(emptyList<String>(), FichaTmdb.de(serie, Tipo.SERIE)!!.titulo.generos)
    }

    @Test
    fun `el estreno de cada temporada sin la 0, y el proximo episodio 1 manda en la suya`() {
        val ficha = FichaTmdb.de(serie, Tipo.SERIE)!!
        assertEquals(mapOf(1 to LocalDate.parse("2021-11-06"), 2 to LocalDate.parse("2024-12-01"), 3 to LocalDate.parse("2026-11-20")), ficha.estrenos)
        assertEquals(mapOf(1 to 9, 2 to 9, 3 to 0), ficha.episodiosPorTemporada)
    }

    @Test
    fun `un proximo episodio que no es el 1 no cambia la fecha de su temporada`() {
        val enMedio = serie.replace("\"episode_number\": 1", "\"episode_number\": 4")
        assertEquals(mapOf(1 to LocalDate.parse("2021-11-06"), 2 to LocalDate.parse("2024-12-01")), FichaTmdb.de(enMedio, Tipo.SERIE)!!.estrenos)
    }

    @Test
    fun `una pelicula, y sin imagenes ni sinopsis quedan vacias`() {
        val ficha = FichaTmdb.de("""{"id": 7, "title": "Torrente", "release_date": "", "poster_path": null, "overview": ""}""", Tipo.PELICULA)!!
        assertEquals(Titulo(Tipo.PELICULA, Ids(7), "Torrente"), ficha.titulo)
    }

    private fun conLogos(vararg logos: Pair<String?, String>) = serie.replace("\"overview\": \"Una serie.\",",
        "\"overview\": \"Una serie.\", \"images\": {\"logos\": [" +
            logos.joinToString { (idioma, ruta) -> "{\"iso_639_1\": ${idioma?.let { "\"$it\"" }}, \"file_path\": \"$ruta\"}" } + "]},")

    @Test
    fun `el logo en castellano antes que en ingles y que sin idioma, como en la ficha`() {
        val ficha = FichaTmdb.de(conLogos(null to "/n.png", "en" to "/en.png", "es" to "/es.png"), Tipo.SERIE)!!
        assertEquals("https://image.tmdb.org/t/p/w500/es.png", ficha.titulo.logo)
        assertEquals("https://image.tmdb.org/t/p/w500/en.png", FichaTmdb.de(conLogos(null to "/n.png", "en" to "/en.png"), Tipo.SERIE)!!.titulo.logo)
        assertEquals("https://image.tmdb.org/t/p/w500/n.png", FichaTmdb.de(conLogos("fr" to "/fr.png", null to "/n.png"), Tipo.SERIE)!!.titulo.logo)
        assertNull(FichaTmdb.de(serie, Tipo.SERIE)!!.titulo.logo)
    }

    @Test
    fun `una respuesta sin id no es una ficha`() {
        assertNull(FichaTmdb.de("""{"status_code": 34}""", Tipo.PELICULA))
    }
}
