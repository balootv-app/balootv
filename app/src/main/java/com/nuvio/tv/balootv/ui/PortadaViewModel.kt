package com.nuvio.tv.balootv.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.balootv.ajustes.PlataformasActivas
import com.nuvio.tv.balootv.catalogo.CatalogoBalooTV
import com.nuvio.tv.balootv.catalogo.ConfigCatalogo
import com.nuvio.tv.balootv.catalogo.Plataforma
import com.nuvio.tv.balootv.catalogo.Portada
import com.nuvio.tv.balootv.catalogo.TipoFila
import com.nuvio.tv.balootv.nota.FuenteNota
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nuvio.tv.balootv.ui.componentes.VueltaFoco
import com.nuvio.tv.balootv.ui.movimiento.AmortiguadorFoco

/** Lo que pinta una portada (tableros 14 y 15). */
data class EstadoPortada(
    val portada: Portada,
    val filas: List<CatalogRow> = emptyList(),
    val cargando: Boolean = true,
    val plataformas: List<Plataforma> = emptyList(),
    val plataforma: Plataforma? = null,
    val enfocado: MetaPreview? = null,
    val nota: Float? = null,
    /** Fondo de la cabecera: cambia con la nota, no a cada paso, para no decodificar una imagen grande por cartel. */
    val fondo: String? = null,
) {
    /** El anime se pide sin plataformas: no tiene sentido filtrarlo por ellas. */
    val ofreceFiltro: Boolean get() = portada != Portada.ANIME
}

/**
 * Portada de Películas, Series o Anime: sus filas del catálogo de BalooTV (todas a la vez, sin vacías),
 * el filtro por plataforma y la nota IMDb del título enfocado (tras 400 ms sin moverse, para no pedir
 * notas de cada cartel por el que se pasa).
 */
@HiltViewModel
class PortadaViewModel @Inject constructor(
    estadoGuardado: SavedStateHandle,
    private val catalogo: CatalogoBalooTV,
    private val notas: FuenteNota,
    private val plataformas: PlataformasActivas,
) : ViewModel() {

    private val portada = runCatching { Portada.valueOf(estadoGuardado.get<String>("portada").orEmpty()) }.getOrDefault(Portada.PELICULAS)
    private val _estado = MutableStateFlow(EstadoPortada(portada, plataformas = plataformas.activas()))
    val estado: StateFlow<EstadoPortada> = _estado.asStateFlow()

    /** El cartel abierto, para devolverle el foco al volver de la ficha. */
    val vuelta = VueltaFoco()

    private var carga: Job? = null
    private var notaPedida: Job? = null

    init {
        cargar()
    }

    fun elegirPlataforma(p: Plataforma?) {
        if (p == _estado.value.plataforma) return
        _estado.update { it.copy(plataforma = p) }
        cargar()
    }

    private val amortiguador = AmortiguadorFoco()
    private var objetivo: String? = null

    fun enfocar(item: MetaPreview) {
        if (item.id == objetivo) return
        objetivo = item.id
        // En ráfaga la cabecera espera a que se pare (AmortiguadorFoco); la primera vez cambia al momento.
        val espera = amortiguador.espera().takeIf { _estado.value.enfocado != null } ?: 0L
        notaPedida?.cancel()
        _estado.update { if (espera == 0L) it.copy(enfocado = item, nota = null) else it.copy(nota = null) }
        notaPedida = viewModelScope.launch {
            if (espera > 0) {
                delay(espera)
                _estado.update { it.copy(enfocado = item) }
            }
            delay(ESPERA_NOTA_MS - espera)
            val nota = notas.de(item.id, item.apiType)
            _estado.update { if (it.enfocado?.id == item.id) it.copy(nota = nota, fondo = item.background) else it }
        }
    }

    private fun cargar() {
        carga?.cancel()
        _estado.update { it.copy(cargando = true) }
        val extra = _estado.value.plataforma?.let { mapOf("plataforma" to it.id.toString()) }.orEmpty()
        carga = viewModelScope.launch {
            val filas = ConfigCatalogo.filas(portada).map { f ->
                async { runCatching { catalogo.fila(f.id, if (f.tipo == TipoFila.SERIE) "series" else "movie", 0, extra) }.getOrNull() }
            }.awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
            _estado.update { it.copy(filas = filas, cargando = false) }
            // Con el foco arriba (pestañas o plataformas) la cabecera enseña el primer título: sin esto quedaba
            // un hueco negro de media pantalla. Al cambiar de plataforma, el anterior puede no estar ya.
            filas.firstOrNull()?.items?.firstOrNull()?.let(::enfocar)
        }
    }

    private companion object {
        const val ESPERA_NOTA_MS = 400L
    }
}
