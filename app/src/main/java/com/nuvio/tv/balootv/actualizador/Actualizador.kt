package com.nuvio.tv.balootv.actualizador

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nuvio.tv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Por dónde va la actualización. */
sealed interface EstadoActualizacion {
    data object Nada : EstadoActualizacion
    data class Disponible(val a: Actualizacion) : EstadoActualizacion
    data class Bajando(val a: Actualizacion, val progreso: Float?) : EstadoActualizacion
    /** Falta el permiso de instalar apps desconocidas: se explica cómo darlo. */
    data class FaltaPermiso(val a: Actualizacion) : EstadoActualizacion
    data class Fallo(val a: Actualizacion, val motivo: String) : EstadoActualizacion
}

/**
 * El actualizador de la edición pública: mira la última versión publicada en el repositorio configurado al compilar
 * (`BuildConfig.ACTUALIZACIONES_URL`), baja la APK de la arquitectura del aparato y la instala solo si es este mismo
 * paquete, más nueva y firmada con la misma clave. La edición casa no lo usa (se actualiza a mano, como siempre).
 * Recuperado de NuvioTV 1.1.0-beta.7 (commit 8b41d3e, `com.nuvio.tv.updater`), reducido a lo que hace falta.
 */
@Singleton
class Actualizador @Inject constructor(@ApplicationContext private val context: Context) {

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val almacen = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("balootv_actualizador") })
    private val aplazada = stringPreferencesKey("aplazada")
    private val aplazadaEn = longPreferencesKey("aplazada_en")

    private val _estado = MutableStateFlow<EstadoActualizacion>(EstadoActualizacion.Nada)
    val estado: StateFlow<EstadoActualizacion> = _estado.asStateFlow()

    val activo: Boolean get() = BuildConfig.ACTUALIZACIONES_URL.isNotBlank()

    /** Busca una versión nueva. [aunqueAplazada] desde Ajustes; el aviso del Inicio respeta «Más tarde». */
    suspend fun comprobar(aunqueAplazada: Boolean = false): Actualizacion? = withContext(Dispatchers.IO) {
        if (!activo) return@withContext null
        val texto = runCatching {
            http.newCall(Request.Builder().url(BuildConfig.ACTUALIZACIONES_URL).header("Accept", "application/vnd.github+json").build())
                .execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return@withContext null
        val p = EleccionPublicacion.leer(texto) ?: return@withContext null
        val a = EleccionPublicacion.elegir(p, BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList(), soloHttps = !BuildConfig.IS_DEBUG_BUILD) ?: run {
            _estado.value = EstadoActualizacion.Nada
            return@withContext null
        }
        val prefs = almacen.data.first()
        if (!aunqueAplazada && Aplazamiento.vigente(prefs[aplazada], prefs[aplazadaEn], a.version, System.currentTimeMillis())) return@withContext null
        if (_estado.value !is EstadoActualizacion.Bajando) _estado.value = EstadoActualizacion.Disponible(a)
        a
    }

    /** «Más tarde»: no se vuelve a avisar de esta versión en unos días. */
    suspend fun aplazar(a: Actualizacion) {
        almacen.edit { it[aplazada] = a.version; it[aplazadaEn] = System.currentTimeMillis() }
        _estado.value = EstadoActualizacion.Nada
    }

    fun puedeInstalar(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** La pantalla del sistema para dar el permiso de instalar apps desconocidas a BalooTV. */
    fun ajustesDelPermiso(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Baja la APK, la comprueba y abre el instalador del sistema. */
    suspend fun actualizar(a: Actualizacion) {
        if (!puedeInstalar()) {
            _estado.value = EstadoActualizacion.FaltaPermiso(a)
            return
        }
        _estado.value = EstadoActualizacion.Bajando(a, null)
        val fichero = withContext(Dispatchers.IO) { runCatching { bajar(a) }.getOrNull() }
        if (fichero == null) {
            _estado.value = EstadoActualizacion.Fallo(a, "No se ha podido bajar. Vuelve a intentarlo.")
            return
        }
        val motivo = withContext(Dispatchers.IO) { comprobarApk(fichero) }
        if (motivo != null) {
            fichero.delete()
            _estado.value = EstadoActualizacion.Fallo(a, motivo)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fichero)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        _estado.value = EstadoActualizacion.Disponible(a)
    }

    private fun bajar(a: Actualizacion): File {
        val carpeta = File(context.cacheDir, "actualizacion").apply { deleteRecursively(); mkdirs() }
        val destino = File(carpeta, "BalooTV-${a.version}.apk")
        http.newCall(Request.Builder().url(a.apk.url).build()).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            val cuerpo = r.body ?: error("sin cuerpo")
            val total = cuerpo.contentLength().takeIf { it > 0 } ?: a.apk.tamano.takeIf { it > 0 }
            cuerpo.byteStream().use { entrada ->
                destino.outputStream().use { salida ->
                    val buf = ByteArray(64 * 1024)
                    var hecho = 0L
                    var ultimo = -1
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        salida.write(buf, 0, n)
                        hecho += n
                        val pct = total?.let { (hecho * 100 / it).toInt() }
                        if (pct != ultimo) { ultimo = pct ?: -1; _estado.value = EstadoActualizacion.Bajando(a, total?.let { hecho.toFloat() / it }) }
                    }
                }
            }
        }
        return destino
    }

    /** null si la APK es este paquete, más nueva y con la misma firma; si no, por qué no se instala. */
    private fun comprobarApk(f: File): String? {
        val pm = context.packageManager
        val nueva = infoFirmas(pm) { pm.getPackageArchiveInfo(f.absolutePath, it) } ?: return "La descarga no es una APK válida."
        val actual = infoFirmas(pm) { pm.getPackageInfo(context.packageName, it) } ?: return "No se puede leer la app instalada."
        if (nueva.packageName != context.packageName) return "La APK no es de BalooTV."
        if (codigo(nueva) <= codigo(actual)) return "La APK no es más nueva que la instalada."
        if (huellas(nueva) != huellas(actual) || huellas(nueva).isEmpty()) return "La APK no está firmada por BalooTV."
        return null
    }

    private fun infoFirmas(pm: PackageManager, leer: (Int) -> PackageInfo?): PackageInfo? = runCatching {
        @Suppress("DEPRECATION")
        leer(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun codigo(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun huellas(p: PackageInfo): Set<String> {
        val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            p.signatures
        }
        return firmas.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}

/** «Más tarde» vale para esa versión durante [DIAS] días; una versión más nueva se avisa enseguida. */
object Aplazamiento {
    const val DIAS = 3

    fun vigente(version: String?, desde: Long?, ofrecida: String, ahora: Long): Boolean =
        version == ofrecida && desde != null && ahora - desde < DIAS * 24L * 3600_000L
}
