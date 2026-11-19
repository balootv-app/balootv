package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.reproductor.VigilanteAtascos.Accion
import org.junit.Assert.assertEquals
import org.junit.Test

class VigilanteAtascosTest {

    private fun paso(v: VigilanteAtascos, t: Long, pos: Long, reproduciendo: Boolean = true, cargado: Long = 50_000) =
        v.observar(ahoraMs = t, reproduciendo = reproduciendo, posMs = pos, cargadoDelanteMs = cargado)

    @Test
    fun `reproduciendo con margen y la posicion quieta 3 s pide buscar`() {
        val v = VigilanteAtascos()
        assertEquals(Accion.NADA, paso(v, 0, 0))
        assertEquals(Accion.NADA, paso(v, 1_500, 0))
        assertEquals(Accion.NADA, paso(v, 2_900, 0))
        assertEquals(Accion.BUSCAR, paso(v, 3_100, 0))
    }

    @Test
    fun `si la posicion avanza no hay atasco`() {
        val v = VigilanteAtascos()
        (0..20).forEach { i -> assertEquals(Accion.NADA, paso(v, i * 500L, i * 500L)) }
    }

    @Test
    fun `cargando, en pausa o sin margen no es un atasco`() {
        val v = VigilanteAtascos()
        (0..10).forEach { i -> assertEquals(Accion.NADA, paso(v, i * 500L, 0, reproduciendo = false)) }
        val w = VigilanteAtascos()
        (0..10).forEach { i -> assertEquals(Accion.NADA, paso(w, i * 500L, 0, cargado = 500)) }
    }

    @Test
    fun `si buscar no basta, poco despues se recrea el reproductor`() {
        val v = VigilanteAtascos()
        paso(v, 0, 0)
        assertEquals(Accion.BUSCAR, paso(v, 3_100, 0))
        assertEquals(Accion.NADA, paso(v, 6_500, 0))
        assertEquals(Accion.REINICIAR, paso(v, 11_200, 0))
    }

    @Test
    fun `si se recupera, el siguiente atasco vuelve a empezar por buscar`() {
        val v = VigilanteAtascos()
        paso(v, 0, 0)
        assertEquals(Accion.BUSCAR, paso(v, 3_100, 0))
        (1..40).forEach { i -> paso(v, 3_100 + i * 500L, i * 500L) }
        paso(v, 30_000, 25_000)
        assertEquals(Accion.BUSCAR, paso(v, 33_200, 25_000))
    }

    @Test
    fun `si tras buscar avanza un par de segundos y se vuelve a quedar quieta, se recrea el reproductor`() {
        // Lupin T4·E3 en la tele (31-10-2026): tras cinco saltos seguidos, cada búsqueda daba unos 2 s de imagen y
        // otra vez quieta. Contar esos 2 s como recuperada dejaba buscar cada 4 s sin llegar a recrear el reproductor.
        val v = VigilanteAtascos()
        paso(v, 0, 99_000)
        assertEquals(Accion.BUSCAR, paso(v, 3_100, 99_000))
        paso(v, 4_500, 100_000)
        paso(v, 5_500, 101_400)
        assertEquals(Accion.NADA, paso(v, 7_000, 101_400))
        assertEquals(Accion.REINICIAR, paso(v, 8_600, 101_400))
    }

    @Test
    fun `tras buscar, un salto lejos del atasco vuelve a empezar por buscar`() {
        val v = VigilanteAtascos()
        paso(v, 0, 300_000)
        assertEquals(Accion.BUSCAR, paso(v, 3_100, 300_000))
        paso(v, 5_000, 240_000)
        assertEquals(Accion.BUSCAR, paso(v, 9_200, 240_000))
    }

    @Test
    fun `el baile de milisegundos tras buscar no cuenta como avance`() {
        val v = VigilanteAtascos()
        paso(v, 0, 0)
        assertEquals(Accion.BUSCAR, paso(v, 3_100, 0))
        paso(v, 4_000, 48)
        paso(v, 6_000, 16)
        assertEquals(Accion.REINICIAR, paso(v, 11_200, 32))
    }
}
