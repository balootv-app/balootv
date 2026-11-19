package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.TrackInfo

data class AvisoAnimeSinSubs(val cabecera: String, val titulo: String, val texto: String, val hayLatinos: Boolean)

/** El subtítulo latino que se pondría si se pide: uno de dentro del fichero o uno externo. */
sealed interface LatinoElegido {
    data class Interno(val pista: TrackInfo) : LatinoElegido
    data class Externo(val subtitulo: Subtitle) : LatinoElegido
}

/** Aviso "No hay subtítulos en castellano" del anime (tablero 23) y su opción de subtítulos latinos. */
object AvisoAnime {

    private val ESPANOL = setOf("es", "spa", "esp", "spl", "spanish")

    fun de(cabecera: String, hayLatinos: Boolean, esEpisodio: Boolean): AvisoAnimeSinSubs {
        val que = if (esEpisodio) "este episodio" else "esta película"
        val la = if (esEpisodio) "lo" else "la"
        return AvisoAnimeSinSubs(
            cabecera = "$cabecera · Japonés",
            titulo = "No hay subtítulos en castellano para $que",
            texto = if (hayLatinos) {
                "Solo los he encontrado en español latinoamericano. Puedes ver${la} así, sin subtítulos, o dejar${la} para más tarde."
            } else {
                "Tampoco los hay en español latinoamericano. Puedes ver${la} sin subtítulos o dejar${la} para más tarde."
            },
            hayLatinos = hayLatinos,
        )
    }

    fun latino(internas: List<TrackInfo>, externos: List<Subtitle>): LatinoElegido? {
        internas.firstOrNull { esLatino(it.language, it.name) }?.let { return LatinoElegido.Interno(it) }
        externos.firstOrNull { esLatino(it.lang, it.addonName + " " + it.id) }?.let { return LatinoElegido.Externo(it) }
        return null
    }

    /** Español que no es castellano: región latinoamericana o "Latino" en el nombre. */
    private fun esLatino(idioma: String?, nombre: String?): Boolean {
        val codigo = idioma?.trim()?.lowercase()?.replace('_', '-') ?: return false
        val base = codigo.substringBefore('-')
        if (base !in ESPANOL) return false
        return !SubtitulosCastellano.esCastellano(idioma, nombre)
    }
}
