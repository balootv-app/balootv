package com.nuvio.tv.balootv.episodios

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumeracionTmdbTest {

    private fun recurso(nombre: String) =
        JsonParser.parseString(javaClass.getResource("/balootv/numeracion/$nombre")!!.readText()).asJsonObject

    /** Los episodios de DAN DA DAN tal como los da Cinemeta (la numeración de la ficha). */
    private val cinemeta: List<Pair<Int, Int>> = recurso("cinemeta_tt30217403.json").getAsJsonObject("meta")
        .getAsJsonArray("videos").map { it.asJsonObject }.filter { it.has("season") && it.has("episode") }
        .map { it.get("season").asInt to it.get("episode").asInt }

    /** Cuántos episodios tiene cada temporada de DAN DA DAN en TMDB (`tv/240411`). */
    private val tmdb: Map<Int, Int> = recurso("tmdb_tv_240411.json").getAsJsonArray("seasons").map { it.asJsonObject }
        .associate { it.get("season_number").asInt to it.get("episode_count").asInt }

    @Test
    fun laSegundaTemporadaDeCinemetaEsElFinalDeLaUnicaDeTmdb() {
        val t = NumeracionTmdb.traducir(cinemeta, tmdb)
        assertEquals(1 to 1, t[1 to 1])
        assertEquals(1 to 12, t[1 to 12])
        assertEquals(1 to 13, t[2 to 1])
        assertEquals(1 to 19, t[2 to 7])
        assertEquals(1 to 24, t[2 to 12])
    }

    @Test
    fun loQueTmdbNoTieneSeQuedaSinTraducir() {
        val t = NumeracionTmdb.traducir(cinemeta, tmdb)
        assertNull(t[3 to 1])
        assertNull(t[0 to 1])
    }

    @Test
    fun alRevesElEpisodioDeTmdbEsElDeLaFicha() {
        assertEquals(2 to 1, NumeracionTmdb.deLaFicha(cinemeta, tmdb, 1 to 13))
        assertEquals(1 to 1, NumeracionTmdb.deLaFicha(cinemeta, tmdb, 1 to 1))
        assertNull(NumeracionTmdb.deLaFicha(cinemeta, tmdb, 2 to 1))
    }

    @Test
    fun conLasMismasTemporadasSeCasaPorNumero() {
        val episodios = listOf(1 to 1, 1 to 2, 2 to 1, 0 to 1)
        assertEquals(mapOf((1 to 1) to (1 to 1), (1 to 2) to (1 to 2), (2 to 1) to (2 to 1)),
            NumeracionTmdb.traducir(episodios, mapOf(0 to 3, 1 to 2, 2 to 1)))
    }
}
