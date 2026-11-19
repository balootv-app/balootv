package com.nuvio.tv.balootv.ajustes

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.balootv.catalogo.ConfigCatalogo
import com.nuvio.tv.balootv.catalogo.Plataforma
import com.nuvio.tv.balootv.cuenta.ClaveTorbox
import com.nuvio.tv.balootv.cuenta.CuentaTorbox
import com.nuvio.tv.balootv.cuenta.EstadoTorbox
import com.nuvio.tv.balootv.cuenta.PasoVinculo
import com.nuvio.tv.balootv.cuenta.VinculoTorbox
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Estado y acciones de la pantalla de Ajustes de BalooTV (tablero 10). */
@HiltViewModel
class AjustesBalooTVViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reproductor: PlayerSettingsDataStore,
    trakt: TraktAuthDataStore,
    private val sesionTrakt: TraktAuthService,
    private val claveTorbox: ClaveTorbox,
    private val vinculo: VinculoTorbox,
    private val plataformas: AlmacenPlataformas,
    addons: AddonRepository,
) : ViewModel() {

    private val torbox = MutableStateFlow<EstadoTorbox?>(null)

    /** La vinculación de otra cuenta de TorBox en curso; null si no se está vinculando. */
    val vinculacion = MutableStateFlow<Vinculacion?>(null)
    private var vinculando: Job? = null
    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

    /** Los addons instalados, para contar las fuentes. */
    private val instalados = addons.getInstalledAddons().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val entrada: StateFlow<EntradaAjustes?> = combine(reproductor.playerSettings, trakt.state, torbox, plataformas.ids, instalados) { p, t, tb, ids, ad ->
        EntradaAjustes(
            usuarioTrakt = t.username?.takeIf { t.isAuthenticated },
            modo = p.streamAutoPlayMode,
            tamanoSubtitulos = p.subtitleStyle.size,
            alturaSubtitulos = p.subtitleStyle.verticalOffset,
            torbox = tb,
            plataformasActivas = PlataformasPreferidas.activas(ids, ConfigCatalogo.plataformas).size,
            fuentesEnUso = ResumenAddons.fuentesEnUso(ad),
            version = BuildConfig.VERSION_NAME,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Plataformas con su estado (activa o no), en el orden elegido; las inactivas al final. */
    val plataformasConEstado: StateFlow<List<Pair<Plataforma, Boolean>>> = plataformas.ids.map { ids ->
        val activas = PlataformasPreferidas.activas(ids, ConfigCatalogo.plataformas)
        activas.map { it to true } + ConfigCatalogo.plataformas.filterNot { it in activas }.map { it to false }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        comprobarTorbox()
    }

    fun comprobarTorbox() {
        torbox.value = null
        viewModelScope.launch {
            torbox.value = CuentaTorbox(http, claveTorbox.actual(), BuildConfig.VERSION_NAME).estado()
        }
    }

    /** Pide un código a TorBox y, cuando se aprueba y la cuenta tiene suscripción, pasa la app a esa clave. */
    fun vincularTorbox() {
        vinculando?.cancel()
        vinculacion.value = Vinculacion.Pidiendo
        vinculando = viewModelScope.launch {
            flujoVinculo(vinculo, claveTorbox, http).collect { v ->
                vinculacion.value = v
                if (v == Vinculacion.Hecha) comprobarTorbox()
            }
        }
    }

    fun cancelarVinculo() {
        vinculando?.cancel()
        vinculacion.value = null
    }

    fun elegirModo(manual: Boolean) = viewModelScope.launch {
        reproductor.setStreamAutoPlayMode(if (manual) StreamAutoPlayMode.MANUAL else StreamAutoPlayMode.BALOOTV)
    }

    fun elegirTamano(t: TamanoSubtitulos) = viewModelScope.launch { reproductor.setSubtitleSize(t.valor) }

    fun elegirAltura(a: AlturaSubtitulos) = viewModelScope.launch { reproductor.setSubtitleVerticalOffset(a.valor) }

    /** Activa o desactiva una plataforma; nunca deja el catálogo sin ninguna. */
    fun alternarPlataforma(p: Plataforma) = viewModelScope.launch {
        val actuales = PlataformasPreferidas.activas(plataformas.ids.value, ConfigCatalogo.plataformas).map { it.id }
        val nuevas = if (p.id in actuales) actuales - p.id else actuales + p.id
        if (nuevas.isNotEmpty()) plataformas.guardar(nuevas)
    }

    /** Sube una plataforma activa un puesto (su logo gana a las de debajo). */
    fun subirPlataforma(p: Plataforma) = viewModelScope.launch {
        val actuales = PlataformasPreferidas.activas(plataformas.ids.value, ConfigCatalogo.plataformas).map { it.id }.toMutableList()
        val i = actuales.indexOf(p.id)
        if (i > 0) {
            actuales[i] = actuales[i - 1]
            actuales[i - 1] = p.id
            plataformas.guardar(actuales)
        }
    }

    /** Vacía la caché (TMDB, imágenes): la próxima vez todo se pide de nuevo. */
    /** Revoca el token en Trakt y borra la sesión; para volver a vincular se usa la pantalla heredada. */
    fun desconectarTrakt(hecho: () -> Unit) = viewModelScope.launch {
        sesionTrakt.revokeAndLogout()
        hecho()
    }

    fun borrarCache(hecho: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        hecho()
    }
}

sealed interface Vinculacion {
    data object Pidiendo : Vinculacion
    data class Codigo(val paso: PasoVinculo.Codigo, val qr: Bitmap) : Vinculacion
    data object Hecha : Vinculacion
    data class Fallo(val motivo: String) : Vinculacion
}
