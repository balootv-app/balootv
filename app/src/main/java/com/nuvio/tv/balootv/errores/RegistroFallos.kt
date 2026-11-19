package com.nuvio.tv.balootv.errores

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro local de fallos (sustituye a Sentry, F6): cierres de la app y errores de reproducción, en
 * `/sdcard/Android/data/<paquete>/files/registro/fallos.txt`, que se lee por ADB con
 * `tele.py fallos`. Nunca guarda URL (llevan claves de TorBox); al pasar de 256 KB se rota a `fallos.1.txt`.
 */
class RegistroFallos(private val carpeta: File, private val version: String, private val ahora: () -> Long = System::currentTimeMillis) {

    private val fichero get() = File(carpeta, "fallos.txt")

    @Synchronized
    fun anotar(etiqueta: String, mensaje: String, error: Throwable? = null) {
        runCatching {
            carpeta.mkdirs()
            if (fichero.length() > MAXIMO_BYTES) {
                val anterior = File(carpeta, "fallos.1.txt")
                anterior.delete()
                fichero.renameTo(anterior)
            }
            fichero.appendText(entrada(etiqueta, mensaje, error))
        }
    }

    internal fun entrada(etiqueta: String, mensaje: String, error: Throwable?): String = buildString {
        append(FORMATO.get()!!.format(Date(ahora()))).append(" · ").append(version).append(" · ").append(etiqueta).append('\n')
        append("  ").append(sinUrls(mensaje)).append('\n')
        error?.let { e ->
            val pila = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
            pila.lineSequence().take(LINEAS_PILA).forEach { append("    ").append(sinUrls(it)).append('\n') }
        }
        append('\n')
    }

    companion object {
        private const val MAXIMO_BYTES = 256 * 1024L
        private const val LINEAS_PILA = 40
        private val FORMATO = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT) }
        private val URL = Regex("""\b(?:https?|magnet):\S+""")

        internal fun sinUrls(texto: String) = URL.replace(texto, "<url>")

        @Volatile
        var actual: RegistroFallos? = null
            private set

        /** Empieza a registrar: cierres inesperados de cualquier hilo, sin quitar el manejador del sistema. */
        fun instalar(context: Context, version: String) {
            val carpeta = File(context.getExternalFilesDir(null) ?: context.filesDir, "registro")
            val registro = RegistroFallos(carpeta, version).also { actual = it }
            val previo = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
                registro.anotar("CIERRE", "Hilo ${hilo.name}", error)
                previo?.uncaughtException(hilo, error)
            }
        }

        /** Atajo para los puntos de la app que quieren dejar constancia de un error que no cierra la app. */
        fun anotar(etiqueta: String, mensaje: String, error: Throwable? = null) = actual?.anotar(etiqueta, mensaje, error)
    }
}
