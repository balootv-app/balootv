package com.nuvio.tv.balootv.arranque

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArranqueTest {

    private val urls = listOf("https://uno.example/providers=a/manifest.json", "https://dos.example/lang=es")

    private fun addon(base: String, conFlujos: Boolean) = Addon(
        id = base, name = base, version = "1", description = null, logo = null, baseUrl = base, catalogs = emptyList(),
        types = listOf(ContentType.MOVIE),
        resources = if (conFlujos) listOf(AddonResource("stream", listOf("movie", "series"), listOf("tt"))) else emptyList(),
    )

    @Test
    fun `las fuentes estan listas cuando todas tienen su manifiesto con flujos`() {
        val instalados = listOf(addon("https://uno.example/providers=a", true), addon("https://dos.example/lang=es", true))
        assertTrue(Arranque.fuentesListas(instalados, urls))
    }

    @Test
    fun `una fuente sin manifiesto todavia (marcador sin recursos) no esta lista`() {
        val instalados = listOf(addon("https://uno.example/providers=a", true), addon("https://dos.example/lang=es", false))
        assertFalse(Arranque.fuentesListas(instalados, urls))
        assertFalse(Arranque.fuentesListas(emptyList(), urls))
    }

    @Test
    fun `sin fuentes de serie configuradas no se espera`() {
        assertTrue(Arranque.fuentesListas(emptyList(), emptyList()))
    }

    @Test
    fun `se espera en carga hasta que esten las fuentes o se acabe el tiempo, y nunca hay cuenta ni perfiles`() {
        assertEquals(DestinoArranque.CARGA, Arranque.destino(fuentesListas = false, tiempoAgotado = false))
        assertEquals(DestinoArranque.INICIO, Arranque.destino(fuentesListas = true, tiempoAgotado = false))
        assertEquals(DestinoArranque.INICIO, Arranque.destino(fuentesListas = false, tiempoAgotado = true))
        assertEquals(setOf(DestinoArranque.CARGA, DestinoArranque.INICIO), DestinoArranque.entries.toSet())
    }
}
