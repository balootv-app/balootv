package com.nuvio.tv.balootv.fuentes

/** Lo que se ha pedido reproducir: títulos de TMDB, año, en series temporada y episodio, y la duración (min). */
data class Peticion(
    val titulos: TitulosTmdb,
    val anio: Int?,
    val temporada: Int?,
    val episodio: Int?,
    val duracionMin: Int? = null,
    /** Anime: audio japonés y subtítulos en castellano en vez de doblaje (spec F4 §5). */
    val anime: Boolean = false,
    /**
     * Hay otra película conocida con el mismo título (remakes: «La noche de Halloween» de 1978 y de 2018): un enlace sin
     * año no se puede confirmar. En el muestreo 002, tres de cuatro homónimos sin año en el nombre eran la otra película.
     */
    val homonimos: Boolean = false,
    /**
     * Películas: false si aún no ha salido en digital, físico ni tele en ningún país (sigue solo en cines): ningún enlace
     * se confirma. null si no se sabe.
     */
    val estrenadaEnCasa: Boolean? = null,
)

/** Qué se sabe de si un enlace es de lo pedido (decisión del encargo 002: ante la duda, no reproducir solo). */
enum class Confirmacion {
    /** El texto nombra el título pedido con su año (o sin año, si no tiene homónimos) o con su episodio. Se elige solo. */
    CONFIRMADO,

    /** Nada lo contradice, pero nada lo confirma: va a la lista para elegirlo a mano, nunca solo. */
    SIN_CONFIRMAR,

    /** Es otra cosa: otro título, otro año, otra entrega, otro episodio, un extra, o un episodio si se pide una película. */
    DESCARTADO,
}

/**
 * Comprueba que un enlace es de lo que se ha pedido. Los addons buscan por id de IMDb, pero a veces devuelven otra
 * película con el mismo título o un episodio de la serie homónima (muestreo 002). Se descarta lo que se contradice y
 * solo se confirma lo que el texto nombra de verdad; lo demás queda sin confirmar.
 */
object ValidadorTitulo {

