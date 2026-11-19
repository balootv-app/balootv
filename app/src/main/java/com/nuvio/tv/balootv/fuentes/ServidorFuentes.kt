package com.nuvio.tv.balootv.fuentes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * La página que sirve la tele para pegar fuentes desde el móvil. Solo existe mientras la pantalla «Añadir fuentes»
 * está abierta ([cerrar] al salir), solo contesta a aparatos de la red local y solo con el código del QR, que vale
 * para un envío: tras usarlo se cambia por otro ([alCambiarCodigo]) y la tele enseña el QR nuevo.
 */
class ServidorFuentes(
    private val ambito: CoroutineScope,
    private val alRecibir: suspend (String) -> List<ResultadoFuente>,
    private val alCambiarCodigo: (String) -> Unit = {},
    private val azar: SecureRandom = SecureRandom(),
) {
    private var socket: ServerSocket? = null
    private var tarea: Job? = null

    @Volatile var codigo: String = nuevoCodigo()
        private set
    private val plazas = java.util.concurrent.Semaphore(4)

    val puerto: Int get() = socket?.localPort ?: -1

    /** Empieza a escuchar en [direccion] (la de la tele en la red local) y un puerto libre. */
    fun abrir(direccion: InetAddress) {
        cerrar()
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(direccion, 0), 4)
        socket = s
        tarea = ambito.launch(Dispatchers.IO) {
            while (isActive && !s.isClosed) {
                val cliente = runCatching { s.accept() }.getOrNull() ?: break
                // Pocas a la vez: alguien lento de la red no deja a la app sin hilos de Dispatchers.IO.
                if (!plazas.tryAcquire()) { runCatching { cliente.close() }; continue }
                launch { try { cliente.use { atender(it) } } finally { plazas.release() } }
            }
        }
    }

    fun cerrar() {
        runCatching { socket?.close() }
        socket = null
        tarea?.cancel()
        tarea = null
    }

    private fun atender(c: Socket) {
        c.soTimeout = 10_000
        if (!esLocal(c.inetAddress)) return responder(c, 403, Paginas.mensaje("Solo desde tu casa", "Esta página solo funciona en la misma red que la tele."))
        val peticion = runCatching { PeticionHttp.leer(BufferedInputStream(c.getInputStream())) }.getOrNull()
            ?: return responder(c, 400, Paginas.mensaje("No se entiende", "Vuelve a escanear el código de la tele."))
        val esperado = "/$codigo"
        when {
            peticion.camino != esperado -> responder(c, 404, Paginas.mensaje("Código caducado", "Vuelve a escanear el código QR que enseña la tele."))
            peticion.metodo == "GET" -> responder(c, 200, Paginas.formulario(esperado))
            peticion.metodo == "POST" -> {
                val texto = PeticionHttp.campo(peticion.cuerpo, "fuentes")
                if (texto.isBlank()) return responder(c, 200, Paginas.formulario(esperado, "Pega al menos una dirección."))
                // Un solo envío por código: el siguiente necesita el QR nuevo (aunque lleguen dos a la vez).
                val nuevo = synchronized(this) { if (codigo != esperado.drop(1)) null else nuevoCodigo().also { codigo = it } }
                    ?: return responder(c, 404, Paginas.mensaje("Código caducado", "Vuelve a escanear el código QR que enseña la tele."))
                alCambiarCodigo(nuevo)
                val resultados = runBlocking { withTimeoutOrNull(60_000) { alRecibir(texto) } }
                responder(c, 200, Paginas.resultado(resultados))
            }
            else -> responder(c, 405, Paginas.mensaje("No se puede", "Usa el formulario de la página."))
        }
    }

    private fun responder(c: Socket, estado: Int, html: String) {
        val cuerpo = html.toByteArray(Charsets.UTF_8)
        val nombre = when (estado) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Method Not Allowed" }
        val cabecera = "HTTP/1.1 $estado $nombre\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${cuerpo.size}\r\n" +
            "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
        runCatching {
            c.getOutputStream().apply { write(cabecera.toByteArray(Charsets.US_ASCII)); write(cuerpo); flush() }
        }
    }

    private fun nuevoCodigo(): String {
        val letras = "abcdefghijkmnpqrstuvwxyz23456789"
        return (1..10).map { letras[azar.nextInt(letras.length)] }.joinToString("")
    }

    companion object {
        /** Direcciones de la red de casa (y la propia tele): nunca algo de fuera. */
        fun esLocal(d: InetAddress): Boolean = d.isLoopbackAddress || d.isSiteLocalAddress || d.isLinkLocalAddress ||
            (d is Inet6Address && (d.address[0].toInt() and 0xFE) == 0xFC)

        /** La IPv4 de la tele en la red de casa (Wi-Fi o cable), o null sin red. */
        fun direccionLocal(): Inet4Address? = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
        }.getOrNull()
    }
}

