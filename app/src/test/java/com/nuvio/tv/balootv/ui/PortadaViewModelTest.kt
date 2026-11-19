package com.nuvio.tv.balootv.ui

import androidx.lifecycle.SavedStateHandle
import com.nuvio.tv.MainDispatcherRule
import com.nuvio.tv.balootv.ajustes.PlataformasActivas
import com.nuvio.tv.balootv.catalogo.CatalogoBalooTV
import com.nuvio.tv.balootv.catalogo.ConfigCatalogo
import com.nuvio.tv.balootv.catalogo.FuenteTmdb
import com.nuvio.tv.balootv.catalogo.Plataforma
import com.nuvio.tv.balootv.catalogo.Portada
import com.nuvio.tv.balootv.catalogo.TipoFila
import com.nuvio.tv.balootv.catalogo.Titulo
import com.nuvio.tv.balootv.nota.FuenteNota
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PortadaViewModelTest {

    @get:Rule
    val reglas = MainDispatcherRule()

    private fun t(id: Int) = Titulo(id, TipoFila.PELICULA, "T$id", "/p.jpg", "/f.jpg", null, "2026-01-01", 7.0, 100, 10.0 - id)

    /** Cada fila (por su género o país) devuelve títulos distintos; "thriller" (53) no devuelve nada. */
    private class Tmdb : FuenteTmdb {
        val pedidas = mutableListOf<Map<String, String>>()
        override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? {
            pedidas += parametros
            if (parametros["with_genres"] == "53") return emptyList()
            return listOf(Titulo(parametros.hashCode() and 0xffff, tipo, "X", null, null, null, null, null, null, 1.0))
        }
        override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = emptyList()
    }

    private class Nota : FuenteNota {
        val pedidas = mutableListOf<String>()
        override suspend fun de(id: String, tipo: String): Float? { pedidas += id; return 7.5f }
    }

    private val todas = object : PlataformasActivas { override fun activas(): List<Plataforma> = ConfigCatalogo.plataformas }

    private fun vm(portada: Portada, tmdb: FuenteTmdb = Tmdb(), nota: FuenteNota = Nota()) = PortadaViewModel(
        SavedStateHandle(mapOf("portada" to portada.name)),
        CatalogoBalooTV(tmdb, hoy = { LocalDate.of(2026, 10, 23) }),
        nota,
        todas,
    )

    @Test
    fun `carga las filas de la portada en su orden y quita las vacias`() = runTest(reglas.dispatcher) {
        val v = vm(Portada.PELICULAS)
        advanceUntilIdle()
        val ids = v.estado.value.filas.map { it.catalogId }
        assertEquals(ConfigCatalogo.filas(Portada.PELICULAS).map { it.id } - "thriller", ids)
        assertTrue(v.estado.value.ofreceFiltro)
    }

    @Test
    fun `el anime no ofrece filtro de plataforma`() = runTest(reglas.dispatcher) {
        val v = vm(Portada.ANIME)
        advanceUntilIdle()
        assertEquals(false, v.estado.value.ofreceFiltro)
        assertEquals(ConfigCatalogo.filas(Portada.ANIME).size, v.estado.value.filas.size)
    }

    @Test
    fun `elegir una plataforma vuelve a pedir solo esa`() = runTest(reglas.dispatcher) {
        val tmdb = Tmdb()
        val v = vm(Portada.SERIES, tmdb)
        advanceUntilIdle()
        tmdb.pedidas.clear()
        v.elegirPlataforma(ConfigCatalogo.plataformas[1])
        advanceUntilIdle()
        assertEquals(setOf("119"), tmdb.pedidas.map { it["with_watch_providers"] }.toSet())
        assertEquals(ConfigCatalogo.plataformas[1], v.estado.value.plataforma)
    }

    @Test
    fun `la nota es la del ultimo titulo enfocado tras una breve espera`() = runTest(reglas.dispatcher) {
        val nota = Nota()
        val v = vm(Portada.PELICULAS, nota = nota)
        advanceUntilIdle()
        val fila = v.estado.value.filas.first()
        val a = fila.items.first()
        v.enfocar(a.copy(id = "tmdb:1"))
        advanceTimeBy(100)
        v.enfocar(a.copy(id = "tmdb:2"))
        advanceUntilIdle()
        // La primera es la del título que se enseña al cargar; la de tmdb:1 no llega a pedirse.
        assertEquals(listOf(a.id, "tmdb:2"), nota.pedidas)
        assertEquals("tmdb:2", v.estado.value.enfocado?.id)
        assertEquals(7.5f, v.estado.value.nota!!, 0.001f)
    }

    @Test
    fun `al cargar la cabecera ensena el primer titulo de la primera fila`() = runTest(reglas.dispatcher) {
        val v = vm(Portada.PELICULAS)
        advanceUntilIdle()
        val primero = v.estado.value.filas.first().items.first()
        assertEquals(primero.id, v.estado.value.enfocado?.id)
        assertEquals(primero.background, v.estado.value.fondo)
    }

    @Test
    fun `al enfocar otro titulo la nota anterior desaparece enseguida`() = runTest(reglas.dispatcher) {
        val v = vm(Portada.PELICULAS)
        advanceUntilIdle()
        val a = v.estado.value.filas.first().items.first()
        v.enfocar(a.copy(id = "tmdb:1"))
        advanceUntilIdle()
        v.enfocar(a.copy(id = "tmdb:2"))
        assertNull(v.estado.value.nota)
    }
}
