package com.nuvio.tv.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Detail : Screen("detail/{itemId}/{itemType}?addonBaseUrl={addonBaseUrl}&returnFocusSeason={returnFocusSeason}&returnFocusEpisode={returnFocusEpisode}&returnToHomeOnBack={returnToHomeOnBack}&heroBackdropUrl={heroBackdropUrl}&playOnLoad={playOnLoad}&manualSelection={manualSelection}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            itemId: String,
            itemType: String,
            addonBaseUrl: String? = null,
            returnFocusSeason: Int? = null,
            returnFocusEpisode: Int? = null,
            returnToHomeOnBack: Boolean = false,
            heroBackdropUrl: String? = null,
            playOnLoad: Boolean = false,
            manualSelection: Boolean = false
        ): String {
            val encodedItemId = encode(itemId)
            val encodedItemType = encode(itemType)
            val encodedAddon = addonBaseUrl?.let { encode(it) } ?: ""
            val encodedHeroBackdrop = heroBackdropUrl?.let { encode(it) } ?: ""
            return "detail/$encodedItemId/$encodedItemType?addonBaseUrl=$encodedAddon&returnFocusSeason=${returnFocusSeason ?: ""}&returnFocusEpisode=${returnFocusEpisode ?: ""}&returnToHomeOnBack=$returnToHomeOnBack&heroBackdropUrl=$encodedHeroBackdrop&playOnLoad=$playOnLoad&manualSelection=$manualSelection"
        }
    }
    data object Stream : Screen("stream/{videoId}/{contentType}/{title}?poster={poster}&backdrop={backdrop}&logo={logo}&season={season}&episode={episode}&episodeName={episodeName}&genres={genres}&year={year}&contentId={contentId}&contentName={contentName}&runtime={runtime}&manualSelection={manualSelection}&returnToDetailOnBack={returnToDetailOnBack}&returnToHomeOnBack={returnToHomeOnBack}&startFromBeginning={startFromBeginning}&contentLanguage={contentLanguage}&profileId={profileId}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            videoId: String,
            contentType: String,
            title: String,
            poster: String? = null,
            backdrop: String? = null,
            logo: String? = null,
            season: Int? = null,
            episode: Int? = null,
            episodeName: String? = null,
            genres: String? = null,
            year: String? = null,
            contentId: String? = null,
            contentName: String? = null,
            runtime: Int? = null,
            manualSelection: Boolean = false,
            returnToDetailOnBack: Boolean = false,
            returnToHomeOnBack: Boolean = false,
            startFromBeginning: Boolean = false,
            contentLanguage: String? = null,
            profileId: Int? = null
        ): String {
            val encodedVideoId = encode(videoId)
            val encodedContentTypePath = encode(contentType)
            val encodedTitle = encode(title)
            val encodedPoster = poster?.let { encode(it) } ?: ""
            val encodedBackdrop = backdrop?.let { encode(it) } ?: ""
            val encodedLogo = logo?.let { encode(it) } ?: ""
            val encodedEpisodeName = episodeName?.let { encode(it) } ?: ""
            val encodedGenres = genres?.let { encode(it) } ?: ""
            val encodedYear = year?.let { encode(it) } ?: ""
            val encodedContentId = contentId?.let { encode(it) } ?: ""
            val encodedContentName = contentName?.let { encode(it) } ?: ""
            val encodedContentLanguage = contentLanguage?.let { encode(it) } ?: ""
            return "stream/$encodedVideoId/$encodedContentTypePath/$encodedTitle?poster=$encodedPoster&backdrop=$encodedBackdrop&logo=$encodedLogo&season=${season ?: ""}&episode=${episode ?: ""}&episodeName=$encodedEpisodeName&genres=$encodedGenres&year=$encodedYear&contentId=$encodedContentId&contentName=$encodedContentName&runtime=${runtime ?: ""}&manualSelection=$manualSelection&returnToDetailOnBack=$returnToDetailOnBack&returnToHomeOnBack=$returnToHomeOnBack&startFromBeginning=$startFromBeginning&contentLanguage=$encodedContentLanguage&profileId=${profileId ?: ""}"
        }
    }
    data object Player : Screen("player/{streamUrl}/{title}?streamName={streamName}&year={year}&headers={headers}&contentId={contentId}&contentType={contentType}&contentName={contentName}&poster={poster}&backdrop={backdrop}&logo={logo}&videoId={videoId}&season={season}&episode={episode}&episodeTitle={episodeTitle}&bingeGroup={bingeGroup}&autoPlayNav={autoPlayNav}&returnToDetailOnBack={returnToDetailOnBack}&returnToHomeOnBack={returnToHomeOnBack}&filename={filename}&videoHash={videoHash}&videoSize={videoSize}&startFromBeginning={startFromBeginning}&addonName={addonName}&addonLogo={addonLogo}&streamDescription={streamDescription}&infoHash={infoHash}&fileIdx={fileIdx}&contentLanguage={contentLanguage}&cloudSessionToken={cloudSessionToken}&profileId={profileId}") {
        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun createRoute(
            streamUrl: String,
            title: String,
            streamName: String? = null,
            year: String? = null,
            headers: Map<String, String>? = null,
            contentId: String? = null,
            contentType: String? = null,
            contentName: String? = null,
            poster: String? = null,
            backdrop: String? = null,
            logo: String? = null,
            videoId: String? = null,
            season: Int? = null,
            episode: Int? = null,
            episodeTitle: String? = null,
            bingeGroup: String? = null,
            autoPlayNav: Boolean = false,
            returnToDetailOnBack: Boolean = false,
            returnToHomeOnBack: Boolean = false,
            filename: String? = null,
            videoHash: String? = null,
            videoSize: Long? = null,
            startFromBeginning: Boolean = false,
            addonName: String? = null,
            addonLogo: String? = null,
            streamDescription: String? = null,
            infoHash: String? = null,
            fileIdx: Int? = null,
            contentLanguage: String? = null,
            cloudSessionToken: String? = null,
            profileId: Int? = null
        ): String {
            val encodedUrl = encode(streamUrl)
            val encodedTitle = encode(title)
            val encodedStreamName = streamName?.let { encode(it) } ?: ""
            val encodedYear = year?.let { encode(it) } ?: ""
            val encodedHeaders = headers?.let {
                encode(org.json.JSONObject(it).toString())
            } ?: ""
            val encodedContentId = contentId?.let { encode(it) } ?: ""
            val encodedContentType = contentType?.let { encode(it) } ?: ""
            val encodedContentName = contentName?.let { encode(it) } ?: ""
            val encodedPoster = poster?.let { encode(it) } ?: ""
            val encodedBackdrop = backdrop?.let { encode(it) } ?: ""
            val encodedLogo = logo?.let { encode(it) } ?: ""
            val encodedVideoId = videoId?.let { encode(it) } ?: ""
            val encodedEpisodeTitle = episodeTitle?.let { encode(it) } ?: ""
            val encodedBingeGroup = bingeGroup?.let { encode(it) } ?: ""
            val encodedFilename = filename?.let { encode(it) } ?: ""
            val encodedVideoHash = videoHash ?: ""
            val encodedAddonName = addonName?.let { encode(it) } ?: ""
            val encodedAddonLogo = addonLogo?.let { encode(it) } ?: ""
            val encodedStreamDescription = streamDescription?.let { encode(it) } ?: ""
            val encodedInfoHash = infoHash ?: ""
            val encodedContentLanguage = contentLanguage?.let { encode(it) } ?: ""
            val encodedCloudSessionToken = cloudSessionToken?.let { encode(it) } ?: ""
            return "player/$encodedUrl/$encodedTitle?streamName=$encodedStreamName&year=$encodedYear&headers=$encodedHeaders&contentId=$encodedContentId&contentType=$encodedContentType&contentName=$encodedContentName&poster=$encodedPoster&backdrop=$encodedBackdrop&logo=$encodedLogo&videoId=$encodedVideoId&season=${season ?: ""}&episode=${episode ?: ""}&episodeTitle=$encodedEpisodeTitle&bingeGroup=$encodedBingeGroup&autoPlayNav=$autoPlayNav&returnToDetailOnBack=$returnToDetailOnBack&returnToHomeOnBack=$returnToHomeOnBack&filename=$encodedFilename&videoHash=$encodedVideoHash&videoSize=${videoSize ?: ""}&startFromBeginning=$startFromBeginning&addonName=$encodedAddonName&addonLogo=$encodedAddonLogo&streamDescription=$encodedStreamDescription&infoHash=$encodedInfoHash&fileIdx=${fileIdx ?: ""}&contentLanguage=$encodedContentLanguage&cloudSessionToken=$encodedCloudSessionToken&profileId=${profileId ?: ""}"
        }
    }
    data object Search : Screen("search")
    data object Discover : Screen("discover")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object Tracking : Screen("trakt")
    /** BalooTV: añadir y quitar fuentes (desde Ajustes o desde una ficha sin fuentes). */
    data object Fuentes : Screen("fuentes")
    /** BalooTV: primer arranque de la edición pública (bienvenida, TorBox, Trakt y fuentes). */
    data object PrimerArranque : Screen("primer_arranque")
    data object ExperienceModeSelection : Screen("experience_mode_selection")
    data object LayoutSelection : Screen("layout_selection")
}
