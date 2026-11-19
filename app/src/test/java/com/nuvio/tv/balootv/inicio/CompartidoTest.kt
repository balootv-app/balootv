package com.nuvio.tv.balootv.inicio

import com.nuvio.tv.balootv.ui.TarjetaSeguir
import com.nuvio.tv.data.repository.traktContinueWatchingCutoffEpochMs
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.CwMetaSummary
import com.nuvio.tv.ui.screens.home.CwVideoSummary
import com.nuvio.tv.ui.screens.home.NextUpInfo
import com.nuvio.tv.ui.screens.home.resolveNextUpVideoFromMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Los contratos C1, C2, C3, C7 y C8 con los casos de `compartido/`, los mismos que ejecuta el móvil (`CompartidoTest` de
 * balootv-movil, commit 3e1d288; D36 de su DECISIONES.md). Los JSON se copian sin tocar: si un caso cambia, se cambia
 * en los dos repos a la vez. La tele no enseña próximos estrenos, así que de temporadas.json solo mira la fila.
 * seguir.json (C7) sale de la tele y el móvil lo copia; siguiente.json (C8) lo escribe el móvil a partir de la tele.
 */
class CompartidoTest {

    @Test
    fun `C1 semillas de Porque viste del día`() = casos("semillas.json") { caso ->
        val vistos = caso.lista("vistos").map { v ->
            VistoFechado(titulo(v), Instant.parse(v.texto("vistoEn")))
        }
        val semillas = Semillas.elegir(
            vistos.filter { it.titulo.tipo == Tipo.PELICULA },
            vistos.filter { it.titulo.tipo == Tipo.SERIE },
            LocalDate.parse(caso.texto("hoy")),
        )
        val esperado = caso.lista("esperado").map { "${it.texto("tipo")}:${it.entero("trakt")}" }
        assertEquals(caso.texto("nombre"), esperado, semillas.map { "${it.tipo.name}:${it.ids.trakt}" })
    }

    @Test
    fun `C3 temporada nueva`() = casos("temporadas.json") { caso ->
        val series = caso.lista("series").map { s ->
            SerieEstrenos(
                Titulo.minimo(Tipo.SERIE, Ids(s.entero("tmdb"), trakt = s.entero("trakt")), "Serie ${s.entero("trakt")}", null),
                s.entero("ultimaTemporadaVista"),
                s.getValue("estrenos").jsonObject.entries.associate { (k, v) -> k.toInt() to LocalDate.parse(v.jsonPrimitive.content) },
            )
        }
        val hoy = LocalDate.parse(caso.texto("hoy"))
        val esperado = caso.lista("temporadaNueva").map { "${it.entero("trakt")}:T${it.entero("temporada")}:${it.texto("fecha")}" }
        assertEquals(caso.texto("nombre"), esperado, TemporadaNueva.calcular(series, hoy).map { "${it.titulo.ids.trakt}:T${it.temporada}:${it.fecha}" })
    }

    @Test
    fun `C2 composición de filas`() = casos("filas.json") { caso ->
        val e = caso.getValue("entrada").jsonObject
        fun ts(campo: String) = e.lista(campo).map(::titulo)
        fun marcas(campo: String) = ts(campo).map { Marca(it.tipo, it.ids) }
        val entrada = EntradaIdeas(
            paraTi = ts("paraTi"),
            porqueViste = e.lista("porqueViste").map { s ->
                Semilla(titulo(s.getValue("titulo").jsonObject), s.lista("relacionados").map(::titulo))
            },
            estaSemana = ts("estaSemana"),
            esteMes = ts("esteMes"),
            vistos = marcas("vistos"),
            enLista = marcas("enLista"),
        )
        val nueva = e.lista("temporadaNueva").map { n ->
            Estreno(titulo(n.getValue("titulo").jsonObject), n.entero("temporada"), LocalDate.of(2026, 10, 1))
        }
        val filas = Ideas.componer(entrada, FiltroIdeas.valueOf(caso.texto("filtro")), temporadaNueva = nueva)
        val esperado = caso.lista("esperado").map { fila ->
            val temporadas = (fila["temporadas"] as? JsonArray)?.map { it.jsonPrimitive.int }
            Triple(fila.texto("clase"), fila.getValue("titulos").jsonArray.map { it.jsonPrimitive.content }, temporadas)
        }
        val obtenido = filas.map { fila ->
            val clase = when (val c = fila.clase) {
                ClaseFila.ParaTi -> "ParaTi"
                is ClaseFila.PorqueViste -> "PorqueViste:${c.titulo.ids.clave}"
                ClaseFila.EstaSemana -> "EstaSemana"
                ClaseFila.EsteMes -> "EsteMes"
                is ClaseFila.TemporadaNueva -> "TemporadaNueva"
            }
            val temporadas = (fila.clase as? ClaseFila.TemporadaNueva)?.let { c -> fila.titulos.map { c.temporadas.getValue(it.clave) } }
            Triple(clase, fila.titulos.map { it.ids.clave }, temporadas)
        }
        assertEquals(caso.texto("nombre"), esperado, obtenido)
    }

    @Test
    fun `C2 orden del día de Para ti`() = casos("filas.json", "ordenDelDia") { caso ->
        val ordenados = Ideas.ordenDelDia(caso.lista("titulos").map(::titulo), LocalDate.parse(caso.texto("hoy")))
        val esperado = caso.getValue("esperado").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(caso.texto("nombre"), esperado, ordenados.map { it.ids.clave })
    }

