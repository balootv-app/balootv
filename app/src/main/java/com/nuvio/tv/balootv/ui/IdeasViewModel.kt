package com.nuvio.tv.balootv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.balootv.inicio.EnCurso
import com.nuvio.tv.balootv.inicio.FilaInicio
import com.nuvio.tv.balootv.inicio.FilasInicio
import com.nuvio.tv.balootv.inicio.InicioGuardado
import com.nuvio.tv.balootv.inicio.Marca
import com.nuvio.tv.balootv.inicio.RepositorioInicio
import com.nuvio.tv.balootv.inicio.Tipo
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Las filas de ideas del Inicio (Parte 1 del diseño del móvil): lo guardado del día cruzado con lo que ahora está en la
 * watchlist, en «Recomendadas» o en Seguir viendo, que no se enseña como idea.
 */
@HiltViewModel
class IdeasViewModel @Inject constructor(
    private val repositorio: RepositorioInicio,
    biblioteca: LibraryRepository,
) : ViewModel() {

    private val enCurso = MutableStateFlow<List<EnCurso>>(emptyList())

    private val _listas = MutableStateFlow(false)
    /** Si ya se sabe qué filas hay (lo guardado, o la primera vez, lo pedido a Trakt). */
    val listas: StateFlow<Boolean> = _listas.asStateFlow()

    val filas: StateFlow<List<FilaInicio>> =
        combine(repositorio.guardado, biblioteca.libraryItems, biblioteca.listTabs, enCurso) { g, e, p, c ->
            g ?: return@combine emptyList()
            FilasInicio.de(g, enLista(g, e, p), c, LocalDate.now(MADRID))
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refrescar()
    }

    /** Al volver al Inicio: si lo guardado es de otro día, se rehace. */
    fun refrescar() {
        viewModelScope.launch {
            repositorio.asegurar()
            _listas.value = true
        }
    }

    fun seguirViendo(items: List<EnCurso>) {
        enCurso.value = items
    }

    private fun enLista(g: InicioGuardado, entradas: List<LibraryEntry>, pestanas: List<LibraryListTab>): List<Marca> {
        val claves = pestanas.filter { it.type == LibraryListTab.Type.WATCHLIST }.map { it.key }.toSet() +
            listOfNotNull(MiLista.recomendadas(pestanas)?.key)
        val porImdb = FilasInicio.indiceImdb(g)
        return entradas.filter { e -> e.listKeys.any(claves::contains) }.mapNotNull { e ->
            val tipo = if (e.type == "movie") Tipo.PELICULA else Tipo.SERIE
            FilasInicio.marca(tipo, e.id, e.tmdbId, e.traktId, porImdb) ?: e.imdbId?.let { FilasInicio.marca(tipo, it, null, null, porImdb) }
        }
    }

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
