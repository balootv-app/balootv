package com.nuvio.tv.balootv.inicio

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un título del que solo importa reconocerlo: lo visto, lo de la watchlist, lo de «Recomendadas». */
@Serializable
data class Marca(val tipo: Tipo, val ids: Ids)

/** «Porque viste»: lo último visto de una clase (película o serie) y lo que Trakt dice que se le parece. */
@Serializable
data class Semilla(val titulo: Titulo, val relacionados: List<Titulo>)

/**
 * Lo que entra en el Inicio. Cada fila trae películas y series en el orden de Trakt, unas detrás de otras; [vistos]
 * son las películas vistas y las series empezadas o terminadas, y [enLista], la watchlist y «Recomendadas».
 */
@Serializable
data class EntradaIdeas(
    val paraTi: List<Titulo>,
    val porqueViste: List<Semilla>,
    val estaSemana: List<Titulo>,
    val esteMes: List<Titulo>,
    val vistos: List<Marca>,
    val enLista: List<Marca>,
)

sealed interface ClaseFila {
    /** [temporadas]: por `Titulo.clave`, la temporada estrenada (numeración de TMDB). */
    data class TemporadaNueva(val temporadas: Map<String, Int>) : ClaseFila
    data object ParaTi : ClaseFila
    data class PorqueViste(val titulo: Titulo) : ClaseFila
    data object EstaSemana : ClaseFila
    data object EsteMes : ClaseFila
}

data class FilaIdeas(val clase: ClaseFila, val titulos: List<Titulo>)

/** La tele enseña siempre «Todo»; los otros dos están por el contrato, que los prueba. */
enum class FiltroIdeas { Todo, Peliculas, Series }

/**
 * Las filas del Inicio a partir de lo que dio Trakt, las mismas que Ideas del móvil. Copia de `Ideas.kt` del móvil
 * (commit 3e1d288): lo prueba `CompartidoTest` con `compartido/filas.json`.
 */
object Ideas {

    const val MAXIMO_POR_FILA = 10
    const val MINIMO_POR_FILA = 4

    private val MADRID: ZoneId = ZoneId.of("Europe/Madrid")

    /**
     * Contrato C2 (D36 del móvil). Fuera lo visto y lo apuntado; cada título en la primera fila donde salga (ParaTi →
     * PorqueViste → EstaSemana → EsteMes), contando solo lo que de verdad se enseña: lo que no cabe en una fila, o una
     * fila que no sale, puede salir más abajo. Como mucho [maximo] por fila; con menos de [MINIMO_POR_FILA], la fila no
     * sale. Delante, [temporadaNueva] (C3): series vistas, así que sin quitar lo visto, con una basta y sus series
     * cuentan como mostradas.
     */
    fun componer(
        entrada: EntradaIdeas,
        filtro: FiltroIdeas,
        maximo: Int = MAXIMO_POR_FILA,
        temporadaNueva: List<Estreno> = emptyList(),
    ): List<FilaIdeas> {
        val fuera = (entrada.vistos + entrada.enLista).flatMapTo(HashSet()) { claves(it.tipo, it.ids) }
        val mostrados = HashSet<String>()
        val nueva = if (filtro == FiltroIdeas.Peliculas) emptyList() else temporadaNueva.take(maximo)
        val primera = nueva.takeIf { it.isNotEmpty() }?.let { estrenos ->
            estrenos.forEach { mostrados += claves(it.titulo.tipo, it.titulo.ids) }
            FilaIdeas(ClaseFila.TemporadaNueva(estrenos.associate { it.titulo.clave to it.temporada }), estrenos.map { it.titulo })
        }
        val filas = listOf(ClaseFila.ParaTi to entrada.paraTi) +
            entrada.porqueViste.map { ClaseFila.PorqueViste(it.titulo) to it.relacionados } +
            listOf(ClaseFila.EstaSemana to entrada.estaSemana, ClaseFila.EsteMes to entrada.esteMes)
        return listOfNotNull(primera) + filas.mapNotNull { (clase, titulos) ->
            val libres = titulos
                .filter { titulo -> claves(titulo.tipo, titulo.ids).none { it in fuera || it in mostrados } }
                .distinctBy { it.clave }
            val elegidos = filtrar(libres, filtro).take(maximo)
            if (elegidos.size < MINIMO_POR_FILA) return@mapNotNull null
            elegidos.forEach { mostrados += claves(it.tipo, it.ids) }
            FilaIdeas(clase, elegidos)
        }
    }

    /**
     * Contrato C2 (D36 del móvil): «Para ti» en un orden fijo de [hoy], el mismo en el móvil y en la tele aunque Trakt
     * devuelva sus recomendaciones en otro orden en cada llamada; cambia cada día. Sin id de Trakt, al final y en su orden.
     */
    fun ordenDelDia(titulos: List<Titulo>, hoy: LocalDate): List<Titulo> {
        val (con, sin) = titulos.partition { it.ids.trakt != null }
        return con.sortedBy { Math.floorMod("$hoy|${it.ids.trakt}".hashCode(), Int.MAX_VALUE) } + sin
    }

    /** Si lo guardado es de otro día de Madrid que [ahora]; sin nada guardado, sí. */
    fun hayQueRefrescar(ahora: Instant, refrescadaEn: Instant?): Boolean {
        refrescadaEn ?: return true
        return ahora.atZone(MADRID).toLocalDate() != refrescadaEn.atZone(MADRID).toLocalDate()
    }

    private fun filtrar(titulos: List<Titulo>, filtro: FiltroIdeas): List<Titulo> = when (filtro) {
        FiltroIdeas.Peliculas -> titulos.filter { it.tipo == Tipo.PELICULA }
        FiltroIdeas.Series -> titulos.filter { it.tipo == Tipo.SERIE }
        FiltroIdeas.Todo -> intercalar(titulos.filter { it.tipo == Tipo.PELICULA }, titulos.filter { it.tipo == Tipo.SERIE })
    }

    private fun intercalar(a: List<Titulo>, b: List<Titulo>): List<Titulo> =
        (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }

    /** Un título se reconoce por su id de TMDB o por el de Trakt: lo que llega de Trakt no siempre trae los dos. */
    private fun claves(tipo: Tipo, ids: Ids): List<String> =
        listOfNotNull(ids.tmdb?.let { "${tipo.name}:tmdb:$it" }, ids.trakt?.let { "${tipo.name}:trakt:$it" })
}
