package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.balootv.cuenta.ClaveTorbox
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Una dirección de las que se intentaron añadir y cómo fue. [sitio] va sin la clave de TorBox. */
data class ResultadoFuente(val sitio: String, val comprobacion: Comprobacion)

/**
 * Añadir y quitar fuentes: las de serie de la edición más las que pone el usuario (desde el móvil con el QR o
 * con el mando). Cada dirección se comprueba antes de instalarla (que responde y que da enlaces).
 */
@Singleton
class GestorFuentes @Inject constructor(
    private val repo: AddonRepository,
    private val almacen: AlmacenFuentes,
    private val claveTorbox: ClaveTorbox,
) {
    private val cerrojo = Mutex()

    /** Las direcciones que deberían estar instaladas: las de serie y las del usuario, con la clave en uso. */
    val urlsEnUso: Flow<List<String>> = combine(claveTorbox.enUso, almacen.plantillas) { clave, propias ->
        FuentesDeSerie.urlsPara(clave, FuentesDeSerie.PLANTILLAS + propias).distinctBy(FuentesDeSerie::base)
    }

    /** Los addons instalados, de cualquier tipo. */
    val instaladas: Flow<List<Addon>> = repo.getInstalledAddons()

    /** Comprueba e instala cada dirección de [texto] (una por línea). */
    suspend fun anadir(texto: String): List<ResultadoFuente> = cerrojo.withLock {
        val clave = claveTorbox.actual()
        val vistas = repo.getInstalledAddons().first().map { FuentesDeSerie.base(it.baseUrl) }.toMutableSet()
        PlantillaFuente.leerLista(texto).map { linea ->
            val plantilla = ComprobacionFuente.normalizar(linea)
            val sitio = plantilla?.let(ComprobacionFuente::sitio) ?: linea.take(40)
            val previa = ComprobacionFuente.antesDeLaRed(plantilla, vistas, clave)
            if (previa != null || plantilla == null) return@map ResultadoFuente(sitio, previa ?: Comprobacion.NoEsDireccion)
            val url = PlantillaFuente.rellenar(plantilla, clave)
            val c = ComprobacionFuente.juzgar(repo.fetchAddon(url.removeSuffix("/manifest.json")))
            if (c is Comprobacion.Valida) {
                repo.addAddon(url)
                almacen.anadir(plantilla)
                vistas += FuentesDeSerie.base(url)
            }
            ResultadoFuente(sitio, c)
        }
    }

    /** Quita la fuente instalada en [baseUrl] y, si la puso el usuario, su plantilla. */
    suspend fun quitar(baseUrl: String) = cerrojo.withLock {
        val clave = claveTorbox.actual()
        val base = FuentesDeSerie.base(baseUrl)
        almacen.actuales().filter { FuentesDeSerie.base(PlantillaFuente.rellenar(it, clave)) == base }.forEach { almacen.quitar(it) }
        repo.removeAddon(baseUrl)
    }

    companion object {
        /** Si [a] da enlaces, o aún no ha contestado (sin manifiesto todavía) y puede darlos. */
        fun daEnlaces(a: Addon): Boolean = a.enabled && (a.resources.isEmpty() || a.resources.any { it.name == "stream" })

        /** Sin ninguna fuente que pueda dar enlaces: la ficha lo dice y lleva a «Añadir fuentes». */
        fun sinFuentes(instaladas: List<Addon>): Boolean = instaladas.none(::daEnlaces)
    }
}
