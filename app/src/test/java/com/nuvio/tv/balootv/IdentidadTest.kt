package com.nuvio.tv.balootv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** BalooTV no se presenta como Nuvio en ningún texto y solo habla castellano (y el idioma base). */
class IdentidadTest {

    private val res = File("src/main/res")
    private val permitidas = setOf("about_gpl_attribution")
    private val cadena = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun `ningun texto menciona Nuvio salvo la atribucion GPL`() {
        val conNuvio = res.listFiles()!!.filter { it.name.startsWith("values") }
            .mapNotNull { File(it, "strings.xml").takeIf(File::exists) }
            .flatMap { f -> cadena.findAll(f.readText()).filter { m -> m.groupValues[1] !in permitidas && m.groupValues[2].contains("Nuvio", ignoreCase = true) }.map { "${f.parentFile.name}:${it.groupValues[1]}" } }
        assertEquals(emptyList<String>(), conNuvio)
    }

    @Test
    fun `solo quedan el idioma base y el castellano`() {
        val idiomas = res.listFiles()!!.map { it.name }.filter { it.startsWith("values-") && !Regex("""values-v\d+""").matches(it) }
        assertEquals(listOf("values-es"), idiomas)
    }

    @Test
    fun `la atribucion GPL a NuvioTV existe`() {
        assertTrue(File(res, "values/strings.xml").readText().contains("name=\"about_gpl_attribution\""))
    }
}
