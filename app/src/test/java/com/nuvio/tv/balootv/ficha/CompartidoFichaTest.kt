package com.nuvio.tv.balootv.ficha

import com.nuvio.tv.data.remote.api.TmdbCollectionPart
import com.nuvio.tv.data.remote.api.TmdbKeyword
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Los contratos C4, C5, C6 y C9 de la ficha con los casos de `compartido/`, los mismos que ejecuta el móvil (termina.json
 * del commit a63806c y sagas.json del 9c0100f, rama sorprendeme-sagas de balootv-movil; poscreditos.json, del móvil; generos.json, de la tele).
 * Los JSON se copian sin tocar: si un caso cambia, se cambia en los dos repos a la vez.
 */
class CompartidoFichaTest {

    @Test
    fun `C4 termina a las`() = casos("termina.json") { caso ->
        val ahora = LocalTime.parse(caso.texto("ahora"))
        // En la tele lo que queda sale de la posición y la duración (TextosFicha.boton); aquí llega ya en minutos.
        val obtenido = caso.enteroONulo("restanteMin")?.let { TerminaALas.quedan(it, ahora) }
            ?: TerminaALas.sinEmpezar(caso.enteroONulo("duracionMin"), ahora)
        assertEquals(caso.texto("nombre"), caso.textoONulo("espera"), obtenido)
    }

    @Test
    fun `C5 sagas`() = casos("sagas.json") { caso ->
        val partes = caso.lista("partes").map { TmdbCollectionPart(id = it.entero("tmdb"), releaseDate = it.textoONulo("fecha")) }
        // Igual que observeRelatedWatchedStatus de MetaDetailsViewModel con las vistas de Trakt.
        val estado = caso.getValue("vistas").jsonArray.associate { "tmdb:${it.jsonPrimitive.int}|movie" to true }
        val saga = Saga.partes(partes, LocalDate.parse(caso.texto("hoy")))
        val obtenido = saga.takeIf { it.isNotEmpty() }?.let { lista ->
            Saga.titulo(caso.textoONulo("coleccion")) to lista.map { it.id to Saga.vista("tmdb:${it.id}", estado) }
        }
        val esperado = (caso.get("espera") as JsonElement?)?.takeUnless { it is JsonNull }?.jsonObject?.let { e ->
            e.texto("nombre") to e.lista("partes").map { it.entero("tmdb") to it.getValue("vista").jsonPrimitive.content.toBoolean() }
        }
        assertEquals(caso.texto("nombre"), esperado, obtenido)
    }

    @Test
    fun `C6 escena poscréditos`() = casos("poscreditos.json") { caso ->
        val palabras = caso.lista("palabras").map { TmdbKeyword(id = it.entero("id"), name = it.texto("name")) }
        assertEquals(caso.texto("nombre"), caso.textoONulo("espera"), EscenaCreditos.de(palabras)?.texto)
    }

    @Test
    fun `C9 géneros en castellano`() = casos("generos.json") { caso ->
        val generos = caso.getValue("generos").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(caso.texto("nombre"), caso.textoONulo("espera"), TextosFicha.generos(generos))
    }

    @Test
    fun `C9 la tabla del contrato es la de la tele`() {
        val texto = requireNotNull(javaClass.classLoader!!.getResource("compartido/generos.json")).readText()
        val tabla = Json.parseToJsonElement(texto).jsonObject.getValue("traducciones").jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(tabla, TextosFicha.TRADUCCION_GENEROS)
    }

    private fun casos(fichero: String, comprobar: (JsonObject) -> Unit) {
        val texto = requireNotNull(javaClass.classLoader!!.getResource("compartido/$fichero")) { "Falta compartido/$fichero" }.readText()
        val casos = Json.parseToJsonElement(texto).jsonObject.lista("casos")
        check(casos.isNotEmpty())
        casos.forEach(comprobar)
    }

    private fun JsonObject.lista(campo: String) = getValue(campo).jsonArray.map { it.jsonObject }
    private fun JsonObject.texto(campo: String) = getValue(campo).jsonPrimitive.content
    private fun JsonObject.entero(campo: String) = getValue(campo).jsonPrimitive.int
    private fun JsonObject.textoONulo(campo: String): String? =
        (get(campo) as JsonElement?)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.enteroONulo(campo: String): Int? =
        (get(campo) as JsonElement?)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
}
