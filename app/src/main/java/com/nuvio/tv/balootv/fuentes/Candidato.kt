package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamDebridCacheState
import java.text.Normalizer

/** Lo que el motor sabe de un enlace: su texto visible unido, de dónde viene y si está en la caché de TorBox. */
data class Candidato(
    val texto: String,
    val addon: String,
    val enCache: Boolean,
    val bytes: Long?,
    val infoHash: String?,
    val bingeGroup: String?,
    val stream: Stream?,
) {
    /** Texto en minúsculas y sin acentos, para buscar marcas. */
    val normalizado: String by lazy { normalizar(texto) }

    companion object {
        private val MARCA_CACHE = Regex("""\[TB\+]|⚡""")

        fun desde(stream: Stream): Candidato {
            val texto = listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints?.filename)
                .joinToString("\n")
            return Candidato(
                texto = texto,
                addon = stream.addonName,
                enCache = stream.debridCacheStatus?.state == StreamDebridCacheState.CACHED || MARCA_CACHE.containsMatchIn(texto),
                bytes = stream.behaviorHints?.videoSize ?: stream.debridCacheStatus?.cachedSize,
                infoHash = stream.infoHash,
                bingeGroup = stream.behaviorHints?.bingeGroup,
                stream = stream,
            )
        }
    }
}

/** Títulos de TMDB para el clasificador y el validador: España, México, original y el de TMDB en inglés. */
data class TitulosTmdb(val es: String? = null, val mx: String? = null, val original: String? = null, val en: String? = null)

private val MARCAS_DIACRITICAS = Regex("\\p{M}+")
private val NO_ALFANUMERICO = Regex("[^a-z0-9]+")

internal fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(MARCAS_DIACRITICAS, "")

/** Texto normalizado con separadores (., _, -, :, paréntesis…) convertidos en espacios simples, para comparar títulos. */
internal fun comoPalabras(texto: String): String =
    normalizar(texto).replace(NO_ALFANUMERICO, " ").trim()
