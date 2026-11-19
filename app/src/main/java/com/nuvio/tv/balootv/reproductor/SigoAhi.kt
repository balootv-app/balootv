package com.nuvio.tv.balootv.reproductor

import java.time.LocalTime

/**
 * «¿Sigues viendo?»: tras unos episodios seguidos sin tocar el mando, el siguiente no empieza solo y espera un OK. Si
 * uno se queda dormido, la serie no avanza sola toda la noche ni marca en Trakt episodios que no ha visto.
 */
object SigoAhi {

    private val MADRUGADA = LocalTime.of(1, 0)..LocalTime.of(5, 59, 59)

    /** Episodios seguidos que empiezan solos antes de preguntar: tres; de madrugada, uno. */
    fun umbral(ahora: LocalTime): Int = if (ahora in MADRUGADA) 1 else 3
}
