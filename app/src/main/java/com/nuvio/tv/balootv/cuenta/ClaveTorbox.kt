package com.nuvio.tv.balootv.cuenta

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.balootv.fuentes.AlmacenFuentes
import com.nuvio.tv.balootv.fuentes.FuentesDeSerie
import com.nuvio.tv.balootv.fuentes.PlantillaFuente
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La clave de TorBox en uso: la que se vinculó (guardada en la tele) o, si no hay, la que trae la APK (solo la
 * edición casa trae una; la pública empieza sin ninguna y se vincula en el primer arranque). Va en un almacén
 * propio y no en los ajustes de debrid de Nuvio: guardarla allí encendería funciones heredadas (nube, debrid directo).
 */
@Singleton
class ClaveTorbox @Inject constructor(
    @ApplicationContext context: Context,
    private val fuentes: FuentesDeSerie,
    private val propias: AlmacenFuentes,
) {
    private val clave = stringPreferencesKey("clave")
    private val almacen = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("balootv_torbox") })

    val enUso: Flow<String> = almacen.data.map { elegir(it[clave].orEmpty()) }.distinctUntilChanged()

    suspend fun actual(): String = enUso.first()

    /** Guarda [nueva] y cambia las fuentes (de serie y del usuario) de la clave anterior por las de la nueva. */
    suspend fun cambiar(nueva: String) {
        val vieja = actual()
        almacen.edit { it[clave] = nueva.trim() }
        val plantillas = FuentesDeSerie.PLANTILLAS + propias.actuales()
        fuentes.sustituir(
            plantillas.map { PlantillaFuente.rellenar(it, vieja) },
            plantillas.map { PlantillaFuente.rellenar(it, nueva.trim()) },
        )
    }

    companion object {
        fun elegir(guardada: String, deLaApk: String = BuildConfig.TORBOX_API_KEY): String =
            guardada.trim().ifBlank { deLaApk.trim() }
    }
}
