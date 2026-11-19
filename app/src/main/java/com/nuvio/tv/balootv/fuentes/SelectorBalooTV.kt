package com.nuvio.tv.balootv.fuentes

import java.util.Locale

/** Un enlace ya clasificado, con su puntuación y la línea que se muestra en "Cambiar fuente". */
data class Puntuado(
    val candidato: Candidato,
    val idioma: Idioma,
    val calidad: Calidad,
    val puntos: Int,
    val linea: String,
    /** El validador confirma que es lo pedido. Sin confirmar, nunca se elige solo (encargo 002). */
    val confirmado: Boolean = true,
)

/**
 * Por qué no se puede reproducir nada solo (reglas de la decisión D7). SOLO_SIN_CONFIRMAR: hay castellano, pero no se
 * puede confirmar que sea lo pedido; AUN_EN_CINES: la película aún no ha salido en casa; SOLO_LATINO: el único español
 * es latino.
 */
enum class Motivo { SIN_CASTELLANO, SOLO_NO_CACHEADO, SIN_CALIDAD, SOLO_DUDOSO, SOLO_SIN_CONFIRMAR, AUN_EN_CINES, SOLO_LATINO }

sealed interface Eleccion {
    val lista: List<Puntuado>

    data class Elegido(val mejor: Puntuado, override val lista: List<Puntuado>) : Eleccion
    /** `vose`: versión original ≥ mínimo y en caché (nunca latino), para ofrecerla preguntando (D7). */
    data class SinEleccion(val motivo: Motivo, override val lista: List<Puntuado>, val vose: List<Puntuado> = emptyList()) : Eleccion
}

/**
 * Elige el enlace que se reproduce solo: castellano (seguro o probable), del título pedido, en caché y a
 * `altoMinimo` o más, con la mejor puntuación. Nunca latino ni por debajo del mínimo. Lo que el aparato no
 * aguanta (más resolución o bitrate de la cuenta, Dolby Vision sin decodificador) queda de reserva: solo se
 * elige si no hay otra cosa, empezando por lo que menos se pasa.
 * La lista para "Cambiar fuente" lleva primero los elegibles, luego los dudosos y al final los castellanos
 * que aún no están en caché.
 * `grupoPreferido` (el `bingeGroup` del episodio anterior) gana entre los elegibles, para seguir con la
 * misma publicación de un episodio a otro; nunca saca de la lista algo que no fuera elegible.
 */
object SelectorBalooTV {

    private val CASTELLANO = setOf(Idioma.CASTELLANO_SEGURO, Idioma.CASTELLANO_PROBABLE)
    private val ORIGEN = Regex("""(?:⚙️|🌐)\s*([^\n|]+)""")
    // Símbolos (emojis y banderas) y los selectores y uniones que los acompañan.
    private val EMOJI = Regex("""[\p{So}\x{FE0F}\x{200D}\x{20E3}]+""")
    private val ESPACIOS = Regex("""\s+""")

    /** Resultado de clasificar un enlace; null si no es del título pedido. Se recuerda entre llamadas. */
    private data class Analisis(val idioma: Idioma, val calidad: Calidad, val puntos: Int, val linea: String, val confirmado: Boolean = true)

