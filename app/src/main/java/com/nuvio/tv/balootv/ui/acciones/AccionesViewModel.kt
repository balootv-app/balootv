package com.nuvio.tv.balootv.ui.acciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.repository.parseContentIds
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * El menú de pulsación larga abierto: sobre qué título y qué se puede hacer con él. En una serie, [episodio] es el
 * que se marca como visto y [desmarcar], el último visto, el que se marca como no visto.
 */
data class MenuAbierto(
    val objetivo: ObjetivoAccion,
    val opciones: List<AccionTitulo>,
    val episodio: EpisodioObjetivo?,
    val desmarcar: Pair<Int, Int>? = null,
) {
    fun texto(accion: AccionTitulo) =
        OpcionesTitulo.texto(accion, objetivo.esSerie, episodio?.let { it.temporada to it.episodio }, desmarcar)
}

@HiltViewModel
class AccionesViewModel @Inject constructor(
    private val watchProgressRepository: WatchProgressRepository,
    private val libraryRepository: LibraryRepository,
    private val metaRepository: MetaRepository,
) : ViewModel() {

    private val _menu = MutableStateFlow<MenuAbierto?>(null)
    val menu: StateFlow<MenuAbierto?> = _menu.asStateFlow()

    /** Avisos para el usuario (confirmaciones y errores). */
    val avisos = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun abrir(objetivo: ObjetivoAccion) {
        viewModelScope.launch {
            val enMiLista = runCatching {
                libraryRepository.isInLibrary(objetivo.id, objetivo.tipo).first() ||
                    libraryRepository.isInWatchlist(objetivo.id, objetivo.tipo).first()
            }.getOrDefault(false)
            var episodio = objetivo.episodio
            val vistos = if (objetivo.esSerie) episodiosVistos(objetivo.id) else emptySet()
            val peliculaVista = !objetivo.esSerie &&
                runCatching { watchProgressRepository.isWatched(objetivo.id, objetivo.id).first() }.getOrDefault(false)
            val vistoEntero = if (!objetivo.esSerie) {
                peliculaVista
            } else if (episodio != null) {
                false
            } else {
                val (siguiente, conocido) = episodioQueToca(objetivo, vistos)
                episodio = siguiente
                // Sin poder saber los episodios no se dice que esté vista: se ofrece marcar y, si falla, se avisa.
                conocido && siguiente == null
            }
            val desmarcar = EpisodioQueToca.ultimoVisto(vistos)
            val algoVisto = if (objetivo.esSerie) desmarcar != null else peliculaVista
            val opciones = OpcionesTitulo.de(SituacionTitulo(objetivo.enSeguirViendo, vistoEntero, algoVisto, enMiLista))
            _menu.value = MenuAbierto(objetivo, opciones, episodio, desmarcar)
        }
    }

    fun cerrar() {
        _menu.value = null
    }

    fun marcarVista(menu: MenuAbierto) {
        cerrar()
        val objetivo = menu.objetivo
        viewModelScope.launch {
            val ids = setOf(objetivo.id)
            runCatching {
                if (!objetivo.esSerie) {
                    watchProgressRepository.applyOptimisticWatchedMovie(ids, add = true)
                    try {
                        watchProgressRepository.markAsCompleted(progresoCompleto(objetivo, null))
                    } catch (e: Exception) {
                        watchProgressRepository.revertOptimisticWatchedMovie(ids, add = true)
                        throw e
                    }
                    avisos.tryEmit("${objetivo.nombre}: marcada como vista")
                } else {
                    val episodio = menu.episodio ?: error("No se sabe qué episodio toca")
                    watchProgressRepository.markAsCompleted(progresoCompleto(objetivo, episodio))
                    avisos.tryEmit("${objetivo.nombre}: T${episodio.temporada} · E${episodio.episodio} marcado como visto")
                }
            }.onFailure { avisos.tryEmit(it.message ?: "No se ha podido marcar como vista") }
        }
    }

    fun desmarcarVista(menu: MenuAbierto) {
        cerrar()
        val objetivo = menu.objetivo
        viewModelScope.launch {
            val ids = setOf(objetivo.id)
            runCatching {
                if (!objetivo.esSerie) {
                    watchProgressRepository.applyOptimisticWatchedMovie(ids, add = false)
                    try {
                        watchProgressRepository.removeFromHistory(objetivo.id)
                    } catch (e: Exception) {
                        watchProgressRepository.revertOptimisticWatchedMovie(ids, add = false)
                        throw e
                    }
                    avisos.tryEmit("${objetivo.nombre}: marcada como no vista")
                } else {
                    val (temporada, episodio) = menu.desmarcar ?: error("No hay ningún episodio visto")
                    watchProgressRepository.removeFromHistory(objetivo.id, season = temporada, episode = episodio)
                    avisos.tryEmit("${objetivo.nombre}: T$temporada · E$episodio marcado como no visto")
                }
            }.onFailure { avisos.tryEmit(it.message ?: "No se ha podido marcar como no vista") }
        }
    }

    fun anadirALista(objetivo: ObjetivoAccion) {
        cerrar()
        viewModelScope.launch {
            runCatching { libraryRepository.toggleDefault(objetivo.comoEntrada()) }
                .onSuccess { avisos.tryEmit("${objetivo.nombre}: añadida a Mi lista") }
                .onFailure { avisos.tryEmit(it.message ?: "No se ha podido añadir a Mi lista") }
        }
    }

    /** Lo quita de todas las listas en las que esté, que son las que junta Mi lista. */
    fun quitarDeLista(objetivo: ObjetivoAccion) {
        cerrar()
        viewModelScope.launch {
            runCatching {
                val entrada = objetivo.comoEntrada()
                val listas = libraryRepository.getMembershipSnapshot(entrada).listMembership
                val resultado = libraryRepository.applyMembershipChanges(entrada, ListMembershipChanges(listas.mapValues { false }))
                check(!resultado.requiresRemovalConfirmation) { "Quítala desde la ficha: el servicio pide confirmación" }
            }
                .onSuccess { avisos.tryEmit("${objetivo.nombre}: quitada de Mi lista") }
                .onFailure { avisos.tryEmit(it.message ?: "No se ha podido quitar de Mi lista") }
        }
    }

    /** Los episodios vistos de la serie, según el historial y el progreso guardado. */
    private suspend fun episodiosVistos(id: String): Set<Pair<Int, Int>> = buildSet {
        addAll(runCatching { watchProgressRepository.getWatchedShowEpisodes()[id] }.getOrNull().orEmpty())
        runCatching { watchProgressRepository.getAllEpisodeProgress(id).first() }.getOrNull().orEmpty()
            .filterValues { it.isCompleted() }.keys.forEach { add(it) }
    }

    /** El episodio sin ver que toca según el progreso, y si se pudieron conocer los episodios. */
    private suspend fun episodioQueToca(objetivo: ObjetivoAccion, vistos: Set<Pair<Int, Int>>): Pair<EpisodioObjetivo?, Boolean> {
        val meta = runCatching {
            metaRepository.getMetaFromPrimaryAddon(objetivo.tipo, objetivo.id)
                .first { it is NetworkResult.Success || it is NetworkResult.Error }
        }.getOrNull() as? NetworkResult.Success ?: return null to false
        val videos = meta.data.videos.filter { it.season != null && it.episode != null && it.available != false }
        if (videos.isEmpty()) return null to false
        val toca = EpisodioQueToca.de(videos.map { it.season!! to it.episode!! }, vistos) ?: return null to true
        val video = videos.first { it.season == toca.first && it.episode == toca.second }
        return EpisodioObjetivo(toca.first, toca.second, video.id, video.title) to true
    }

    private fun progresoCompleto(objetivo: ObjetivoAccion, episodio: EpisodioObjetivo?) = WatchProgress(
        contentId = objetivo.id, contentType = objetivo.tipo, name = objetivo.nombre,
        poster = objetivo.poster, backdrop = objetivo.fondo, logo = objetivo.logo,
        videoId = episodio?.videoId ?: objetivo.id, season = episodio?.temporada, episode = episodio?.episodio,
        episodeTitle = episodio?.titulo, position = 1L, duration = 1L,
        lastWatched = System.currentTimeMillis(), progressPercent = 100f,
    )

    private fun ObjetivoAccion.comoEntrada(): LibraryEntryInput {
        val ids = parseContentIds(id)
        return LibraryEntryInput(
            itemId = id, itemType = tipo, title = nombre,
            year = Regex("(\\d{4})").find(releaseInfo.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull(),
            traktId = ids.trakt, imdbId = ids.imdb, tmdbId = ids.tmdb,
            poster = poster, posterShape = PosterShape.POSTER, background = fondo, logo = logo,
            releaseInfo = releaseInfo,
        )
    }
}
