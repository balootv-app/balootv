package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.data.local.StreamAutoPlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModoSeleccionTest {

    @Test
    fun `en ajustes esenciales se alterna entre BalooTV y manual y siempre se puede volver a BalooTV`() {
        assertEquals(StreamAutoPlayMode.MANUAL, ModoSeleccion.siguienteEsencial(StreamAutoPlayMode.BALOOTV))
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.siguienteEsencial(StreamAutoPlayMode.MANUAL))
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.siguienteEsencial(StreamAutoPlayMode.FIRST_STREAM))
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.siguienteEsencial(StreamAutoPlayMode.REGEX_MATCH))
    }

    @Test
    fun `en ajustes completos BalooTV es la primera opcion`() {
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.opcionesCompletas.first())
        assertEquals(StreamAutoPlayMode.entries.toSet(), ModoSeleccion.opcionesCompletas.toSet())
    }

    @Test
    fun `los modos heredados de Nuvio pasan a BalooTV y manual se respeta`() {
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.normalizar(StreamAutoPlayMode.FIRST_STREAM))
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.normalizar(StreamAutoPlayMode.REGEX_MATCH))
        assertEquals(StreamAutoPlayMode.BALOOTV, ModoSeleccion.normalizar(StreamAutoPlayMode.BALOOTV))
        assertEquals(StreamAutoPlayMode.MANUAL, ModoSeleccion.normalizar(StreamAutoPlayMode.MANUAL))
    }
}
