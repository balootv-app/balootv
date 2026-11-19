package com.nuvio.tv.balootv.catalogo

import java.time.LocalDate

/** Plataforma de streaming en España, con su id y la ruta de su logo en TMDB. */
data class Plataforma(val id: Int, val nombre: String, val logo: String)

enum class TipoFila { PELICULA, SERIE }

enum class Orden { POPULARIDAD, FECHA, VALORACION }

/** Pantallas que muestran filas del catálogo (F4 §1). */
enum class Portada { PELICULAS, SERIES, ANIME }

/**
 * Una fila de catálogo. `ventanaDias`: solo títulos con algo estrenado en esos últimos días (en emisión).
 * `extra`: parámetros de `discover` propios de la fila (género, país, votos mínimos…).
 * `portadas`: en qué pantallas sale, en el orden de [ConfigCatalogo.filas].
 * `sinPlataformas`: se pide sin filtrar por plataforma (el anime vive sobre todo en Crunchyroll, que no está).
 */
data class Fila(
    val id: String,
    val titulo: String,
    val tipo: TipoFila,
    val orden: Orden,
    val extra: Map<String, String> = emptyMap(),
    val ventanaDias: Int? = null,
    val portadas: Set<Portada> = emptySet(),
    val sinPlataformas: Boolean = false,
)

/**
 * Catálogo de BalooTV como código (ARQUITECTURA §4): plataformas en orden de preferencia para el logo y
 * filas por intención, todas mezclando plataformas y en castellano.
 */
object ConfigCatalogo {

    val plataformas = listOf(
        Plataforma(8, "Netflix", "/rK1KljqmbvO9HQa1PBFLILWah72.png"),
        Plataforma(119, "Prime Video", "/gMZdpavHmxFNnLpMHwVxfqeux2g.png"),
        Plataforma(337, "Disney+", "/5eZ872CghnHFLB1j8grszbrx0dx.png"),
        Plataforma(1899, "HBO Max", "/skypuy7SXuugIQeYg0IglmzoKaS.png"),
        Plataforma(350, "Apple TV+", "/9icYBfYFcwgCbky5VdGUIKJ4C5i.png"),
        Plataforma(2241, "Movistar Plus+", "/53JKjrhdG4z8DQ4pfdbHoAgPWeO.png"),
        Plataforma(1773, "SkyShowtime", "/ube1pVpP3FklRkeyQjlDkkC3tL2.png"),
        Plataforma(63, "Filmin", "/pQ3G3Ts5SWjy3Ds4Y6QZWoZFAKJ.png"),
    )

    private val conVotos = mapOf("vote_count.gte" to "200")
    private val anime = mapOf("with_genres" to "16", "with_original_language" to "ja")
    private val P = Portada.PELICULAS
    private val S = Portada.SERIES
    private val A = Portada.ANIME

