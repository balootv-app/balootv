package com.nuvio.tv.balootv.actualizador

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineaDeNotasTest {
    @Test
    fun `quita las comillas de codigo y la negrita`() {
        val notas = "## Novedades\n\n- Las fuentes con `{torbox}` se añaden **solas**.\n- Otra cosa."
        assertEquals("Las fuentes con {torbox} se añaden solas.", lineaDeNotas(notas))
    }

    @Test
    fun `sin texto no hay linea`() {
        assertNull(lineaDeNotas("## Solo un título\n\n"))
    }
}
