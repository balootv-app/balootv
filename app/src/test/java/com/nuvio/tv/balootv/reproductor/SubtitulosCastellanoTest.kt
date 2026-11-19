package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitulosCastellanoTest {

    @Test
    fun `reconoce castellano y rechaza latino aunque diga spa`() {
        assertTrue(SubtitulosCastellano.esCastellano("spa", "Castellano"))
        assertTrue(SubtitulosCastellano.esCastellano("es", null))
        assertTrue(SubtitulosCastellano.esCastellano("es-ES", "Forzados"))
        assertFalse(SubtitulosCastellano.esCastellano("spa", "Español (Latinoamérica)"))
        assertFalse(SubtitulosCastellano.esCastellano("es-419", null))
        assertFalse(SubtitulosCastellano.esCastellano("spa", "LAT"))
        assertFalse(SubtitulosCastellano.esCastellano("es-MX", null))
        assertFalse(SubtitulosCastellano.esCastellano("eng", "English"))
        assertFalse(SubtitulosCastellano.esCastellano(null, null))
    }

    private fun pista(i: Int, idioma: String?, nombre: String, forzada: Boolean = false) =
        TrackInfo(index = i, name = nombre, language = idioma, isForced = forzada)

    private fun externo(idioma: String) = Subtitle(id = idioma, url = "https://x/$idioma.srt", lang = idioma, addonName = "OpenSubtitles", addonLogo = null)

    @Test
    fun `separa forzados de completos y prefiere los internos`() {
        val forzado = pista(0, "spa", "Forzados", forzada = true)
        val completo = pista(1, "spa", "Castellano")
        val latino = pista(2, "spa", "Latino")
        val o = SubtitulosCastellano.opciones(listOf(forzado, completo, latino), listOf(externo("es")))
        assertEquals(forzado, o.forzados)
        assertEquals(completo, o.completoInterno)
        assertTrue(o.hayCastellano)
    }

    @Test
    fun `sin interno castellano usa el externo y sin nada lo dice`() {
        val o = SubtitulosCastellano.opciones(listOf(pista(0, "eng", "English")), listOf(externo("es"), externo("es-419")))
        assertNull(o.completoInterno)
        assertEquals("es", o.completoExterno?.lang)

        val nada = SubtitulosCastellano.opciones(listOf(pista(0, "spa", "Latino")), listOf(externo("es-MX")))
        assertFalse(nada.hayCastellano)
    }

    @Test
    fun `forzados reconocidos por el nombre aunque no lleven la marca`() {
        val o = SubtitulosCastellano.opciones(listOf(pista(0, "spa", "Castellano"), pista(1, "spa", "Spanish Forced")), emptyList())
        assertEquals(1, o.forzados?.index)
        assertEquals(0, o.completoInterno?.index)
    }

    private fun sinNombre(i: Int, idioma: String? = "spa", porDefecto: Boolean = false) =
        TrackInfo(index = i, name = idioma ?: "", language = idioma, isDefault = porDefecto)

    @Test
    fun `dos pistas castellanas sin nombre - la que se pone sola son los forzados y la otra los completos`() {
        // Resident Evil (2026) 4K de un proveedor español, visto en el móvil el 18-11-2026 (balootv-movil 1d8b0f7).
        val primera = sinNombre(0, porDefecto = true)
        val segunda = sinNombre(1)
        val o = SubtitulosCastellano.opciones(listOf(primera, segunda, pista(2, "eng", "English")), emptyList())
        assertEquals(primera, o.forzados)
        assertEquals(segunda, o.completoInterno)
        // La marcada «por defecto» manda aunque vaya segunda.
        val alReves = SubtitulosCastellano.opciones(listOf(sinNombre(0), sinNombre(1, porDefecto = true)), emptyList())
        assertEquals(1, alReves.forzados?.index)
        assertEquals(0, alReves.completoInterno?.index)
        // Sin marca de «por defecto», la primera.
        val sinDefecto = SubtitulosCastellano.opciones(listOf(sinNombre(0), sinNombre(1)), emptyList())
        assertEquals(0, sinDefecto.forzados?.index)
        assertEquals(1, sinDefecto.completoInterno?.index)
        // Lo que está puesto se ve como «Solo forzados» en el panel.
        assertEquals(EleccionSubtitulos.FORZADOS, SubtitulosCastellano.eleccion(o, listOf(primera, segunda), 0, conAddon = false))
        assertEquals(EleccionSubtitulos.CASTELLANO, SubtitulosCastellano.eleccion(o, listOf(primera, segunda), 1, conAddon = false))
    }

    @Test
    fun `una sola pista sin nombre son los completos`() {
        val sola = sinNombre(0, idioma = "es", porDefecto = true)
        val o = SubtitulosCastellano.opciones(listOf(sola), emptyList())
        assertNull(o.forzados)
        assertEquals(sola, o.completoInterno)
    }

    @Test
    fun `con una forzada marcada y varias iguales detras, la completa es la ultima`() {
        // La llegada 4K de otro proveedor, visto en el Oppo y la Pad 6 el 18-11-2026 (balootv-movil e3a037e): tres
        // pistas «[www.ejemplo-descargas.com]»; la forzada, una sin una línea en los diálogos y la completa.
        val forzada = TrackInfo(index = 0, name = "[www.ejemplo-descargas.com]", language = "es", isForced = true, isDefault = true)
        val vacia = pista(1, "es", "[www.ejemplo-descargas.com]")
        val completa = pista(2, "es", "[www.ejemplo-descargas.com]")
        val o = SubtitulosCastellano.opciones(listOf(forzada, vacia, completa, pista(3, "en", "[www.ejemplo-descargas.com]")), emptyList())
        assertEquals(forzada, o.forzados)
        assertEquals(completa, o.completoInterno)
        // Tres sin marca ni nombre: la que se pone sola son los forzados; de las otras dos, la última.
        val tres = SubtitulosCastellano.opciones(listOf(sinNombre(0, porDefecto = true), sinNombre(1), sinNombre(2)), emptyList())
        assertEquals(0, tres.forzados?.index)
        assertEquals(2, tres.completoInterno?.index)
    }

    @Test
    fun `dos completas con nombre no se toman por forzados`() {
        val completos = TrackInfo(index = 0, name = "Completos", language = "spa", isDefault = true)
        val sordos = TrackInfo(index = 1, name = "SDH", language = "spa")
        val o = SubtitulosCastellano.opciones(listOf(completos, sordos), emptyList())
        assertNull(o.forzados)
        assertEquals(completos, o.completoInterno)
    }

    @Test
    fun `dos completas que se distinguen por el nombre no se toman por forzados`() {
        val castellano = TrackInfo(index = 0, name = "Castellano", language = "spa", isDefault = true)
        for (otra in listOf("Castellano SDH", "Castellano PGS")) {
            val o = SubtitulosCastellano.opciones(listOf(castellano, TrackInfo(index = 1, name = otra, language = "spa")), emptyList())
            assertNull(otra, o.forzados)
            assertEquals(castellano, o.completoInterno)
        }
        // Con el mismo nombre en las dos no hay forma de distinguirlas: se aplica la regla.
        val iguales = SubtitulosCastellano.opciones(
            listOf(castellano, TrackInfo(index = 1, name = "Castellano", language = "spa")), emptyList(),
        )
        assertEquals(0, iguales.forzados?.index)
        assertEquals(1, iguales.completoInterno?.index)
    }

    @Test
    fun `una latina por medio no cuenta como segunda pista castellana`() {
        val castellana = sinNombre(0, porDefecto = true)
        val latina = pista(1, "spa", "Latino")
        val o = SubtitulosCastellano.opciones(listOf(castellana, latina), emptyList())
        assertNull(o.forzados)
        assertEquals(castellana, o.completoInterno)
        // Con dos castellanas y una latina en medio, la regla se aplica solo a las castellanas.
        val tres = SubtitulosCastellano.opciones(listOf(castellana, latina, sinNombre(2)), emptyList())
        assertEquals(0, tres.forzados?.index)
        assertEquals(2, tres.completoInterno?.index)
    }

    @Test
    fun `Latin American no es castellano`() {
        // Encargo 002: así se llaman las pistas latinas de los discos y ripeos de plataformas.
        assertFalse(SubtitulosCastellano.esCastellano("spa", "Latin American"))
        assertFalse(SubtitulosCastellano.esCastellano("spa", "Latin American PGS"))
        assertFalse(SubtitulosCastellano.esCastellano("spa", "Spanish (Latin America)"))
        assertTrue(SubtitulosCastellano.esCastellano("spa", "European Spanish (SDH)"))
        assertTrue(SubtitulosCastellano.esCastellano("spa", "Castilian [Full]"))
    }
}
