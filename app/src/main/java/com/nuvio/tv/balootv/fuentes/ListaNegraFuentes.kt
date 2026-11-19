package com.nuvio.tv.balootv.fuentes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuentes que se han marcado con "Esta no está en castellano": el selector no las vuelve a elegir ni a
 * enseñar. La clave es el infohash y el fichero (o la URL si no hay hash), para reconocerla venga del addon
 * que venga.
 */
object ListaNegraFuentes {

    private val claves = LinkedHashSet<String>()

    fun clave(c: Candidato): String =
        c.infoHash?.lowercase()?.let { "$it#${c.stream?.fileIdx ?: ""}" } ?: c.stream?.url ?: c.texto

    fun marcar(c: Candidato) = synchronized(claves) {
        claves.remove(clave(c))
        claves += clave(c)
        while (claves.size > MAXIMO) claves.remove(claves.first())
    }

    fun contiene(c: Candidato): Boolean = synchronized(claves) { claves.isNotEmpty() && clave(c) in claves }

    fun cargar(guardadas: Collection<String>) = synchronized(claves) { claves += guardadas.filter { it.isNotBlank() } }

    fun claves(): Set<String> = synchronized(claves) { claves.toSet() }

    fun limpiar() = synchronized(claves) { claves.clear() }

    const val MAXIMO = 500
}

/** Guarda la lista negra en un fichero de la app (una clave por línea). */
@Singleton
class AlmacenListaNegra @Inject constructor(@ApplicationContext context: Context) {

    private val fichero = File(context.filesDir, "balootv_lista_negra.txt")

    /** Sube cada vez que la lista cambia (para la copia de seguridad). */
    val cambios = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun cargar() {
        runCatching { if (fichero.exists()) ListaNegraFuentes.cargar(fichero.readLines()) }
        cambios.value++
    }

    fun marcar(c: Candidato) {
        ListaNegraFuentes.marcar(c)
        guardar()
    }

    /** Sustituye la lista entera (al restaurar una copia). */
    fun reemplazar(claves: List<String>) {
        ListaNegraFuentes.limpiar()
        ListaNegraFuentes.cargar(claves.takeLast(ListaNegraFuentes.MAXIMO))
        guardar()
    }

    private fun guardar() {
        runCatching { fichero.writeText(ListaNegraFuentes.claves().joinToString("\n")) }
        cambios.value++
    }
}