/** Una petición HTTP mínima: método, camino y cuerpo (como mucho 64 KB). */
internal data class PeticionHttp(val metodo: String, val camino: String, val cuerpo: String) {
    companion object {
        private const val MAXIMO = 64 * 1024

        fun leer(entrada: InputStream): PeticionHttp? {
            val primera = linea(entrada) ?: return null
            val partes = primera.split(' ')
            if (partes.size < 2) return null
            var largo = 0
            var cabeceras = 0
            while (true) {
                val l = linea(entrada) ?: return null
                if (++cabeceras > 64) return null
                if (l.isEmpty()) break
                val (nombre, valor) = l.split(':', limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
                if (nombre == "content-length") largo = valor.toIntOrNull()?.takeIf { it in 0..MAXIMO } ?: return null
            }
            val cuerpo = ByteArray(largo)
            var leido = 0
            while (leido < largo) {
                val n = entrada.read(cuerpo, leido, largo - leido)
                if (n < 0) return null
                leido += n
            }
            return PeticionHttp(partes[0].uppercase(), partes[1].substringBefore('?'), String(cuerpo, Charsets.UTF_8))
        }

        /** El valor de [nombre] en un formulario `application/x-www-form-urlencoded`. */
        fun campo(cuerpo: String, nombre: String): String = cuerpo.split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it[0] == nombre }
            ?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            .orEmpty()

        private fun linea(entrada: InputStream): String? {
            val b = ByteArrayOutputStream()
            while (true) {
                val c = entrada.read()
                if (c < 0) return if (b.size() == 0) null else b.toString("UTF-8")
                if (c == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
                if (b.size() > 8 * 1024) return null
                b.write(c)
            }
        }
    }
}

/** Las páginas para el móvil: una sola columna, oscura como la tele, sin nada de fuera. */
internal object Paginas {
    private const val ESTILO = """
        :root{color-scheme:dark}body{margin:0;font:17px/1.45 -apple-system,system-ui,sans-serif;background:#0d1012;color:#fff}
        main{max-width:34rem;margin:0 auto;padding:28px 20px 40px}h1{font-size:1.6rem;margin:.2rem 0 1rem}
        p{color:#ffffffb3}textarea{box-sizing:border-box;width:100%;min-height:11rem;border-radius:12px;border:1px solid #ffffff2a;
        background:#ffffff12;color:#fff;font:15px/1.4 ui-monospace,monospace;padding:12px}
        button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:12px;background:#fff;color:#000;font-size:1.05rem;font-weight:600}
        a{color:#9cc8ff}li{margin:.4rem 0}.ok{color:#9be29b}.no{color:#ffb3a8}code{background:#ffffff14;padding:1px 5px;border-radius:5px}
    """

    private fun pagina(titulo: String, cuerpo: String) =
        "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>$titulo · BalooTV</title><style>$ESTILO</style></head><body><main>$cuerpo</main></body></html>"

    fun formulario(accion: String, aviso: String? = null) = pagina(
        "Añadir fuentes",
        "<h1>Añadir fuentes a BalooTV</h1>" +
            "<p>Pega la dirección de cada addon de Stremio, una por línea. Si lleva tu clave de TorBox, puedes escribir " +
            "<code>{torbox}</code> en su lugar y la tele pone la suya.</p>" +
            "<p>${GuiaFuentes.PREGUNTA} <a href=\"${GuiaFuentes.DIRECCION}\">${GuiaFuentes.corta}</a></p>" +
            (aviso?.let { "<p class=\"no\">${escapar(it)}</p>" } ?: "") +
            "<form method=\"post\" action=\"$accion\"><textarea name=\"fuentes\" autofocus autocomplete=\"off\" autocapitalize=\"off\" " +
            "spellcheck=\"false\" placeholder=\"https://…/manifest.json\"></textarea><button type=\"submit\">Enviar a la tele</button></form>",
    )

    fun resultado(resultados: List<ResultadoFuente>?): String {
        if (resultados == null) return mensaje("La tele no ha contestado", "Mira la pantalla de la tele y vuelve a escanear el código.")
        val lista = resultados.joinToString("") { r ->
            val bien = r.comprobacion is Comprobacion.Valida
            "<li class=\"${if (bien) "ok" else "no"}\"><b>${escapar(r.sitio)}</b>: ${escapar(ComprobacionFuente.texto(r.comprobacion))}</li>"
        }
        val anadidas = resultados.count { it.comprobacion is Comprobacion.Valida }
        val titulo = when (anadidas) { 0 -> "No se ha añadido ninguna"; 1 -> "Fuente añadida"; else -> "$anadidas fuentes añadidas" }
        return pagina(titulo, "<h1>$titulo</h1><ul>$lista</ul><p>Para añadir más, vuelve a escanear el código QR de la tele.</p>")
    }

    fun mensaje(titulo: String, texto: String) = pagina(titulo, "<h1>${escapar(titulo)}</h1><p>${escapar(texto)}</p>")

    fun escapar(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
