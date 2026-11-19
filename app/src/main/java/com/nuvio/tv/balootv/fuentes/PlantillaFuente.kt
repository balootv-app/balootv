package com.nuvio.tv.balootv.fuentes

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

/**
 * Una fuente es la dirección del manifiesto de un addon de Stremio. Puede llevar el marcador [MARCADOR] donde va la
 * clave de TorBox, para que una misma lista le valga a cualquiera: tal cual, escapado (`%7Btorbox%7D`) o dentro de
 * una configuración en base64 (lo habitual en los addons que se configuran con JSON).
 */
object PlantillaFuente {
    const val MARCADOR = "{torbox}"
    private val ESCAPADO = Regex("%7Btorbox%7D", RegexOption.IGNORE_CASE)
    private val PARECE_BASE64 = Regex("^[A-Za-z0-9_+-]{8,}={0,2}$")

    /** Si [plantilla] necesita la clave de TorBox para funcionar. */
    fun lleva(plantilla: String): Boolean =
        plantilla.contains(MARCADOR) || ESCAPADO.containsMatchIn(plantilla) ||
            plantilla.split('/', '?', '&').any { decodificar(it)?.contains(MARCADOR) == true }

    /** [plantilla] con la [clave] en lugar del marcador; sin marcador, la devuelve igual. */
    fun rellenar(plantilla: String, clave: String): String {
        val directo = plantilla.replace(MARCADOR, clave).replace(ESCAPADO, clave)
        return directo.split('/').joinToString("/") { trozo ->
            val texto = decodificar(trozo)
            if (texto == null || !texto.contains(MARCADOR)) trozo
            else codificar(texto.replace(MARCADOR, clave), comoEn = trozo)
        }
    }

    /** Las direcciones de un texto pegado: una por línea, sin vacías, comentarios (`#`) ni repetidas. */
    fun leerLista(texto: String): List<String> =
        texto.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.distinct()

    private fun decodificar(trozo: String): String? {
        if (!PARECE_BASE64.matches(trozo)) return null
        val bytes = trozo.decodeBase64() ?: return null
        val texto = runCatching { bytes.utf8() }.getOrNull() ?: return null
        // Solo cuenta si es texto de verdad: un trozo cualquiera de URL decodifica a bytes sin sentido.
        return texto.takeIf { t -> t.isNotEmpty() && t.all { it == '\n' || it == '\t' || it >= ' ' } && '�' !in t }
    }

    private fun codificar(texto: String, comoEn: String): String {
        val bytes = texto.encodeUtf8()
        val urlSegura = '+' !in comoEn
        // Sin '=' al final puede ser que no lo necesitara: el base64 estándar se rellena si su longitud lo admite.
        val conRelleno = comoEn.endsWith('=') || (!urlSegura && comoEn.length % 4 == 0)
        val salida = if (urlSegura) bytes.base64Url() else bytes.base64()
        return if (conRelleno) salida else salida.trimEnd('=')
    }
}
