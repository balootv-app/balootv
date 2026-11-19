package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.reproductor.SubtitulosAlRetroceder.Tramo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitulosAlRetrocederTest {

    @Test
    fun `al retroceder 10 s con los subtitulos apagados se encienden hasta el punto de partida`() {
        val tramo = SubtitulosAlRetroceder.alRetroceder(600_000, 590_000, EleccionSubtitulos.DESACTIVADOS, hayCompletos = true, tramo = null)
        assertEquals(Tramo(hastaMs = 600_000, antes = EleccionSubtitulos.DESACTIVADOS), tramo)
    }

    @Test
    fun `con solo los forzados tambien, y al acabar se vuelve a los forzados`() {
        val tramo = SubtitulosAlRetroceder.alRetroceder(600_000, 590_000, EleccionSubtitulos.FORZADOS, hayCompletos = true, tramo = null)
        assertEquals(EleccionSubtitulos.FORZADOS, tramo?.antes)
    }

    @Test
    fun `con los completos ya puestos no hay nada que hacer`() {
        assertNull(SubtitulosAlRetroceder.alRetroceder(600_000, 590_000, EleccionSubtitulos.CASTELLANO, hayCompletos = true, tramo = null))
    }

    @Test
    fun `sin pista interna completa en castellano no se enciende nada`() {
        assertNull(SubtitulosAlRetroceder.alRetroceder(600_000, 590_000, EleccionSubtitulos.DESACTIVADOS, hayCompletos = false, tramo = null))
    }

    @Test
    fun `ir hacia delante o muy atras no es repetir una frase`() {
        assertNull(SubtitulosAlRetroceder.alRetroceder(600_000, 610_000, EleccionSubtitulos.DESACTIVADOS, hayCompletos = true, tramo = null))
        assertNull(SubtitulosAlRetroceder.alRetroceder(600_000, 600_000 - SubtitulosAlRetroceder.MAX_TRAMO_MS - 1, EleccionSubtitulos.DESACTIVADOS, hayCompletos = true, tramo = null))
    }

    @Test
    fun `retroceder otra vez dentro del tramo lo alarga sin perder el punto de partida`() {
        val primero = Tramo(hastaMs = 600_000, antes = EleccionSubtitulos.FORZADOS)
        // La elección actual ya es «castellano» porque el tramo los encendió: no cuenta.
        val segundo = SubtitulosAlRetroceder.alRetroceder(595_000, 585_000, EleccionSubtitulos.CASTELLANO, hayCompletos = true, tramo = primero)
        assertEquals(primero, segundo)
    }

    @Test
    fun `retroceder mucho dentro del tramo lo termina`() {
        val tramo = Tramo(hastaMs = 600_000, antes = EleccionSubtitulos.DESACTIVADOS)
        assertNull(SubtitulosAlRetroceder.alRetroceder(595_000, 500_000, EleccionSubtitulos.CASTELLANO, hayCompletos = true, tramo = tramo))
    }

    @Test
    fun `el tramo termina al volver al punto de partida`() {
        val tramo = Tramo(hastaMs = 600_000, antes = EleccionSubtitulos.DESACTIVADOS)
        assertFalse(SubtitulosAlRetroceder.terminado(599_500, tramo))
        assertTrue(SubtitulosAlRetroceder.terminado(600_000, tramo))
    }
}
