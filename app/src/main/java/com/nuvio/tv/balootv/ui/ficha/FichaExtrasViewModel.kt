package com.nuvio.tv.balootv.ui.ficha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.balootv.catalogo.Plataforma
import com.nuvio.tv.balootv.ficha.DisponibleEn
import com.nuvio.tv.balootv.fuentes.PrecargaFuentes
import com.nuvio.tv.balootv.nota.FuenteNota
import com.nuvio.tv.balootv.ui.componentes.VueltaFoco
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExtrasFicha(val nota: Float? = null, val plataformas: List<Plataforma> = emptyList())

/** Lo que la ficha nueva añade a MetaDetailsViewModel: nota IMDb, "Disponible en" y la fuente ya elegida. */
@HiltViewModel
class FichaExtrasViewModel @Inject constructor(
    private val notas: FuenteNota,
    private val disponible: DisponibleEn,
    precarga: PrecargaFuentes,
    addons: com.nuvio.tv.domain.repository.AddonRepository,
) : ViewModel() {

    /** Sin ninguna fuente que pueda dar enlaces (edición pública recién instalada, o todas quitadas). */
    val sinFuentes: StateFlow<Boolean> = addons.getInstalledAddons()
        .map { com.nuvio.tv.balootv.fuentes.GestorFuentes.sinFuentes(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _estado = MutableStateFlow(ExtrasFicha())
    val estado: StateFlow<ExtrasFicha> = _estado.asStateFlow()

    /** Clave `tipo|videoId|temporada|episodio` → línea de la fuente que arrancaría Reproducir. */
    val lineaElegida: StateFlow<Map<String, com.nuvio.tv.balootv.fuentes.Formato>> = precarga.lineaElegida

    /** Misma clave → por qué Reproducir no arrancaría solo. */
    val motivos: StateFlow<Map<String, com.nuvio.tv.balootv.fuentes.Motivo>> = precarga.motivos

    /** Lo que se pulsó para salir de la ficha, para devolverle el foco al volver. */
    val vuelta = VueltaFoco()

    private var cargado: String? = null

    fun cargar(id: String, tipo: String) {
        if (cargado == "$tipo|$id") return
        cargado = "$tipo|$id"
        _estado.value = ExtrasFicha()
        viewModelScope.launch {
            val nota = notas.de(id, tipo)
            _estado.update { it.copy(nota = nota) }
        }
        viewModelScope.launch {
            val plataformas = disponible.de(id, esSerie = tipo == "series" || tipo == "tv")
            _estado.update { it.copy(plataformas = plataformas) }
        }
    }
}
