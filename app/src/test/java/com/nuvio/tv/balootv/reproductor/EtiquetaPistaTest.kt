package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.ui.screens.player.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EtiquetaPistaTest {

    private fun audio(idioma: String?, nombre: String, codec: String?, canales: Int?) =
        TrackInfo(index = 0, name = nombre, language = idioma, codec = codec, channelCount = canales)

    @Test
    fun `idioma en castellano, codec y canales`() {
        assertEquals("Castellano · DTS 5.1", EtiquetaPista.audio(audio("spa", "Castellano", "audio/vnd.dts", 6)))
        assertEquals("Castellano · AC-3 5.1", EtiquetaPista.audio(audio("es", "", "audio/ac3", 6)))
        assertEquals("Inglés · TrueHD 7.1", EtiquetaPista.audio(audio("eng", "English", "audio/true-hd", 8)))
        assertEquals("Latino · E-AC-3 5.1", EtiquetaPista.audio(audio("spa", "Español Latino", "audio/eac3", 6)))
        assertEquals("Japonés · AAC estéreo", EtiquetaPista.audio(audio("jpn", "", "audio/mp4a-latm", 2)))
        assertEquals("Pista 2", EtiquetaPista.audio(audio(null, "Pista 2", null, null)))
    }

    @Test
    fun `pista sin idioma de un fichero en castellano`() {
        val fichero = "Regreso al futuro [4K UHDmicro][HDR10][Castellano DTS 5.1]"
        assertEquals("Castellano · DTS 5.1", EtiquetaPista.audio(audio("und", "Audio 1 (DTS 5.1)", null, null), fichero))
        assertEquals("Castellano · AC-3 5.1", EtiquetaPista.audio(audio(null, "Audio 2 (AC-3 5.1)", null, 6), fichero))
        assertEquals("Audio 3 (AAC 2.0)", EtiquetaPista.audio(audio("und", "Audio 3 (AAC 2.0)", null, null), "Movie.2020.1080p.mkv"))
        assertEquals("Inglés · AC-3 5.1", EtiquetaPista.audio(audio("eng", "", "audio/ac3", 6), fichero))
    }
}
