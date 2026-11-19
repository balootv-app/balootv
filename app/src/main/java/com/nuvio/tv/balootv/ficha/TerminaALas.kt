package com.nuvio.tv.balootv.ficha

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * «Termina a las» (contrato C4, igual que en el móvil; casos en compartido/termina.json): la ficha antes de pulsar y
 * la pantalla de pausa. Lo que queda se redondea al minuto y la hora de fin es ahora más esos minutos, quitando los
 * segundos sin redondear.
 */
object TerminaALas {

    private val HORA = DateTimeFormatter.ofPattern("H:mm")

    /** "45 min", "1 h 55 min" y, con la hora justa, "2 h". */
    fun duracion(minutos: Int): String = when {
        minutos < 60 -> "$minutos min"
        minutos % 60 == 0 -> "${minutos / 60} h"
        else -> "${minutos / 60} h ${minutos % 60} min"
    }

    /**
     * Lo de C4 por partes, para dibujar la hora con su bandera: [medida] es lo que dura o lo que queda y [hora], la de
     * fin (null con menos de un minuto). [texto] es la frase entera del contrato.
     */
    data class Partes(val medida: String, val hora: String?) {
        val texto: String get() = medida + (hora?.let { " · termina a las $it" } ?: "")
    }

    /** Sin empezar: "1 h 56 min · termina a las 23:18"; null si no se sabe cuánto dura. */
    fun sinEmpezar(duracionMin: Int?, ahora: LocalTime): String? = partesSinEmpezar(duracionMin, ahora)?.texto

    fun partesSinEmpezar(duracionMin: Int?, ahora: LocalTime): Partes? {
        if (duracionMin == null || duracionMin <= 0) return null
        return Partes(duracion(duracionMin), ahora.plusMinutes(duracionMin.toLong()).format(HORA))
    }

    /** Empezado: "Quedan 34 min · termina a las 23:18"; con menos de un minuto, solo eso. Null sin duración. */
    fun empezado(posMs: Long, durMs: Long, ahora: LocalTime): String? = partesEmpezado(posMs, durMs, ahora)?.texto

    fun partesEmpezado(posMs: Long, durMs: Long, ahora: LocalTime): Partes? {
        if (durMs <= 0) return null
        return partesQuedan(minutosQueda(posMs, durMs), ahora)
    }

    /** Lo que queda, ya en minutos: "Quedan 34 min · termina a las 21:57", "Queda 1 min…"; 0, "Queda menos de un minuto". */
    fun quedan(minutos: Int, ahora: LocalTime): String = partesQuedan(minutos, ahora).texto

    fun partesQuedan(minutos: Int, ahora: LocalTime): Partes {
        if (minutos <= 0) return Partes(queda(0), null)
        return Partes(queda(minutos), ahora.plusMinutes(minutos.toLong()).format(HORA))
    }

    /**
     * Lo que queda en la tarjeta de Seguir viendo, sin «Quedan» porque ya lo dice la barra (como el móvil,
     * `SeguirViendo.corto`): "35 min", "1 h 27 min" y, con menos de un minuto, "menos de 1 min".
     */
    fun corto(minutos: Int): String = if (minutos < 1) "menos de 1 min" else duracion(minutos)

    /** "Quedan 34 min", "Queda 1 min" y, con 0, "Queda menos de un minuto". La tarjeta de Seguir viendo (C7) lo usa solo. */
    fun queda(minutos: Int): String = when {
        minutos <= 0 -> "Queda menos de un minuto"
        minutos == 1 -> "Queda 1 min"
        else -> "Quedan ${duracion(minutos)}"
    }

    /** Lo que queda redondeado al minuto; menos de un minuto cuenta como 0, aunque al redondear diera 1. */
    fun minutosQueda(posMs: Long, durMs: Long): Int {
        val queda = (durMs - posMs).coerceAtLeast(0)
        return if (queda < 60_000) 0 else Math.round(queda / 60_000.0).toInt()
    }
}
