package com.nuvio.tv.balootv.fuentes

/**
 * La guía para quien no sabe qué fuentes poner. BalooTV no trae ni recomienda ninguna: solo manda a esta página de la
 * web del proyecto, que a su vez enlaza la guía que toque (así el destino cambia sin actualizar la app).
 */
object GuiaFuentes {
    const val DIRECCION = "https://balootv-app.github.io/fuentes"

    /** La dirección sin `https://`, para leerla en la tele y escribirla en el móvil. */
    val corta: String = DIRECCION.removePrefix("https://")

    const val PREGUNTA = "¿No sabes qué poner? Mira la guía:"

    val linea: String = "$PREGUNTA $corta"
}
