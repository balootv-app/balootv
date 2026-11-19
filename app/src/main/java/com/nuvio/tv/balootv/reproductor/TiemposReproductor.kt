package com.nuvio.tv.balootv.reproductor

import com.nuvio.tv.balootv.ficha.TerminaALas
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Los tiempos de la capa del reproductor (tablero 21): "44:05" y "−1:11:55 · termina a las 23:18". */
object TiemposReproductor {

    private val HORA = DateTimeFormatter.ofPattern("H:mm")

    fun transcurrido(ms: Long): String = reloj(ms.coerceAtLeast(0))

    fun restante(posMs: Long, durMs: Long, ahora: LocalTime): String {
        if (durMs <= 0) return ""
        val queda = (durMs - posMs).coerceAtLeast(0)
        val fin = ahora.plusSeconds(queda / 1000)
        return "−" + reloj(queda) + " · termina a las " + fin.format(HORA)
    }

    /** La pantalla de pausa, en palabras: "Quedan 1 h 12 min · termina a las 23:18" (como la ficha, contrato C4). */
    fun enPausa(posMs: Long, durMs: Long, ahora: LocalTime): String = TerminaALas.empezado(posMs, durMs, ahora).orEmpty()

    private fun reloj(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = s % 3600 / 60
        val seg = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%d:%02d".format(m, seg)
    }
}
