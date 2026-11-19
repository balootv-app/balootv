package com.nuvio.tv.balootv.arranque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.balootv.ajustes.Vinculacion
import com.nuvio.tv.balootv.ajustes.flujoVinculo
import com.nuvio.tv.balootv.cuenta.ClaveTorbox
import com.nuvio.tv.balootv.cuenta.CuentaTorbox
import com.nuvio.tv.balootv.cuenta.EstadoTorbox
import com.nuvio.tv.balootv.cuenta.PasoTrakt
import com.nuvio.tv.balootv.cuenta.VinculoTrakt
import com.nuvio.tv.balootv.cuenta.VinculoTorbox
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.data.repository.TraktProgressService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** El primer arranque: la vinculación de TorBox, si Trakt está conectado y marcarlo como hecho. */
@HiltViewModel
class PrimerArranqueViewModel @Inject constructor(
    private val vinculo: VinculoTorbox,
    private val claveTorbox: ClaveTorbox,
    private val almacen: AlmacenPrimerArranque,
    trakt: TraktAuthDataStore,
    private val sesionTrakt: TraktAuthService,
    private val progresoTrakt: TraktProgressService,
) : ViewModel() {

    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

    private val _vinculacion = MutableStateFlow<Vinculacion?>(null)
    val vinculacion: StateFlow<Vinculacion?> = _vinculacion.asStateFlow()

    private val _torbox = MutableStateFlow<EstadoTorbox?>(null)
    /** Cómo está la cuenta que hay (null: sin cuenta o comprobando). */
    val torbox: StateFlow<EstadoTorbox?> = _torbox.asStateFlow()

    val usuarioTrakt: StateFlow<String?> = trakt.state.map { it.username?.takeIf { _ -> it.isAuthenticated } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var vinculando: Job? = null

    init {
        comprobarTorbox()
    }

    private fun comprobarTorbox() {
        viewModelScope.launch {
            val clave = claveTorbox.actual()
            _torbox.value = if (clave.isBlank()) null else CuentaTorbox(http, clave, BuildConfig.VERSION_NAME).estado()
        }
    }

    /** Empieza (o repite) la vinculación con código. */
    fun vincularTorbox() {
        vinculando?.cancel()
        vinculando = viewModelScope.launch {
            flujoVinculo(vinculo, claveTorbox, http).collect { v ->
                _vinculacion.value = v
                if (v == Vinculacion.Hecha) comprobarTorbox()
            }
        }
    }

    fun cancelarVinculo() {
        vinculando?.cancel()
        _vinculacion.value = null
        vinculandoTrakt?.cancel()
        _trakt.value = null
    }

    private val _trakt = MutableStateFlow<PasoTrakt?>(null)
    /** La vinculación de Trakt dentro de la guía (el código a la vista); null si no se está vinculando. */
    val vinculacionTrakt: StateFlow<PasoTrakt?> = _trakt.asStateFlow()
    private var vinculandoTrakt: Job? = null

    fun vincularTrakt() {
        vinculandoTrakt?.cancel()
        vinculandoTrakt = viewModelScope.launch {
            VinculoTrakt(sesionTrakt::startDeviceAuth, sesionTrakt::pollDeviceToken).vincular().collect { p ->
                _trakt.value = p
                if (p is PasoTrakt.Hecho) progresoTrakt.refreshNow()
            }
        }
    }

    fun terminar(hecho: () -> Unit) {
        cancelarVinculo()
        viewModelScope.launch {
            almacen.terminar()
            hecho()
        }
    }
}
