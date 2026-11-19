package com.nuvio.tv.balootv.ajustes

import android.content.Context
import com.google.gson.Gson
import com.nuvio.tv.balootv.errores.RegistroFallos
import com.nuvio.tv.balootv.fuentes.AlmacenListaNegra
import com.nuvio.tv.balootv.fuentes.ListaNegraFuentes
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.StreamAutoPlayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que se copia de BalooTV (F6). El progreso y Mi lista viven en Trakt y la sesión de Trakt no se copia
 * (son claves): tras reinstalar se vuelve a vincular en Ajustes. `plataformas` null = nunca se han tocado.
 */
data class CopiaPreferencias(
    val version: Int = 1,
    val modoManual: Boolean = false,
    val tamanoSubtitulos: Int = TamanoSubtitulos.MEDIANO.valor,
    val alturaSubtitulos: Int = AlturaSubtitulos.ABAJO.valor,
    val plataformas: List<Int>? = null,
    val listaNegra: List<String> = emptyList(),
) {
    fun aJson(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()

        /** Null si el texto no es una copia de BalooTV válida. */
        fun deJson(texto: String): CopiaPreferencias? = runCatching { GSON.fromJson(texto, CopiaPreferencias::class.java) }
            .getOrNull()
            ?.takeIf { it.version == 1 }
            // Gson no respeta los valores por defecto de Kotlin: una lista ausente llega como null.
            ?.let { it.copy(listaNegra = (it.listaNegra as List<String>?).orEmpty()) }
    }
}

/**
 * Copia de seguridad en `/sdcard/Android/data/<paquete>/files/copia/`: `preferencias.json` se
 * reescribe en cada cambio y `tele.py copia` la baja al PC; si al arrancar hay un `restaurar.json` (lo deja
 * `tele.py restaurar`), se aplica y se renombra para no aplicarlo dos veces.
 */
@Singleton
class CopiaSeguridad @Inject constructor(
    @ApplicationContext context: Context,
    private val reproductor: PlayerSettingsDataStore,
    private val plataformas: AlmacenPlataformas,
    private val listaNegra: AlmacenListaNegra,
) {
    private val carpeta = File(context.getExternalFilesDir(null) ?: context.filesDir, "copia")

    /** Aplica `restaurar.json` si existe y después mantiene `preferencias.json` al día. No vuelve. */
    @OptIn(FlowPreview::class)
    suspend fun vigilar() {
        restaurarSiHay()
        combine(reproductor.playerSettings, plataformas.ids, listaNegra.cambios) { p, ids, _ ->
            CopiaPreferencias(
                modoManual = p.streamAutoPlayMode == StreamAutoPlayMode.MANUAL,
                tamanoSubtitulos = p.subtitleStyle.size,
                alturaSubtitulos = p.subtitleStyle.verticalOffset,
                plataformas = ids,
                listaNegra = ListaNegraFuentes.claves().toList(),
            )
        }.debounce(2_000).collect { copia ->
            runCatching {
                carpeta.mkdirs()
                File(carpeta, "preferencias.json.tmp").apply { writeText(copia.aJson()) }.renameTo(File(carpeta, "preferencias.json"))
            }
        }
    }

    private suspend fun restaurarSiHay() {
        val fichero = File(carpeta, "restaurar.json")
        if (!fichero.exists()) return
        val copia = CopiaPreferencias.deJson(fichero.readText())
        if (copia == null) {
            RegistroFallos.anotar("COPIA", "restaurar.json no es una copia válida; no se ha aplicado")
            fichero.renameTo(File(carpeta, "restaurar-invalida.json"))
            return
        }
        reproductor.setStreamAutoPlayMode(if (copia.modoManual) StreamAutoPlayMode.MANUAL else StreamAutoPlayMode.BALOOTV)
        reproductor.setSubtitleSize(copia.tamanoSubtitulos)
        reproductor.setSubtitleVerticalOffset(copia.alturaSubtitulos)
        copia.plataformas?.let { plataformas.guardar(it) }
        listaNegra.reemplazar(copia.listaNegra)
        reproductor.playerSettings.first()
        val sello = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        fichero.renameTo(File(carpeta, "restaurada-$sello.json"))
    }
}
