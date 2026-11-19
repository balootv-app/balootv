package com.nuvio.tv.balootv.ajustes

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.balootv.cuenta.ClaveTorbox
import com.nuvio.tv.balootv.cuenta.CuentaTorbox
import com.nuvio.tv.balootv.cuenta.EstadoTorbox
import com.nuvio.tv.balootv.cuenta.PasoVinculo
import com.nuvio.tv.balootv.cuenta.VinculoTorbox
import com.nuvio.tv.core.qr.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Vincular TorBox con un código (Ajustes y primer arranque): pide el código, espera a que se apruebe y, si la
 * cuenta tiene suscripción, pasa la app a esa clave (con sus fuentes).
 */
fun flujoVinculo(vinculo: VinculoTorbox, claveTorbox: ClaveTorbox, http: OkHttpClient): Flow<Vinculacion> = flow {
    emit(Vinculacion.Pidiendo)
    vinculo.vincular().collect { paso ->
        emit(
            when (paso) {
                is PasoVinculo.Codigo -> Vinculacion.Codigo(paso, withContext(Dispatchers.Default) { QrCodeGenerator.generate(paso.enlace, 360) })
                is PasoVinculo.Clave -> when (CuentaTorbox(http, paso.clave, BuildConfig.VERSION_NAME).estado()) {
                    EstadoTorbox.Funciona -> {
                        claveTorbox.cambiar(paso.clave)
                        Vinculacion.Hecha
                    }
                    EstadoTorbox.Caducada -> Vinculacion.Fallo("Esa cuenta no tiene la suscripción activa.")
                    EstadoTorbox.SinConexion -> Vinculacion.Fallo("No se pudo comprobar la cuenta. Vuelve a intentarlo.")
                }
                PasoVinculo.Fallo -> Vinculacion.Fallo("No se pudo vincular. Vuelve a intentarlo.")
            }
        )
    }
}
