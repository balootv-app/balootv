package com.nuvio.tv.balootv.ui.ficha

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavegacionFichasTest {

    private fun m(id: String) = MetaPreview(
        id = id, type = ContentType.MOVIE, name = id, poster = null, posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = emptyList(),
    )

    @Test
    fun `la ficha conoce el cartel con el que se abrio y no otro`() {
        val n = NavegacionFichas()
        n.abrir(m("a"))
        assertEquals("a", n.previa("a")?.id)
        assertNull(n.previa("z"))
    }
}
