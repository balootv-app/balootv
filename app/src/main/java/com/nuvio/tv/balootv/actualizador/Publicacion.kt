package com.nuvio.tv.balootv.actualizador

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Una publicación de GitHub Releases, con lo que hace falta. */
@Serializable
data class Publicacion(
    @SerialName("tag_name") val etiqueta: String? = null,
    @SerialName("name") val nombre: String? = null,
    @SerialName("body") val notas: String? = null,
    @SerialName("draft") val borrador: Boolean = false,
    @SerialName("prerelease") val previa: Boolean = false,
    @SerialName("assets") val ficheros: List<Fichero> = emptyList(),
)

@Serializable
data class Fichero(
    @SerialName("name") val nombre: String,
    @SerialName("browser_download_url") val url: String,
    @SerialName("size") val tamano: Long = 0,
)

/** Una versión nueva lista para bajar: la APK que corresponde a la arquitectura del aparato. */
data class Actualizacion(val version: String, val notas: String, val apk: Fichero)

/**
 * Qué versión ofrecer. Basado en `ReleaseSelector` y `AbiSelector` de NuvioTV 1.1.0-beta.7 (commit 8b41d3e), con
 * dos cambios: solo versiones estables (sin canal beta) y nunca una APK de otra arquitectura «por si acaso».
 */
object EleccionPublicacion {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Las arquitecturas que se publican; una APK sin ninguna en el nombre vale para todas. */
    val ARQUITECTURAS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun leer(texto: String): Publicacion? = runCatching { json.decodeFromString(Publicacion.serializer(), texto) }.getOrNull()

    /**
     * La actualización para un aparato con [abis] (en su orden de preferencia, `Build.SUPPORTED_ABIS`) que lleva
     * [versionActual], o null si no hay nada más nuevo o no hay APK para él.
     */
    fun elegir(p: Publicacion, versionActual: String, abis: List<String>, soloHttps: Boolean = true): Actualizacion? {
        if (p.borrador || p.previa) return null
        val etiqueta = p.etiqueta?.takeIf { it.isNotBlank() } ?: p.nombre ?: return null
        if (VersionUtils.isPrerelease(etiqueta)) return null
        if (!VersionUtils.isRemoteNewer(etiqueta, versionActual)) return null
        val apk = apkPara(p.ficheros, abis, soloHttps) ?: return null
        return Actualizacion(VersionUtils.normalize(etiqueta), p.notas.orEmpty(), apk)
    }

    /** [soloHttps] falso solo en las compilaciones de desarrollo, para probar con una publicación servida en local. */
    fun apkPara(ficheros: List<Fichero>, abis: List<String>, soloHttps: Boolean = true): Fichero? {
        val apks = ficheros.filter {
            it.nombre.endsWith(".apk", ignoreCase = true) && (it.url.startsWith("https://") || (!soloHttps && it.url.startsWith("http://")))
        }
        for (abi in abis) {
            // «armeabi-v7a» no contiene «arm64-v8a» ni al revés, pero «x86» sí está dentro de «x86_64».
            apks.firstOrNull { f -> contiene(f.nombre, abi) }?.let { return it }
        }
        return apks.firstOrNull { f -> ARQUITECTURAS.none { contiene(f.nombre, it) } }
    }

    private fun contiene(nombre: String, abi: String): Boolean =
        Regex("(?:^|[^A-Za-z0-9_])${Regex.escape(abi)}(?:[^A-Za-z0-9_]|$)", RegexOption.IGNORE_CASE).containsMatchIn(nombre)
}
