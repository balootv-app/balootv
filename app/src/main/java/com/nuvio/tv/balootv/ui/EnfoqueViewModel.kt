package com.nuvio.tv.balootv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.balootv.nota.FuenteNota
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nuvio.tv.balootv.ui.componentes.VueltaFoco
import com.nuvio.tv.balootv.ui.movimiento.AmortiguadorFoco

/** Título enfocado en el Inicio, con su nota IMDb y su fondo (tras 400 ms quieto, como en las portadas). */
data class EstadoEnfoque(val enfocado: MetaPreview? = null, val nota: Float? = null, val fondo: String? = null)

@HiltViewModel
class EnfoqueViewModel @Inject constructor(private val notas: FuenteNota) : ViewModel() {

    /** Qué se abrió (cartel, Seguir viendo o cabecera) para devolverle el foco al volver. */
    val vuelta = VueltaFoco()

    private val _estado = MutableStateFlow(EstadoEnfoque())
    val estado: StateFlow<EstadoEnfoque> = _estado.asStateFlow()
    private var pedida: Job? = null

    private val amortiguador = AmortiguadorFoco()
    private var objetivo: String? = null

    fun enfocar(item: MetaPreview) {
        if (item.id == objetivo) return
        objetivo = item.id
        // En ráfaga la cabecera espera a que se pare; al bajar desde arriba (sin título) cambia siempre al momento, y
        // también el fondo: si no, el de la cabecera se fundía a negro mientras llegaba el del cartel.
        val desdeArriba = _estado.value.enfocado == null
        val espera = amortiguador.espera().takeIf { !desdeArriba } ?: 0L
        pedida?.cancel()
        _estado.update {
            when {
                desdeArriba -> it.copy(enfocado = item, nota = null, fondo = item.background)
                espera == 0L -> it.copy(enfocado = item, nota = null)
                else -> it.copy(nota = null)
            }
        }
        pedida = viewModelScope.launch {
            if (espera > 0) {
                delay(espera)
                _estado.update { it.copy(enfocado = item) }
            }
            delay(400 - espera)
            val nota = notas.de(item.id, item.apiType)
            _estado.update { if (it.enfocado?.id == item.id) it.copy(nota = nota, fondo = item.background) else it }
        }
    }

    /** El foco ha vuelto a la cabecera o a Seguir viendo: se vuelve a la vista de arriba. */
    fun limpiar() {
        pedida?.cancel()
        objetivo = null
        _estado.value = EstadoEnfoque()
    }
}
