package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import org.junit.Assert.assertEquals
import org.junit.Test

class TextoNoReproducibleTest {

    private val fuente = Addon(
        id = "f", name = "f", version = "1", description = null, logo = null, baseUrl = "https://a.example",
        catalogs = emptyList(), types = emptyList(), resources = listOf(AddonResource("stream", listOf("movie"), listOf("tt"))),
    )

    @Test
    fun `un estreno sin IMDb lo explica`() {
        assertEquals(TextoNoReproducible.SIN_IMDB, TextoNoReproducible.de("tmdb:123", listOf(fuente)))
    }

    @Test
    fun `con identificador de IMDb o sin fuentes de enlaces no culpa a IMDb`() {
        assertEquals(TextoNoReproducible.NINGUNA, TextoNoReproducible.de("tt0111161", listOf(fuente)))
        assertEquals(TextoNoReproducible.NINGUNA, TextoNoReproducible.de("tmdb:123", emptyList()))
        assertEquals(TextoNoReproducible.NINGUNA, TextoNoReproducible.de("tmdb:123", listOf(fuente.copy(enabled = false))))
    }
}
