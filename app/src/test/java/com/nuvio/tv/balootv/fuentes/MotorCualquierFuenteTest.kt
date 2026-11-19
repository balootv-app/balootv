package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.balootv.arranque.Arranque
import com.nuvio.tv.balootv.arranque.DestinoArranque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El motor de castellano no depende de qué fuentes haya: con cero, una o varias (de nombres cualesquiera) elige
 * igual, y el arranque no espera a fuentes que no existen (edición pública recién instalada).
 */
class MotorCualquierFuenteTest {

    private val peticion = Peticion(TitulosTmdb(es = "Regreso al futuro", mx = "Volver al futuro", original = "Back to the Future"), 1985, null, null)

    private fun c(texto: String, addon: String) =
        Candidato(texto = texto, addon = addon, enCache = true, bytes = null, infoHash = null, bingeGroup = null, stream = null)

    @Test
    fun `sin fuentes no hay eleccion y se dice por que`() {
        val r = SelectorBalooTV.elegir(emptyList(), peticion)
        assertTrue(r is Eleccion.SinEleccion)
        assertEquals(Motivo.SIN_CASTELLANO, (r as Eleccion.SinEleccion).motivo)
    }

    @Test
    fun `con una sola fuente de nombre cualquiera elige su castellano`() {
        val bueno = c("Regreso al futuro (1985) 2160p UHD BluRay [Castellano AC3 5.1] [HDR10]", "Mi addon")
        val r = SelectorBalooTV.elegir(listOf(c("Back.to.the.Future.1985.2160p.UHD.BluRay.x265-ENG", "Mi addon"), bueno), peticion)
        assertEquals(bueno, (r as Eleccion.Elegido).mejor.candidato)
    }

    @Test
    fun `con varias fuentes gana el mejor castellano venga de donde venga`() {
        val a1080 = c("Regreso al Futuro 1 [BluRay 1080p][AC3 5.1 Castellano]", "Fuente A")
        val b4k = c("Regreso al futuro [4K UHDremux][HDR10][Castellano TrueHD 7.1]", "Fuente B")
        val cLatino = c("Volver.al.futuro.1985.2160p.WEB-DL-Dual-Lat.mkv", "Fuente C")
        val r = SelectorBalooTV.elegir(listOf(cLatino, a1080, b4k), peticion) as Eleccion.Elegido
        assertEquals(b4k, r.mejor.candidato)
        assertTrue(r.lista.none { it.candidato == cLatino })
    }

    @Test
    fun `sin fuentes de serie el arranque no espera`() {
        assertTrue(Arranque.fuentesListas(emptyList(), FuentesDeSerie.urlsPara("clave", emptyList())))
        assertEquals(DestinoArranque.INICIO, Arranque.destino(fuentesListas = true, tiempoAgotado = false))
    }
}
