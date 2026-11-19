package com.nuvio.tv.balootv.errores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RegistroFallosTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `cada entrada lleva hora, version, etiqueta y la pila sin URL`() {
        val carpeta = tmp.newFolder("registro")
        val r = RegistroFallos(carpeta, "0.8.2", ahora = { 0L })
        r.anotar("REPRODUCCION", "Falló https://api.torbox.app/x?token=SECRETO", IllegalStateException("magnet:?xt=urn:btih:abc roto"))
        val texto = File(carpeta, "fallos.txt").readText()
        assertTrue(texto.contains(" · 0.8.2 · REPRODUCCION"))
        assertTrue(texto.contains("Falló <url>"))
        assertTrue(texto.contains("IllegalStateException: <url> roto"))
        assertFalse(texto.contains("SECRETO"))
    }

    @Test
    fun `al pasar del maximo rota a un unico fichero anterior`() {
        val carpeta = tmp.newFolder("registro")
        val r = RegistroFallos(carpeta, "0.8.2")
        val grande = "x".repeat(10_000)
        repeat(30) { r.anotar("PRUEBA", grande) }
        assertTrue(File(carpeta, "fallos.1.txt").exists())
        assertTrue(File(carpeta, "fallos.txt").length() <= 256 * 1024L + 11_000)
        assertEquals(setOf("fallos.txt", "fallos.1.txt"), carpeta.list()!!.toSet())
    }
}
