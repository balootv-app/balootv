package com.nuvio.tv.balootv.fuentes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

class ServidorFuentesTest {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recibido = mutableListOf<String>()
    private val servidor = ServidorFuentes(ambito, alRecibir = { t ->
        recibido += t
        listOf(ResultadoFuente("a.example", Comprobacion.Valida("Mi fuente")))
    }).also { it.abrir(InetAddress.getLoopbackAddress()) }

    @After fun cerrar() { servidor.cerrar(); ambito.cancel() }

    private fun pedir(camino: String, cuerpo: String? = null): Pair<Int, String> {
        val c = URL("http://127.0.0.1:${servidor.puerto}$camino").openConnection() as HttpURLConnection
        if (cuerpo != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(cuerpo.toByteArray()) }
        }
        val codigo = c.responseCode
        val texto = (if (codigo < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return codigo to texto
    }

    @Test
    fun `sin el codigo no hay pagina`() {
        assertEquals(404, pedir("/").first)
        assertEquals(404, pedir("/otro").first)
    }

    @Test
    fun `con el codigo ensena el formulario`() {
        val (codigo, html) = pedir("/${servidor.codigo}")
        assertEquals(200, codigo)
        assertTrue(html.contains("<textarea name=\"fuentes\""))
        // La guía de fuentes, como enlace que se puede tocar.
        assertTrue(html.contains("<a href=\"https://balootv-app.github.io/fuentes\">balootv-app.github.io/fuentes</a>"))
    }

    @Test
    fun `un envio por codigo y luego cambia`() {
        val primero = servidor.codigo
        val cuerpo = "fuentes=" + URLEncoder.encode("https://a.example/manifest.json\nhttps://b.example/{torbox}", "UTF-8")
        val (estado, html) = pedir("/$primero", cuerpo)
        assertEquals(200, estado)
        assertTrue(html, html.contains("Fuente añadida"))
        assertEquals(listOf("https://a.example/manifest.json\nhttps://b.example/{torbox}"), recibido)
        assertNotEquals(primero, servidor.codigo)
        assertEquals(404, pedir("/$primero", cuerpo).first)
        assertEquals(1, recibido.size)
    }

    @Test
    fun `solo la red de casa`() {
        assertTrue(ServidorFuentes.esLocal(InetAddress.getByName("192.168.1.20")))
        assertTrue(ServidorFuentes.esLocal(InetAddress.getByName("10.0.0.5")))
        assertTrue(!ServidorFuentes.esLocal(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun `el formulario se lee bien`() {
        assertEquals("a b\nc", PeticionHttp.campo("x=1&fuentes=a+b%0Ac", "fuentes"))
        assertEquals("", PeticionHttp.campo("x=1", "fuentes"))
        assertEquals("&lt;b&gt;", Paginas.escapar("<b>"))
    }
}
