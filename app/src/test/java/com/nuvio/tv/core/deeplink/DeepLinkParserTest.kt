package com.nuvio.tv.core.deeplink

import com.nuvio.tv.domain.deeplink.AppDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    @Test
    fun parsesMetaQueryDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://meta?type=series&id=tt0944947")
        )
    }

    // BalooTV: sin watch party ni instalación de addons por enlace (las fuentes van en el código).
    @Test
    fun ignoraEnlacesDeWatchParty() {
        assertNull(DeepLinkParser.parse("nuvio://party/abc234"))
        assertNull(DeepLinkParser.parse("nuvio://party?code=ABC-234"))
    }

    @Test
    fun ignoraEnlacesQueInstalanAddons() {
        assertNull(DeepLinkParser.parse("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json"))
        assertNull(DeepLinkParser.parse("stremio://free.nebulapro.xyz/sports/i/free/manifest.json"))
    }

    @Test
    fun ignoraRutasDePantallasBorradas() {
        listOf("nuvio://collections", "nuvio://profiles", "nuvio://watch_party?code=X", "nuvio://plugins").forEach {
            assertNull(DeepLinkParser.parse(it))
        }
    }

    @Test
    fun parsesDirectImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://series/tt0944947")
        )
    }

    @Test
    fun parsesProviderImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://imdb/series/tt0944947")
        )
    }

    @Test
    fun parsesProviderTmdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tmdb:1399"),
            DeepLinkParser.parse("nuvio://tmdb/tv/1399")
        )
    }

    @Test
    fun doesNotTreatAuthLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("nuvio://auth/trakt?code=abc"))
    }

    @Test
    fun doesNotTreatNonHostStremioLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("stremio://detail/series/tt0944947"))
    }

    // Enlaces que manda la app del móvil (balootv://).
    @Test
    fun reproducePeliculaDelMovil() {
        assertEquals(
            AppDeepLink.Reproducir(type = "movie", id = "tmdb:603"),
            DeepLinkParser.parse("balootv://reproducir/tmdb/movie/603")
        )
    }

    @Test
    fun reproduceElSiguienteEpisodioDeUnaSerie() {
        assertEquals(
            AppDeepLink.Reproducir(type = "series", id = "tmdb:1396"),
            DeepLinkParser.parse("balootv://reproducir/tmdb/series/1396")
        )
    }

    @Test
    fun reproduceUnEpisodioConcreto() {
        assertEquals(
            AppDeepLink.Reproducir(type = "series", id = "tmdb:1396", season = 2, episode = 5),
            DeepLinkParser.parse("balootv://reproducir/tmdb/series/1396/2/5")
        )
    }

    @Test
    fun abreLaFichaDesdeBalootv() {
        assertEquals(
            AppDeepLink.Meta(type = "movie", id = "tmdb:603"),
            DeepLinkParser.parse("balootv://tmdb/movie/603")
        )
    }

    @Test
    fun ignoraEnlacesDeBalootvMalFormados() {
        listOf(
            "balootv://reproducir",
            "balootv://reproducir/tmdb/movie",
            "balootv://reproducir/tmdb/musica/603",
            "balootv://reproducir/imdb/movie/tt0133093",
            "balootv://reproducir/tmdb/series/1396/2",
            "balootv://reproducir/tmdb/series/1396/dos/cinco",
            "balootv://reproducir/tmdb/movie/603/1/1",
            "balootv://nada",
            "balootv:///"
        ).forEach { assertNull(it, DeepLinkParser.parse(it)) }
    }

    @Test
    fun nuvioNoReproduce() {
        assertNull(DeepLinkParser.parse("nuvio://reproducir/tmdb/movie/603"))
    }
}
