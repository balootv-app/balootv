package com.nuvio.tv.balootv.catalogo

import com.nuvio.tv.domain.model.TmdbSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenciasDeSerieTest {

    @Test
    fun `las fichas se completan con TMDB en castellano de España sin tocar ajustes`() {
        val s = TmdbSettings()
        assertTrue(s.enabled)
        assertEquals("es-ES", s.language)
    }
}
