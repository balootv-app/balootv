package com.nuvio.tv.balootv.inicio

import com.nuvio.tv.balootv.inicio.SemillaSiguiente.Visto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemillaSiguienteTest {

    private val dia = 24L * 60 * 60 * 1000
    private val ahora = 100 * dia
    private val desde = ahora - 60 * dia

    @Test
    fun `lo mas avanzado de lo visto en los dias del Inicio, aunque antes se viera mas`() {
        val vistos = listOf(Visto(1, 5, ahora - 90 * dia), Visto(1, 1, ahora - 11 * dia), Visto(1, 2, ahora - 20 * dia))
        assertEquals(Visto(1, 2, ahora - 20 * dia), SemillaSiguiente.elegir(vistos, masAvanzado = true, desdeMs = desde))
    }

    @Test
    fun `sin nada en esos dias, lo mas avanzado de todo, que luego corta el Inicio`() {
        val vistos = listOf(Visto(1, 5, ahora - 90 * dia), Visto(1, 1, ahora - 80 * dia))
        assertEquals(Visto(1, 5, ahora - 90 * dia), SemillaSiguiente.elegir(vistos, masAvanzado = true, desdeMs = desde))
        // Con «Todo» en los días del Inicio no hay corte.
        assertEquals(Visto(1, 5, ahora - 90 * dia), SemillaSiguiente.elegir(vistos, masAvanzado = true, desdeMs = null))
    }

    @Test
    fun `sin especiales, y con el ajuste de Nuvio quitado, lo ultimo visto`() {
        val vistos = listOf(Visto(0, 3, ahora - dia), Visto(2, 1, ahora - 30 * dia), Visto(1, 4, ahora - 2 * dia))
        assertEquals(Visto(2, 1, ahora - 30 * dia), SemillaSiguiente.elegir(vistos, masAvanzado = true, desdeMs = desde))
        assertEquals(Visto(1, 4, ahora - 2 * dia), SemillaSiguiente.elegir(vistos, masAvanzado = false, desdeMs = desde))
        assertNull(SemillaSiguiente.elegir(listOf(Visto(0, 1, ahora)), masAvanzado = true, desdeMs = desde))
    }

    @Test
    fun `un episodio recien visto sustituye a una semilla de fuera de los dias aunque este por detras`() {
        val vieja = Visto(1, 5, ahora - 90 * dia)
        assertTrue(SemillaSiguiente.prefiere(Visto(1, 1, ahora), vieja, desde))
        // Dentro de los días manda lo más avanzado; a igualdad, lo más reciente.
        assertFalse(SemillaSiguiente.prefiere(Visto(1, 1, ahora), Visto(1, 5, ahora - 20 * dia), desde))
        assertTrue(SemillaSiguiente.prefiere(Visto(1, 6, ahora), Visto(1, 5, ahora - 20 * dia), desde))
        assertTrue(SemillaSiguiente.prefiere(Visto(1, 5, ahora), Visto(1, 5, ahora - 20 * dia), desde))
        // Sin corte, como en Nuvio.
        assertFalse(SemillaSiguiente.prefiere(Visto(1, 1, ahora), vieja, null))
    }
}