    // La pantalla de fuentes vuelve a elegir cada vez que responde una fuente, con casi los mismos enlaces:
    // recordar el análisis evita repetirlo (en la tele, cada análisis completo cuesta del orden de 1 s).
    private val memoria = object : LinkedHashMap<String, Analisis?>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Analisis?>?) = size > 4000
    }

    fun limpiarMemoria() = synchronized(memoria) { memoria.clear() }

    fun elegir(
        candidatos: List<Candidato>,
        p: Peticion,
        pref: Preferencias = PreferenciasAparato.actual,
        grupoPreferido: String? = null,
    ): Eleccion {
        if (p.anime) return elegirAnime(candidatos, p, pref, grupoPreferido)
        val validos = candidatos
            .filter { ClasificadorIdioma.puedeSerEspanol(it) && !ListaNegraFuentes.contiene(it) }
            .mapNotNull { c -> analizar(c, p, pref)?.let { Puntuado(c, it.idioma, it.calidad, it.puntos, it.linea, it.confirmado) } }
            .filter { !it.calidad.camara }
        validos.forEach { x ->
            x.candidato.stream?.let { s -> RegistroIdioma.recordar(listOf(s.url, s.behaviorHints?.filename), x.idioma) }
        }
        val alcanzaMinimo = { x: Puntuado -> (x.calidad.alto ?: 0) >= pref.altoMinimo }
        val porPuntos = compareByDescending<Puntuado> { it.puntos }

        val (cacheados, sinConfirmar) = validos.filter { it.idioma in CASTELLANO && alcanzaMinimo(it) && it.candidato.enCache }
            .sortedWith(porPuntos).partition { it.confirmado }
        val (elegibles, reserva) = cacheados.partition { excesos(it, p, pref) == 0 }
        val pesados = reserva.sortedWith(compareBy<Puntuado> { excesos(it, p, pref) }.then(porPuntos))
        val dudosos = validos.filter { it.idioma == Idioma.DUDOSO && alcanzaMinimo(it) }
            .sortedWith(compareByDescending<Puntuado> { it.candidato.enCache }.then(porPuntos))
        val noCacheados = validos.filter { it.idioma in CASTELLANO && alcanzaMinimo(it) && !it.candidato.enCache }.sortedWith(porPuntos)
        val lista = elegibles + pesados + sinConfirmar + dudosos + noCacheados

        if (elegibles.isNotEmpty()) {
            val delGrupo = grupoPreferido?.let { g -> elegibles.firstOrNull { it.candidato.bingeGroup == g } }
            return Eleccion.Elegido(delGrupo ?: elegibles.first(), lista)
        }
        // Todo lo castellano se pasa de lo que aguanta el aparato: mejor intentarlo que no reproducir nada.
        if (pesados.isNotEmpty()) return Eleccion.Elegido(pesados.first(), lista)
        val castellano = validos.any { it.idioma in CASTELLANO }
        val motivo = when {
            p.estrenadaEnCasa == false && (castellano || dudosos.isNotEmpty()) -> Motivo.AUN_EN_CINES
            sinConfirmar.isNotEmpty() -> Motivo.SOLO_SIN_CONFIRMAR
            noCacheados.isNotEmpty() -> Motivo.SOLO_NO_CACHEADO
            dudosos.any { it.candidato.enCache } -> Motivo.SOLO_DUDOSO
            castellano -> Motivo.SIN_CALIDAD
            dudosos.isNotEmpty() -> Motivo.SOLO_DUDOSO
            validos.any { it.idioma == Idioma.LATINO } -> Motivo.SOLO_LATINO
            else -> Motivo.SIN_CASTELLANO
        }
        return Eleccion.SinEleccion(motivo, lista, if (lista.isEmpty()) versionOriginal(candidatos, p, pref) else emptyList())
    }

    /**
     * Anime (spec F4 §5): audio japonés, ≥ mínimo y en caché; nunca un doblaje sin japonés. A igual calidad gana
     * la que trae subtítulos en español dentro. La lista lleva primero las elegibles y luego las que faltan en caché.
     */
    private fun elegirAnime(candidatos: List<Candidato>, p: Peticion, pref: Preferencias, grupoPreferido: String?): Eleccion {
        val validos = candidatos.asSequence()
            .filter { !ListaNegraFuentes.contiene(it) }
            .mapNotNull { c -> analizarAnime(c, p, pref)?.let { Puntuado(c, Idioma.JAPONES, it.calidad, it.puntos, it.linea) } }
            .filter { !it.calidad.camara && (it.calidad.alto ?: 0) >= pref.altoMinimo }
            .sortedByDescending { it.puntos }
            .toList()
        val (cacheados, noCacheados) = validos.partition { it.candidato.enCache }
        // En anime no hay techo de bitrate (nunca lo hubo): solo cuenta lo que el aparato decodifica.
        val (elegibles, reserva) = cacheados.partition { excesos(it, p, pref, red = false) == 0 }
        val lista = elegibles + reserva.sortedBy { excesos(it, p, pref, red = false) } + noCacheados
        if (elegibles.isEmpty() && reserva.isNotEmpty()) return Eleccion.Elegido(lista.first(), lista)
        if (elegibles.isNotEmpty()) {
            // El grupo del episodio anterior gana solo si no pierde los subtítulos en español de la mejor.
            val conSubs = { x: Puntuado -> x.puntos >= PUNTOS_SUBS_ESPANOL }
            val delGrupo = grupoPreferido?.let { g ->
                elegibles.firstOrNull { it.candidato.bingeGroup == g && (conSubs(it) || !conSubs(elegibles.first())) }
            }
            return Eleccion.Elegido(delGrupo ?: elegibles.first(), lista)
        }
        return Eleccion.SinEleccion(if (noCacheados.isNotEmpty()) Motivo.SOLO_NO_CACHEADO else Motivo.SIN_CALIDAD, lista)
    }

    /** En anime, los subtítulos en español dentro mandan sobre la calidad: sin ellos casi nunca hay castellano. */
    private const val PUNTOS_SUBS_ESPANOL = 10_000

    /**
     * Anime: los enlaces rara vez llevan año, así que «sin confirmar» vale, salvo con la película aún en cines: ahí,
     * como en el resto, solo lo confirmado (encargo 002). Los homónimos no cuentan: en el muestreo, exigir año en
     * «Pokémon: La película» dejaba fuera los fansubs japoneses y elegía un doblaje inglés.
     */
    private fun validoParaAnime(c: Candidato, p: Peticion): Boolean = when (ValidadorTitulo.comprobar(c, p)) {
        Confirmacion.CONFIRMADO -> true
        Confirmacion.SIN_CONFIRMAR -> p.estrenadaEnCasa != false
        Confirmacion.DESCARTADO -> false
    }

    /** null si no vale para anime (solo doblaje, china o de otro título). Se recuerda como [analizar]. */
    private fun analizarAnime(c: Candidato, p: Peticion, pref: Preferencias): Analisis? {
        val clave = "anime|${p.hashCode()}|${pref.hashCode()}|${c.addon}|${c.enCache}|${c.bytes}|${c.texto}"
        synchronized(memoria) { if (memoria.containsKey(clave)) return memoria[clave] }
        val analisis = if (ClasificadorAnime.audio(c) == AudioAnime.SOLO_DOBLAJE || ClasificadorAnime.china(c) ||
            !validoParaAnime(c, p)
        ) {
            null
        } else {
            val q = ClasificadorCalidad.clasificar(c)
            val puntos = (pref.puntosAlto[q.alto] ?: 0) + (pref.puntosHdr[q.hdr] ?: 0) + (pref.puntosAudio[q.audio] ?: 0) +
                (pref.puntosTipo[q.tipo] ?: 0) + (if (ClasificadorAnime.subtitulosEspanol(c)) PUNTOS_SUBS_ESPANOL else 0)
            Analisis(Idioma.JAPONES, q, puntos, linea(Idioma.JAPONES, q, origen(c)))
        }
        synchronized(memoria) { memoria[clave] = analisis }
        return analisis
    }

    /** Solo cuando no hay nada en castellano: enlaces sin español, válidos, ≥ mínimo, en caché y en la red. */
    private fun versionOriginal(candidatos: List<Candidato>, p: Peticion, pref: Preferencias): List<Puntuado> =
        candidatos.asSequence()
            .filter { it.enCache && !ClasificadorIdioma.puedeSerEspanol(it) && !ListaNegraFuentes.contiene(it) }
            .mapNotNull { c -> analizar(c, p, pref)?.let { Puntuado(c, it.idioma, it.calidad, it.puntos, it.linea) } }
            .filter { it.idioma == Idioma.SIN_ESPANOL && !it.calidad.camara && (it.calidad.alto ?: 0) >= pref.altoMinimo }
            .sortedByDescending { it.puntos }
            .toList()

    private fun analizar(c: Candidato, p: Peticion, pref: Preferencias): Analisis? {
        val clave = "${p.hashCode()}|${pref.hashCode()}|${c.addon}|${c.enCache}|${c.bytes}|${c.texto}"
        synchronized(memoria) { if (memoria.containsKey(clave)) return memoria[clave] }
        val confirmacion = ValidadorTitulo.comprobar(c, p)
        val analisis = if (confirmacion == Confirmacion.DESCARTADO) null else puntuar(c, p, pref).let {
            Analisis(it.idioma, it.calidad, it.puntos, it.linea, confirmacion == Confirmacion.CONFIRMADO)
        }
        synchronized(memoria) { memoria[clave] = analisis }
        return analisis
    }

    /** Cuántos límites del aparato pasa [x]: resolución, Dolby Vision y, con [red], bitrate. 0, si lo aguanta todo. */
    private fun excesos(x: Puntuado, p: Peticion, pref: Preferencias, red: Boolean = true): Int =
        listOf(
            (x.calidad.alto ?: 0) > pref.altoMaximo,
            !pref.dolbyVision && x.calidad.hdr == Hdr.DV,
            red && (mbps(x, p)?.let { it > pref.mbpsMaximo } ?: false),
        ).count { it }

    /** Bitrate medio estimado en Mbps, o null si falta el tamaño o la duración. */
    private fun mbps(x: Puntuado, p: Peticion): Double? {
        val bytes = x.calidad.bytes ?: return null
        val minutos = p.duracionMin?.takeIf { it > 0 } ?: return null
        return bytes * 8.0 / (minutos * 60.0) / 1e6
    }

    fun puntuar(c: Candidato, p: Peticion, pref: Preferencias = PreferenciasAparato.actual): Puntuado {
        val idioma = ClasificadorIdioma.clasificar(c, p.titulos)
        val q = ClasificadorCalidad.clasificar(c)
        val origen = origen(c)
        val puntos = (pref.puntosAlto[q.alto] ?: 0) +
            (pref.puntosHdr[q.hdr] ?: 0) +
            (pref.puntosAudio[q.audio] ?: 0) +
            (pref.puntosTipo[q.tipo] ?: 0) +
            (if (idioma == Idioma.CASTELLANO_SEGURO) pref.puntosCastellanoSeguro else 0) +
            (if (pref.origenesConfianza.any { c.normalizado.contains(it) }) pref.puntosOrigenConfianza else 0)
        return Puntuado(c, idioma, q, puntos, linea(idioma, q, origen))
    }

    /** De dónde sale el enlace, sin los emojis que ponen los addons («SGF 🔍 Proveedor» → «SGF Proveedor»). */
    internal fun origen(c: Candidato): String =
        ORIGEN.findAll(c.texto).lastOrNull()?.groupValues?.get(1)
            ?.replace(EMOJI, " ")?.replace(ESPACIOS, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: c.addon

    private fun linea(idioma: Idioma, q: Calidad, origen: String): String {
        val partes = mutableListOf(principal(idioma, q))
        q.bytes?.let { partes += tamano(it) }
        partes += origen
        return partes.joinToString(" · ")
    }

    /** "4K HDR10 · Castellano DTS": lo que se ve y se oye. */
    internal fun principal(idioma: Idioma, q: Calidad): String = formato(idioma, q).texto

    /** Lo mismo por partes, para las insignias. El códec solo va con el castellano y el japonés. */
    internal fun formato(idioma: Idioma, q: Calidad): Formato {
        val audio = when (q.audio) {
            Audio.AC3 -> "AC-3"; Audio.DTS -> "DTS"; Audio.EAC3 -> "E-AC-3"; Audio.TRUEHD -> "TrueHD"
            Audio.DTSHD -> "DTS-HD"; Audio.AAC -> "AAC"; Audio.OTRO, null -> null
        }
        val (nombre, codec) = when (idioma) {
            Idioma.CASTELLANO_SEGURO, Idioma.CASTELLANO_PROBABLE -> "Castellano" to audio
            Idioma.SIN_ESPANOL -> "Versión original" to null
            Idioma.JAPONES -> "Japonés" to audio
            else -> "Idioma por confirmar" to null
        }
        val resolucion = when (q.alto) { 2160 -> "4K"; null -> null; else -> "${q.alto}p" }
        return Formato(resolucion, q.hdr, nombre, codec)
    }

    internal fun tamano(bytes: Long): String = String.format(Locale("es", "ES"), "%.1f GB", bytes / 1e9)
}
