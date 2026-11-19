package com.nuvio.tv.balootv.cuenta

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CuentaTorboxTest {

    private val expira = "2026-11-05T17:33:34Z"

    @Test
    fun `con la suscripcion en vigor funciona, aunque falte poco para renovarse`() {
        assertEquals(EstadoTorbox.Funciona, CaducidadTorbox.estado(expira, LocalDate.of(2026, 10, 23)))
        assertEquals(EstadoTorbox.Funciona, CaducidadTorbox.estado(expira, LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun `pasada la fecha esta caducada`() {
        assertEquals(EstadoTorbox.Caducada, CaducidadTorbox.estado(expira, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `sin fecha o con fecha rara se trata como sin conexion`() {
        listOf(null, "", "basura").forEach {
            assertEquals(EstadoTorbox.SinConexion, CaducidadTorbox.estado(it, LocalDate.of(2026, 10, 23)))
        }
    }

    private val server = MockWebServer().apply { start() }

    @After
    fun cerrar() = server.shutdown()

    private fun cuenta() = CuentaTorbox(OkHttpClient(), "clave-de-prueba", "0.5.0", server.url("/").toString().trimEnd('/'))

    @Test
    fun `pide la cuenta con la clave y un agente propio y lee la caducidad`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"plan":1,"is_subscribed":true,"premium_expires_at":"$expira"}}"""))
        val e = cuenta().estado(LocalDate.of(2026, 10, 23))
        val peticion = server.takeRequest()
        assertEquals("/v1/api/user/me", peticion.requestUrl!!.encodedPath)
        assertEquals("Bearer clave-de-prueba", peticion.getHeader("Authorization"))
        assertEquals("BalooTV/0.5.0", peticion.getHeader("User-Agent"))
        assertEquals(EstadoTorbox.Funciona, e)
    }

    @Test
    fun `si TorBox no responde esta sin conexion`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(EstadoTorbox.SinConexion, cuenta().estado(LocalDate.of(2026, 10, 23)))
    }
}
