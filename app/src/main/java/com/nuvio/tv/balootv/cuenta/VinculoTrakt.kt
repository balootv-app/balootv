package com.nuvio.tv.balootv.cuenta

import com.nuvio.tv.balootv.edicion.Edicion
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceCodeResponseDto
import com.nuvio.tv.data.repository.TraktTokenPollResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Por dónde va la vinculación de Trakt (primer arranque y Ajustes), con el código a la vista como la de TorBox. */
sealed interface PasoTrakt {
    data object Pidiendo : PasoTrakt
    /** Hay que escribir [codigo] en [direccion] (sin `https://`). */
    data class Codigo(val codigo: String, val direccion: String) : PasoTrakt
    data class Hecho(val usuario: String?) : PasoTrakt
    data class Fallo(val motivo: String) : PasoTrakt
}

/**
 * Vincula Trakt con un código sin salir de BalooTV: lo pide, lo enseña y espera a que se apruebe en el móvil. La red
 * va por las dos funciones que se le pasan (las de `TraktAuthService`), para poder probarlo sin Trakt.
 */
class VinculoTrakt(
    private val pedirCodigo: suspend () -> Result<TraktDeviceCodeResponseDto>,
    private val preguntar: suspend () -> TraktTokenPollResult,
) {

    fun vincular(): Flow<PasoTrakt> = flow {
        emit(PasoTrakt.Pidiendo)
        val inicio = pedirCodigo().getOrElse {
            emit(PasoTrakt.Fallo(TextosTrakt.sinCodigo(it.message)))
            return@flow
        }
        emit(PasoTrakt.Codigo(inicio.userCode, inicio.verificationUrl.removePrefix("https://").removeSuffix("/")))
        var intervalo = inicio.interval.coerceAtLeast(1)
        var restante = inicio.expiresIn.coerceAtLeast(intervalo) * 1_000L
        var fallosSeguidos = 0
        while (restante > 0) {
            delay(intervalo * 1_000L)
            restante -= intervalo * 1_000L
            when (val r = preguntar()) {
                TraktTokenPollResult.Pending -> fallosSeguidos = 0
                is TraktTokenPollResult.SlowDown -> intervalo = r.pollIntervalSeconds.coerceAtLeast(1)
                is TraktTokenPollResult.Approved -> {
                    emit(PasoTrakt.Hecho(r.username))
                    return@flow
                }
                TraktTokenPollResult.Denied -> {
                    emit(PasoTrakt.Fallo(TextosTrakt.DENEGADO))
                    return@flow
                }
                TraktTokenPollResult.AlreadyUsed, TraktTokenPollResult.Expired -> {
                    emit(PasoTrakt.Fallo(TextosTrakt.CADUCADO))
                    return@flow
                }
                // Un corte de red no tira la vinculación; varios seguidos, sí.
                is TraktTokenPollResult.Failed -> if (++fallosSeguidos >= MAXIMO_FALLOS) {
                    emit(PasoTrakt.Fallo(r.reason))
                    return@flow
                }
            }
        }
        emit(PasoTrakt.Fallo(TextosTrakt.CADUCADO))
    }

    companion object {
        const val MAXIMO_FALLOS = 3
    }
}

/** Los textos de la vinculación de Trakt. */
object TextosTrakt {
    /**
     * Mientras la app de Trakt de BalooTV siga en pruebas, Trakt solo deja entrar a su dueño: a cualquier otro le dice
     * en su web que la app no está disponible y la tele nunca recibe la aprobación. Por eso se avisa ya con el código.
     */
    const val NO_ABIERTO = "Trakt aún no está abierto para todos en BalooTV; la app funciona igual sin él."

    const val PISTA = "Si Trakt te dice que la app no está disponible: $NO_ABIERTO"

    const val DENEGADO = "Trakt no ha dejado vincularla. $PISTA"

    const val CADUCADO = "El código ha caducado sin aprobarse. Pide otro. $PISTA"

    /** No se pudo ni pedir el código: el motivo que da Trakt, o uno genérico. */
    fun sinCodigo(motivo: String?): String =
        motivo?.takeIf { it.isNotBlank() } ?: "No se pudo pedir el código a Trakt. Vuelve a intentarlo."

    /** La pista bajo el código, solo en la edición pública (en casa entra su dueño). */
    val pistaBajoCodigo: String? get() = PISTA.takeIf { Edicion.esPublica }
}
