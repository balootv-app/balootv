package com.nuvio.tv.balootv.ficha

import com.nuvio.tv.balootv.fuentes.Formato
import com.nuvio.tv.balootv.fuentes.Motivo
import com.nuvio.tv.balootv.fuentes.TextoSinEleccion
import com.nuvio.tv.balootv.iconos.Pieza
import com.nuvio.tv.balootv.iconos.Simbolo
import com.nuvio.tv.balootv.iconos.conSeparadores
import com.nuvio.tv.core.util.parseEpisodeReleaseLocalDate
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * Botón grande de la ficha: "Continuar T2 · E3" arriba y, debajo, cuánto queda o dura y a qué hora termina
 * (tableros 18 y 19, contrato C4). [detalle] es la frase de C4 entera.
 */
data class TextoBoton(val titulo: String, val termina: TerminaALas.Partes?) {
    val detalle: String? get() = termina?.texto
}

/** Los textos de la ficha de película y de serie. */
object TextosFicha {

    private val ESPANA = Locale("es", "ES")
    // A mano y no con DateTimeFormatter: según la versión de Android, los meses cortos llevan punto ("oct.") o no.
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

    /**
     * [video] es el episodio que arrancaría Reproducir (null en películas); [progreso], lo visto de él; [duracionMin], lo
     * que dura la película o ese episodio según TMDB.
     */
    fun boton(n: NextToWatch?, video: Video?, progreso: WatchProgress?, duracionMin: Int?, ahora: LocalTime): TextoBoton {
        val posicion = progreso?.let(::posicionReanudable)
        val reanudar = posicion != null && (video == null || n == null || n.isResume)
        val termina = if (reanudar) TerminaALas.partesEmpezado(posicion!!, progreso!!.duration, ahora) else TerminaALas.partesSinEmpezar(duracionMin, ahora)
        val accion = if (reanudar) "Continuar" else "Reproducir"
        val titulo = if (video == null) accion else "$accion T${video.season ?: 1} · E${video.episode ?: 1}"
        return TextoBoton(titulo, termina)
    }

    /**
     * Encima del título del episodio: "Episodio 4" y, detrás, el ✓ si está visto, lo que queda si está a medias (la
     * barra ya dice que es lo que queda) o lo que dura. Sin mayúsculas (propuesta de iconos, regla 7).
     */
    fun etiquetaEpisodio(v: Video, visto: Boolean, progreso: WatchProgress?): List<Pieza> {
        val numero = Pieza.Texto("Episodio ${v.episode ?: ""}".trim())
        val quedan = progreso?.let(::posicionReanudable)?.let { ((progreso.duration - it + 59_999) / 60_000).toInt() }
        val detras = when {
            visto -> Pieza.Icono(Simbolo.VISTO, "Visto")
            quedan != null -> Pieza.Texto(TerminaALas.duracion(quedan))
            v.runtime != null && v.runtime > 0 -> Pieza.Texto(TerminaALas.duracion(v.runtime))
            else -> null
        }
        return conSeparadores(listOf(numero), listOfNotNull(detras))
    }

    /**
     * Fecha de estreno bajo el título del episodio: "8 ene 2021", "Hoy" o, sin emitir, el calendario y "6 nov" (como el
     * móvil); null si no se sabe.
     */
    fun fechaEpisodio(estreno: String?, hoy: LocalDate): List<Pieza>? {
        val fecha = parseEpisodeReleaseLocalDate(estreno) ?: return null
        val diaMes = "${fecha.dayOfMonth} ${MESES[fecha.monthValue - 1]}"
        return when {
            fecha == hoy -> listOf(Pieza.Texto("Hoy"))
            fecha.isBefore(hoy) -> listOf(Pieza.Texto("$diaMes ${fecha.year}"))
            fecha.year == hoy.year -> listOf(Pieza.Icono(Simbolo.SALE, "Sale el"), Pieza.Texto(diaMes))
            else -> listOf(Pieza.Icono(Simbolo.SALE, "Sale el"), Pieza.Texto("$diaMes ${fecha.year}"))
        }
    }

    /** Año de cada temporada (el de su primer episodio) para las pestañas; los especiales mezclan años y no lo llevan. */
    fun aniosTemporada(videos: List<Video>): Map<Int, String> = videos
        .filter { (it.season ?: 0) > 0 }
        .mapNotNull { v -> parseEpisodeReleaseLocalDate(v.released)?.let { v.season!! to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, fechas) -> fechas.min().year.toString() }

    /**
     * Año, géneros y temporadas. La escena en los créditos (C6) va detrás en su icono, y la duración bajo el botón, con
     * la hora a la que termina.
     */
    fun meta(anio: String?, generos: List<String>, temporadas: Int?): String = listOfNotNull(
        anio?.takeIf { it.isNotBlank() },
        generos(generos),
        temporadas?.takeIf { it > 0 }?.let { if (it == 1) "1 temporada" else "$it temporadas" },
    ).joinToString(" · ")

