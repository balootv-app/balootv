package com.nuvio.tv.balootv.ficha

import androidx.compose.runtime.Immutable
import com.nuvio.tv.data.remote.api.TmdbKeyword

/**
 * Escena durante o después de los créditos según las palabras clave de TMDB de la película (contrato C6 con el móvil,
 * casos en compartido/poscreditos.json). Que TMDB no la tenga no dice que no haya: por eso no hay «Sin escena».
 */
@Immutable
data class EscenaCreditos(val durante: Boolean, val despues: Boolean) {

    /** Lo que va al final de la línea de datos de la ficha. */
    val texto: String
        get() = when {
            durante && despues -> "Escenas durante y después de los créditos"
            despues -> "Escena poscréditos"
            else -> "Escena durante los créditos"
        }

    companion object {
        private const val DESPUES_ID = 179430
        private const val DURANTE_ID = 179431

        /** Null si ninguna palabra habla de escenas en los créditos. Por id y, si el id no cuadra, por el nombre. */
        fun de(palabras: List<TmdbKeyword>): EscenaCreditos? {
            fun hay(id: Int, nombre: String) = palabras.any { it.id == id || it.name?.trim().equals(nombre, ignoreCase = true) }
            val durante = hay(DURANTE_ID, "duringcreditsstinger")
            val despues = hay(DESPUES_ID, "aftercreditsstinger")
            return if (durante || despues) EscenaCreditos(durante, despues) else null
        }
    }
}
