package com.nuvio.tv.balootv.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerSmartTubeTest {

    @Test
    fun `elige SmartTube estable cuando esta instalado`() {
        val instalados = setOf("org.smarttube.stable", "com.google.android.youtube.tv")
        assertEquals("org.smarttube.stable", TrailerSmartTube.paquete { it in instalados })
    }

    @Test
    fun `prefiere la version estable a la beta`() {
        val instalados = setOf("org.smarttube.beta", "org.smarttube.stable")
        assertEquals("org.smarttube.stable", TrailerSmartTube.paquete { it in instalados })
    }

    @Test
    fun `usa la beta si es la unica instalada`() {
        assertEquals("org.smarttube.beta", TrailerSmartTube.paquete { it == "org.smarttube.beta" })
    }

    @Test
    fun `sin SmartTube no fuerza ninguna app`() {
        assertNull(TrailerSmartTube.paquete { false })
    }

    @Test
    fun `construye el enlace de YouTube del trailer`() {
        assertEquals("https://www.youtube.com/watch?v=abc123", TrailerSmartTube.enlace("abc123"))
    }
}
