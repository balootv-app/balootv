package com.nuvio.tv.balootv.ui.fuentes

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.balootv.ajustes.FilaAddon
import com.nuvio.tv.balootv.ajustes.ResumenAddons
import com.nuvio.tv.balootv.fuentes.GestorFuentes
import com.nuvio.tv.balootv.fuentes.ResultadoFuente
import com.nuvio.tv.balootv.fuentes.ServidorFuentes
import com.nuvio.tv.core.qr.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** La dirección que se abre en el móvil y su QR. */
data class EnlaceMovil(val direccion: String, val qr: Bitmap)

/** La pantalla «Fuentes»: la página para el móvil mientras está abierta, añadir con el mando y quitar. */
@HiltViewModel
class FuentesViewModel @Inject constructor(private val gestor: GestorFuentes) : ViewModel() {

    /** Los addons instalados explicados; null mientras carga. */
    val filas: StateFlow<List<FilaAddon>?> = gestor.instaladas.map { ResumenAddons.filas(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val numeroFuentes: StateFlow<Int?> = gestor.instaladas.map { ResumenAddons.fuentesEnUso(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _enlace = MutableStateFlow<EnlaceMovil?>(null)
    /** null sin red local (entonces solo se puede escribir con el mando). */
    val enlace: StateFlow<EnlaceMovil?> = _enlace.asStateFlow()

    private val _sinRed = MutableStateFlow(false)
    val sinRed: StateFlow<Boolean> = _sinRed.asStateFlow()

    private val _resultados = MutableStateFlow<List<ResultadoFuente>>(emptyList())
    val resultados: StateFlow<List<ResultadoFuente>> = _resultados.asStateFlow()

    private val _comprobando = MutableStateFlow(false)
    val comprobando: StateFlow<Boolean> = _comprobando.asStateFlow()

    private var servidor: ServidorFuentes? = null
    private var direccion: String? = null

    /** Abre la página para el móvil. Se llama al entrar en la pantalla y se cierra con [cerrarPagina] al salir. */
    fun abrirPagina() {
        if (servidor != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val ip = ServidorFuentes.direccionLocal()
            if (ip == null) { _sinRed.value = true; return@launch }
            val s = ServidorFuentes(
                ambito = viewModelScope,
                alRecibir = { texto -> anadirAhora(texto) },
                alCambiarCodigo = { codigo -> viewModelScope.launch { pintarEnlace(codigo) } },
            )
            runCatching { s.abrir(ip) }.onFailure { _sinRed.value = true; return@launch }
            servidor = s
            direccion = "http://${ip.hostAddress}:${s.puerto}"
            _sinRed.value = false
            pintarEnlace(s.codigo)
        }
    }

    fun cerrarPagina() {
        servidor?.cerrar()
        servidor = null
        _enlace.value = null
    }

    private suspend fun pintarEnlace(codigo: String) {
        val base = direccion ?: return
        val url = "$base/$codigo"
        _enlace.value = EnlaceMovil(url, withContext(Dispatchers.Default) { QrCodeGenerator.generate(url, 360) })
    }

    /** Lo escrito con el mando. */
    fun anadir(texto: String) {
        viewModelScope.launch { anadirAhora(texto) }
    }

    private suspend fun anadirAhora(texto: String): List<ResultadoFuente> {
        _comprobando.value = true
        return try {
            gestor.anadir(texto).also { _resultados.value = it }
        } finally {
            _comprobando.value = false
        }
    }

    fun quitar(fila: FilaAddon) {
        if (fila.baseUrl.isBlank()) return
        viewModelScope.launch { gestor.quitar(fila.baseUrl) }
    }

    override fun onCleared() {
        cerrarPagina()
    }
}
