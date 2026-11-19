package com.nuvio.tv.balootv.cuenta

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Estado del servicio TorBox para la fila de Ajustes. La suscripción se renueva sola cada mes: no se avisa antes;
 * solo se dice "caducada" si de verdad ha pasado la fecha (una renovación fallida).
 */
sealed interface EstadoTorbox {
    data object Funciona : EstadoTorbox
    data object Caducada : EstadoTorbox
    data object SinConexion : EstadoTorbox
}

object CaducidadTorbox {

    /** `expiraIso`: `premium_expires_at` de TorBox (ISO 8601). */
    fun estado(expiraIso: String?, hoy: LocalDate): EstadoTorbox {
        val caduca = expiraIso?.takeIf { it.isNotBlank() }
            ?.let { runCatching { OffsetDateTime.parse(it).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate() }.getOrNull() }
            ?: return EstadoTorbox.SinConexion
        return if (caduca.isBefore(hoy)) EstadoTorbox.Caducada else EstadoTorbox.Funciona
    }
}

/**
 * Lee la cuenta de TorBox (`/v1/api/user/me`). TorBox rechaza (403) peticiones sin agente de usuario, por eso
 * va uno propio. Cualquier fallo es "Sin conexión": Ajustes nunca se rompe por TorBox.
 */
class CuentaTorbox(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val version: String,
    private val base: String = "https://api.torbox.app",
) {
    suspend fun estado(hoy: LocalDate = LocalDate.now()): EstadoTorbox = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext EstadoTorbox.SinConexion
        runCatching {
            val peticion = Request.Builder()
                .url("$base/v1/api/user/me")
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "BalooTV/$version")
                .build()
            http.newCall(peticion).execute().use { r ->
                val cuerpo = r.body?.string()
                if (!r.isSuccessful || cuerpo == null) return@use EstadoTorbox.SinConexion
                val expira = JsonParser.parseString(cuerpo).asJsonObject.getAsJsonObject("data")
                    ?.get("premium_expires_at")?.takeIf { it.isJsonPrimitive }?.asString
                CaducidadTorbox.estado(expira, hoy)
            }
        }.getOrDefault(EstadoTorbox.SinConexion)
    }
}
