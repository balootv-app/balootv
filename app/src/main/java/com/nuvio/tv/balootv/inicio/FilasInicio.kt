package com.nuvio.tv.balootv.inicio

import com.nuvio.tv.data.remote.dto.trakt.TraktIdsDto
import com.nuvio.tv.data.remote.dto.trakt.TraktMovieDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowProgressResponseDto
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/** El episodio de la ficha (numeración de Cinemeta) con que empieza una temporada nueva. */
@Serializable
data class EpisodioFicha(val temporada: Int, val episodio: Int)

/**
 * Lo que el Inicio guarda en disco y rehace una vez al día de Madrid. [entrada] va sin [EntradaIdeas.enLista]: Mi lista
 * y Seguir viendo cambian a cada rato y se cruzan al dibujar. [enLaFicha]: por [claveTemporada], dónde empieza la
 * temporada nueva en la ficha.
 */
@Serializable
data class InicioGuardado(
    val refrescadaEn: Long,
    /** El formato de lo guardado: si es de otro, se rehace aunque sea de hoy (la 1 no tenía géneros; la 0, ni logos). */
    val version: Int = 0,
    val entrada: EntradaIdeas,
    val series: List<SerieEstrenos>,
    val enLaFicha: Map<String, EpisodioFicha> = emptyMap(),
    /** La cuenta de Trakt con la que se hizo (su slug); null, sin cuenta: solo lo más visto. */
    val cuenta: String? = null,
) {
    companion object {
        const val VERSION = 2

        fun claveTemporada(titulo: Titulo, temporadaTmdb: Int) = "${titulo.clave}|$temporadaTmdb"
    }
}

/** Una fila del Inicio lista para dibujar. [temporadas]: por id de cartel, dónde abrir la ficha (la temporada nueva). */
data class FilaInicio(
    val clave: String,
    val titulo: String,
    val items: List<MetaPreview>,
    val temporadas: Map<String, EpisodioFicha> = emptyMap(),
)

/** Un título de Seguir viendo: su id (el de la ficha: `tt…` o `tmdb:N`) y si es una serie. */
data class EnCurso(val id: String, val serie: Boolean)

/** Lo que llega de Trakt, en los tipos del Inicio. */
object DeTrakt {
    private val TERMINADAS = setOf("ended", "canceled")

    fun ids(d: TraktIdsDto?): Ids? = d?.takeIf { it.tmdb != null || it.trakt != null }?.let { Ids(it.tmdb, it.imdb, it.trakt) }

    fun pelicula(m: TraktMovieDto?): Titulo? =
        m?.let { ids(it.ids) }?.let { Titulo.minimo(Tipo.PELICULA, it, m.title.orEmpty(), m.year) }

    fun serie(s: TraktShowDto?): Titulo? =
        s?.let { ids(it.ids) }?.let { Titulo.minimo(Tipo.SERIE, it, s.title.orEmpty(), s.year) }

    fun instante(texto: String?): Instant? = texto?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** Si la serie puede estrenar temporada: ni terminada ni cancelada, y con ids de Trakt y de TMDB. */
    fun puedeEstrenar(s: TraktShowDto?): Boolean =
        s?.status?.lowercase() !in TERMINADAS && s?.ids?.trakt != null && s.ids.tmdb != null

    /** La mayor temporada con algún episodio completado (`progress/watched`); 0 si ninguna. */
    fun ultimaTemporadaVista(p: TraktShowProgressResponseDto?): Int =
        p?.seasons.orEmpty().filter { t -> t.episodes.orEmpty().any { it.completed == true } }.maxOfOrNull { it.number ?: 0 } ?: 0
}

/** Las filas del Inicio de la tele a partir de lo guardado y de lo que está en Mi lista o en Seguir viendo ahora. */
object FilasInicio {

