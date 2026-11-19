package com.nuvio.tv.balootv.fuentes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClasificadorCalidadTest {

    private fun calidad(texto: String, bytes: Long? = null) = ClasificadorCalidad.clasificar(
        Candidato(texto = texto, addon = "ejemplo.addon", enCache = true, bytes = bytes, infoHash = null, bingeGroup = null, stream = null)
    )

    @Test
    fun `remux 4K HDR10 con audio castellano DTS y tamano del texto`() {
        val q = calidad("Regreso al futuro [4K UHDremux][HDR10][Castellano DTS 5.1-AC3 5.1-Ingles Dolby TrueHD 7.1+Subs] | 👤 0 💾 64.39 GB ⚙️ Proveedor")
        assertEquals(2160, q.alto)
        assertEquals(Hdr.HDR10, q.hdr)
        assertEquals(Tipo.REMUX, q.tipo)
        assertEquals(Audio.DTS, q.audio)
        assertEquals(64_390_000_000L, q.bytes)
        assertFalse(q.camara)
    }

    @Test
    fun `WEB-DL 1080p x265 con castellano EAC3`() {
        val q = calidad("Dune Parte Dos (2024) [WEB-DL 1080p X265 10bits][Castellano EAC3 5.1Ingles AC3 5.1][Subs].mkv")
        assertEquals(1080, q.alto)
        assertEquals(Audio.EAC3, q.audio)
        assertEquals(Tipo.WEBDL, q.tipo)
        assertEquals("HEVC", q.codec)
    }

    @Test
    fun `Dolby Vision se reconoce antes que HDR10`() {
        assertEquals(Hdr.DV, calidad("The.Last.Of.Us.S02E01.2160p.UHD.BluRay.REMUX.DV.P7.HDR.TrueHD.7.1.mkv").hdr)
    }

    @Test
    fun `720p se lee como 720`() {
        assertEquals(720, calidad("Dune Parte dos (2024) [Bluray 720p][Esp](ejemplo.addon).mkv").alto)
    }

    @Test
    fun `4k en el nombre es 2160`() {
        assertEquals(2160, calidad("Dune Parte Dos (2024) [4k 2160p][Esp]").alto)
    }

    @Test
    fun `CAM y TS son camara`() {
        assertTrue(calidad("Superman.2025.CAM.x264.mkv").camara)
        assertTrue(calidad("Superman 2025 TS 1080p").camara)
    }

    @Test
    fun `los bytes del candidato tienen prioridad sobre el texto`() {
        assertEquals(5_000L, calidad("Peli 1080p 💾 9.62 GB", bytes = 5_000L).bytes)
    }

    @Test
    fun `tamano en MB`() {
        assertEquals(590_690_000L, calidad("Making of [HDTV 1080p] 💾 590.69 MB").bytes)
    }
}
