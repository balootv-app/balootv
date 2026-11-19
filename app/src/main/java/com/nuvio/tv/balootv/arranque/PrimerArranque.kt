package com.nuvio.tv.balootv.arranque

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nuvio.tv.balootv.edicion.Edicion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Los pasos del primer arranque de la edición pública, en orden. */
enum class PasoPrimerArranque { BIENVENIDA, TORBOX, TRAKT, FUENTES }

/**
 * Si falta el primer arranque (bienvenida, TorBox, Trakt y fuentes). Solo en la edición pública: la de casa ya
 * viene configurada. Se puede repetir desde Ajustes.
 */
@Singleton
class AlmacenPrimerArranque @Inject constructor(@ApplicationContext context: Context) {
    private val hecho = booleanPreferencesKey("hecho")
    private val almacen = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("balootv_primer_arranque") })

    val pendiente: Flow<Boolean> = almacen.data.map { pendiente(Edicion.esPublica, it[hecho] == true) }

    suspend fun terminar() {
        almacen.edit { it[hecho] = true }
    }

    companion object {
        fun pendiente(publica: Boolean, hecho: Boolean): Boolean = publica && !hecho

        /** El paso que sigue a [p]; null tras el último (se va al Inicio). */
        fun siguiente(p: PasoPrimerArranque): PasoPrimerArranque? = PasoPrimerArranque.entries.getOrNull(p.ordinal + 1)

        fun anterior(p: PasoPrimerArranque): PasoPrimerArranque? = PasoPrimerArranque.entries.getOrNull(p.ordinal - 1)
    }
}