    fun de(guardado: InicioGuardado, enLista: List<Marca>, enCurso: List<EnCurso>, hoy: LocalDate): List<FilaInicio> {
        val porImdb = indiceImdb(guardado)
        val deSeguir = enCurso.mapNotNull { marca(if (it.serie) Tipo.SERIE else Tipo.PELICULA, it.id, null, null, porImdb) }
        val nueva = TemporadaNueva.calcular(guardado.series, hoy)
        val filas = Ideas.componer(guardado.entrada.copy(enLista = enLista + deSeguir), FiltroIdeas.Todo, temporadaNueva = nueva)
        return filas.mapNotNull { fila ->
            val clase = fila.clase
            val temporadas = (clase as? ClaseFila.TemporadaNueva)?.temporadas.orEmpty()
            val enFicha = HashMap<String, EpisodioFicha>()
            val items = fila.titulos.mapNotNull { t ->
                val temporada = temporadas[t.clave] ?: return@mapNotNull meta(t)
                val episodio = guardado.enLaFicha[InicioGuardado.claveTemporada(t, temporada)] ?: EpisodioFicha(temporada, 1)
                meta(t, "Temporada ${episodio.temporada}")?.also { enFicha[it.id] = episodio }
            }
            items.takeIf { it.isNotEmpty() }?.let { FilaInicio(clave(clase), nombre(clase), it, enFicha) }
        }
    }

    /**
     * Las sugerencias del salvapantallas: lo que sale hoy en las filas de ideas (sin cruzar con Mi lista ni Seguir
     * viendo), solo con fondo y sin repetir. Cambian cada día, como las filas.
     */
    fun salvapantallas(guardado: InicioGuardado, hoy: LocalDate): List<MetaPreview> =
        de(guardado, emptyList(), emptyList(), hoy).flatMap { it.items }.filter { it.background != null }.distinctBy { it.id }

    /**
     * Un título de Mi lista o de Seguir viendo como [Marca]. La ficha lo nombra con `tmdb:N`, `trakt:N` o el id de
     * IMDb; las ideas, con TMDB o Trakt: un `tt…` sin más se busca entre los títulos del Inicio por su id de IMDb.
     */
    fun marca(tipo: Tipo, id: String, tmdb: Int?, trakt: Int?, porImdb: Map<Pair<Tipo, String?>, Titulo>): Marca? {
        val deTmdb = tmdb ?: id.removePrefix("tmdb:").takeIf { id.startsWith("tmdb:") }?.toIntOrNull()
        val deTrakt = trakt ?: id.removePrefix("trakt:").takeIf { id.startsWith("trakt:") }?.toIntOrNull()
        if (deTmdb != null || deTrakt != null) return Marca(tipo, Ids(deTmdb, null, deTrakt))
        return porImdb[tipo to id]?.let { Marca(tipo, it.ids) }
    }

    fun indiceImdb(guardado: InicioGuardado): Map<Pair<Tipo, String?>, Titulo> =
        todos(guardado.entrada).filter { it.ids.imdb != null }.associateBy { it.tipo to it.ids.imdb }

    fun meta(t: Titulo, detalle: String? = null): MetaPreview? {
        val id = t.ids.tmdb?.let { "tmdb:$it" } ?: t.ids.imdb ?: return null
        return MetaPreview(
            id = id,
            type = if (t.tipo == Tipo.PELICULA) ContentType.MOVIE else ContentType.SERIES,
            name = t.titulo,
            poster = t.cartel,
            posterShape = PosterShape.POSTER,
            background = t.fondo,
            logo = t.logo,
            description = t.sinopsis,
            releaseInfo = detalle ?: t.anio?.toString(),
            imdbRating = null,
            genres = t.generos,
            imdbId = t.ids.imdb,
        )
    }

    private fun todos(e: EntradaIdeas): List<Titulo> =
        e.paraTi + e.porqueViste.flatMap { it.relacionados } + e.estaSemana + e.esteMes

    private fun clave(c: ClaseFila): String = when (c) {
        is ClaseFila.TemporadaNueva -> "ideas:temporada"
        ClaseFila.ParaTi -> "ideas:para-ti"
        is ClaseFila.PorqueViste -> "ideas:porque:${c.titulo.clave}"
        ClaseFila.EstaSemana -> "ideas:semana"
        ClaseFila.EsteMes -> "ideas:mes"
    }

    private fun nombre(c: ClaseFila): String = when (c) {
        is ClaseFila.TemporadaNueva -> "Ya está aquí la temporada nueva"
        ClaseFila.ParaTi -> "Para ti"
        is ClaseFila.PorqueViste -> "Porque viste «${c.titulo.titulo}»"
        ClaseFila.EstaSemana -> "Lo que se está viendo esta semana"
        ClaseFila.EsteMes -> "Lo más visto del mes"
    }
}
