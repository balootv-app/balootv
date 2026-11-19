package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.flow.first
import com.nuvio.tv.BuildConfig
import javax.inject.Inject

/**
 * Instala, si faltan, las fuentes de serie de la edición (solo hay en `casa`; la `publica` no trae ninguna y las
 * añade el usuario). Las URL llevan la clave de TorBox dentro, así que se generan con [urlsPara] desde la clave en
 * uso; no toca otros addons.
 */
class FuentesDeSerie @Inject constructor(private val repo: AddonRepository) {

    suspend fun asegurar(urls: List<String>) {
        if (urls.isEmpty()) return
        val instaladas = repo.getInstalledAddons().first().map { base(it.baseUrl) }.toSet()
        urls.filter { base(it) !in instaladas }.forEach { repo.addAddon(it) }
    }

    /**
     * Cambia de cuenta de TorBox: cada fuente de [viejas] que esté instalada se cambia por la de [nuevas] del mismo
     * puesto, en su mismo sitio de la lista; las que falten se añaden al final. El resto de addons no se toca.
     */
    suspend fun sustituir(viejas: List<String>, nuevas: List<String>) {
        val antes = repo.getInstalledAddons().first().map { it.baseUrl }
        val porBase = viejas.zip(nuevas).associate { (v, n) -> base(v) to n }
        // Una fuente sin la clave dentro es la misma con las dos cuentas: se queda donde está.
        val quitar = antes.filter { porBase[base(it)]?.let { n -> base(n) != base(it) } == true }
        nuevas.forEach { repo.addAddon(it) }
        quitar.forEach { repo.removeAddon(it) }
        val orden = antes.map { porBase[base(it)] ?: it }
        repo.setAddonOrder((orden + nuevas).distinctBy(::base))
    }

    companion object {
        /** Las plantillas de la edición: vacías en `publica`; en `casa`, las de `%USERPROFILE%/.balootv/fuentes-casa.txt`. */
        val PLANTILLAS: List<String> by lazy { PlantillaFuente.leerLista(BuildConfig.FUENTES_DE_SERIE) }

        /**
         * Las fuentes de serie con la clave de TorBox [clave]. Sin clave, solo las que no la necesitan.
         * Las de casa: docs/superpowers/specs/2026-10-22-motor-fuentes-design.md.
         */
        fun urlsPara(clave: String, plantillas: List<String> = PLANTILLAS): List<String> =
            plantillas.filter { clave.isNotBlank() || !PlantillaFuente.lleva(it) }
                .map { PlantillaFuente.rellenar(it, clave.trim()) }

        fun base(url: String): String =
            url.trim().trimEnd('/').removeSuffix("/manifest.json").trimEnd('/').lowercase()
    }
}
