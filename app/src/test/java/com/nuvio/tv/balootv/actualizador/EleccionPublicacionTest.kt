package com.nuvio.tv.balootv.actualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EleccionPublicacionTest {

    private fun apk(nombre: String) = Fichero(nombre, "https://github.com/x/y/releases/download/v1.2.0/$nombre", 30_000_000)

    private val ficheros = listOf(
        apk("BalooTV-1.2.0-armeabi-v7a.apk"),
        apk("BalooTV-1.2.0-arm64-v8a.apk"),
        Fichero("NOTAS.md", "https://github.com/x/y/NOTAS.md"),
    )
    private val publicacion = Publicacion(etiqueta = "v1.2.0", notas = "## Novedades\n- Fuentes desde el móvil", ficheros = ficheros)

    @Test
    fun `elige la APK de la arquitectura del aparato en su orden`() {
        assertEquals("BalooTV-1.2.0-arm64-v8a.apk", EleccionPublicacion.apkPara(ficheros, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))?.nombre)
        assertEquals("BalooTV-1.2.0-armeabi-v7a.apk", EleccionPublicacion.apkPara(ficheros, listOf("armeabi-v7a", "armeabi"))?.nombre)
    }

    @Test
    fun `nunca da una APK de otra arquitectura`() {
        assertNull(EleccionPublicacion.apkPara(ficheros, listOf("x86_64", "x86")))
        // x86 no se confunde con x86_64.
        assertNull(EleccionPublicacion.apkPara(listOf(apk("BalooTV-1.2.0-x86_64.apk")), listOf("x86")))
    }

    @Test
    fun `una APK sin arquitectura en el nombre vale para todas`() {
        assertEquals("BalooTV-1.2.0.apk", EleccionPublicacion.apkPara(listOf(apk("BalooTV-1.2.0.apk")), listOf("x86"))?.nombre)
    }

    @Test
    fun `solo una version mas nueva, estable y publicada`() {
        assertEquals("1.2.0", EleccionPublicacion.elegir(publicacion, "1.1.0", listOf("arm64-v8a"))?.version)
        assertNull(EleccionPublicacion.elegir(publicacion, "1.2.0", listOf("arm64-v8a")))
        assertNull(EleccionPublicacion.elegir(publicacion, "1.3.0", listOf("arm64-v8a")))
        assertNull(EleccionPublicacion.elegir(publicacion.copy(borrador = true), "1.1.0", listOf("arm64-v8a")))
        assertNull(EleccionPublicacion.elegir(publicacion.copy(previa = true), "1.1.0", listOf("arm64-v8a")))
        assertNull(EleccionPublicacion.elegir(publicacion.copy(etiqueta = "v1.2.0-rc.1"), "1.1.0", listOf("arm64-v8a")))
    }

    @Test
    fun `solo descargas por https`() {
        assertNull(EleccionPublicacion.apkPara(listOf(Fichero("BalooTV-1.2.0-arm64-v8a.apk", "http://x/a.apk")), listOf("arm64-v8a")))
        // Solo una compilación de desarrollo, para probar con una publicación servida en local.
        assertEquals("http://x/a.apk", EleccionPublicacion.apkPara(listOf(Fichero("BalooTV-1.2.0-arm64-v8a.apk", "http://x/a.apk")), listOf("arm64-v8a"), soloHttps = false)?.url)
    }

    @Test
    fun `lee la respuesta de GitHub`() {
        val json = """{"tag_name":"v1.2.0","name":"BalooTV 1.2.0","body":"Hola","draft":false,"prerelease":false,"id":1,
            "assets":[{"name":"BalooTV-1.2.0-arm64-v8a.apk","browser_download_url":"https://github.com/a.apk","size":12,"state":"uploaded"}]}"""
        val p = EleccionPublicacion.leer(json)!!
        assertEquals("v1.2.0", p.etiqueta)
        assertEquals(12L, p.ficheros.single().tamano)
        assertNull(EleccionPublicacion.leer("<html>"))
    }

    @Test
    fun `mas tarde vale tres dias para esa version`() {
        val dia = 24L * 3600_000L
        assertTrue(Aplazamiento.vigente("1.2.0", 0L, "1.2.0", 2 * dia))
        assertFalse(Aplazamiento.vigente("1.2.0", 0L, "1.2.0", 4 * dia))
        assertFalse(Aplazamiento.vigente("1.2.0", 0L, "1.3.0", dia))
        assertFalse(Aplazamiento.vigente(null, null, "1.2.0", dia))
    }
}
