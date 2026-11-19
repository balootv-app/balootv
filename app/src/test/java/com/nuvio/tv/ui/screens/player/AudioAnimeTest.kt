package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioAnimeTest {

    @Test
    fun `en anime el audio preferido es el japones`() {
        assertEquals(listOf("ja"), resolvePreferredAudioLanguages("es", null, listOf("spa"), "ja", anime = true))
    }

    @Test
    fun `sin anime no cambia nada`() {
        assertEquals(listOf("es"), resolvePreferredAudioLanguages("es", null, listOf("spa"), "ja"))
    }
}
