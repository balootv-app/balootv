package com.nuvio.tv.balootv.catalogo

import android.content.Context
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogExtra
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.InsigniaPlataforma
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import javax.inject.Singleton

/**
 * Addon de catálogo de BalooTV, dentro de la app (como los servidores de NuvioTV): filas por intención
 * que mezclan las plataformas de España (ARQUITECTURA §4) y búsqueda en castellano, todo desde TMDB.
 * Los títulos llevan id `tmdb:<id>`; la ficha ya sabe convertirlo a IMDb.
 */
class CatalogoBalooTV(
    private val tmdb: FuenteTmdb,
    private val hoy: () -> LocalDate = LocalDate::now,
    private val plataformas: () -> List<Plataforma> = { ConfigCatalogo.plataformas },
) {

    val addon: Addon = Addon(
        id = ADDON_ID,
        name = "BalooTV",
        version = "1",
        description = "Catálogo de España",
        logo = null,
        baseUrl = BASE_URL,
        // Todas las filas se pueden pedir por id; ninguna sale en el Inicio, que es de ideas de Trakt (balootv.inicio).
        catalogs = ConfigCatalogo.filas.map { fila ->
            CatalogDescriptor(
                type = contenido(fila.tipo),
                id = fila.id,
                name = fila.titulo,
                extra = listOf(CatalogExtra(name = "skip")),
                showInHome = false,
                hasExplicitShowInHome = true,
            )
        } + listOf(
            busqueda(BUSCAR_PELICULAS, ContentType.MOVIE, "Películas"),
            busqueda(BUSCAR_SERIES, ContentType.SERIES, "Series"),
        ),
        types = listOf(ContentType.MOVIE, ContentType.SERIES),
        resources = listOf(AddonResource(name = "catalog", types = emptyList(), idPrefixes = null)),
    )

    /** Una página de una fila o de una búsqueda. Si TMDB no responde, la fila sale vacía y sin más páginas. */
    suspend fun fila(catalogId: String, tipo: String, skip: Int, extra: Map<String, String>): CatalogRow {
        if (catalogId == BUSCAR_PELICULAS || catalogId == BUSCAR_SERIES) return buscar(catalogId, skip, extra["search"].orEmpty())
        val fila = ConfigCatalogo.filas.firstOrNull { it.id == catalogId } ?: return vacia(catalogId, catalogId, tipo, skip)
        val pagina = skip / POR_PAGINA + 1
        val elegida = extra["plataforma"]?.toIntOrNull()?.let { id -> ConfigCatalogo.plataformas.firstOrNull { it.id == id } }
        val consultadas: List<Plataforma?> = when {
            fila.sinPlataformas -> listOf(null)
            elegida != null -> listOf(elegida)
            else -> plataformas()
        }
        val respuestas = coroutineScope {
            consultadas.map { p ->
                async { p to tmdb.descubrir(fila.tipo, ConfigCatalogo.parametros(fila, p, pagina, hoy())) }
            }.awaitAll()
        }
        val titulos: List<Pair<Titulo, Plataforma?>> = if (fila.sinPlataformas) {
            sinJapones(fila, pagina, respuestas.single().second.orEmpty().take(POR_PAGINA)).map { it to null }
        } else {
            FusionPlataformas.fusionar(respuestas.map { (p, r) -> p!! to r.orEmpty() }, fila.orden, POR_PAGINA).map { it.titulo to it.plataforma }
        }
        val hayMas = respuestas.any { (_, r) -> (r?.size ?: 0) >= TMDB_POR_PAGINA }
        return CatalogRow(
            addonId = ADDON_ID,
            addonName = addon.name,
            addonBaseUrl = BASE_URL,
            catalogId = fila.id,
            catalogName = fila.titulo,
            type = contenido(fila.tipo),
            items = titulos.map { (t, p) -> vista(t, p) },
            hasMore = hayMas,
            currentPage = pagina - 1,
            supportsSkip = true,
            skipStep = POR_PAGINA,
            nextSkip = pagina * POR_PAGINA,
        )
    }

    /**
     * Anime sin traducción española: TMDB devuelve el título japonés. Se pide la misma página en inglés (una
     * consulta más, con la misma caché) y se usa ese título solo para los que vienen en japonés.
     */
    private suspend fun sinJapones(fila: Fila, pagina: Int, titulos: List<Titulo>): List<Titulo> {
        if (titulos.none { TituloEspanol.esJapones(it.nombre) }) return titulos
        val parametros = ConfigCatalogo.parametros(fila, null, pagina, hoy()) + ("language" to "en-US")
        val enIngles = tmdb.descubrir(fila.tipo, parametros).orEmpty().associate { it.tmdbId to it.nombre }
        return titulos.map { t -> t.copy(nombre = TituloEspanol.elegir(t.nombre, enIngles[t.tmdbId])) }
    }

    private suspend fun buscar(catalogId: String, skip: Int, texto: String): CatalogRow {
        val tipo = if (catalogId == BUSCAR_SERIES) TipoFila.SERIE else TipoFila.PELICULA
        val pagina = skip / TMDB_POR_PAGINA + 1
        val encontrados = tmdb.buscar(tipo, texto, pagina).orEmpty()
        // TMDB ordena por parecido del título; lo útil en la tele es lo conocido primero, y sin cartel no sirve.
        val titulos = encontrados.filter { it.poster != null }.sortedByDescending { it.popularidad ?: 0.0 }
        return CatalogRow(
            addonId = ADDON_ID,
            addonName = addon.name,
            addonBaseUrl = BASE_URL,
            catalogId = catalogId,
            catalogName = if (tipo == TipoFila.SERIE) "Series" else "Películas",
            type = contenido(tipo),
            items = titulos.map { vista(it, null) },
            hasMore = encontrados.size >= TMDB_POR_PAGINA,
            currentPage = pagina - 1,
            supportsSkip = true,
            skipStep = TMDB_POR_PAGINA,
            nextSkip = pagina * TMDB_POR_PAGINA,
            extraArgs = mapOf("search" to texto),
        )
    }

    private fun vacia(catalogId: String, nombre: String, tipo: String, skip: Int) = CatalogRow(
        addonId = ADDON_ID, addonName = addon.name, addonBaseUrl = BASE_URL, catalogId = catalogId,
        catalogName = nombre, type = ContentType.fromString(tipo), items = emptyList(), hasMore = false,
        nextSkip = skip,
    )

    private fun vista(t: Titulo, plataforma: Plataforma?) = MetaPreview(
        id = "tmdb:${t.tmdbId}",
        type = contenido(t.tipo),
        name = t.nombre,
        poster = t.poster?.let { "$IMAGENES/w342$it" },
        posterShape = PosterShape.POSTER,
        background = t.fondo?.let { "$IMAGENES/w1280$it" },
        logo = null,
        description = t.sinopsis,
        releaseInfo = t.fecha?.take(4),
        // La nota de TMDB no se muestra: la que se enseña es la de IMDb, al enfocar (NotaImdb).
        imdbRating = null,
        // Solo se marca la animación (género 16 de TMDB): con el idioma original basta para separar el anime.
        genres = if (16 in t.generos) listOf("Animación") else emptyList(),
        language = t.idiomaOriginal,
        released = t.fecha,
        voteCount = t.votos,
        plataforma = plataforma?.let { InsigniaPlataforma(it.nombre, "$IMAGENES/w92${it.logo}") },
    )

    private fun busqueda(id: String, tipo: ContentType, nombre: String) = CatalogDescriptor(
        type = tipo,
        id = id,
        name = nombre,
        extra = listOf(CatalogExtra(name = "search", isRequired = true), CatalogExtra(name = "skip")),
    )

    private fun contenido(tipo: TipoFila) = if (tipo == TipoFila.SERIE) ContentType.SERIES else ContentType.MOVIE

    companion object {
        const val BASE_URL = "balootv://catalogo"
        const val ADDON_ID = "balootv.catalogo"
        const val BUSCAR_PELICULAS = "buscar-peliculas"
        const val BUSCAR_SERIES = "buscar-series"
        private const val POR_PAGINA = 40
        private const val TMDB_POR_PAGINA = 20
        private const val IMAGENES = "https://image.tmdb.org/t/p"

        fun esPropio(baseUrl: String?): Boolean = baseUrl?.trimEnd('/') == BASE_URL
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloCatalogoBalooTV {
    @Provides
    @Singleton
    fun catalogo(@ApplicationContext context: Context, almacen: com.nuvio.tv.balootv.ajustes.AlmacenPlataformas): CatalogoBalooTV =
        CatalogoBalooTV(ClienteTmdbCatalogo.deLaApp(context), plataformas = almacen::activas)
}
