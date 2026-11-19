package com.nuvio.tv.balootv.edicion

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.balootv.fuentes.FuentesDeSerie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La edición que se reparte no lleva nada de casa: ni clave de TorBox, ni secreto de Trakt, ni fuentes. */
class EdicionPublicaTest {

    @Test
    fun `es la edicion publica`() {
        assertEquals(Edicion.PUBLICA, BuildConfig.EDICION)
        assertTrue(Edicion.esPublica)
    }

    @Test
    fun `no lleva clave de TorBox ni secreto de Trakt`() {
        assertEquals("", BuildConfig.TORBOX_API_KEY)
        assertEquals("", BuildConfig.TRAKT_CLIENT_SECRET)
    }

    @Test
    fun `no lleva fuentes de serie`() {
        assertEquals("", BuildConfig.FUENTES_DE_SERIE)
        assertEquals(emptyList<String>(), FuentesDeSerie.PLANTILLAS)
        assertEquals(emptyList<String>(), FuentesDeSerie.urlsPara("cualquier-clave"))
    }
}
