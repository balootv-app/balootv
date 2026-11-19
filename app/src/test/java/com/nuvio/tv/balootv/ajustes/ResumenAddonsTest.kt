package com.nuvio.tv.balootv.ajustes

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumenAddonsTest {

    private fun addon(nombre: String, url: String, vararg recursos: String, enabled: Boolean = true) = Addon(
        id = nombre.lowercase(), name = nombre, version = "1.0.0", description = null, logo = null, baseUrl = url,
        catalogs = emptyList(), types = emptyList(), resources = recursos.map { AddonResource(it, emptyList(), null) }, enabled = enabled,
    )

    private val uno = addon("Fuente uno", "https://uno.example/idioma=es%7Ctorbox=CLAVE", "stream")
    private val dos = addon("Fuente dos", "https://dos.example/torbox=CLAVE", "stream")
    private val catalogo = addon("Catálogo", "https://catalogo.example", "catalog", "meta")
    private val subtitulos = addon("Subtítulos", "https://subs.example", "subtitles")

    @Test
    fun `las fuentes van primero en el orden en que se anadieron`() {
        val filas = ResumenAddons.filas(listOf(subtitulos, dos, catalogo, uno))
        assertEquals(listOf("Fuente dos", "Fuente uno", "Catálogo", "Subtítulos"), filas.map { it.nombre })
        assertEquals(listOf("Fuente", "Fuente", "Fichas y catálogo", "Subtítulos"), filas.map { it.papel })
        assertEquals(dos.baseUrl, filas[0].baseUrl)
    }

    @Test
    fun `cada fila explica para que se usa y nunca ensena la clave de TorBox`() {
        ResumenAddons.filas(listOf(uno, dos, catalogo, subtitulos)).forEach { f ->
            assertTrue(f.nombre, f.explicacion.isNotBlank())
            assertFalse(f.nombre, "CLAVE" in f.nombre + f.papel + f.explicacion)
        }
    }

    @Test
    fun `un addon desactivado o que no ha respondido lo dice`() {
        val apagado = dos.copy(enabled = false)
        val mudo = addon("addon.ejemplo.com", "https://addon.ejemplo.com")
        val filas = ResumenAddons.filas(listOf(mudo, apagado))
        assertEquals("Fuente · desactivado", filas.first { it.nombre == "Fuente dos" }.papel)
        assertEquals("Sin respuesta", filas.last().papel)
        assertEquals(0, ResumenAddons.fuentesEnUso(listOf(apagado, mudo)))
    }

    @Test
    fun `en uso cuenta solo las fuentes activas que dan enlaces`() {
        assertEquals(2, ResumenAddons.fuentesEnUso(listOf(uno, dos, catalogo, subtitulos)))
        assertEquals(0, ResumenAddons.fuentesEnUso(emptyList()))
    }
}
