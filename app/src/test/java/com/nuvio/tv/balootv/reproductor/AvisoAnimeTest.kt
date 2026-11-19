package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisoAnimeTest {

    private fun pista(i: Int, idioma: String?, nombre: String) = TrackInfo(index = i, name = nombre, language = idioma)
    private fun externo(idioma: String) = Subtitle(id = idioma, url = "https://x/$idioma.srt", lang = idioma, addonName = "OpenSubtitles", addonLogo = null)

    @Test
    fun `textos del tablero 23`() {
        val a = AvisoAnime.de("Kaiju n.º 8 · T2 · E4", hayLatinos = true, esEpisodio = true)
        assertEquals("Kaiju n.º 8 · T2 · E4 · Japonés", a.cabecera)
        assertEquals("No hay subtítulos en castellano para este episodio", a.titulo)
        assertEquals("Solo los he encontrado en español latinoamericano. Puedes verlo así, sin subtítulos, o dejarlo para más tarde.", a.texto)
        val b = AvisoAnime.de("Suzume", hayLatinos = false, esEpisodio = false)
        assertEquals("No hay subtítulos en castellano para esta película", b.titulo)
        assertEquals("Tampoco los hay en español latinoamericano. Puedes verla sin subtítulos o dejarla para más tarde.", b.texto)
    }

    @Test
    fun `el latino interno va antes que el externo y nunca se toma un castellano por latino`() {
        val interno = pista(2, "spa", "Español (Latinoamérica)")
        assertEquals(LatinoElegido.Interno(interno), AvisoAnime.latino(listOf(pista(1, "eng", "English"), interno), listOf(externo("es-MX"))))
        assertEquals(LatinoElegido.Externo(externo("es-419")), AvisoAnime.latino(listOf(pista(1, "eng", "English")), listOf(externo("en"), externo("es-419"))))
        assertNull(AvisoAnime.latino(listOf(pista(1, "spa", "Castellano")), listOf(externo("en"))))
    }
}
