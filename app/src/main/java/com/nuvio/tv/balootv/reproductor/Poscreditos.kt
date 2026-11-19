package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.ficha.EscenaCreditos
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.screens.player.followingPostCreditsScene

/**
 * La escena poscréditos en el reproductor de películas (C6; la parte de la ficha es [EscenaCreditos]). Al empezar los
 * créditos de IntroDB:
 * - con la escena marcada en IntroDB, una tarjeta con cuenta atrás lleva a ella;
 * - sin marca pero con la palabra clave de TMDB, un aviso de que no se quite;
 * - sin escena según los dos (los créditos llegan al final y TMDB no dice nada), una tarjeta con «Salir».
 * Sin créditos de IntroDB no se sabe cuándo empiezan: si TMDB dice que hay escena, el primer Atrás del último 10 %
 * avisa antes de salir. Sin datos, todo queda como en Nuvio.
 */
object Poscreditos {

    /** Segundos de la tarjeta antes de ir sola a la escena. */
    const val CUENTA_ATRAS_ESCENA_S = 5
    /** Segundos de la tarjeta «Sin escena poscréditos» antes de salir sola. */
    const val CUENTA_ATRAS_SALIR_S = 10
    /** Segundos que se ve el aviso de que no se quite. */
    const val AVISO_S = 10

    sealed interface Aviso {
        data class IrALaEscena(val escenaMs: Long) : Aviso
        data class NoLoQuites(val texto: String) : Aviso
        data object SinEscena : Aviso
    }

    fun alEmpezarLosCreditos(creditos: SkipInterval, tramos: List<SkipInterval>, duracionMs: Long, tmdb: EscenaCreditos?): Aviso? {
        if (creditos.type != "movie-credits") return null
        // La de Nuvio: la marca de IntroDB o, con más de 5 s tras los créditos, una supuesta («heuristic»).
        val escena = creditos.followingPostCreditsScene(tramos, duracionMs)
        return when {
            escena != null && escena.provider != "heuristic" -> Aviso.IrALaEscena((escena.startTime * 1_000).toLong())
            tmdb != null -> Aviso.NoLoQuites("Tiene ${tmdb.texto.replaceFirstChar(Char::lowercase)}: no lo quites")
            // Sin escena supuesta, los créditos acaban a menos de 5 s del final.
            escena == null && duracionMs > 0 -> Aviso.SinEscena
            else -> null
        }
    }

    /** Atrás en el último 10 % de una película sin créditos de IntroDB y con escena según TMDB: avisar antes de salir. */
    fun avisarAlSalir(posMs: Long, duracionMs: Long, tramos: List<SkipInterval>, tmdb: EscenaCreditos?): Boolean =
        tmdb != null && duracionMs > 0 && posMs >= duracionMs * 0.9 && tramos.none { it.type == "movie-credits" }

    fun textoAlSalir(tmdb: EscenaCreditos): String =
        "Tiene ${tmdb.texto.replaceFirstChar(Char::lowercase)}. Pulsa Atrás otra vez para salir"

    /** El botón de saltar sobra si la tarjeta ya lleva a la escena o a la salida, y estorba si saltaría la escena. */
    fun ocultaSaltar(aviso: Aviso?, aPoscreditos: Boolean): Boolean = when (aviso) {
        is Aviso.IrALaEscena, Aviso.SinEscena -> true
        is Aviso.NoLoQuites -> !aPoscreditos
        null -> false
    }
}
