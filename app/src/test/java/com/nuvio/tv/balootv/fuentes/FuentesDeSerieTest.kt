package com.nuvio.tv.balootv.fuentes

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FuentesDeSerieTest {

    private fun instalado(baseUrl: String) = mockk<Addon>(relaxed = true).also { every { it.baseUrl } returns baseUrl }

    @Test
    fun `solo instala las fuentes que faltan`() = runTest {
        val repo = mockk<AddonRepository>(relaxed = true)
        every { repo.getInstalledAddons() } returns flowOf(
            listOf(instalado("https://uno.example/a"), instalado("https://dos.example/b"))
        )
        FuentesDeSerie(repo).asegurar(
            listOf(
                "https://uno.example/a/manifest.json",
                "https://dos.example/b/manifest.json",
                "https://tres.example/stremio/c/manifest.json",
                "https://uno.example/d/manifest.json",
            )
        )
        coVerify(exactly = 1) { repo.addAddon("https://tres.example/stremio/c/manifest.json") }
        coVerify(exactly = 1) { repo.addAddon("https://uno.example/d/manifest.json") }
        coVerify(exactly = 2) { repo.addAddon(any()) }
    }

    private val plantillas = listOf(
        "https://uno.example/idioma=es%7Ctorbox={torbox}/manifest.json",
        "https://dos.example/stremio/eyJ0Ijoie3RvcmJveH0ifQ/manifest.json",
        "https://tres.example/sin-clave/manifest.json",
        "https://cuatro.example/torbox={torbox}/manifest.json",
    )

    @Test
    fun `sin clave solo quedan las que no la necesitan`() {
        assertEquals(listOf("https://tres.example/sin-clave/manifest.json"), FuentesDeSerie.urlsPara(" ", plantillas))
    }

    @Test
    fun `la edicion de las pruebas no trae fuentes de serie si no hay plantillas`() {
        assertEquals(emptyList<String>(), FuentesDeSerie.urlsPara("abc", emptyList()))
    }

    @Test
    fun `genera las fuentes con la clave`() {
        assertEquals(
            listOf(
                "https://uno.example/idioma=es%7Ctorbox=abc-123/manifest.json",
                "https://dos.example/stremio/eyJ0IjoiYWJjLTEyMyJ9/manifest.json",
                "https://tres.example/sin-clave/manifest.json",
                "https://cuatro.example/torbox=abc-123/manifest.json",
            ),
            FuentesDeSerie.urlsPara("abc-123", plantillas),
        )
    }

    @Test
    fun `al cambiar de clave cada fuente se cambia en su sitio y lo demas no se toca`() = runTest {
        val viejas = FuentesDeSerie.urlsPara("vieja", plantillas)
        val nuevas = FuentesDeSerie.urlsPara("nueva", plantillas)
        val cinemeta = "https://catalogo.example"
        val repo = mockk<AddonRepository>(relaxed = true)
        every { repo.getInstalledAddons() } returns flowOf(
            listOf(instalado(viejas[0].removeSuffix("/manifest.json")), instalado(cinemeta), instalado(viejas[2].removeSuffix("/manifest.json")))
        )
        FuentesDeSerie(repo).sustituir(viejas, nuevas)
        coVerify(exactly = 4) { repo.addAddon(any()) }
        // La de en medio no lleva clave: es la misma con las dos cuentas y no se quita.
        coVerify(exactly = 1) { repo.removeAddon(any()) }
        coVerify(exactly = 0) { repo.removeAddon(cinemeta) }
        coVerifyOrder {
            repo.addAddon(nuevas[3])
            repo.removeAddon(viejas[0].removeSuffix("/manifest.json"))
            repo.setAddonOrder(listOf(nuevas[0], cinemeta, nuevas[2], nuevas[1], nuevas[3]))
        }
    }
}
