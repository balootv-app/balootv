package com.nuvio.tv.balootv.cuenta

import com.nuvio.tv.data.remote.api.TorboxApi
import com.nuvio.tv.data.remote.dto.TorboxDeviceAuthorizationDto
import com.nuvio.tv.data.remote.dto.TorboxDeviceTokenDto
import com.nuvio.tv.data.remote.dto.TorboxEnvelopeDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class VinculoTorboxTest {

    /** Lo que respondió TorBox el 02-11-2026 a `device/start` (con el código de dispositivo cambiado). */
    private val inicio = TorboxDeviceAuthorizationDto(
        deviceCode = "dispositivo",
        code = "275470",
        verificationUrl = "https://torbox.app/oauth/device?app=BalooTV",
        verificationUrlComplete = "https://torbox.app/oauth/device?app=BalooTV&code=275470",
        friendlyVerificationUrl = "https://tor.box/link",
        interval = 5,
    )

    /** Y a `device/token` antes de aprobarlo. */
    private val sinUsar = Response.error<TorboxEnvelopeDto<TorboxDeviceTokenDto>>(
        400,
        """{"success":false,"error":"DEVICE_CODE_NOT_USED","data":null}""".toResponseBody(),
    )

    @Test
    fun `ensena el codigo, espera mientras no se aprueba y entrega la clave`() = runTest {
        val api = mockk<TorboxApi>()
        coEvery { api.startDeviceAuthorization(any()) } returns Response.success(TorboxEnvelopeDto(true, inicio))
        coEvery { api.redeemDeviceAuthorization(any()) } returnsMany listOf(
            sinUsar,
            sinUsar,
            Response.success(TorboxEnvelopeDto(true, TorboxDeviceTokenDto(accessToken = "clave-nueva"))),
        )
        assertEquals(
            listOf(
                PasoVinculo.Codigo("275470", "tor.box/link", "https://torbox.app/oauth/device?app=BalooTV&code=275470"),
                PasoVinculo.Clave("clave-nueva"),
            ),
            VinculoTorbox(api).vincular().toList(),
        )
    }

    @Test
    fun `si nadie lo aprueba a tiempo falla`() = runTest {
        val api = mockk<TorboxApi>()
        coEvery { api.startDeviceAuthorization(any()) } returns Response.success(TorboxEnvelopeDto(true, inicio))
        coEvery { api.redeemDeviceAuthorization(any()) } returns sinUsar
        assertEquals(PasoVinculo.Fallo, VinculoTorbox(api).vincular().toList().last())
    }

    @Test
    fun `sin red al empezar falla sin ensenar codigo`() = runTest {
        val api = mockk<TorboxApi>()
        coEvery { api.startDeviceAuthorization(any()) } throws java.io.IOException("sin red")
        assertEquals(listOf(PasoVinculo.Fallo), VinculoTorbox(api).vincular().toList())
    }
}
