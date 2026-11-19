package com.nuvio.tv.core.deeplink

import com.nuvio.tv.domain.deeplink.AppDeepLink
import java.net.URI
import java.net.URLDecoder

object DeepLinkParser {
    fun parse(url: String): AppDeepLink? {
        val parsedUrl = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = parsedUrl.scheme?.lowercase().orEmpty()
        // BalooTV: ni instalación de addons (stremio://, nuvio://<host>) ni watch party.
        // balootv:// es el esquema de la app del móvil; nuvio:// se conserva solo para abrir fichas.
        if (scheme != "nuvio" && scheme != "balootv") return null

        val host = parsedUrl.host?.lowercase().orEmpty()
        val pathSegments = parsedUrl.rawPath
            ?.split("/")
            .orEmpty()
            .mapNotNull { segment -> decode(segment).trim().takeIf(String::isNotBlank) }

        return when (host) {
            "reproducir" -> if (scheme == "balootv") parseReproducir(pathSegments) else null
            "meta" -> parseMetaFromParameters(parsedUrl) ?: parseMetaFromPath(pathSegments)
            "detail", "details", "open", "watch" -> parseMetaFromPath(pathSegments)
            "movie", "movies", "series", "show", "shows", "tv" -> {
                val type = normalizeMediaType(host)
                val id = pathSegments.firstOrNull()?.let(::normalizeId).orEmpty()
                if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
            }
            "imdb", "tmdb" -> parseProviderMetaDeepLink(host, pathSegments, parsedUrl)
            else -> null
        }
    }

    /** `reproducir/tmdb/<tipo>/<id>` (película o siguiente episodio) o `…/<tipo>/<id>/<temporada>/<episodio>` (solo series). */
    private fun parseReproducir(pathSegments: List<String>): AppDeepLink.Reproducir? {
        if (pathSegments.firstOrNull()?.lowercase() != "tmdb") return null
        val type = pathSegments.getOrNull(1)?.let(::normalizeMediaType).orEmpty()
        val id = pathSegments.getOrNull(2)?.removePrefix("tmdb:")?.trim().orEmpty()
        if (type.isBlank() || id.isBlank() || id.any { !it.isDigit() }) return null
        return when (pathSegments.size) {
            3 -> AppDeepLink.Reproducir(type = type, id = "tmdb:$id")
            5 -> {
                if (type != "series") return null
                val season = pathSegments[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                val episode = pathSegments[4].toIntOrNull()?.takeIf { it >= 1 } ?: return null
                AppDeepLink.Reproducir(type = type, id = "tmdb:$id", season = season, episode = episode)
            }
            else -> null
        }
    }

    private fun parseMetaFromParameters(parsedUrl: URI): AppDeepLink.Meta? {
        val parameters = queryParameters(parsedUrl)
        val type = firstParameter(parameters, "type", "mediaType", "media_type")
            ?.let(::normalizeMediaType)
            .orEmpty()
        val id = firstParameter(parameters, "id", "imdb", "imdbId", "imdb_id")
            ?.let(::normalizeId)
            ?: firstParameter(parameters, "tmdb", "tmdbId", "tmdb_id")
                ?.let { "tmdb:${it.removePrefix("tmdb:").trim()}" }
            ?: ""
        return if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
    }

    private fun parseMetaFromPath(pathSegments: List<String>): AppDeepLink.Meta? {
        if (pathSegments.size < 2) return null
        val type = normalizeMediaType(pathSegments[0])
        val id = normalizeId(pathSegments[1])
        return if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
    }

    private fun parseProviderMetaDeepLink(
        provider: String,
        pathSegments: List<String>,
        parsedUrl: URI
    ): AppDeepLink.Meta? {
        val first = pathSegments.firstOrNull().orEmpty()
        val second = pathSegments.getOrNull(1).orEmpty()
        val firstAsType = normalizeMediaType(first)
        val queryType = firstParameter(queryParameters(parsedUrl), "type", "mediaType", "media_type")
            ?.let(::normalizeMediaType)
            .orEmpty()
        val type = firstAsType.ifBlank { queryType }
        val rawId = if (firstAsType.isNotBlank()) second else first
        val id = when (provider) {
            "tmdb" -> rawId.removePrefix("tmdb:").trim().takeIf(String::isNotBlank)?.let { "tmdb:$it" }
            else -> normalizeId(rawId).takeIf(String::isNotBlank)
        }.orEmpty()
        return if (type.isBlank() || id.isBlank()) null else AppDeepLink.Meta(type = type, id = id)
    }

    private fun queryParameters(parsedUrl: URI): Map<String, String> {
        return parsedUrl.rawQuery
            ?.split("&")
            .orEmpty()
            .mapNotNull { pair ->
                val index = pair.indexOf("=")
                if (index < 0) return@mapNotNull null
                val key = decode(pair.substring(0, index)).trim()
                val value = decode(pair.substring(index + 1)).trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun firstParameter(parameters: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            parameters[key]?.trim()?.takeIf(String::isNotBlank)
        }
    }

    private fun normalizeMediaType(value: String): String {
        return when (value.trim().lowercase()) {
            "movie", "movies", "film", "films" -> "movie"
            "series", "show", "shows", "tv", "tvshow", "tvshows" -> "series"
            else -> ""
        }
    }

    private fun normalizeId(value: String): String {
        return value.trim()
            .removePrefix("imdb:")
            .takeIf(String::isNotBlank)
            .orEmpty()
    }

    private fun decode(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
