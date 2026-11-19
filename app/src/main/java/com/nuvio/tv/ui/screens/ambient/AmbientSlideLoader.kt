package com.nuvio.tv.ui.screens.ambient

import com.nuvio.tv.balootv.ficha.TextosFicha
import com.nuvio.tv.balootv.inicio.FilasInicio
import com.nuvio.tv.balootv.inicio.RepositorioInicio
import com.nuvio.tv.domain.model.MetaPreview
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BalooTV: las sugerencias del salvapantallas son las de las filas de ideas del Inicio de hoy (Para ti, Porque viste,
 * lo de la semana y del mes), así que cambian cada día. Antes salían los diez primeros de un solo catálogo, siempre los
 * mismos. Bajo el título, sus géneros de TMDB escritos como en la ficha.
 */
@Singleton
class AmbientSlideLoader @Inject constructor(
    private val inicio: RepositorioInicio,
) {
    suspend fun load(): List<AmbientSlide> {
        inicio.asegurar()
        val guardado = inicio.guardado.value ?: return emptyList()
        return FilasInicio.salvapantallas(guardado, LocalDate.now(MADRID)).mapNotNull { it.toSlide() }
    }

    private fun MetaPreview.toSlide(): AmbientSlide? {
        val backdrop = background?.takeIf { it.isNotBlank() }
            ?: landscapePoster?.takeIf { it.isNotBlank() }
            ?: return null
        return AmbientSlide(
            id = "$apiType:$id",
            title = name,
            backdropUrl = backdrop,
            logoUrl = logo?.takeIf { it.isNotBlank() },
            generos = TextosFicha.generos(genres)
        )
    }

    private companion object {
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