    private val EXTRA = Regex("""\b(making of|featurettes?|trailer|behind the scenes|detras de las camaras|deleted scenes|escenas eliminadas)\b""")
    // Un .rar o .zip no se reproduce: TorBox lo sirve como fichero suelto, sin vídeo (muestreo 002, Ratatouille).
    private val COMPRIMIDO = Regex("""\.(rar|zip|7z)\b""")
    private val ANIO = Regex("""\b(19|20)\d{2}\b""")
    private val SXXEYY = Regex("""\bs(\d{1,2})[ .]?e(\d{1,3})(?:[- ]?e?(\d{1,3}))?\b""")
    private val NXNN = Regex("""\b(\d{1,2})x(\d{2,3})\b""")
    private val CAP = Regex("""\bcap\.?\s?(\d{3,4})\b""")
    private val ROMANOS = mapOf("ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9)
    private val TEMPORADA = Regex("""\bs(\d{1,2})\b|\b(?:temporada|season)\s(\d{1,2})\b""")

    private enum class Titulo { NOMBRADO, AUSENTE, OTRO }
    private enum class Marca { COINCIDE, NINGUNA, OTRA }

    fun esValido(c: Candidato, p: Peticion): Boolean = comprobar(c, p) != Confirmacion.DESCARTADO

    fun comprobar(c: Candidato, p: Peticion): Confirmacion {
        val palabras = comoPalabras(c.texto)
        if (EXTRA.containsMatchIn(palabras) || COMPRIMIDO.containsMatchIn(c.texto.lowercase())) return Confirmacion.DESCARTADO
        val titulo = titulo(c.texto, palabras, p.titulos)
        if (titulo == Titulo.OTRO) return Confirmacion.DESCARTADO
        if (p.temporada != null) {
            return when (episodio(c.normalizado, p.temporada, p.episodio)) {
                Marca.OTRA -> Confirmacion.DESCARTADO
                Marca.COINCIDE -> if (titulo == Titulo.NOMBRADO) Confirmacion.CONFIRMADO else Confirmacion.SIN_CONFIRMAR
                Marca.NINGUNA -> Confirmacion.SIN_CONFIRMAR
            }
        }
        // "The Mandalorian 1x07" cuando se pide la película The Mandalorian and Grogu.
        if (marcasDeEpisodio(c.normalizado)) return Confirmacion.DESCARTADO
        val anio = anio(palabras, p)
        if (anio == Marca.OTRA) return Confirmacion.DESCARTADO
        if (p.estrenadaEnCasa == false || titulo != Titulo.NOMBRADO) return Confirmacion.SIN_CONFIRMAR
        return if (anio == Marca.COINCIDE || !p.homonimos) Confirmacion.CONFIRMADO else Confirmacion.SIN_CONFIRMAR
    }

    /**
     * Películas: si el texto trae año y ninguno está a ±1 del pedido, es otra película con el mismo título
     * ("Dune (2021)" cuando se pide Dune: Parte dos). Los años que forman parte del título no cuentan.
     */
    private fun anio(palabras: String, p: Peticion): Marca {
        val pedido = p.anio ?: return Marca.NINGUNA
        val delTitulo = listOfNotNull(p.titulos.es, p.titulos.mx, p.titulos.original, p.titulos.en)
            .flatMap { t -> ANIO.findAll(comoPalabras(t)).map { it.value }.toList() }.toSet()
        val anios = ANIO.findAll(palabras).map { it.value }.filter { it !in delTitulo }.map { it.toInt() }.toList()
        return when {
            anios.isEmpty() -> Marca.NINGUNA
            anios.any { kotlin.math.abs(it - pedido) <= 1 } -> Marca.COINCIDE
            else -> Marca.OTRA
        }
    }

    private fun titulo(texto: String, palabras: String, titulos: TitulosTmdb): Titulo {
        val conocidos = listOfNotNull(titulos.es, titulos.mx, titulos.original, titulos.en)
            // Las publicaciones suelen quitar lo que va tras ":" o "(" ("Furiosa: De la saga Mad Max" → "Furiosa").
            .flatMap { listOf(it, it.substringBefore("("), it.substringBefore(":")) }
            .map(::comoPalabras).filter { it.length >= 2 }.distinct()
        if (conocidos.isEmpty()) return Titulo.AUSENTE
        val variantes = conocidos.flatMap { listOfNotNull(it, conOtraNumeracion(it)) }.distinct()
        if (variantes.any { apareceComoEsaEntrega(palabras, it) }) return Titulo.NOMBRADO
        // "Gladiator V.Extendida" cuando se pide Gladiator II: la base de la saga sin el número pedido.
        val bases = conocidos.filter { numeroEntrega(it.substringAfterLast(' ')) != null }
            .map { it.substringBeforeLast(' ') }.filter { it.length >= 2 }
        if (bases.any { " $palabras ".contains(" $it ") }) return Titulo.OTRO
        // El título solo aparece seguido de otro número de entrega ("Superman III" cuando se pide Superman).
        if (variantes.any { " $palabras ".contains(" $it ") }) return Titulo.OTRO
        // Sin ningún título conocido: es otra cosa si el texto lleva su propio título con año; si no, no se sabe.
        return if (ANIO.containsMatchIn(normalizar(texto))) Titulo.OTRO else Titulo.AUSENTE
    }

    /** Alguna aparición del título que no vaya seguida de un número de entrega que el título no tiene ("Superman III"). */
    private fun apareceComoEsaEntrega(palabras: String, titulo: String): Boolean {
        val conEspacios = " $palabras "
        val pedidoNumerado = numeroEntrega(titulo.substringAfterLast(' ')) != null
        var desde = 0
        while (true) {
            val i = conEspacios.indexOf(" $titulo ", desde)
            if (i < 0) return false
            val siguiente = conEspacios.substring(i + titulo.length + 2).substringBefore(' ')
            if (pedidoNumerado || numeroEntrega(siguiente) == null) return true
            desde = i + 1
        }
    }

    /** "gladiator ii" ↔ "gladiator 2". */
    private fun conOtraNumeracion(titulo: String): String? {
        val ultima = titulo.substringAfterLast(' ', "")
        val n = numeroEntrega(ultima) ?: return null
        val otra = if (ultima.all { it.isDigit() }) ROMANOS.entries.first { it.value == n }.key else n.toString()
        return titulo.substringBeforeLast(' ') + " " + otra
    }

    private fun numeroEntrega(palabra: String): Int? = ROMANOS[palabra] ?: palabra.toIntOrNull()?.takeIf { it in 2..9 && palabra.length == 1 }

    private fun marcasDeEpisodio(t: String): Boolean = SXXEYY.containsMatchIn(t) || NXNN.containsMatchIn(t) || CAP.containsMatchIn(t)

    private fun episodio(t: String, temporada: Int, episodio: Int?): Marca {
        val refs = mutableListOf<Pair<Int, IntRange>>()
        SXXEYY.findAll(t).forEach { m ->
            val s = m.groupValues[1].toInt(); val e1 = m.groupValues[2].toInt()
            val e2 = m.groupValues[3].toIntOrNull() ?: e1
            refs += s to (e1..maxOf(e1, e2))
        }
        NXNN.findAll(t).forEach { m -> refs += m.groupValues[1].toInt() to (m.groupValues[2].toInt()..m.groupValues[2].toInt()) }
        CAP.findAll(t).forEach { m ->
            val n = m.groupValues[1]
            refs += n.dropLast(2).toInt() to (n.takeLast(2).toInt()..n.takeLast(2).toInt())
        }
        if (refs.isNotEmpty()) {
            return if (refs.any { (s, eps) -> s == temporada && (episodio == null || episodio in eps) }) Marca.COINCIDE else Marca.OTRA
        }
        // Pack de temporada ("S02 COMPLETE"): el addon ya apunta al fichero del episodio.
        val temporadas = TEMPORADA.findAll(t).mapNotNull { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }.toList()
        return when {
            temporadas.isEmpty() -> Marca.NINGUNA
            temporada in temporadas -> Marca.COINCIDE
            else -> Marca.OTRA
        }
    }
}
