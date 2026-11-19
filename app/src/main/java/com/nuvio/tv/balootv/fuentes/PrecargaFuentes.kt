package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.repository.StreamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Singleton

/**
 * Pide las fuentes de lo que se reproduciría en cuanto se abre la ficha. La búsqueda queda en la caché de
 * sesiones de [StreamRepository], así que al pulsar Reproducir la pantalla de fuentes recoge lo ya recibido
 * en vez de empezar de cero. Con la petición de BalooTV, además clasifica cada tanda en segundo plano: el
 * selector recuerda el análisis y al pulsar Reproducir elige al momento. Una vez por título o episodio cada
 * 5 minutos.
 */
class PrecargaFuentes(
    private val repositorio: StreamRepository,
    private val ambito: CoroutineScope,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val calentar: (List<Stream>, Peticion) -> Eleccion = { enlaces, p ->
        SelectorBalooTV.elegir(enlaces.map(Candidato::desde), p)
    },
) {
    private val ultimas = HashMap<String, Long>()
    private val _lineaElegida = MutableStateFlow<Map<String, Formato>>(emptyMap())

    /** Clave `tipo|videoId|temporada|episodio` → formato de la fuente que arrancaría (bajo el botón de la ficha). */
    val lineaElegida: StateFlow<Map<String, Formato>> = _lineaElegida.asStateFlow()

    private val _motivos = MutableStateFlow<Map<String, Motivo>>(emptyMap())

    /**
     * Misma clave → por qué no arrancaría solo (encargo 002: la ficha lo dice bajo el botón). Solo con la búsqueda
     * acabada: una tanda temprana sin castellano no es motivo mientras faltan fuentes por contestar.
     */
    val motivos: StateFlow<Map<String, Motivo>> = _motivos.asStateFlow()

    fun precargar(
        tipo: String,
        videoId: String,
        temporada: Int?,
        episodio: Int?,
        peticion: (suspend () -> Peticion?)? = null,
    ) {
        val clave = "$tipo|$videoId|$temporada|$episodio"
        val t = ahora()
        synchronized(ultimas) {
            val previa = ultimas[clave]
            if (previa != null && t - previa < VENTANA_MS) return
            ultimas[clave] = t
        }
        ambito.launch {
            withTimeoutOrNull(LIMITE_MS) {
                runCatching {
                    val p = peticion?.invoke()
                    var ultima: Eleccion? = null
                    var elegida = false
                    repositorio.getStreamsFromAllAddons(tipo, videoId, temporada, episodio).collect { r ->
                        if (p != null && r is NetworkResult.Success) {
                            val e = calentar(r.data.flatMap { it.streams }, p)
                            ultima = e
                            if (e is Eleccion.Elegido) {
                                elegida = true
                                _lineaElegida.update { it + (clave to LineasFuente.de(e.mejor).formato) }
                            }
                        }
                    }
                    // Lo que ya se pintó como elegido no se cambia por un motivo (el selector lo recuerda).
                    val sin = ultima as? Eleccion.SinEleccion
                    if (sin != null && !elegida) _motivos.update { it + (clave to sin.motivo) } else _motivos.update { it - clave }
                }
            }
        }
    }

    private companion object {
        const val VENTANA_MS = 5 * 60_000L
        const val LIMITE_MS = 90_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloPrecargaFuentes {
    @Provides
    @Singleton
    fun precargaFuentes(repositorio: StreamRepository): PrecargaFuentes =
        PrecargaFuentes(repositorio, CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
