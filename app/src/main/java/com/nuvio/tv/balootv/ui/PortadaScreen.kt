package com.nuvio.tv.balootv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.balootv.ui.componentes.Text
import com.nuvio.tv.balootv.ui.acciones.ObjetivoAccion
import com.nuvio.tv.balootv.ui.acciones.rememberMenuTitulo
import com.nuvio.tv.balootv.catalogo.Portada
import com.nuvio.tv.balootv.ui.componentes.CabeceraEnfocada
import com.nuvio.tv.balootv.ui.componentes.ChipsPlataforma
import com.nuvio.tv.balootv.ui.componentes.FilaCarteles
import com.nuvio.tv.balootv.ui.componentes.FondoEnfocado
import com.nuvio.tv.domain.model.MetaPreview
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import com.nuvio.tv.balootv.ui.componentes.FilaNormal
import com.nuvio.tv.balootv.ui.componentes.rememberFilasArriba
import com.nuvio.tv.balootv.ui.componentes.EscalaTv
import com.nuvio.tv.balootv.ui.componentes.RecordarAlSalir
import com.nuvio.tv.balootv.ui.movimiento.ConRitmo

/**
 * Portada de Películas, Series o Anime (tableros 13–15): barra de pestañas, filtro por plataforma (no en
 * Anime), la información del título enfocado con su nota IMDb y las filas de carteles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortadaScreen(
    onIr: (String) -> Unit,
    onAbrir: (MetaPreview) -> Unit,
    viewModel: PortadaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val abrirMenu = rememberMenuTitulo()
    val focoBarra = remember { FocusRequester() }
    val pestana = when (estado.portada) {
        Portada.PELICULAS -> Pestana.PELICULAS
        Portada.SERIES -> Pestana.SERIES
        Portada.ANIME -> Pestana.ANIME
    }
    RecordarAlSalir(viewModel.vuelta)
    LaunchedEffect(Unit) { if (!viewModel.vuelta.activa) runCatching { focoBarra.requestFocus() } }

    ConRitmo(Modifier.fillMaxSize()) {
        FondoEnfocado(estado.fondo)
        Column(Modifier.fillMaxSize()) {
            BarraPestanas(actual = pestana, onIr = onIr, foco = focoBarra)
            if (estado.ofreceFiltro) {
                ChipsPlataforma(estado.plataformas, estado.plataforma, viewModel::elegirPlataforma)
            }
            CabeceraEnfocada(estado.enfocado, estado.nota, Modifier.padding(top = 4.dp))
            when {
                estado.cargando && estado.filas.isEmpty() -> Unit
                estado.filas.isEmpty() -> Text(
                    if (estado.plataforma != null) "Nada en ${estado.plataforma!!.nombre} ahora mismo" else "No se ha podido cargar el catálogo",
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp),
                    color = Color.White.copy(alpha = 0.6f), fontSize = EscalaTv.CUERPO,
                )
                else -> {
                    val normal = LocalBringIntoViewSpec.current
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberFilasArriba()) {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(estado.filas, key = { it.catalogId }) { fila ->
                                FilaNormal(normal) {
                                    FilaCarteles(
                                        fila.catalogName, fila.items, viewModel::enfocar,
                                        onClick = { viewModel.vuelta.abrir(fila.catalogId, it.id); onAbrir(it) },
                                        onMantener = { abrirMenu(ObjetivoAccion.de(it)) },
                                        idAlVolver = viewModel.vuelta.en(fila.catalogId), onVuelto = viewModel.vuelta::cumplida,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
