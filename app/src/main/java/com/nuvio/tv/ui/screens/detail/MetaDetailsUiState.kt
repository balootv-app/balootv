package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.core.tracking.TrackingMembershipRemovalConfirmation
import com.nuvio.tv.domain.model.DetailImdbRatingsVisibility
import com.nuvio.tv.domain.model.EpisodeOptionsOverlayStyle
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.TraktCommentReview
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.HomeImdbRatingsVisibility

enum class MoreLikeThisSource {
    TMDB,
    TRAKT
}

enum class CommentsMode {
    TITLE,
    EPISODE
}

data class MetaDetailsUiState(
    val isLoading: Boolean = true,
    val meta: Meta? = null,
    val error: String? = null,
    val selectedSeason: Int = 1,
    val seasons: List<Int> = emptyList(),
    val episodesForSeason: List<Video> = emptyList(),
    val isInLibrary: Boolean = false,
    val nextToWatch: NextToWatch? = null,
    val episodeProgressMap: Map<Pair<Int, Int>, WatchProgress> = emptyMap(),
    val trailerUrl: String? = null,
    val trailerAudioUrl: String? = null,
    val isTrailerPlaying: Boolean = false,
    val isBackgroundTrailerPlaying: Boolean = false,
    val pauseBackgroundTrailerOnScroll: Boolean = true,
    val isTrailerLoading: Boolean = false,
    val showTrailerControls: Boolean = false,
    val hideLogoDuringTrailer: Boolean = false,
    val trailerButtonEnabled: Boolean = false,
    val librarySourceMode: LibrarySourceMode = LibrarySourceMode.LOCAL,
    val libraryListTabs: List<LibraryListTab> = emptyList(),
    val isInWatchlist: Boolean = false,
    val pickerMembership: Map<String, Boolean> = emptyMap(),
    val defaultLibraryTogglePending: Boolean = false,
    val removalConfirmations: List<TrackingMembershipRemovalConfirmation> = emptyList(),
    val isMovieWatched: Boolean = false,
    val watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    val episodeWatchedPendingKeys: Set<String> = emptySet(),
    val randomEpisodeEnabled: Boolean = false,
    val episodeShuffle: com.nuvio.tv.domain.model.EpisodeShuffleSettings = com.nuvio.tv.domain.model.EpisodeShuffleSettings(),
    val shufflePoolEmpty: Boolean = false,
    val episodeOptionsOverlayStyle: EpisodeOptionsOverlayStyle = EpisodeOptionsOverlayStyle.BLUR,
    val overallRatingsVisibility: HomeImdbRatingsVisibility = HomeImdbRatingsVisibility.SHOW_ALL,
    val detailImdbRatingsVisibility: DetailImdbRatingsVisibility = DetailImdbRatingsVisibility.SHOW_ALL,
    val showFullReleaseDate: Boolean = true,
    val moreLikeThis: List<MetaPreview> = emptyList(),
    val moreLikeThisSource: MoreLikeThisSource? = null,
    val collection: List<MetaPreview> = emptyList(),
    val collectionName: String? = null,
    val escenaCreditos: com.nuvio.tv.balootv.ficha.EscenaCreditos? = null,
    val relatedWatchedStatus: Map<String, Boolean> = emptyMap(),
    val tmdbRating: Float? = null,
    val comments: List<TraktCommentReview> = emptyList(),
    val commentsCurrentPage: Int = 0,
    val commentsPageCount: Int = 0,
    val isCommentsLoading: Boolean = false,
    val isCommentsLoadingMore: Boolean = false,
    val commentsError: String? = null,
    val shouldShowCommentsSection: Boolean = false,
    val commentsMode: CommentsMode = CommentsMode.TITLE,
    val commentsEpisodeTarget: Video? = null,
    val selectedComment: TraktCommentReview? = null,
    val userMessage: String? = null,
    val userMessageIsError: Boolean = false
)

sealed class MetaDetailsEvent {
    data class OnSeasonSelected(val season: Int) : MetaDetailsEvent()
    data object OnToggleLibrary : MetaDetailsEvent()
    data object OnTrailerButtonClick : MetaDetailsEvent()
    data class OnToggleEpisodeWatched(val video: Video) : MetaDetailsEvent()
}
