package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComprobacionFuenteTest {

    private fun addon(id: String = "fuente", vararg recursos: String) = Addon(
        id = id, name = "Mi fuente", version = "1.0.0", description = null, logo = null, baseUrl = "https://a.example",
        catalogs = emptyList(), types = emptyList(), resources = recursos.map { AddonResource(it, emptyList(), null) },
    )

    @Test
    fun `normaliza lo que se pega`() {
        assertEquals("https://a.example/x/manifest.json", ComprobacionFuente.normalizar("  https://a.example/x/manifest.json "))
        assertEquals("https://a.example/x/manifest.json", ComprobacionFuente.normalizar("https://a.example/x/"))
        assertEquals("https://a.example/x/manifest.json", ComprobacionFuente.normalizar("stremio://a.example/x/manifest.json"))
        assertEquals("https://a.example/manifest.json?k=1", ComprobacionFuente.normalizar("https://a.example/manifest.json?k=1"))
    }

    @Test
    fun `vacia o que no es direccion no se acepta`() {
        assertNull(ComprobacionFuente.normalizar(""))
        assertNull(ComprobacionFuente.normalizar("   "))
        assertNull(ComprobacionFuente.normalizar("hola que tal"))
        assertNull(ComprobacionFuente.normalizar("ftp://a.example/manifest.json"))
        assertNull(ComprobacionFuente.normalizar("https://sinpunto/manifest.json"))
        assertEquals(Comprobacion.NoEsDireccion, ComprobacionFuente.antesDeLaRed(null, emptySet(), "k"))
    }

    @Test
    fun `repetida no se vuelve a anadir, tambien con el marcador`() {
        val instaladas = setOf(FuentesDeSerie.base("https://a.example/torbox=k1/manifest.json"))
        assertEquals(Comprobacion.Repetida, ComprobacionFuente.antesDeLaRed("https://a.example/torbox=k1/manifest.json", instaladas, "k1"))
        assertEquals(Comprobacion.Repetida, ComprobacionFuente.antesDeLaRed("https://a.example/torbox={torbox}/manifest.json", instaladas, "k1"))
        assertNull(ComprobacionFuente.antesDeLaRed("https://b.example/manifest.json", instaladas, "k1"))
    }

    @Test
    fun `con el marcador hace falta cuenta de TorBox`() {
        assertEquals(Comprobacion.NecesitaTorbox, ComprobacionFuente.antesDeLaRed("https://a.example/{torbox}/manifest.json", emptySet(), ""))
        assertNull(ComprobacionFuente.antesDeLaRed("https://a.example/manifest.json", emptySet(), ""))
    }

    @Test
    fun `no responde, no es un addon o no da enlaces`() {
        assertEquals(Comprobacion.NoResponde, ComprobacionFuente.juzgar(NetworkResult.Error("Unable to resolve host \"a.example\"")))
        assertEquals(Comprobacion.NoResponde, ComprobacionFuente.juzgar(NetworkResult.Error("timeout")))
        assertEquals(Comprobacion.NoResponde, ComprobacionFuente.juzgar(NetworkResult.Error("Server Error", 503)))
        assertEquals(Comprobacion.NoEsAddon, ComprobacionFuente.juzgar(NetworkResult.Error("Not Found", 404)))
        assertEquals(
            Comprobacion.NoEsAddon,
            ComprobacionFuente.juzgar(NetworkResult.Error("Use JsonReader.setLenient(true) to accept malformed JSON at path $")),
        )
        assertEquals(Comprobacion.NoEsAddon, ComprobacionFuente.juzgar(NetworkResult.Error("Expected BEGIN_OBJECT but was STRING at path $")))
        assertEquals(Comprobacion.NoEsAddon, ComprobacionFuente.juzgar(NetworkResult.Success(addon("x"))))
        assertEquals(Comprobacion.SinEnlaces("Mi fuente"), ComprobacionFuente.juzgar(NetworkResult.Success(addon("x", "catalog", "meta"))))
        assertEquals(Comprobacion.Valida("Mi fuente"), ComprobacionFuente.juzgar(NetworkResult.Success(addon("x", "meta", "stream"))))
    }

    @Test
    fun `el sitio se ensena sin la clave`() {
        assertEquals("a.example", ComprobacionFuente.sitio("https://a.example/torbox=secreto/manifest.json"))
    }

    private fun enlace(url: String? = null, infoHash: String? = null) = Stream(
        name = "x", title = null, description = null, url = url, ytId = null, infoHash = infoHash, fileIdx = null,
        externalUrl = null, behaviorHints = null, addonName = "a", addonLogo = null,
    )

    @Test
    fun `solo torrents cuando ningun enlace tiene direccion web`() {
        assertEquals(true, ComprobacionFuente.soloTorrents(listOf(enlace(infoHash = "abc"), enlace(url = "magnet:?xt=urn:btih:abc"))))
        // Con la parte de TorBox los enlaces llevan dirección web aunque traigan también el hash.
        assertEquals(false, ComprobacionFuente.soloTorrents(listOf(enlace(url = "https://a.example/play/1", infoHash = "abc"))))
        assertEquals(false, ComprobacionFuente.soloTorrents(listOf(enlace(infoHash = "abc"), enlace(url = "https://a.example/1"))))
        // Sin enlaces no se sabe: no se rechaza.
        assertEquals(false, ComprobacionFuente.soloTorrents(emptyList()))
    }

    @Test
    fun `solo se pregunta a las fuentes sin la parte de TorBox`() {
        val peliculas = addon("f", "stream").copy(types = emptyList(), idPrefixes = listOf("tt"))
        assertEquals("movie" to "tt0111161", ComprobacionFuente.sondeo("https://a.example/manifest.json", peliculas))
        assertNull(ComprobacionFuente.sondeo("https://a.example/torbox={torbox}/manifest.json", peliculas))
        // Solo series: se pregunta por un episodio.
        val series = addon("f").copy(resources = listOf(AddonResource("stream", listOf("series"), listOf("tt"))))
        assertEquals("series" to "tt0903747:1:1", ComprobacionFuente.sondeo("https://a.example/manifest.json", series))
        // Que no busca por IMDb: no hay a quién preguntar y se acepta.
        val otra = addon("f").copy(resources = listOf(AddonResource("stream", emptyList(), listOf("kitsu"))))
        assertNull(ComprobacionFuente.sondeo("https://a.example/manifest.json", otra))
    }

    @Test
    fun `el aviso de fuente sin TorBox dice que le falta el marcador`() {
        val t = ComprobacionFuente.texto(Comprobacion.SinTorbox("Mi fuente"))
        assertEquals(true, "no está preparada para TorBox" in t && "{torbox}" in t)
    }
}
