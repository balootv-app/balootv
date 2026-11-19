package com.nuvio.tv.balootv.fuentes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Las fuentes que ha añadido el usuario, tal como las pegó: con el marcador `{torbox}` si lo llevaban, para poder
 * rellenarlas otra vez si cambia de cuenta de TorBox. Los addons instalados (ya rellenados) viven en
 * `AddonRepository`; esto solo guarda las plantillas, en orden.
 */
@Singleton
class AlmacenFuentes internal constructor(private val almacen: DataStore<Preferences>) {

    @Inject constructor(@ApplicationContext context: Context) :
        this(PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("balootv_fuentes") }))

    private val clave = stringPreferencesKey("plantillas")

    val plantillas: Flow<List<String>> =
        almacen.data.map { PlantillaFuente.leerLista(it[clave].orEmpty()) }.distinctUntilChanged()

    suspend fun actuales(): List<String> = plantillas.first()

    suspend fun anadir(plantilla: String) = guardar { (it + plantilla.trim()).distinct() }

    suspend fun quitar(plantilla: String) = guardar { it - plantilla }

    private suspend fun guardar(cambio: (List<String>) -> List<String>) {
        almacen.edit { it[clave] = cambio(PlantillaFuente.leerLista(it[clave].orEmpty())).joinToString("\n") }
    }
}
