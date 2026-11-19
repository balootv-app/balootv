package com.nuvio.tv.balootv.edicion

import com.nuvio.tv.BuildConfig

/**
 * La edición con la que se compiló la app (sabores de `app/build.gradle.kts`): `publica` es la que se reparte y
 * `casa` la de la tele de casa, con sus fuentes, su clave de TorBox y las preferencias de su salón.
 */
object Edicion {
    const val CASA = "casa"
    const val PUBLICA = "publica"

    val actual: String get() = BuildConfig.EDICION
    val esCasa: Boolean get() = actual == CASA
    val esPublica: Boolean get() = !esCasa
}
