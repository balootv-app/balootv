package com.nuvio.tv.balootv.iconos

import org.junit.Assert.assertEquals
import org.junit.Test

/** Las piezas en una línea para comparar en las pruebas: los iconos como <SIMBOLO> y las insignias como [4K]. */
fun List<Pieza>.leidas(): String = joinToString(" ") {
    when (it) {
        is Pieza.Texto -> it.texto
        is Pieza.Icono -> "<${it.simbolo}>"
        is Pieza.Insignia -> "[${it.texto}]"
        Pieza.Separador -> "·"
    }
}

class PiezasTest {

    @Test
    fun `los grupos se unen con un punto y los vacios no cuentan`() {
        val piezas = conSeparadores(listOf(Pieza.Texto("2 h")), emptyList(), listOf(Pieza.Icono(Simbolo.TERMINA), Pieza.Texto("19:55")))
        assertEquals("2 h · <TERMINA> 19:55", piezas.leidas())
        assertEquals(emptyList<Pieza>(), conSeparadores(emptyList(), emptyList()))
    }
}