    /**
     * Los dos primeros géneros de TMDB, en castellano y empezando en minúscula salvo el primero: "Acción, aventura",
     * "Ciencia ficción y fantasía, película de TV". El móvil los escribe igual (contrato C9, `compartido/generos.json`)
     * y el salvapantallas los pone bajo el título. Null sin géneros.
     */
    fun generos(generos: List<String>): String? = generos.take(2).takeIf { it.isNotEmpty() }
        ?.mapIndexed { i, g ->
            val es = traducirGenero(g)
            if (i == 0) es.replaceFirstChar { it.titlecase(ESPANA) } else es.replaceFirstChar { it.lowercase(ESPANA) }
        }
        ?.joinToString(", ")

    /** C9: TMDB deja en inglés varios géneros de series en es-ES y, si cae al inglés, también los de películas. */
    val TRADUCCION_GENEROS: Map<String, String> = mapOf(
        "Action & Adventure" to "Acción y aventura",
        "Sci-Fi & Fantasy" to "Ciencia ficción y fantasía",
        "War & Politics" to "Bélica y política",
        "Kids" to "Infantil",
        "News" to "Noticias",
        "Reality" to "Reality",
        "Soap" to "Telenovela",
        "Talk" to "Entrevistas",
        "Action" to "Acción",
        "Adventure" to "Aventura",
        "Animation" to "Animación",
        "Comedy" to "Comedia",
        "Crime" to "Crimen",
        "Documentary" to "Documental",
        "Drama" to "Drama",
        "Family" to "Familia",
        "Fantasy" to "Fantasía",
        "History" to "Historia",
        "Horror" to "Terror",
        "Music" to "Música",
        "Mystery" to "Misterio",
        "Romance" to "Romance",
        "Science Fiction" to "Ciencia ficción",
        "TV Movie" to "Película de TV",
        "Thriller" to "Suspense",
        "War" to "Bélica",
        "Western" to "Western",
    )
    private val TRADUCCION_POR_CLAVE = TRADUCCION_GENEROS.mapKeys { it.key.lowercase(Locale.ROOT) }

    private fun traducirGenero(genero: String): String = genero.trim().let { TRADUCCION_POR_CLAVE[it.lowercase(Locale.ROOT)] ?: it }

    /**
     * Lo de debajo del botón: lo que dura o queda, la bandera con la hora de fin y la fuente ya elegida en insignias,
     * sin el códec (solo sale en «Cambiar fuente»): "2 h 9 min · ⚑ 19:55 · [4K] [HDR10] · 🔊 Castellano".
     */
    /** Bajo el botón cuando no hay ninguna fuente instalada: el botón lleva a añadirlas. */
    const val SIN_FUENTES = "Aún no tienes fuentes: añade una para reproducir"

    fun detalle(boton: TextoBoton, formato: Formato?, motivo: Motivo? = null): List<Pieza> {
        val termina = boton.termina
        return conSeparadores(
            listOfNotNull(termina?.let { Pieza.Texto(it.medida) }),
            termina?.hora?.let { listOf(Pieza.Icono(Simbolo.TERMINA, "Termina a las"), Pieza.Texto(it)) }.orEmpty(),
            formato?.insignias.orEmpty(),
            formato?.sonido(conCodec = false).orEmpty(),
            // Sin fuente que arranque sola, el porqué (encargo 002): «Aún en cines», «Solo en latino»…
            if (formato == null && motivo != null) listOf(Pieza.Texto(TextoSinEleccion.corto(motivo))) else emptyList(),
        )
    }

    /** Duración en minutos desde el texto de los metadatos ("116 min", "1h 56m", "2h", "45"). */
    fun minutos(texto: String?): Int? {
        val t = texto?.lowercase() ?: return null
        val horas = Regex("""(\d+)\s*h""").find(t)?.groupValues?.get(1)?.toInt()
        val mins = Regex("""(\d+)\s*m""").find(t)?.groupValues?.get(1)?.toInt()
        if (horas != null || mins != null) return (horas ?: 0) * 60 + (mins ?: 0)
        return Regex("""^\s*(\d+)\s*$""").find(t)?.groupValues?.get(1)?.toInt()
    }

    /**
     * Dónde va si el reproductor lo reanuda: con lo visto entre el 2 % y el 90 % (`WatchProgress.isInProgress`, la
     * misma regla que el reproductor); null si no, que entonces empieza de cero.
     */
    private fun posicionReanudable(p: WatchProgress): Long? {
        if (p.duration <= 0 || !p.isInProgress()) return null
        // Trakt trae el porcentaje y no la posición.
        val posicion = if (p.position > 0) p.position else (p.duration * p.progressPercentage).toLong()
        return posicion.takeIf { it > 0 }
    }
}
