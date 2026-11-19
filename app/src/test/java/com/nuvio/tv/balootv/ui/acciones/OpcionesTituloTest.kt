package com.nuvio.tv.balootv.ui.acciones

import com.nuvio.tv.balootv.ui.acciones.AccionTitulo.ANADIR_A_LISTA
import com.nuvio.tv.balootv.ui.acciones.AccionTitulo.DESMARCAR_VISTA
import com.nuvio.tv.balootv.ui.acciones.AccionTitulo.IR_A_FICHA
import com.nuvio.tv.balootv.ui.acciones.AccionTitulo.MARCAR_VISTA
import com.nuvio.tv.balootv.ui.acciones.AccionTitulo.QUITAR
import com.nuvio.tv.balootv.ui.acciones.AccionTitulo.QUITAR_DE_LISTA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpcionesTituloTest {

    @Test
    fun peliculaAMediasEnSeguirViendoOfreceQuitarMarcarYAnadir() {
        val s = SituacionTitulo(enSeguirViendo = true, vistoEntero = false, algoVisto = false, enMiLista = false)
        assertEquals(listOf(IR_A_FICHA, QUITAR, MARCAR_VISTA, ANADIR_A_LISTA), OpcionesTitulo.de(s))
    }

    @Test
    fun peliculaVistaSeDesmarcaEnLugarDeMarcarse() {
        val s = SituacionTitulo(enSeguirViendo = false, vistoEntero = true, algoVisto = true, enMiLista = false)
        assertEquals(listOf(DESMARCAR_VISTA, ANADIR_A_LISTA), OpcionesTitulo.de(s))
    }

    @Test
    fun serieEmpezadaOfreceMarcarElSiguienteYDesmarcarElUltimo() {
        val s = SituacionTitulo(enSeguirViendo = true, vistoEntero = false, algoVisto = true, enMiLista = false)
        assertEquals(listOf(IR_A_FICHA, QUITAR, MARCAR_VISTA, DESMARCAR_VISTA, ANADIR_A_LISTA), OpcionesTitulo.de(s))
    }

    @Test
    fun serieSinEmpezarEnUnaCaratulaSoloMarcaYAnade() {
        val s = SituacionTitulo(enSeguirViendo = false, vistoEntero = false, algoVisto = false, enMiLista = false)
        assertEquals(listOf(MARCAR_VISTA, ANADIR_A_LISTA), OpcionesTitulo.de(s))
    }

    @Test
    fun yaEnMiListaSeOfreceQuitarDeLaLista() {
        val s = SituacionTitulo(enSeguirViendo = true, vistoEntero = false, algoVisto = false, enMiLista = true)
        assertEquals(listOf(IR_A_FICHA, QUITAR, MARCAR_VISTA, QUITAR_DE_LISTA), OpcionesTitulo.de(s))
    }

    @Test
    fun caratulaVistaYEnMiListaSeDeshaceTodo() {
        val s = SituacionTitulo(enSeguirViendo = false, vistoEntero = true, algoVisto = true, enMiLista = true)
        assertEquals(listOf(DESMARCAR_VISTA, QUITAR_DE_LISTA), OpcionesTitulo.de(s))
    }

    @Test
    fun enUnaSerieElTextoDiceQueEpisodio() {
        assertEquals("Marcar T1 · E4 como visto", OpcionesTitulo.texto(MARCAR_VISTA, true, 1 to 4, 1 to 3))
        assertEquals("Marcar T1 · E3 como no visto", OpcionesTitulo.texto(DESMARCAR_VISTA, true, 1 to 4, 1 to 3))
    }

    @Test
    fun enUnaPeliculaElTextoEsGenerico() {
        assertEquals("Marcar como vista", OpcionesTitulo.texto(MARCAR_VISTA, false, null, null))
        assertEquals("Marcar como no vista", OpcionesTitulo.texto(DESMARCAR_VISTA, false, null, null))
    }

    private val serie = listOf(0 to 1, 1 to 1, 1 to 2, 2 to 1)

    @Test
    fun sinProgresoMarcaElUnoPorUno() {
        assertEquals(1 to 1, EpisodioQueToca.de(serie, emptySet()))
    }

    @Test
    fun aMediasMarcaElSiguienteSinVer() {
        assertEquals(1 to 2, EpisodioQueToca.de(serie, setOf(1 to 1)))
    }

    @Test
    fun saltaLosEpisodiosYaVistosAunqueNoSeanSeguidos() {
        assertEquals(2 to 1, EpisodioQueToca.de(serie, setOf(1 to 1, 1 to 2)))
    }

    @Test
    fun conTodoVistoNoQuedaNada() {
        assertNull(EpisodioQueToca.de(serie, setOf(1 to 1, 1 to 2, 2 to 1)))
    }

    @Test
    fun siSoloHayEspecialesMarcaElPrimero() {
        assertEquals(0 to 1, EpisodioQueToca.de(listOf(0 to 2, 0 to 1), emptySet()))
    }

    @Test
    fun episodiosDesordenadosSeOrdenan() {
        assertEquals(1 to 1, EpisodioQueToca.de(listOf(2 to 1, 1 to 2, 1 to 1), emptySet()))
    }

    @Test
    fun seDesmarcaElUltimoVistoSinContarEspeciales() {
        assertEquals(2 to 1, EpisodioQueToca.ultimoVisto(setOf(0 to 5, 1 to 2, 2 to 1, 1 to 9)))
    }

    @Test
    fun siSoloHayEspecialesVistosSeDesmarcaElUltimo() {
        assertEquals(0 to 3, EpisodioQueToca.ultimoVisto(setOf(0 to 1, 0 to 3)))
    }

    @Test
    fun sinNadaVistoNoHayQueDesmarcar() {
        assertNull(EpisodioQueToca.ultimoVisto(emptySet()))
    }
}