    val filas = listOf(
        Fila("novedades-peliculas", "Novedades en películas", TipoFila.PELICULA, Orden.FECHA, mapOf("vote_count.gte" to "20"), portadas = setOf(P)),
        Fila("series-en-emision", "Series en emisión", TipoFila.SERIE, Orden.POPULARIDAD, ventanaDias = 30, portadas = setOf(S)),
        Fila("peliculas-populares", "Películas populares en España", TipoFila.PELICULA, Orden.POPULARIDAD, portadas = setOf(P)),
        Fila("series-populares", "Series populares en España", TipoFila.SERIE, Orden.POPULARIDAD, portadas = setOf(S)),
        Fila("cine-espanol", "Cine español", TipoFila.PELICULA, Orden.POPULARIDAD, mapOf("with_origin_country" to "ES"), portadas = setOf(P)),
        Fila("series-espanolas", "Series españolas", TipoFila.SERIE, Orden.POPULARIDAD, mapOf("with_origin_country" to "ES"), portadas = setOf(S)),
        Fila("thriller", "Thriller", TipoFila.PELICULA, Orden.POPULARIDAD, conVotos + ("with_genres" to "53"), portadas = setOf(P)),
        Fila("comedia", "Comedia", TipoFila.PELICULA, Orden.POPULARIDAD, conVotos + ("with_genres" to "35"), portadas = setOf(P)),
        Fila("animacion", "Animación", TipoFila.PELICULA, Orden.POPULARIDAD, conVotos + ("with_genres" to "16"), portadas = setOf(P)),
        Fila("ciencia-ficcion", "Ciencia ficción", TipoFila.PELICULA, Orden.POPULARIDAD, conVotos + ("with_genres" to "878"), portadas = setOf(P)),
        Fila("mejor-valoradas", "Mejor valoradas", TipoFila.PELICULA, Orden.VALORACION, mapOf("vote_count.gte" to "2000"), portadas = setOf(P)),
        Fila("series-mejor-valoradas", "Series mejor valoradas", TipoFila.SERIE, Orden.VALORACION, mapOf("vote_count.gte" to "1000"), portadas = setOf(S)),
        Fila("series-comedia", "Comedia", TipoFila.SERIE, Orden.POPULARIDAD, conVotos + ("with_genres" to "35"), portadas = setOf(S)),
        Fila("series-crimen", "Crimen", TipoFila.SERIE, Orden.POPULARIDAD, conVotos + ("with_genres" to "80"), portadas = setOf(S)),
        Fila("anime-en-emision", "En emisión esta temporada", TipoFila.SERIE, Orden.POPULARIDAD, anime, ventanaDias = 30, portadas = setOf(A), sinPlataformas = true),
        Fila("anime-populares", "Populares", TipoFila.SERIE, Orden.POPULARIDAD, anime + ("vote_count.gte" to "300"), portadas = setOf(A), sinPlataformas = true),
        Fila("anime-mejor-valorados", "Mejor valorados", TipoFila.SERIE, Orden.VALORACION, anime + ("vote_count.gte" to "500"), portadas = setOf(A), sinPlataformas = true),
        Fila("anime-peliculas", "Películas de anime", TipoFila.PELICULA, Orden.POPULARIDAD, anime + conVotos, portadas = setOf(A), sinPlataformas = true),
    )

    fun filas(portada: Portada): List<Fila> = filas.filter { portada in it.portadas }

    // Tertulias, noticias y realities: llenan las series populares de España y no son lo que se busca aquí.
    private const val SIN_PROGRAMAS = "10763,10764,10767"

    /** Parámetros de `discover/<movie|tv>` para una fila, una plataforma (null = sin filtrar) y una página. */
    fun parametros(fila: Fila, plataforma: Plataforma?, pagina: Int, hoy: LocalDate): Map<String, String> {
        val pelicula = fila.tipo == TipoFila.PELICULA
        val p = linkedMapOf("language" to "es-ES", "page" to pagina.toString())
        if (plataforma != null) {
            p["watch_region"] = "ES"
            p["with_watch_monetization_types"] = "flatrate"
            p["with_watch_providers"] = plataforma.id.toString()
        }
        p["sort_by"] = when (fila.orden) {
            Orden.POPULARIDAD -> "popularity.desc"
            Orden.VALORACION -> "vote_average.desc"
            Orden.FECHA -> if (pelicula) "primary_release_date.desc" else "first_air_date.desc"
        }
        if (fila.orden == Orden.FECHA) p[if (pelicula) "primary_release_date.lte" else "first_air_date.lte"] = hoy.toString()
        fila.ventanaDias?.let { dias ->
            p[if (pelicula) "primary_release_date.gte" else "air_date.gte"] = hoy.minusDays(dias.toLong()).toString()
            p[if (pelicula) "primary_release_date.lte" else "air_date.lte"] = hoy.toString()
        }
        if (!pelicula && !fila.sinPlataformas) p["without_genres"] = SIN_PROGRAMAS
        p.putAll(fila.extra)
        return p
    }
}
