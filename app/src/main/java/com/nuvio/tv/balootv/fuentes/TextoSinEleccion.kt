package com.nuvio.tv.balootv.fuentes

/**
 * Por qué BalooTV no ha empezado solo, para la cabecera de "Cambiar fuente" (D7: nunca una lista sin
 * explicación). Sin castellano en absoluto se usa el aviso de `TextoSinCastellano`.
 */
object TextoSinEleccion {

    fun de(motivo: Motivo?): String? = when (motivo) {
        Motivo.SOLO_DUDOSO ->
            "No he encontrado castellano seguro a 1080p, así que no he empezado solo. Estas pueden estarlo: " +
                "si al reproducir no lo está, pulsa «Esta no está en castellano» y salto a otra."
        Motivo.SOLO_NO_CACHEADO ->
            "Hay castellano a 1080p, pero TorBox aún no lo tiene listo, así que no he empezado solo. " +
                "Las que llevan la flecha de descarga tardan unos minutos en estar disponibles."
        Motivo.SIN_CALIDAD -> "Solo hay castellano por debajo de 1080p, así que no he empezado solo."
        Motivo.SOLO_SIN_CONFIRMAR ->
            "Hay castellano, pero el nombre de estos enlaces no deja claro que sean lo que has pedido, así que no he " +
                "empezado solo. Mira el título y el año antes de elegir uno."
        Motivo.AUN_EN_CINES ->
            "Esta película aún no ha salido en casa, solo en cines: estos enlaces pueden ser de otra película o una " +
                "grabación, así que no he empezado solo."
        Motivo.SIN_CASTELLANO, Motivo.SOLO_LATINO, null -> null
    }

    /** El porqué en pocas palabras, bajo el botón Reproducir de la ficha cuando no hay fuente que arranque sola. */
    fun corto(motivo: Motivo): String = when (motivo) {
        Motivo.AUN_EN_CINES -> "Aún en cines"
        Motivo.SOLO_LATINO -> "Solo en latino"
        Motivo.SIN_CASTELLANO -> "Sin castellano todavía"
        Motivo.SIN_CALIDAD -> "Castellano solo por debajo de 1080p"
        Motivo.SOLO_NO_CACHEADO -> "Castellano aún sin preparar en TorBox"
        Motivo.SOLO_DUDOSO -> "Castellano sin confirmar"
        Motivo.SOLO_SIN_CONFIRMAR -> "Enlaces sin confirmar: elige a mano"
    }

    /** Sin nada que ofrecer en castellano: el aviso «Aún no hay versión en castellano» con la versión original. */
    fun sinCastellano(motivo: Motivo?): Boolean = motivo == Motivo.SIN_CASTELLANO || motivo == Motivo.SOLO_LATINO
}
