package com.nuvio.tv.balootv.reproductor

/**
 * Subtítulos para repetir una frase que no se ha entendido, como en tvOS: al retroceder un poco con los subtítulos
 * apagados (o solo los forzados), se encienden los completos en castellano hasta volver al punto del que se partió y
 * luego se deja lo que había. Solo con pista interna: un subtítulo del addon obliga a preparar otra vez el reproductor.
 */
object SubtitulosAlRetroceder {

    /** Más atrás que esto ya no es repetir una frase, es buscar otra escena. */
    const val MAX_TRAMO_MS = 60_000L

    /** Hasta dónde van los subtítulos y qué había antes, para dejarlo igual al llegar. */
    data class Tramo(val hastaMs: Long, val antes: EleccionSubtitulos)

    /**
     * El tramo tras retroceder de [desdeMs] a [aMs] con la elección [actual]; null si no toca ninguno (y, si había
     * [tramo], se deja lo de antes). Retroceder otra vez dentro de un tramo lo mantiene con el mismo punto de partida.
     */
    fun alRetroceder(desdeMs: Long, aMs: Long, actual: EleccionSubtitulos, hayCompletos: Boolean, tramo: Tramo?): Tramo? {
        if (tramo != null) return tramo.takeIf { tramo.hastaMs - aMs <= MAX_TRAMO_MS }
        if (actual == EleccionSubtitulos.CASTELLANO || !hayCompletos) return null
        if (aMs >= desdeMs || desdeMs - aMs > MAX_TRAMO_MS) return null
        return Tramo(hastaMs = desdeMs, antes = actual)
    }

    fun terminado(posicionMs: Long, tramo: Tramo): Boolean = posicionMs >= tramo.hastaMs
}
