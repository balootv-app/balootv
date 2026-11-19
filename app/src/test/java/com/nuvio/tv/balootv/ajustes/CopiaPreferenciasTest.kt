package com.nuvio.tv.balootv.ajustes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopiaPreferenciasTest {

    @Test
    fun `ida y vuelta sin perder nada`() {
        val c = CopiaPreferencias(modoManual = true, tamanoSubtitulos = 130, alturaSubtitulos = 15, plataformas = listOf(8, 337), listaNegra = listOf("abc#0", "def#"))
        assertEquals(c, CopiaPreferencias.deJson(c.aJson()))
    }

    @Test
    fun `un fichero roto, de otra version o sin lista negra`() {
        assertNull(CopiaPreferencias.deJson("no es json"))
        assertNull(CopiaPreferencias.deJson("""{"version":2}"""))
        assertEquals(emptyList<String>(), CopiaPreferencias.deJson("""{"version":1,"modoManual":false}""")!!.listaNegra)
    }
}
