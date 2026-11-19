package com.nuvio.tv.balootv.ui

import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryListTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiListaTest {

    private val lista = LibraryListTab(key = "trakt:watchlist", title = "Watchlist", type = LibraryListTab.Type.WATCHLIST)
    private val otra = LibraryListTab(key = "trakt:list:9", title = "Favoritas", type = LibraryListTab.Type.PERSONAL)
    private val recomendadas = LibraryListTab(key = "trakt:list:7", title = "Recomendadas", type = LibraryListTab.Type.PERSONAL)

    private fun e(
        id: String, tipo: String, generos: List<String> = emptyList(), listas: Set<String> = setOf("trakt:watchlist"),
        cuando: Long = 0, fechas: Map<String, Long> = emptyMap(), tmdb: Int? = null,
    ) = LibraryEntry(
        id = id, type = tipo, name = id, poster = null, background = null, logo = null, description = null,
        releaseInfo = null, imdbRating = null, genres = generos, addonBaseUrl = null, listKeys = listas, listedAt = cuando,
        fechaEnLista = fechas, tmdbId = tmdb,
    )

    private val entradas = listOf(
        e("tt1", "movie", cuando = 1),
        e("tt2", "series", listOf("Drama"), cuando = 3),
        e("tt3", "series", listOf("Animación", "Acción"), cuando = 2),
        e("tt4", "movie", listas = setOf("trakt:list:9")),
    )

    @Test
    fun `solo la lista de seguimiento, de lo ultimo añadido a lo primero`() {
        assertEquals(listOf("tt2", "tt3", "tt1"), MiLista.de(entradas, listOf(lista, otra), FiltroMiLista.TODO).lista.map { it.id })
    }

    @Test
    fun `filtros por peliculas, series y anime`() {
        assertEquals(listOf("tt1"), MiLista.de(entradas, listOf(lista), FiltroMiLista.PELICULAS).lista.map { it.id })
        assertEquals(listOf("tt2"), MiLista.de(entradas, listOf(lista), FiltroMiLista.SERIES).lista.map { it.id })
        assertEquals(listOf("tt3"), MiLista.de(entradas, listOf(lista), FiltroMiLista.ANIME).lista.map { it.id })
    }

    @Test
    fun `sin pestaña de lista de seguimiento no hay nada`() {
        assertEquals(FilasMiLista(), MiLista.de(entradas, listOf(otra), FiltroMiLista.TODO))
    }

    // Recomendadas (contrato con el móvil, 31-10-2026): la lista personal «Recomendadas» de Trakt, sin «quién».

    private val conRecomendadas = listOf(
        e("tt1", "movie", cuando = 1),
        // Recomendada y en la lista de seguimiento, como la apunta el móvil: sale solo en Recomendadas.
        e("tt5", "movie", listas = setOf("trakt:watchlist", "trakt:list:7"), cuando = 1,
            fechas = mapOf("trakt:watchlist" to 1, "trakt:list:7" to 50)),
        e("tt6", "series", listOf("Drama"), listas = setOf("trakt:watchlist", "trakt:list:7"), cuando = 2,
            fechas = mapOf("trakt:watchlist" to 2, "trakt:list:7" to 40)),
        // Solo en Recomendadas (quitada de la lista de seguimiento en trakt.tv): también sale.
        e("tt7", "movie", listas = setOf("trakt:list:7"), cuando = 60, fechas = mapOf("trakt:list:7" to 60)),
    )

    @Test
    fun `lo recomendado sale en Recomendadas y no se repite en Mi lista`() {
        val filas = MiLista.de(conRecomendadas, listOf(lista, recomendadas), FiltroMiLista.TODO)
        assertEquals(listOf("tt7", "tt5", "tt6"), filas.recomendadas.map { it.id })
        assertEquals(listOf("tt1"), filas.lista.map { it.id })
    }

    @Test
    fun `Recomendadas se ordena por cuando entro en esa lista, no en la de seguimiento`() {
        val filas = MiLista.de(conRecomendadas, listOf(lista, recomendadas), FiltroMiLista.TODO)
        // tt5 entró en la de seguimiento antes que tt6 (1 < 2), pero en Recomendadas después (50 > 40).
        assertEquals(listOf("tt5", "tt6"), filas.recomendadas.filter { it.id != "tt7" }.map { it.id })
    }

    @Test
    fun `los filtros se aplican tambien a Recomendadas`() {
        val filas = MiLista.de(conRecomendadas, listOf(lista, recomendadas), FiltroMiLista.SERIES)
        assertEquals(listOf("tt6"), filas.recomendadas.map { it.id })
        assertEquals(emptyList<LibraryEntry>(), filas.lista)
    }

    @Test
    fun `Recomendadas oculta las peliculas vistas y las series terminadas`() {
        val visto = Visto(peliculas = setOf("tt5"), series = setOf("tt6"))
        val filas = MiLista.de(conRecomendadas, listOf(lista, recomendadas), FiltroMiLista.TODO, visto)
        assertEquals(listOf("tt7"), filas.recomendadas.map { it.id })
        // Lo visto no vuelve a Mi lista: sigue siendo una recomendación.
        assertEquals(listOf("tt1"), filas.lista.map { it.id })
    }

    @Test
    fun `lo visto se reconoce por cualquiera de sus ids y sin mezclar peliculas con series`() {
        val peli = e("tt8", "movie", listas = setOf("trakt:list:7"), tmdb = 99)
        val serie = e("tt9", "series", listas = setOf("trakt:list:7"), tmdb = 99)
        val filas = MiLista.de(listOf(peli, serie), listOf(lista, recomendadas), FiltroMiLista.TODO, Visto(peliculas = setOf("tmdb:99")))
        assertEquals(listOf("tt9"), filas.recomendadas.map { it.id })
    }

    @Test
    fun `la lista Recomendadas se busca por nombre, sin mayusculas ni espacios, y la primera que da Trakt`() {
        val rara = recomendadas.copy(key = "trakt:list:3", title = "  recomendadas ")
        assertEquals("trakt:list:3", MiLista.recomendadas(listOf(lista, otra, rara, recomendadas))?.key)
        assertNull(MiLista.recomendadas(listOf(lista, otra)))
        // La lista de seguimiento nunca cuenta como «Recomendadas», aunque se llame así.
        assertNull(MiLista.recomendadas(listOf(lista.copy(title = "Recomendadas"))))
    }

    @Test
    fun `sin lista Recomendadas, Mi lista es la lista de seguimiento entera`() {
        val filas = MiLista.de(conRecomendadas, listOf(lista), FiltroMiLista.TODO)
        assertEquals(emptyList<LibraryEntry>(), filas.recomendadas)
        assertEquals(listOf("tt6", "tt1", "tt5"), filas.lista.map { it.id })
    }
}
