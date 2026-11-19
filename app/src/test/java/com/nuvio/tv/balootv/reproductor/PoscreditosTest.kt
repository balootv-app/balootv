package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.ficha.EscenaCreditos
import com.nuvio.tv.balootv.reproductor.Poscreditos.Aviso
import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.screens.player.shouldShowPostPlayRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoscreditosTest {

    // Una película de 2 h (7 200 s) con los créditos de IntroDB desde 6 900 s.
    private val duracionMs = 7_200_000L
    private fun creditos(fin: Double = 7_200.0) = SkipInterval(6_900.0, fin, "movie-credits", "introdb")
    private val escena = SkipInterval(7_080.0, 7_150.0, "post-credits", "introdb")
    private val despues = EscenaCreditos(durante = false, despues = true)

    @Test
    fun `con la escena marcada en IntroDB, tarjeta que lleva a ella`() {
        val c = creditos(fin = 7_080.0)
        assertEquals(Aviso.IrALaEscena(7_080_000L), Poscreditos.alEmpezarLosCreditos(c, listOf(c, escena), duracionMs, null))
        // Aunque TMDB también lo diga, manda la marca.
        assertEquals(Aviso.IrALaEscena(7_080_000L), Poscreditos.alEmpezarLosCreditos(c, listOf(c, escena), duracionMs, despues))
    }

    @Test
    fun `sin marca pero con la palabra clave de TMDB, aviso de que no se quite`() {
        val c = creditos()
        assertEquals(Aviso.NoLoQuites("Tiene escena poscréditos: no lo quites"), Poscreditos.alEmpezarLosCreditos(c, listOf(c), duracionMs, despues))
        assertEquals(
            Aviso.NoLoQuites("Tiene escena durante los créditos: no lo quites"),
            Poscreditos.alEmpezarLosCreditos(c, listOf(c), duracionMs, EscenaCreditos(durante = true, despues = false)),
        )
        // Con un hueco largo tras los créditos (escena supuesta por Nuvio) también avisa: la suposición no es una marca.
        val corto = creditos(fin = 7_000.0)
        assertEquals(Aviso.NoLoQuites("Tiene escena poscréditos: no lo quites"), Poscreditos.alEmpezarLosCreditos(corto, listOf(corto), duracionMs, despues))
    }

    @Test
    fun `sin escena solo si los creditos llegan al final y TMDB no dice nada`() {
        val c = creditos(fin = 7_197.0)
        assertEquals(Aviso.SinEscena, Poscreditos.alEmpezarLosCreditos(c, listOf(c), duracionMs, null))
        // Con más de 5 s detrás de los créditos puede haber algo: como hoy.
        val corto = creditos(fin = 7_000.0)
        assertNull(Poscreditos.alEmpezarLosCreditos(corto, listOf(corto), duracionMs, null))
        // Sin duración no se sabe dónde acaba la película.
        assertNull(Poscreditos.alEmpezarLosCreditos(c, listOf(c), 0L, null))
    }

    @Test
    fun `fuera de los creditos de pelicula no hay nada`() {
        val outro = SkipInterval(1_300.0, 1_380.0, "outro", "introdb")
        assertNull(Poscreditos.alEmpezarLosCreditos(outro, listOf(outro), 1_400_000L, despues))
        val intro = SkipInterval(0.0, 90.0, "intro", "introdb")
        assertNull(Poscreditos.alEmpezarLosCreditos(intro, listOf(intro), duracionMs, null))
    }

    @Test
    fun `sin creditos de IntroDB, el primer Atras del ultimo 10 por ciento avisa si TMDB dice que hay escena`() {
        assertTrue(Poscreditos.avisarAlSalir(6_500_000L, duracionMs, emptyList(), despues))
        assertFalse(Poscreditos.avisarAlSalir(6_400_000L, duracionMs, emptyList(), despues))
        assertFalse(Poscreditos.avisarAlSalir(6_500_000L, duracionMs, emptyList(), null))
        // Con créditos de IntroDB ya avisa la tarjeta al empezar los créditos.
        assertFalse(Poscreditos.avisarAlSalir(7_000_000L, duracionMs, listOf(creditos()), despues))
        assertFalse(Poscreditos.avisarAlSalir(6_500_000L, 0L, emptyList(), despues))
        assertEquals("Tiene escena poscréditos. Pulsa Atrás otra vez para salir", Poscreditos.textoAlSalir(despues))
        assertEquals(
            "Tiene escenas durante y después de los créditos. Pulsa Atrás otra vez para salir",
            Poscreditos.textoAlSalir(EscenaCreditos(durante = true, despues = true)),
        )
    }

    @Test
    fun `el boton de saltar se esconde si la tarjeta ya lo resuelve o si saltaria la escena`() {
        assertTrue(Poscreditos.ocultaSaltar(Aviso.IrALaEscena(1L), aPoscreditos = true))
        assertTrue(Poscreditos.ocultaSaltar(Aviso.SinEscena, aPoscreditos = false))
        // «Saltar créditos» se comería la escena que avisa TMDB; «Ir a la escena poscréditos» no.
        assertTrue(Poscreditos.ocultaSaltar(Aviso.NoLoQuites("x"), aPoscreditos = false))
        assertFalse(Poscreditos.ocultaSaltar(Aviso.NoLoQuites("x"), aPoscreditos = true))
        assertFalse(Poscreditos.ocultaSaltar(null, aPoscreditos = false))
    }

    @Test
    fun `si solo TMDB dice que hay escena, la recomendacion de Nuvio espera al final`() {
        val c = creditos()
        fun sale(posMs: Long, tmdb: Boolean) = shouldShowPostPlayRecommendation(
            contentType = "movie", positionMs = posMs, durationMs = duracionMs, skipIntervals = listOf(c),
            movieThresholdPercent = 95, episodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
            episodeThresholdPercent = 99f, episodeThresholdMinutesBeforeEnd = 2f, escenaSegunTmdb = tmdb,
        )
        // Como en Nuvio: con los créditos hasta el final, sale al empezar los créditos.
        assertTrue(sale(6_910_000L, tmdb = false))
        // Con escena según TMDB, solo al final, para no taparla.
        assertFalse(sale(6_910_000L, tmdb = true))
        assertFalse(sale(7_150_000L, tmdb = true))
        assertTrue(sale(duracionMs, tmdb = true))
    }
}
