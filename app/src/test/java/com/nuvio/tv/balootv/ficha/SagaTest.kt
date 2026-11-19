package com.nuvio.tv.balootv.ficha

import com.nuvio.tv.data.remote.api.TmdbCollectionPart
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SagaTest {

    private val hoy = LocalDate.of(2026, 10, 31)

    private fun parte(id: Int, fecha: String?) = TmdbCollectionPart(id = id, title = "P$id", releaseDate = fecha)

    @Test
    fun `por fecha de estreno, sin lo que no ha salido ni lo que no tiene fecha`() {
        // La colección de Avatar tal como la ordena TMDB: la 4 y la 5 aún no han salido.
        val partes = listOf(
            parte(216527, "2029-12-19"),
            parte(76600, "2022-12-14"),
            parte(19995, "2009-12-15"),
            parte(393209, null),
            parte(83533, "2025-12-17"),
        )
        assertEquals(listOf(19995, 76600, 83533), Saga.partes(partes, hoy).map { it.id })
    }

    @Test
    fun `la que se estrena hoy se queda`() {
        val partes = listOf(parte(1, "2020-01-01"), parte(2, "2026-10-31"), parte(3, "2026-11-01"))
        assertEquals(listOf(1, 2), Saga.partes(partes, hoy).map { it.id })
    }

    @Test
    fun `si empatan en fecha, por id`() {
        val partes = listOf(parte(9, "2001-05-01"), parte(4, "2001-05-01"), parte(7, "1999-01-01"))
        assertEquals(listOf(7, 4, 9), Saga.partes(partes, hoy).map { it.id })
    }

    @Test
    fun `con menos de dos partes estrenadas no hay fila`() {
        val partes = listOf(parte(1, "2024-03-01"), parte(2, "2027-03-01"), parte(3, ""))
        assertEquals(emptyList<Int>(), Saga.partes(partes, hoy).map { it.id })
    }

    @Test
    fun `una fecha que no se entiende cuenta como sin fecha`() {
        val partes = listOf(parte(1, "2010"), parte(2, "2011-02-03"), parte(3, "2012-02-03"))
        assertEquals(listOf(2, 3), Saga.partes(partes, hoy).map { it.id })
    }

    @Test
    fun `el titulo es el nombre de la coleccion tal cual, o La saga si viene vacio`() {
        assertEquals("Avatar - Colección", Saga.titulo("Avatar - Colección"))
        assertEquals("La saga", Saga.titulo(""))
        assertEquals("La saga", Saga.titulo("  "))
        assertEquals("La saga", Saga.titulo(null))
    }
}
