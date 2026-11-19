package com.nuvio.tv.balootv.ajustes

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nuvio.tv.balootv.catalogo.ConfigCatalogo
import com.nuvio.tv.balootv.catalogo.Plataforma
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Qué plataformas salen en el catálogo y en qué orden (el orden decide el logo de la carátula). */
object PlataformasPreferidas {

    /** `ids` guardados por el usuario; sin preferencia o sin ninguna válida, todas: el catálogo nunca se vacía. */
    fun activas(ids: List<Int>?, todas: List<Plataforma>): List<Plataforma> {
        val porId = todas.associateBy { it.id }
        return ids.orEmpty().mapNotNull(porId::get).distinct().ifEmpty { todas }
    }
}

/** Plataformas activas ahora mismo; en las pruebas, una lista fija. */
interface PlataformasActivas {
    fun activas(): List<Plataforma>
}

/** Guarda la elección de Ajustes › Plataformas (lista de ids en orden). */
@Singleton
class AlmacenPlataformas @Inject constructor(@ApplicationContext context: Context) : PlataformasActivas {

    private val clave = stringPreferencesKey("ids")
    private val almacen = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("balootv_plataformas") })
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ids guardados, o null si nunca se han tocado. */
    val ids: StateFlow<List<Int>?> = almacen.data
        .map { p -> p[clave]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } }
        .stateIn(ambito, SharingStarted.Eagerly, null)

    override fun activas(): List<Plataforma> = PlataformasPreferidas.activas(ids.value, ConfigCatalogo.plataformas)

    suspend fun guardar(ids: List<Int>) {
        almacen.edit { it[clave] = ids.joinToString(",") }
    }
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object ModuloPlataformas {
    @dagger.Provides
    fun activas(almacen: AlmacenPlataformas): PlataformasActivas = almacen
}
