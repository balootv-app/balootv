package com.nuvio.tv.balootv.fuentes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nuvio.tv.balootv.cuenta.ClaveTorbox
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.StreamRepository
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

    private val enlaces = mockk<StreamRepository>(relaxed = true)

    private fun enlace(url: String? = null, infoHash: String? = null) = Stream(
        name = "x", title = null, description = null, url = url, ytId = null, infoHash = infoHash, fileIdx = null,
        externalUrl = null, behaviorHints = null, addonName = "a", addonLogo = null,
    )

    private fun TestScope.gestor(): Pair<GestorFuentes, AlmacenFuentes> {
        val almacen = AlmacenFuentes(PreferenceDataStoreFactory.create(scope = backgroundScope) { carpeta.newFile("f.preferences_pb") })
        return GestorFuentes(repo, almacen, clave, enlaces) to almacen
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
    fun `una fuente de solo torrents sin la parte de TorBox no se anade y se dice por que`() = runTest {
        val (g, almacen) = gestor()
        coEvery { repo.fetchAddon("https://t.example") } returns NetworkResult.Success(addon("https://t.example", "stream"))
        coEvery { enlaces.getStreamsFromAddon(any(), "movie", "tt0111161") } returns NetworkResult.Success(listOf(enlace(infoHash = "abc")))
        coEvery { repo.fetchAddon("https://w.example") } returns NetworkResult.Success(addon("https://w.example", "stream"))

        val r = g.anadir("https://t.example/manifest.json")
        assertEquals(listOf(Comprobacion.SinTorbox("t.example")), r.map { it.comprobacion })
        coVerify(exactly = 0) { repo.addAddon(any()) }
        assertEquals(emptyList<String>(), almacen.actuales())

        // La misma clase de fuente con enlaces web (la clave ya va dentro, u otra que no es de torrents) sí entra.
        coEvery { enlaces.getStreamsFromAddon(match { it.baseUrl == "https://w.example" }, "movie", "tt0111161") } returns
            NetworkResult.Success(listOf(enlace(url = "https://w.example/play/1", infoHash = "abc")))
        assertEquals(listOf(Comprobacion.Valida("w.example")), g.anadir("https://w.example/manifest.json").map { it.comprobacion })
    }

    @Test
    fun `si la pregunta de prueba falla, la fuente se acepta`() = runTest {
        val (g, _) = gestor()
        coEvery { repo.fetchAddon(any()) } returns NetworkResult.Success(addon("https://a.example", "stream"))
        coEvery { enlaces.getStreamsFromAddon(any(), any(), any()) } returns NetworkResult.Error("timeout")
        assertEquals(listOf(Comprobacion.Valida("a.example")), g.anadir("https://a.example/manifest.json").map { it.comprobacion })
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
