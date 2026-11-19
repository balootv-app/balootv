package com.nuvio.tv.balootv.ajustes

import com.nuvio.tv.balootv.cuenta.EstadoTorbox
import com.nuvio.tv.data.local.StreamAutoPlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumenAjustesTest {

    private val base = EntradaAjustes(
        usuarioTrakt = "usuario", modo = StreamAutoPlayMode.BALOOTV, tamanoSubtitulos = 100,
        alturaSubtitulos = 5, torbox = EstadoTorbox.Funciona, plataformasActivas = 8, fuentesEnUso = 6, version = "0.5.0",
    )

    private fun valor(e: EntradaAjustes, s: SeccionAjustes) = ResumenAjustes.filas(e).first { it.seccion == s }.valor

    @Test
    fun `siete filas, las de la maqueta y Fuentes antes de Acerca de`() {
        assertEquals(
            listOf(SeccionAjustes.TRAKT, SeccionAjustes.FUENTE, SeccionAjustes.SUBTITULOS, SeccionAjustes.TORBOX, SeccionAjustes.PLATAFORMAS, SeccionAjustes.FUENTES, SeccionAjustes.ACERCA_DE),
            ResumenAjustes.filas(base).map { it.seccion },
        )
    }

    @Test
    fun `muestra los valores ya guardados`() {
        val guardado = base.copy(modo = StreamAutoPlayMode.MANUAL, tamanoSubtitulos = 130, alturaSubtitulos = 15)
        assertEquals("Preguntarme siempre", valor(guardado, SeccionAjustes.FUENTE))
        assertEquals("Grande · más arriba", valor(guardado, SeccionAjustes.SUBTITULOS))
        assertEquals("Conectado · usuario", valor(base, SeccionAjustes.TRAKT))
        assertEquals("BalooTV automático", valor(base, SeccionAjustes.FUENTE))
        assertEquals("Mediano · abajo", valor(base, SeccionAjustes.SUBTITULOS))
        assertEquals("8 activas", valor(base, SeccionAjustes.PLATAFORMAS))
        assertEquals("6 fuentes", valor(base, SeccionAjustes.FUENTES))
        assertEquals("Ninguna", valor(base.copy(fuentesEnUso = 0), SeccionAjustes.FUENTES))
        assertEquals("BalooTV 0.5.0", valor(base, SeccionAjustes.ACERCA_DE))
    }

    @Test
    fun `estados de TorBox y Trakt sin vincular`() {
        assertEquals("Funcionando", valor(base, SeccionAjustes.TORBOX))
        assertEquals("Sin conexión", valor(base.copy(torbox = EstadoTorbox.SinConexion), SeccionAjustes.TORBOX))
        assertEquals("Suscripción caducada", valor(base.copy(torbox = EstadoTorbox.Caducada), SeccionAjustes.TORBOX))
        assertEquals("Comprobando…", valor(base.copy(torbox = null), SeccionAjustes.TORBOX))
        assertEquals("Sin conectar", valor(base.copy(usuarioTrakt = null), SeccionAjustes.TRAKT))
    }

    @Test
    fun `cualquier modo heredado que no sea manual cuenta como automatico y un tamaño raro se muestra en porcentaje`() {
        assertEquals("BalooTV automático", valor(base.copy(modo = StreamAutoPlayMode.FIRST_STREAM), SeccionAjustes.FUENTE))
        assertEquals("115 % · abajo", valor(base.copy(tamanoSubtitulos = 115), SeccionAjustes.SUBTITULOS))
    }
}
