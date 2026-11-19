package com.nuvio.tv.balootv.fuentes

import org.junit.Assert.assertEquals
import org.junit.Test

class TextoSinCastellanoTest {

    @Test
    fun `aviso del tablero 22`() {
        val a = TextoSinCastellano.de("The Bear · T4 · E1", "en")
        assertEquals("The Bear · T4 · E1", a.cabecera)
        assertEquals("Aún no hay versión en castellano", a.titulo)
        assertEquals(
            "He buscado en todas tus fuentes y no hay audio en castellano de España a 1080p o más. Suele llegar unas semanas después del estreno.",
            a.texto
        )
        assertEquals("Ver en versión original", a.botonVO)
        assertEquals("Inglés · subtítulos en castellano", a.detalleVO)
    }

    @Test
    fun `idioma original en castellano o desconocido`() {
        assertEquals("Japonés · subtítulos en castellano", TextoSinCastellano.de("Frieren", "ja").detalleVO)
        assertEquals("Coreano · subtítulos en castellano", TextoSinCastellano.de("x", "ko").detalleVO)
        assertEquals("Subtítulos en castellano", TextoSinCastellano.de("x", null).detalleVO)
        assertEquals("Subtítulos en castellano", TextoSinCastellano.de("x", "zz").detalleVO)
    }

    @Test
    fun `solo en latino lo dice`() {
        val a = TextoSinCastellano.de("Coco", "en", Motivo.SOLO_LATINO)
        assertEquals("Solo hay versión en latino", a.titulo)
        assertEquals("He buscado en todas tus fuentes y solo hay doblaje latino, no castellano de España.", a.texto)
        assertEquals("Aún no hay versión en castellano", TextoSinCastellano.de("x", null, Motivo.SIN_CASTELLANO).titulo)
    }
}
