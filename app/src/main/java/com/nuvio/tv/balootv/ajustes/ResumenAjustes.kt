package com.nuvio.tv.balootv.ajustes

import com.nuvio.tv.balootv.cuenta.EstadoTorbox
import com.nuvio.tv.data.local.StreamAutoPlayMode

/** Las secciones de Ajustes (spec F4 §6, tablero 10) más Fuentes (antes Addons, cambiado el 22-10). */
enum class SeccionAjustes(val titulo: String, val explicacion: String) {
    TRAKT("Trakt", "Seguir viendo, Mi lista y las ideas del Inicio salen de tu cuenta de Trakt."),
    FUENTE("Elegir fuente", "Cómo elige BalooTV el enlace al pulsar Reproducir. En automático empieza solo con el mejor en castellano; si falla, prueba el siguiente."),
    SUBTITULOS("Subtítulos", "Siempre en castellano. Aquí solo eliges cómo se ven."),
    TORBOX("TorBox", "El servicio que prepara los enlaces. Si no funciona, no se puede reproducir nada. Con «Usar otra cuenta» pasas a la tuya con un código."),
    PLATAFORMAS("Plataformas", "Qué plataformas salen en el catálogo. El orden decide qué logo lleva cada carátula."),
    FUENTES("Fuentes", "Los addons de Stremio de donde salen los enlaces. Aquí los añades (desde el móvil o con el mando) y los quitas."),
    ACERCA_DE("Acerca de", "Versión de BalooTV y limpieza de la caché."),
}

/** Lo que Ajustes necesita saber para pintar sus filas. `torbox` null = aún comprobando. */
data class EntradaAjustes(
    val usuarioTrakt: String?,
    val modo: StreamAutoPlayMode,
    val tamanoSubtitulos: Int,
    val alturaSubtitulos: Int,
    val torbox: EstadoTorbox?,
    val plataformasActivas: Int,
    val fuentesEnUso: Int,
    val version: String,
)

data class FilaAjuste(val seccion: SeccionAjustes, val valor: String)

/** Tamaños y alturas de subtítulo que ofrece Ajustes (valores de `PlayerSettingsDataStore`). */
enum class TamanoSubtitulos(val nombre: String, val valor: Int) { PEQUENO("Pequeño", 80), MEDIANO("Mediano", 100), GRANDE("Grande", 130) }

enum class AlturaSubtitulos(val nombre: String, val valor: Int) { ABAJO("abajo", 5), ARRIBA("más arriba", 15) }

object ResumenAjustes {

    fun filas(e: EntradaAjustes): List<FilaAjuste> = SeccionAjustes.entries.map { s ->
        FilaAjuste(s, when (s) {
            SeccionAjustes.TRAKT -> e.usuarioTrakt?.let { "Conectado · $it" } ?: "Sin conectar"
            SeccionAjustes.FUENTE -> if (e.modo == StreamAutoPlayMode.MANUAL) "Preguntarme siempre" else "BalooTV automático"
            SeccionAjustes.SUBTITULOS -> "${tamano(e.tamanoSubtitulos)} · ${altura(e.alturaSubtitulos)}"
            SeccionAjustes.TORBOX -> when (e.torbox) {
                EstadoTorbox.Funciona -> "Funcionando"
                EstadoTorbox.SinConexion -> "Sin conexión"
                EstadoTorbox.Caducada -> "Suscripción caducada"
                null -> "Comprobando…"
            }
            SeccionAjustes.PLATAFORMAS -> "${e.plataformasActivas} activas"
            SeccionAjustes.FUENTES -> when (e.fuentesEnUso) { 0 -> "Ninguna"; 1 -> "1 fuente"; else -> "${e.fuentesEnUso} fuentes" }
            SeccionAjustes.ACERCA_DE -> "BalooTV ${e.version}"
        })
    }

    private fun tamano(v: Int) = TamanoSubtitulos.entries.firstOrNull { it.valor == v }?.nombre ?: "$v %"

    private fun altura(v: Int) = if (v > AlturaSubtitulos.ABAJO.valor) AlturaSubtitulos.ARRIBA.nombre else AlturaSubtitulos.ABAJO.nombre
}