    @Test
    fun `C7 tarjeta de Seguir viendo`() = casos("seguir.json") { caso ->
        val serie = caso.texto("tipo") == "serie"
        val progreso = caso.getValue("progreso").jsonPrimitive.double
        val durMs = (caso.enteroONulo("duracionMin") ?: 0) * 60_000L
        // Sin duración el progreso llega solo en porcentaje, como el de Trakt.
        val p = WatchProgress(
            contentId = "tt1", contentType = if (serie) "series" else "movie", name = "Título", poster = null, backdrop = null,
            logo = null, videoId = "tt1", season = caso.enteroONulo("temporada"), episode = caso.enteroONulo("episodio"),
            episodeTitle = null, position = Math.round(progreso * durMs), duration = durMs, lastWatched = 0L,
            progressPercent = if (durMs > 0) null else (progreso * 100).toFloat(),
        )
        val obtenido = when {
            TarjetaSeguir.aMedias(p) -> TarjetaSeguir.de(ContinueWatchingItem.InProgress(p)).detalle
            // Un episodio sin empezar no frena el siguiente episodio en HomeViewModel: si el anterior es el último visto
            // (la semilla de NextUp), sale él mismo como NextUp. Sin nada visto antes, como un T1 · E1, no hay semilla.
            serie && progreso < WatchProgress.STARTED_THRESHOLD &&
                (caso.get("anteriorVisto") as JsonElement?)?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull == true -> TarjetaSeguir.de(ContinueWatchingItem.NextUp(NextUpInfo(
                contentId = "tt1", contentType = "series", name = "Título", poster = null, backdrop = null, logo = null,
                videoId = "tt1", season = p.season!!, episode = p.episode!!, episodeTitle = null, thumbnail = null,
                lastWatched = 0L, sortTimestamp = 0L,
            ))).detalle
            else -> "fuera"
        }
        assertEquals(caso.texto("nombre"), caso.texto("espera"), obtenido)
    }

    @Test
    fun `C8 siguiente episodio de Seguir viendo`() = casos("siguiente.json") { caso ->
        val ahora = Instant.parse(caso.texto("ahora"))
        // La semilla de Trakt, como en TraktProgressService.mapWatchedShowSeed con «desde el episodio más avanzado»
        // (viene puesto) y 60 días en el Inicio. Después, el corte de esos días que hace el Inicio.
        val desde = traktContinueWatchingCutoffEpochMs(60, ahora.toEpochMilli())!!
        val vistos = caso.lista("vistos").map {
            SemillaSiguiente.Visto(it.entero("temporada"), it.entero("episodio"), Instant.parse(it.texto("vistoEn")).toEpochMilli())
        }
        val semilla = SemillaSiguiente.elegir(vistos, masAvanzado = true, desdeMs = desde)?.takeIf { it.vistoEnMs >= desde }
        val siguiente = semilla?.let { (t, e, vistoEn) ->
            val visto = WatchProgress(
                contentId = "tt1", contentType = "series", name = "Serie", poster = null, backdrop = null, logo = null,
                videoId = "tt1:$t:$e", season = t, episode = e, episodeTitle = null, position = 1L, duration = 1L,
                lastWatched = vistoEn, progressPercent = 100f,
            )
            val episodios = caso.lista("episodios").map { ep ->
                val et = ep.entero("temporada")
                val ee = ep.entero("episodio")
                CwVideoSummary("tt1:$et:$ee", null, ep.textoONulo("fecha"), null, et, ee, null)
            }
            val meta = CwMetaSummary("tt1", "Serie", null, null, null, null, emptyList(), null, null, null, null, episodios)
            resolveNextUpVideoFromMeta(visto, meta, showUnairedNextUp = true, todayLocal = ahora.atZone(ZoneId.of("Europe/Madrid")).toLocalDate())
        }
        assertEquals(caso.texto("nombre"), caso.textoONulo("espera"), siguiente?.let { "T${it.season} · E${it.episode}" })
    }

    private fun casos(fichero: String, campo: String = "casos", comprobar: (JsonObject) -> Unit) {
        val texto = requireNotNull(javaClass.classLoader!!.getResource("compartido/$fichero")) { "Falta compartido/$fichero" }.readText()
        val casos = Json.parseToJsonElement(texto).jsonObject.lista(campo)
        check(casos.isNotEmpty())
        casos.forEach(comprobar)
    }

    private fun titulo(t: JsonObject): Titulo {
        val tipo = Tipo.valueOf(t.texto("tipo"))
        val ids = Ids(t.enteroONulo("tmdb"), trakt = t.enteroONulo("trakt"))
        return Titulo.minimo(tipo, ids, "${tipo.name} ${ids.clave}", null)
    }

    private fun JsonObject.lista(campo: String) = getValue(campo).jsonArray.map { it.jsonObject }
    private fun JsonObject.texto(campo: String) = getValue(campo).jsonPrimitive.content
    private fun JsonObject.textoONulo(campo: String): String? =
        (get(campo) as JsonElement?)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.entero(campo: String) = getValue(campo).jsonPrimitive.int
    private fun JsonObject.enteroONulo(campo: String): Int? =
        (get(campo) as JsonElement?)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
}
