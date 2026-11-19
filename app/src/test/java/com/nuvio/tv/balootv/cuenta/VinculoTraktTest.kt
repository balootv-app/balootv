package com.nuvio.tv.balootv.cuenta

import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceCodeResponseDto
import com.nuvio.tv.data.repository.TraktTokenPollResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinculoTraktTest {

    private val codigo = TraktDeviceCodeResponseDto(
        deviceCode = "d", userCode = "ABCD1234", verificationUrl = "https://trakt.tv/activate", expiresIn = 600, interval = 5,
    )

    private fun vinculo(vararg respuestas: TraktTokenPollResult): VinculoTrakt {
        val cola = ArrayDeque(respuestas.toList())
        return VinculoTrakt({ Result.success(codigo) }, { cola.removeFirstOrNull() ?: TraktTokenPollResult.Pending })
    }

    @Test
    fun `ensena el codigo y al aprobarlo acaba conectado`() = runTest {
        val pasos = vinculo(TraktTokenPollResult.Pending, TraktTokenPollResult.Approved("usuaria")).vincular().toList()
        assertEquals(
            listOf(PasoTrakt.Pidiendo, PasoTrakt.Codigo("ABCD1234", "trakt.tv/activate"), PasoTrakt.Hecho("usuaria")),
            pasos,
        )
    }

    @Test
    fun `sin aprobar no se queda esperando para siempre y dice por que puede ser`() = runTest {
        val ultimo = vinculo().vincular().toList().last()
        assertEquals(PasoTrakt.Fallo(TextosTrakt.CADUCADO), ultimo)
        assertTrue(TextosTrakt.NO_ABIERTO in (ultimo as PasoTrakt.Fallo).motivo)
    }

    @Test
    fun `denegado lo explica`() = runTest {
        val ultimo = vinculo(TraktTokenPollResult.Denied).vincular().toList().last()
        assertEquals(PasoTrakt.Fallo(TextosTrakt.DENEGADO), ultimo)
        assertTrue(TextosTrakt.NO_ABIERTO in TextosTrakt.DENEGADO)
    }

    @Test
    fun `un corte de red suelto no tira la vinculacion, tres seguidos si`() = runTest {
        val red = TraktTokenPollResult.Failed("Error de red")
        assertEquals(
            PasoTrakt.Hecho(null),
            vinculo(red, red, TraktTokenPollResult.Approved(null)).vincular().toList().last(),
        )
        assertEquals(PasoTrakt.Fallo("Error de red"), vinculo(red, red, red).vincular().toList().last())
    }

    @Test
    fun `si no da el codigo lo dice`() = runTest {
        val sinCodigo = VinculoTrakt({ Result.failure(IllegalStateException("Trakt está limitando")) }, { TraktTokenPollResult.Pending })
        assertEquals(listOf(PasoTrakt.Pidiendo, PasoTrakt.Fallo("Trakt está limitando")), sinCodigo.vincular().toList())
    }
}
