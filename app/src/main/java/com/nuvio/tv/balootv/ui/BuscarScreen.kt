package com.nuvio.tv.balootv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.acciones.ObjetivoAccion
import com.nuvio.tv.balootv.ui.acciones.rememberMenuTitulo
import com.nuvio.tv.balootv.catalogo.CatalogoBalooTV
import com.nuvio.tv.balootv.ui.componentes.FilaCarteles
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.VueltaFoco
import com.nuvio.tv.balootv.ui.componentes.RecordarAlSalir
import com.nuvio.tv.balootv.ui.movimiento.ConRitmo

/** Teclado de una sola línea, como el de tvOS (tablero 17). */
object TecladoLineal {
    const val ESPACIO = "␣"
    const val BORRAR = "⌫"
    const val NUMEROS = "123"
    const val LETRAS = "abc"

    private val letras = "abcdefghijklmnñopqrstuvwxyz".map { it.toString() }

    fun teclas(numeros: Boolean): List<String> =
        if (numeros) (0..9).map { it.toString() } + listOf(ESPACIO, BORRAR, LETRAS) else letras + listOf(ESPACIO, BORRAR, NUMEROS)

    fun pulsar(texto: String, tecla: String): String = when (tecla) {
        ESPACIO -> if (texto.isEmpty() || texto.endsWith(" ")) texto else "$texto "
        BORRAR -> texto.dropLast(1)
        NUMEROS, LETRAS -> texto
        else -> texto + tecla
    }
}

/** Agrupa los resultados como en el tablero 17: el anime (animación japonesa) va aparte y primero. */
object Resultados {
    private fun esAnime(m: MetaPreview) = m.language == "ja" && m.genres.any { it.equals("Animación", ignoreCase = true) }

    /**
     * Películas, Series y Anime, salvo que un título de otro grupo empiece por lo escrito: ese grupo sube
     * («stra» → Series, por Stranger Things; «frie» → Anime, por Frieren).
     */
    fun agrupar(peliculas: List<MetaPreview>, series: List<MetaPreview>, texto: String = ""): List<Pair<String, List<MetaPreview>>> = listOf(
        "Películas" to peliculas.filterNot(::esAnime),
        "Series" to series.filterNot(::esAnime),
        "Anime" to (series + peliculas).filter(::esAnime),
    ).filter { it.second.isNotEmpty() }
        .sortedByDescending { (_, items) -> items.maxOf { coincidencia(it.name, texto) } }

    private fun normal(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** 2 si el título empieza por lo escrito, 1 si alguna palabra empieza por ello, 0 si no. */
    private fun coincidencia(nombre: String, texto: String): Int {
        val t = normal(texto.trim())
        if (t.isEmpty()) return 0
        val n = normal(nombre)
        return when {
            n.startsWith(t) -> 2
            n.split(' ').any { it.startsWith(t) } -> 1
            else -> 0
        }
    }
}

data class EstadoBusqueda(
    val texto: String = "",
    val grupos: List<Pair<String, List<MetaPreview>>> = emptyList(),
    val buscando: Boolean = false,
)

/** Busca en TMDB en castellano (catálogo de BalooTV) con 300 ms de espera entre pulsaciones. */
@HiltViewModel
class BuscarViewModel @Inject constructor(private val catalogo: CatalogoBalooTV) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoBusqueda())
    val estado: StateFlow<EstadoBusqueda> = _estado.asStateFlow()
    private var busqueda: Job? = null

    /** El resultado abierto, para devolverle el foco al volver de la ficha. */
    val vuelta = VueltaFoco()

    fun pulsar(tecla: String) = escribir(TecladoLineal.pulsar(_estado.value.texto, tecla))

    fun escribir(texto: String) {
        if (texto == _estado.value.texto) return
        _estado.update { it.copy(texto = texto) }
        busqueda?.cancel()
        if (texto.isBlank()) {
            _estado.update { it.copy(grupos = emptyList(), buscando = false) }
            return
        }
        busqueda = viewModelScope.launch {
            delay(300)
            _estado.update { it.copy(buscando = true) }
            val extra = mapOf("search" to texto.trim())
            val pelis = async { runCatching { catalogo.fila(CatalogoBalooTV.BUSCAR_PELICULAS, "movie", 0, extra).items }.getOrDefault(emptyList()) }
            val series = async { runCatching { catalogo.fila(CatalogoBalooTV.BUSCAR_SERIES, "series", 0, extra).items }.getOrDefault(emptyList()) }
            val grupos = Resultados.agrupar(pelis.await(), series.await(), texto)
            _estado.update { if (it.texto == texto) it.copy(grupos = grupos, buscando = false) else it }
        }
    }
}

private val PILDORA = RoundedCornerShape(20.dp)
private val TECLA = RoundedCornerShape(6.dp)

/** Buscar (tablero 17): campo de cristal, teclado en una línea y resultados agrupados. */
@Composable
fun BuscarScreen(onVolver: () -> Unit, onAbrir: (MetaPreview) -> Unit, viewModel: BuscarViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val abrirMenu = rememberMenuTitulo()
    var numeros by remember { mutableStateOf(false) }
    val primeraTecla = remember { FocusRequester() }
    BackHandler(onBack = onVolver)
    RecordarAlSalir(viewModel.vuelta)
    LaunchedEffect(Unit) { if (!viewModel.vuelta.activa) runCatching { primeraTecla.requestFocus() } }

    ConRitmo(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(top = 25.dp)) {
        Row(
            Modifier.align(Alignment.CenterHorizontally).width(450.dp)
                .background(Color(0x9928282C), PILDORA).border(0.5.dp, Color.White.copy(alpha = 0.2f), PILDORA)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
            Text(estado.texto.ifEmpty { "Películas, series y anime" }, fontSize = EscalaTv.LLAMADA, fontWeight = FontWeight.Medium,
                color = if (estado.texto.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White)
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        ) {
            items(TecladoLineal.teclas(numeros), key = { it }) { tecla ->
                Surface(
                    onClick = {
                        when (tecla) {
                            TecladoLineal.NUMEROS -> numeros = true
                            TecladoLineal.LETRAS -> numeros = false
                            else -> viewModel.pulsar(tecla)
                        }
                    },
                    modifier = (if (tecla == TecladoLineal.teclas(numeros).first()) Modifier.focusRequester(primeraTecla) else Modifier)
                        .size(if (tecla.length > 1) 34.dp else 23.dp, 27.dp),
                    shape = ClickableSurfaceDefaults.shape(TECLA),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent, contentColor = Color.White.copy(alpha = 0.85f),
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(tecla, fontSize = EscalaTv.CUERPO, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        when {
            estado.texto.isBlank() -> Unit
            estado.grupos.isEmpty() && !estado.buscando -> Text(
                "Sin resultados para «${estado.texto.trim()}»",
                Modifier.fillMaxWidth().padding(top = 60.dp), color = Color.White.copy(alpha = 0.6f), fontSize = EscalaTv.CUERPO, textAlign = TextAlign.Center,
            )
            else -> LazyColumn(Modifier.padding(top = 18.dp), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(estado.grupos, key = { it.first }) { (titulo, items) ->
                    FilaCarteles(
                        titulo, items, onFoco = {},
                        onClick = { viewModel.vuelta.abrir(titulo, it.id); onAbrir(it) },
                        onMantener = { abrirMenu(ObjetivoAccion.de(it)) },
                        idAlVolver = viewModel.vuelta.en(titulo), onVuelto = viewModel.vuelta::cumplida,
                    )
                }
            }
        }
    }
    }
}
