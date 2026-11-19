package com.nuvio.tv.balootv.fuentes

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantillaFuenteTest {

    private fun b64url(texto: String) = texto.encodeUtf8().base64Url().trimEnd('=')

    @Test
    fun `cambia el marcador tal cual y escapado`() {
        assertEquals(
            "https://fuente.example/idioma=es%7Ctorbox=abc-123/manifest.json",
            PlantillaFuente.rellenar("https://fuente.example/idioma=es%7Ctorbox={torbox}/manifest.json", "abc-123"),
        )
        assertEquals(
            "https://fuente.example/abc-123/manifest.json",
            PlantillaFuente.rellenar("https://fuente.example/%7btorbox%7D/manifest.json", "abc-123"),
        )
    }

    @Test
    fun `cambia el marcador dentro de una configuracion en base64 sin relleno`() {
        val plantilla = "https://otra.example/stremio/ejemplo/${b64url("""{"stores":[{"c":"tb","t":"{torbox}"}]}""")}/manifest.json"
        assertEquals(
            "https://otra.example/stremio/ejemplo/eyJzdG9yZXMiOlt7ImMiOiJ0YiIsInQiOiJhYmMtMTIzIn1dfQ/manifest.json",
            PlantillaFuente.rellenar(plantilla, "abc-123"),
        )
        assertTrue(PlantillaFuente.lleva(plantilla))
    }

    private fun trozo(url: String) = url.removePrefix("https://x.example/").removeSuffix("/manifest.json")

    @Test
    fun `respeta el alfabeto estandar del base64 original`() {
        val estandar = """{"debrid":"{torbox}","q":"~>"}""".encodeUtf8().base64()
        assertTrue('+' in estandar)
        val salida = trozo(PlantillaFuente.rellenar("https://x.example/$estandar/manifest.json", "k"))
        assertEquals("""{"debrid":"k","q":"~>"}""", salida.decodeBase64()!!.utf8())
        assertEquals("""{"debrid":"k","q":"~>"}""".encodeUtf8().base64(), salida)
    }

    @Test
    fun `respeta el relleno del base64 original`() {
        val conRelleno = """{"k":"{torbox}","z":"~~"}""".encodeUtf8().base64Url()
        assertTrue(conRelleno.endsWith("="))
        val salida = trozo(PlantillaFuente.rellenar("https://x.example/$conRelleno/manifest.json", "clave-1"))
        assertEquals("""{"k":"clave-1","z":"~~"}""".encodeUtf8().base64Url(), salida)
    }

    @Test
    fun `sin marcador la direccion no cambia ni necesita clave`() {
        val url = "https://v3-cinemeta.strem.io/manifest.json"
        assertEquals(url, PlantillaFuente.rellenar(url, "abc"))
        assertFalse(PlantillaFuente.lleva(url))
        // Un trozo que parece base64 pero no es texto se deja como está.
        val raro = "https://x.example/AAAAAAAAAAAA/manifest.json"
        assertEquals(raro, PlantillaFuente.rellenar(raro, "abc"))
    }

    @Test
    fun `lee una lista pegada quitando vacias comentarios y repetidas`() {
        assertEquals(
            listOf("https://a.example/manifest.json", "https://b.example/manifest.json"),
            PlantillaFuente.leerLista(" https://a.example/manifest.json\n\n# comentario\r\nhttps://b.example/manifest.json\nhttps://a.example/manifest.json "),
        )
    }
}
