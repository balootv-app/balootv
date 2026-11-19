package com.nuvio.tv.balootv.cuenta

import com.nuvio.tv.data.remote.api.TorboxApi
import com.nuvio.tv.data.remote.dto.TorboxDeviceTokenRequestDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Por dónde va la vinculación de TorBox en Ajustes. */
sealed interface PasoVinculo {
    /** Hay que escribir [codigo] en [direccion] (o escanear [enlace], que ya lo lleva). */
    data class Codigo(val codigo: String, val direccion: String, val enlace: String) : PasoVinculo
    data class Clave(val clave: String) : PasoVinculo
    data object Fallo : PasoVinculo
}

/**
 * Vincula una cuenta de TorBox con un código, como Trakt: TorBox da un código, se aprueba en el móvil y la tele
 * recibe la clave de la API. Así nadie teclea la clave con el mando. Sin respuesta en [MAXIMO_MS], falla.
 */
class VinculoTorbox @Inject constructor(private val api: TorboxApi) {

    fun vincular(): Flow<PasoVinculo> = flow {
        val inicio = runCatching { api.startDeviceAuthorization(APP).body()?.data }.getOrNull()
        val dispositivo = inicio?.deviceCode
        val codigo = inicio?.code
        if (dispositivo.isNullOrBlank() || codigo.isNullOrBlank()) {
            emit(PasoVinculo.Fallo)
            return@flow
        }
        val direccion = inicio.friendlyVerificationUrl ?: inicio.verificationUrl ?: DIRECCION
        emit(PasoVinculo.Codigo(codigo, direccion.removePrefix("https://"), inicio.verificationUrlComplete ?: direccion))
        val espera = (inicio.interval ?: 5).coerceAtLeast(2) * 1_000L
        repeat((MAXIMO_MS / espera).toInt()) {
            delay(espera)
            // Mientras no se aprueba, TorBox responde 400 (DEVICE_CODE_NOT_USED): se sigue esperando.
            val clave = runCatching {
                api.redeemDeviceAuthorization(TorboxDeviceTokenRequestDto(dispositivo)).body()?.data?.accessToken
            }.getOrNull()
            if (!clave.isNullOrBlank()) {
                emit(PasoVinculo.Clave(clave))
                return@flow
            }
        }
        emit(PasoVinculo.Fallo)
    }

    companion object {
        const val APP = "BalooTV"
        const val DIRECCION = "https://tor.box/link"
        const val MAXIMO_MS = 600_000L
    }
}
