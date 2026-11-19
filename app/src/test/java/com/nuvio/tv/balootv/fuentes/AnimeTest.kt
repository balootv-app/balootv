package com.nuvio.tv.balootv.fuentes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeTest {

    @Test
    fun `anime es animacion japonesa y nada mas`() {
        assertTrue(Anime.es(setOf(16, 10765), "ja", "tt22248376"))
        assertFalse(Anime.es(setOf(16, 10751), "en", "tt2380307"))
        assertFalse(Anime.es(setOf(18), "ja", "tt0000001"))
        assertFalse(Anime.es(emptySet(), null, "tt0000002"))
    }

    @Test
    fun `la lista en codigo manda sobre TMDB`() {
        assertTrue(Anime.es(emptySet(), null, "tt1", siempre = setOf("tt1"), nunca = emptySet()))
        assertFalse(Anime.es(setOf(16), "ja", "tt2", siempre = emptySet(), nunca = setOf("tt2")))
    }
}
