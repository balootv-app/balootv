package com.nuvio.tv.balootv.arranque

import com.nuvio.tv.domain.model.Addon

/** A dónde va la app al arrancar: nunca cuenta, perfiles ni "elige tu experiencia" (F4 §8). */
enum class DestinoArranque { CARGA, INICIO }

object Arranque {

    /** Pantallas heredadas de Nuvio que BalooTV no enseña nunca (se borran en la limpieza). */
    const val CUENTA_NUVIO = false
    const val PERFILES = false

    /** Tiempo máximo en la pantalla de carga esperando a las fuentes (sin red, se entra igual). */
    const val ESPERA_MAXIMA_MS = 8_000L

    /**
     * Las fuentes de serie están listas cuando cada una ya tiene su manifiesto con recursos. Mientras el
     * manifiesto se descarga, el addon es un marcador sin recursos y las pantallas no lo ven como fuente.
     */
    fun fuentesListas(instalados: List<Addon>, urls: List<String>): Boolean {
        val conRecursos = instalados.filter { it.resources.isNotEmpty() }.map { base(it.baseUrl) }.toSet()
        return urls.all { base(it) in conRecursos }
    }

    fun destino(fuentesListas: Boolean, tiempoAgotado: Boolean): DestinoArranque =
        if (fuentesListas || tiempoAgotado) DestinoArranque.INICIO else DestinoArranque.CARGA

    private fun base(url: String): String =
        url.trim().trimEnd('/').removeSuffix("/manifest.json").trimEnd('/').lowercase()
}
