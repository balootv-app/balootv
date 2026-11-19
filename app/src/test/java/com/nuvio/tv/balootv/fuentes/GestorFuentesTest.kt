package com.nuvio.tv.balootv.fuentes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nuvio.tv.balootv.cuenta.ClaveTorbox
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GestorFuentesTest {

    @get:Rule val carpeta = TemporaryFolder()

    private fun addon(base: String, vararg recursos: String) = Addon(
        id = base, name = base.substringAfter("://").substringBefore('/'), version = "1", description = null, logo = null,
        baseUrl = base, catalogs = emptyList(), types = emptyList(), resources = recursos.map { AddonResource(it, emptyList(), null) },
    )

    private val instaladas = MutableStateFlow<List<Addon>>(emptyList())
    private val repo = mockk<AddonRepository>(relaxed = true).also { every { it.getInstalledAddons() } returns instaladas }
    private val clave = mockk<ClaveTorbox>().also {
        every { it.enUso } returns flowOf("k1")
        coEvery { it.actual() } returns "k1"
    }

    private fun TestScope.gestor(): Pair<GestorFuentes, AlmacenFuentes> {
        val almacen = AlmacenFuentes(PreferenceDataStoreFactory.create(scope = backgroundScope) { carpeta.newFile("f.preferences_pb") })
        return GestorFuentes(repo, almacen, clave) to almacen
    }

    @Test
    fun `anade las que responden con enlaces y guarda la plantilla con el marcador`() = runTest {
        val (g, almacen) = gestor()
        coEvery { repo.fetchAddon("https://a.example/torbox=k1") } returns NetworkResult.Success(addon("https://a.example/torbox=k1", "stream"))
        coEvery { repo.fetchAddon("https://b.example") } returns NetworkResult.Error("timeout")
        coEvery { repo.fetchAddon("https://c.example") } returns NetworkResult.Success(addon("https://c.example", "catalog"))

        val r = g.anadir("https://a.example/torbox={torbox}/manifest.json\nhttps://b.example\nhttps://c.example\nno es nada\n")

        assertEquals(
            listOf(Comprobacion.Valida("a.example"), Comprobacion.NoResponde, Comprobacion.SinEnlaces("c.example"), Comprobacion.NoEsDireccion),
            r.map { it.comprobacion },
        )
        assertEquals(listOf("a.example", "b.example", "c.example"), r.take(3).map { it.sitio })
        coVerify(exactly = 1) { repo.addAddon("https://a.example/torbox=k1/manifest.json") }
        coVerify(exactly = 1) { repo.addAddon(any()) }
        assertEquals(listOf("https://a.example/torbox={torbox}/manifest.json"), almacen.actuales())
    }

    @Test
    fun `la misma direccion dos veces en un envio se anade una sola vez`() = runTest {
        val (g, _) = gestor()
        coEvery { repo.fetchAddon(any()) } returns NetworkResult.Success(addon("https://a.example", "stream"))
        val r = g.anadir("https://a.example/manifest.json\nhttps://a.example/")
        assertEquals(listOf(Comprobacion.Valida("a.example"), Comprobacion.Repetida), r.map { it.comprobacion })
    }

    @Test
    fun `quitar borra el addon y su plantilla`() = runTest {
        val (g, almacen) = gestor()
        almacen.anadir("https://a.example/torbox={torbox}/manifest.json")
        almacen.anadir("https://b.example/manifest.json")
        g.quitar("https://a.example/torbox=k1")
        coVerify { repo.removeAddon("https://a.example/torbox=k1") }
        assertEquals(listOf("https://b.example/manifest.json"), almacen.actuales())
    }

    @Test
    fun `sin fuentes cuando nada da enlaces`() {
        assertEquals(true, GestorFuentes.sinFuentes(emptyList()))
        assertEquals(true, GestorFuentes.sinFuentes(listOf(addon("https://c.example", "catalog", "meta"))))
        assertEquals(false, GestorFuentes.sinFuentes(listOf(addon("https://a.example", "stream"))))
        // Una que aún no ha contestado puede dar enlaces: no se avisa en falso al arrancar.
        assertEquals(false, GestorFuentes.sinFuentes(listOf(addon("https://a.example"))))
    }
}
