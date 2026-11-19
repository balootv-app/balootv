package com.nuvio.tv.balootv.ajustes

import com.nuvio.tv.balootv.catalogo.CatalogoBalooTV
import com.nuvio.tv.balootv.catalogo.ConfigCatalogo
import com.nuvio.tv.balootv.catalogo.FuenteTmdb
import com.nuvio.tv.balootv.catalogo.TipoFila
import com.nuvio.tv.balootv.catalogo.Titulo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlataformasPreferidasTest {

    private val todas = ConfigCatalogo.plataformas

    @Test
    fun `sin preferencia guardada salen todas en el orden por defecto`() {
        assertEquals(todas, PlataformasPreferidas.activas(null, todas))
        assertEquals(todas, PlataformasPreferidas.activas(emptyList(), todas))
    }

    @Test
    fun `respeta las elegidas y su orden`() {
        assertEquals(listOf(1899, 8), PlataformasPreferidas.activas(listOf(1899, 8), todas).map { it.id })
    }

    @Test
    fun `ignora ids que ya no existen`() {
        assertEquals(listOf(8), PlataformasPreferidas.activas(listOf(999, 8), todas).map { it.id })
    }

    @Test
    fun `si no queda ninguna valida salen todas para que el catalogo no se quede vacio`() {
        assertEquals(todas, PlataformasPreferidas.activas(listOf(999), todas))
    }

    @Test
    fun `el catalogo solo pregunta por las plataformas activas`() = runBlocking {
        val pedidas = mutableListOf<String?>()
        val tmdb = object : FuenteTmdb {
            override suspend fun descubrir(tipo: TipoFila, parametros: Map<String, String>): List<Titulo>? { pedidas += parametros["with_watch_providers"]; return emptyList() }
            override suspend fun buscar(tipo: TipoFila, texto: String, pagina: Int): List<Titulo>? = emptyList()
        }
        val catalogo = CatalogoBalooTV(tmdb, { LocalDate.of(2026, 10, 23) }) { PlataformasPreferidas.activas(listOf(1899, 8), todas) }
        catalogo.fila("peliculas-populares", "movie", 0, emptyMap())
        assertEquals(listOf("1899", "8"), pedidas)
    }
}
