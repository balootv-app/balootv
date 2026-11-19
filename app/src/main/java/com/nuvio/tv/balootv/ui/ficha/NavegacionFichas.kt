package com.nuvio.tv.balootv.ui.ficha

import com.nuvio.tv.domain.model.MetaPreview

/** El cartel desde el que se abrió una ficha: la ficha lo usa de fondo y título mientras carga. */
class NavegacionFichas {

    private var abierta: MetaPreview? = null

    fun abrir(abierta: MetaPreview) {
        this.abierta = abierta
    }

    /** El cartel con el que se abrió [id], si se abrió desde una fila. */
    fun previa(id: String): MetaPreview? = abierta?.takeIf { it.id == id }

    companion object {
        /** Una sola para la app: solo hay una ficha abierta a la vez. */
        val actual = NavegacionFichas()
    }
}
