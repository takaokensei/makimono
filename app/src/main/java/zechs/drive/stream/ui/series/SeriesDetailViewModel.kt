package zechs.drive.stream.ui.series

import zechs.drive.stream.utils.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.data.local.CatalogEntry
import zechs.drive.stream.data.local.toAnimeMetadata
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.remote.AnimeMetadata
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.repository.FranchiseGroupingManager
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.series.adapter.SeasonTab
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.domain.library.CategoryChip
import zechs.drive.stream.domain.library.EpisodeCategorizer
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.domain.library.SeriesMediaCategory
import zechs.drive.stream.domain.library.SeriesMediaClassifier
import zechs.drive.stream.domain.library.EpisodeDeduplicationPolicy
import zechs.drive.stream.utils.PlaybackProgressPolicy
import zechs.drive.stream.utils.SeasonEpisodeGrouper
import zechs.drive.stream.utils.SeasonGroup
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.data.repository.DriveFailureClassifier
import zechs.drive.stream.utils.state.ResourceErrorKind
import zechs.drive.stream.data.remote.EpisodeDetails
import zechs.drive.stream.data.remote.EpisodeMetadataResolver
import java.util.Locale
import javax.inject.Inject

enum class SeriesNavTab {
    EPISODES,
    DETAILS,
    MORE_LIKE_THIS
}

sealed class SeriesDetailUiState {
    data class Loading(
        val folderId: String,
        val seriesTitle: String,
        val posterUrl: String? = null,
        val bannerUrl: String? = null
    ) : SeriesDetailUiState()
    data class Success(
        val folderId: String,
        val seriesTitle: String,
        val animeEntry: TenraiAnimeService.TenraiAnimeEntry?,
        val aniListMetadata: AnimeMetadata? = null,
        val seasonTabs: List<SeasonTab>,
        val currentEpisodes: List<SeriesEpisodeItem>,
        val continueWatchingItem: SeriesEpisodeItem?,
        val trailerItem: SeriesEpisodeItem? = null,
        val continueWatchingSubtitle: String,
        val isStarred: Boolean,
        val isFollowed: Boolean = false,
        val fallbackPosterUrl: String? = null,
        val qualityBadge: String? = null,
        val audioBadge: String? = null,
        val subtitleBadge: String? = null,
        val hasIncompleteDriveListing: Boolean = false,
        val isMovie: Boolean = false,
        val selectedNavTab: SeriesNavTab = SeriesNavTab.EPISODES,
        val franchiseHub: FranchiseGroupingManager.FranchiseHub? = null,
        val activeMemberFolderId: String? = null,
        val isFranchiseHub: Boolean = false,
        val franchiseMembersCount: Int = 0,
        val activeArcId: String? = null,
        val activeArcTitle: String? = null,
        val activeArcYear: Int? = null,
        val activeArcScore: Float? = null,
        val activeArcScoreSource: String = "MAL",
        val categoryChips: List<CategoryChip> = emptyList(),
        val selectedCategory: SeriesMediaCategory = SeriesMediaCategory.EPISODES,
        val allCurrentArcEpisodes: List<SeriesEpisodeItem> = emptyList(),
        val bannerUrl: String? = null
    ) : SeriesDetailUiState()
    data class Error(
        val error: AppError,
        val message: String = error.displayMessage,
        val uiError: zechs.drive.stream.ui.common.UiError = zechs.drive.stream.ui.common.UiError.fromAppError(error)
    ) : SeriesDetailUiState()
}

sealed interface SeriesDetailEvent {
    data class SelectNavTab(val tab: SeriesNavTab) : SeriesDetailEvent
    data class SelectSeason(val tab: SeasonTab) : SeriesDetailEvent
    data class SelectCategory(val category: SeriesMediaCategory) : SeriesDetailEvent
    data class SaveFocus(val position: Int) : SeriesDetailEvent
    data class ToggleStar(val folderId: String) : SeriesDetailEvent
    data class ToggleFollow(val folderId: String) : SeriesDetailEvent
    data class Retry(val folderId: String, val seriesTitle: String) : SeriesDetailEvent
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val tenraiAnimeService: TenraiAnimeService,
    private val watchListRepository: WatchListRepository,
    private val folderMetadataRepository: FolderMetadataRepository,
    private val animePosterResolver: AnimePosterResolver,
    private val favoriteRepository: zechs.drive.stream.data.repository.FavoriteRepository,
    private val followedFolderRepository: FollowedFolderRepository,
    private val watchQueueRepository: WatchQueueRepository,
    private val franchiseGroupingManager: FranchiseGroupingManager,
    private val episodeMetadataResolver: EpisodeMetadataResolver,
    private val catalogRepository: zechs.drive.stream.data.repository.CatalogRepository? = null,
    private val downloadRepository: zechs.drive.stream.data.repository.DownloadRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "SeriesDetailVM"
        private val seriesDetailCache = java.util.concurrent.ConcurrentHashMap<String, SeriesDetailUiState.Success>()
        private val folderEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, List<SeriesEpisodeItem>>()

        fun clearCache() {
            seriesDetailCache.clear()
            folderEpisodesCache.clear()
        }

        fun getCachedEpisodes(folderId: String): List<SeriesEpisodeItem>? {
            return folderEpisodesCache[folderId]
        }

        fun cacheEpisodes(folderId: String, episodes: List<SeriesEpisodeItem>) {
            folderEpisodesCache[folderId] = episodes
        }
    }

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading("", ""))
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private var allSeasonGroups = listOf<SeasonGroup>()
    private var allEpisodeItems = listOf<SeriesEpisodeItem>()
    private var currentFolderId: String = ""
    private var currentSeriesTitle: String = ""
    private var isFolderStarred: Boolean = false
    private var isFolderFollowed: Boolean = false
    private var animeEntry: TenraiAnimeService.TenraiAnimeEntry? = null
    private var franchiseArcs = listOf<TenraiAnimeService.TenraiFranchiseArc>()
    private var currentResolvedEpisodes = listOf<EpisodeDetails>()

    var lastFocusedPosition: Int? = null
    var selectedSeasonPosition: Int = 0

    fun onEvent(event: SeriesDetailEvent) {
        when (event) {
            is SeriesDetailEvent.SelectNavTab -> selectNavTab(event.tab)
            is SeriesDetailEvent.SelectSeason -> selectSeasonTab(event.tab)
            is SeriesDetailEvent.SelectCategory -> selectCategory(event.category)
            is SeriesDetailEvent.SaveFocus -> { lastFocusedPosition = event.position }
            is SeriesDetailEvent.ToggleStar -> toggleStar()
            is SeriesDetailEvent.ToggleFollow -> toggleFollow()
            is SeriesDetailEvent.Retry -> loadSeriesDetails(event.folderId, event.seriesTitle)
        }
    }

    fun selectCategory(category: SeriesMediaCategory) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return
        val allEpisodes = currentState.allCurrentArcEpisodes.ifEmpty { allEpisodeItems }

        val filtered = allEpisodes.filter { ep ->
            val parsed = EpisodeParser.parse(ep.file.name)
            val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
            val mappedCat = SeriesMediaClassifier.mapCategory(domainCat)
            mappedCat == category
        }

        val categoryCounts = mutableMapOf<SeriesMediaCategory, Int>()
        allEpisodes.forEach { ep ->
            val parsed = EpisodeParser.parse(ep.file.name)
            val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
            val cat = SeriesMediaClassifier.mapCategory(domainCat)
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
        }
        val chips = SeriesMediaClassifier.buildChips(categoryCounts, category)

        val updatedTabs = currentState.seasonTabs.map { tab ->
            if (tab.isSelected) {
                tab.copy(selectedCategory = category, chips = chips)
            } else tab
        }

        _uiState.value = currentState.copy(
            selectedCategory = category,
            categoryChips = chips,
            seasonTabs = updatedTabs,
            currentEpisodes = filtered
        )
    }

    fun selectNavTab(tab: SeriesNavTab) {
        val current = _uiState.value as? SeriesDetailUiState.Success ?: return
        if (current.selectedNavTab != tab) {
            _uiState.value = current.copy(selectedNavTab = tab)
        }
    }

    fun loadSeriesDetails(folderId: String, seriesTitle: String, initialPoster: String? = null) {
        currentFolderId = folderId
        currentSeriesTitle = seriesTitle
        observeDownloads(folderId)

        val cachedSuccess = seriesDetailCache[folderId]
        if (cachedSuccess != null) {
            _uiState.value = cachedSuccess
        } else {
            _uiState.value = SeriesDetailUiState.Loading(folderId, seriesTitle, initialPoster)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check local Room cache immediately so cached posters/banners display with zero delay
                val initialCached = try {
                    folderMetadataRepository.getMetadata(folderId)
                } catch (e: Exception) {
                    null
                }
                if (initialCached != null) {
                    val quickPoster = initialPoster ?: initialCached.posterUrl
                    val quickBanner = initialCached.bannerUrl
                    withContext(Dispatchers.Main) {
                        if (_uiState.value is SeriesDetailUiState.Loading) {
                            _uiState.value = SeriesDetailUiState.Loading(
                                folderId = folderId,
                                seriesTitle = seriesTitle,
                                posterUrl = quickPoster,
                                bannerUrl = quickBanner
                            )
                        }
                    }
                }

                // Record folder opened for Home screen Recents / Continue
                folderMetadataRepository.recordFolderOpened(folderId, seriesTitle)

                val hasCompleteMetadata = initialCached != null && !initialCached.synopsis.isNullOrBlank() && initialCached.score != null

                // 1.5 Offline-First check: if catalogRepository already has cached videos, emit immediate success state!
                val cachedEntries = catalogRepository?.getChildren(folderId).orEmpty()
                val cachedVideoFiles = cachedEntries
                    .filter { entry ->
                        val lower = entry.name.lowercase()
                        val isVideoMime = entry.mimeType.startsWith("video/") || entry.mimeType == "application/x-matroska"
                        val isVideoExt = lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".avi")
                        isVideoMime || isVideoExt
                    }
                    .map { entry ->
                        DriveFile(
                            id = entry.id,
                            name = entry.name,
                            size = null,
                            mimeType = entry.mimeType,
                            iconLink = null,
                            thumbnailLink = entry.posterUrl,
                            posterUrl = entry.posterUrl,
                            shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
                            starred = zechs.drive.stream.data.model.Starred.UNSTARRED
                        )
                    }

                if (cachedVideoFiles.isNotEmpty() && initialCached != null) {
                    val quickVideoIds = cachedVideoFiles.map { it.id }
                    val quickWatchList = watchListRepository.getWatches(quickVideoIds)
                    val quickWatchMap = quickWatchList.associateBy { it.videoId }
                    val isLocalFav = favoriteRepository.isFavorite(folderId)
                    isFolderStarred = isLocalFav || (cachedVideoFiles.firstOrNull()?.starred == zechs.drive.stream.data.model.Starred.STARRED)
                    isFolderFollowed = followedFolderRepository.isFollowed(folderId)

                    val quickEpisodes = try {
                        episodeMetadataResolver.fetchShowEpisodes(seriesTitle)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val immediateState = buildSuccessState(
                        folderId = folderId,
                        seriesTitle = seriesTitle,
                        driveFiles = cachedVideoFiles,
                        watchMap = quickWatchMap,
                        fetchedAnime = null,
                        aniListMeta = initialCached.toAnimeMetadata(),
                        arcs = emptyList(),
                        fallbackPosterUrl = initialPoster ?: initialCached.posterUrl,
                        hasIncompleteDriveListing = false,
                        resolvedEpisodes = quickEpisodes
                    )
                    withContext(Dispatchers.Main) {
                        _uiState.value = immediateState
                    }
                }

                // 1. Concurrently fetch Tenrai metadata & AniList high-res metadata (bypassed if already cached)
                val tenraiDeferred = async {
                    if (hasCompleteMetadata) {
                        null to emptyList()
                    } else {
                        try {
                            val searchResults = tenraiAnimeService.searchAnime(seriesTitle)
                            val bestMatch = searchResults.firstOrNull {
                                it.title.contains(seriesTitle, ignoreCase = true) || seriesTitle.contains(it.title, ignoreCase = true)
                            } ?: searchResults.firstOrNull()

                            val details = bestMatch?.let { tenraiAnimeService.getAnimeDetails(it.malId) } ?: bestMatch
                            val arcs = tenraiAnimeService.resolveFranchiseArcs(seriesTitle)
                            details to arcs
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            AppLog.w(TAG, "Tenrai fetch error: ${e.message}")
                            null to emptyList()
                        }
                    }
                }

                val aniListDeferred = async {
                    if (hasCompleteMetadata) {
                        initialCached?.toAnimeMetadata()
                    } else {
                        try {
                            animePosterResolver.resolveMetadata(seriesTitle)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            AppLog.w(TAG, "AniList metadata fetch error: ${e.message}")
                            null
                        }
                    }
                }

                val episodeMetaDeferred = async {
                    try {
                        episodeMetadataResolver.fetchShowEpisodes(seriesTitle)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        AppLog.w(TAG, "Episode metadata fetch error: ${e.message}")
                        emptyList()
                    }
                }

                // 2. Fetch Drive files in folder
                val driveResult = fetchAllDriveVideos(folderId)
                val driveFiles = driveResult.files
                if (driveFiles.isEmpty() && driveResult.appError != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = SeriesDetailUiState.Error(driveResult.appError)
                    }
                    return@launch
                }
                if (driveFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = SeriesDetailUiState.Error(
                            AppError.Empty("Nenhum arquivo de vídeo encontrado na pasta.")
                        )
                    }
                    return@launch
                }

                // Publish the Drive-backed state with cached Room metadata immediately so
                // that the banner, synopsis, titles and badges appear without delay.
                val cachedFolder = folderMetadataRepository.getMetadata(folderId)
                val cachedAniListMeta = cachedFolder?.toAnimeMetadata()
                val cachedPoster = initialPoster
                    ?: cachedFolder?.posterUrl
                val videoIds = driveFiles.map { it.id }
                val watchList = watchListRepository.getWatches(videoIds)
                val watchMap = watchList.associateBy { it.videoId }

                // Check folder star status (local favorite has precedence, fallback to Drive star)
                val isLocalFav = favoriteRepository.isFavorite(folderId)
                isFolderStarred = isLocalFav || (driveFiles.firstOrNull()?.starred == zechs.drive.stream.data.model.Starred.STARRED)
                isFolderFollowed = followedFolderRepository.isFollowed(folderId)

                animeEntry = null
                franchiseArcs = emptyList()
                val shellState = buildSuccessState(
                    folderId = folderId,
                    seriesTitle = seriesTitle,
                    driveFiles = driveFiles,
                    watchMap = watchMap,
                    fetchedAnime = null,
                    aniListMeta = cachedAniListMeta,
                    arcs = emptyList(),
                    fallbackPosterUrl = cachedPoster,
                    hasIncompleteDriveListing = driveResult.isIncomplete
                )
                withContext(Dispatchers.Main) {
                    _uiState.value = shellState
                }

                val (fetchedAnime, fetchedArcs) = tenraiDeferred.await()
                val freshAniListMeta = aniListDeferred.await()
                val aniListMeta = freshAniListMeta ?: cachedAniListMeta

                val isMovie = aniListMeta?.isMovie == true
                val isNonAnime = aniListMeta?.isAnime == false
                val mediaType = aniListMeta?.mediaType

                // If this is a movie or non-anime from TMDB, verify fetchedAnime similarity before trusting it
                val cleanTitle = AnimePosterResolver.cleanAnimeTitle(seriesTitle)
                val isLegitAnime = fetchedAnime != null && !isNonAnime && AnimePosterResolver.calculateSimilarity(cleanTitle, fetchedAnime.title) >= 0.58
                val validAnime = if (isLegitAnime) fetchedAnime else null
                val validArcs = if (isLegitAnime) fetchedArcs else emptyList()

                animeEntry = validAnime
                franchiseArcs = validArcs

                // Prioritize AniList high-res poster (extraLarge 460x650), then Tenrai/MAL
                val finalPoster = aniListMeta?.posterUrl
                    ?: validAnime?.imageUrl
                    ?: initialPoster
                    ?: cachedPoster

                if (freshAniListMeta != null) {
                    folderMetadataRepository.saveAnimeMetadata(
                        folderId = folderId,
                        folderName = seriesTitle,
                        metadata = freshAniListMeta
                    )
                } else if (!finalPoster.isNullOrBlank() || isMovie) {
                    folderMetadataRepository.updateMetadataDetails(
                        folderId = folderId,
                        folderName = seriesTitle,
                        posterUrl = finalPoster ?: "",
                        isMovie = isMovie,
                        mediaType = mediaType
                    )
                }

                val resolvedEpisodes = try {
                    episodeMetaDeferred.await()
                } catch (_: Exception) {
                    emptyList()
                }
                currentResolvedEpisodes = resolvedEpisodes

                val enrichedState = buildSuccessState(
                    folderId = folderId,
                    seriesTitle = seriesTitle,
                    driveFiles = driveFiles,
                    watchMap = watchMap,
                    fetchedAnime = validAnime,
                    aniListMeta = aniListMeta,
                    arcs = validArcs,
                    fallbackPosterUrl = finalPoster,
                    hasIncompleteDriveListing = driveResult.isIncomplete,
                    resolvedEpisodes = resolvedEpisodes
                )
                withContext(Dispatchers.Main) {
                    val previous = _uiState.value as? SeriesDetailUiState.Success
                    val selectedNavTab = previous?.selectedNavTab ?: SeriesNavTab.EPISODES
                    val selectedId = previous?.seasonTabs?.firstOrNull { it.isSelected }?.id
                    val stateToPublish = if (selectedId == null) {
                        enrichedState.copy(selectedNavTab = selectedNavTab)
                    } else {
                        val selectedGroup = allSeasonGroups.firstOrNull { it.id == selectedId }
                        val eps = if (enrichedState.franchiseHub != null) {
                            folderEpisodesCache[selectedId] ?: enrichedState.currentEpisodes
                        } else {
                            getEpisodesForGroup(selectedGroup)
                        }
                        enrichedState.copy(
                            selectedNavTab = selectedNavTab,
                            seasonTabs = enrichedState.seasonTabs.map { it.copy(isSelected = it.id == selectedId) },
                            currentEpisodes = eps,
                            activeMemberFolderId = selectedId
                        )
                    }
                    seriesDetailCache[folderId] = stateToPublish
                    _uiState.value = stateToPublish
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLog.e(TAG, "Error loading series details", e)
                withContext(Dispatchers.Main) {
                    val errorKind = DriveFailureClassifier.fromException(e)
                    val appError = when (errorKind) {
                        ResourceErrorKind.NETWORK -> AppError.Network(e.message ?: "Falha de rede.")
                        ResourceErrorKind.AUTHENTICATION -> AppError.Authentication(e.message ?: "Sessão expirada.")
                        ResourceErrorKind.NOT_FOUND -> AppError.NotFound(e.message ?: "Pasta não encontrada.")
                        ResourceErrorKind.RATE_LIMITED -> AppError.RateLimited(e.message ?: "Limite de requisições excedido.")
                        ResourceErrorKind.ACCESS_DENIED -> AppError.AccessDenied(e.message ?: "Acesso negado.")
                        ResourceErrorKind.SERVER -> AppError.Server(e.message ?: "Instabilidade nos servidores.")
                        else -> AppError.Unknown(e.message ?: "Erro ao carregar detalhes da série.")
                    }
                    _uiState.value = SeriesDetailUiState.Error(appError)
                }
            }
        }
    }

    private suspend fun buildSuccessState(
        folderId: String,
        seriesTitle: String,
        driveFiles: List<DriveFile>,
        watchMap: Map<String, WatchList>,
        fetchedAnime: TenraiAnimeService.TenraiAnimeEntry?,
        aniListMeta: AnimeMetadata?,
        arcs: List<TenraiAnimeService.TenraiFranchiseArc>,
        fallbackPosterUrl: String?,
        hasIncompleteDriveListing: Boolean,
        resolvedEpisodes: List<EpisodeDetails> = emptyList()
    ): SeriesDetailUiState.Success {
        val fileModels = driveFiles.map { FilesDataModel.File(it) }
        allSeasonGroups = SeasonEpisodeGrouper.groupFiles(seriesTitle, fileModels)

        val explicitMovie = isExplicitMovieTitle(seriesTitle)
        val isMovie = (aniListMeta?.isMovie == true || explicitMovie) &&
            driveFiles.size <= 2 &&
            allSeasonGroups.size <= 1
        val movieCanonical = if (isMovie) {
            aniListMeta?.titleRomaji ?: aniListMeta?.titleEnglish ?: seriesTitle
        } else null
        val fallbackThumb = aniListMeta?.bannerUrl ?: aniListMeta?.posterUrl ?: fallbackPosterUrl
        val seriesAgeRating = formatRating(aniListMeta?.ageRating)
            ?: formatRating(fetchedAnime?.rating)

        allEpisodeItems = driveFiles.map { file ->
            mapToEpisodeItem(
                file = file,
                watch = watchMap[file.id],
                arcs = arcs,
                isMovie = isMovie,
                canonicalMovieTitle = movieCanonical,
                fallbackThumbnail = fallbackThumb,
                resolvedEpisodes = resolvedEpisodes,
                seriesAgeRating = seriesAgeRating,
                fallbackRuntimeMinutes = aniListMeta?.runtime
            )
        }

        val hub = try {
            franchiseGroupingManager.getOrCreateFranchiseHub(folderId, seriesTitle)
        } catch (_: Exception) { null }

        val members = hub?.members
        val tabs = if (isMovie) {
            emptyList()
        } else if (members != null && members.size > 1) {
            members.mapIndexed { index, member ->
                val count = member.episodeCount ?: 1
                val subtitle = formatCountLabel(count, member.isMovie)
                val isSelected = member.folderId == folderId || (index == 0 && members.none { it.folderId == folderId })
                SeasonTab(
                    id = member.folderId,
                    title = member.arcTitle,
                    subtitle = subtitle,
                    posterUrl = member.posterUrl,
                    bannerUrl = member.bannerUrl,
                    releaseYear = member.releaseYear,
                    score = member.score,
                    scoreSource = member.scoreSource,
                    iconRes = if (member.isMovie) R.drawable.ic_player_24 else R.drawable.ic_play_24,
                    isSelected = isSelected
                )
            }
        } else {
            allSeasonGroups.mapIndexed { index, group ->
                val icon = when {
                    group.id == "season_all" -> R.drawable.ic_list_view_24
                    group.id.startsWith("season_") -> R.drawable.ic_play_24
                    group.id.contains("special", true) -> R.drawable.ic_star_filled_24
                    group.id.contains("music", true) || group.id.contains("opening", true) -> R.drawable.ic_audio_24
                    else -> null
                }
                SeasonTab(
                    id = group.id,
                    title = group.name,
                    subtitle = formatCountLabel(group.fileItems.size, isMovie = false),
                    posterUrl = aniListMeta?.posterUrl ?: fallbackPosterUrl,
                    bannerUrl = aniListMeta?.bannerUrl,
                    releaseYear = aniListMeta?.year,
                    score = aniListMeta?.score,
                    scoreSource = if (aniListMeta?.isAnime == false) "TMDB" else "MAL",
                    iconRes = icon,
                    isSelected = index == 0
                )
            }
        }

        folderEpisodesCache[folderId] = allEpisodeItems

        val activeTab = tabs.firstOrNull { it.isSelected } ?: tabs.firstOrNull()
        val categoryCounts = mutableMapOf<SeriesMediaCategory, Int>()
        allEpisodeItems.forEach { ep ->
            val parsed = EpisodeParser.parse(ep.file.name)
            val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
            val cat = SeriesMediaClassifier.mapCategory(domainCat)
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
        }
        val defaultCategory = if ((categoryCounts[SeriesMediaCategory.EPISODES] ?: 0) > 0) {
            SeriesMediaCategory.EPISODES
        } else {
            categoryCounts.keys.firstOrNull() ?: SeriesMediaCategory.EPISODES
        }
        val initialChips = SeriesMediaClassifier.buildChips(categoryCounts, defaultCategory)
        val tabsWithChips = tabs.map { tab ->
            if (tab.isSelected) {
                tab.copy(chips = initialChips, selectedCategory = defaultCategory)
            } else tab
        }

        val initialEpisodes = if (tabs.isNotEmpty()) {
            allEpisodeItems.filter { ep ->
                val parsed = EpisodeParser.parse(ep.file.name)
                val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
                SeriesMediaClassifier.mapCategory(domainCat) == defaultCategory
            }
        } else {
            getEpisodesForGroup(allSeasonGroups.firstOrNull())
        }

        val continueItem = PrimaryEpisodeSelection.select(allEpisodeItems)
        val continueSubtitle = PrimaryEpisodeSelection.formatContinueSubtitle(continueItem, isMovie)

        val isFranchise = hub != null && hub.members.size > 1
        val knownFranchiseKey = franchiseGroupingManager.resolveFranchiseKey(seriesTitle)
            ?.takeIf(String::isNotBlank)
        val franchiseTitle = when {
            isFranchise && hub != null -> hub.canonicalTitle
            knownFranchiseKey != null -> franchiseGroupingManager.getCanonicalTitle(knownFranchiseKey)
            else -> aniListMeta?.titleRomaji?.takeIf(String::isNotBlank) ?: seriesTitle
        }
        val hubSynopsis = hub?.synopsis
        val effectiveAniListMeta = if (isFranchise && !hubSynopsis.isNullOrBlank()) {
            aniListMeta?.copy(synopsis = hubSynopsis) ?: AnimeMetadata(
                posterUrl = fallbackPosterUrl,
                titleRomaji = franchiseTitle,
                synopsis = hubSynopsis,
                isAnime = true
            )
        } else aniListMeta

        val successState = SeriesDetailUiState.Success(
            folderId = folderId,
            seriesTitle = franchiseTitle,
            animeEntry = fetchedAnime,
            aniListMetadata = effectiveAniListMeta,
            seasonTabs = tabsWithChips,
            currentEpisodes = initialEpisodes,
            continueWatchingItem = continueItem,
            trailerItem = SeriesDetailEpisodeSelection.firstTrailer(allEpisodeItems),
            continueWatchingSubtitle = continueSubtitle,
            isStarred = isFolderStarred,
            isFollowed = isFolderFollowed,
            fallbackPosterUrl = fallbackPosterUrl,
            qualityBadge = SeriesMediaMetadataPolicy.quality(
                driveFiles.map { it.name }, listingComplete = !hasIncompleteDriveListing
            )?.formatQuality(),
            audioBadge = SeriesMediaMetadataPolicy.audio(
                driveFiles.map { it.name }, listingComplete = !hasIncompleteDriveListing
            )?.formatAudio(),
            subtitleBadge = SeriesMediaMetadataPolicy.subtitles(
                driveFiles.map { it.name }, listingComplete = !hasIncompleteDriveListing
            )?.formatSubtitles(),
            hasIncompleteDriveListing = hasIncompleteDriveListing,
            isMovie = isMovie,
            franchiseHub = hub,
            activeMemberFolderId = activeTab?.id ?: folderId,
            isFranchiseHub = isFranchise,
            franchiseMembersCount = hub?.members?.size ?: 0,
            activeArcId = activeTab?.id ?: folderId,
            activeArcTitle = activeTab?.title ?: seriesTitle,
            activeArcYear = activeTab?.releaseYear ?: aniListMeta?.year,
            activeArcScore = if (isFranchise) activeTab?.score else activeTab?.score ?: aniListMeta?.score,
            activeArcScoreSource = activeTab?.scoreSource ?: if (aniListMeta?.isAnime == false) "TMDB" else "MAL",
            categoryChips = initialChips,
            selectedCategory = defaultCategory,
            allCurrentArcEpisodes = allEpisodeItems,
            bannerUrl = aniListMeta?.bannerUrl ?: hub?.bannerUrl
        )

        seriesDetailCache[folderId] = successState
        return successState
    }

    private data class DriveVideoFetch(
        val files: List<DriveFile>,
        val isIncomplete: Boolean,
        val appError: AppError? = null,
        val errorMessage: String? = null
    )

    private suspend fun fetchAllDriveVideos(folderId: String): DriveVideoFetch {
        val directRes = driveRepository.getAllFilesDetailed(
            query = "'$folderId' in parents and trashed = false",
            pageSize = 100
        )
        val directCollection = when (directRes) {
            is Resource.Success -> directRes.data
            is Resource.Error -> directRes.data
            is Resource.Loading -> null
        }
        if (directCollection == null) {
            val errorKind = (directRes as? Resource.Error)?.errorKind
            val message = (directRes as? Resource.Error)?.message
                ?: "Não foi possível carregar os arquivos desta pasta."
            val appError = when (errorKind) {
                ResourceErrorKind.NETWORK -> AppError.Network(message)
                ResourceErrorKind.AUTHENTICATION -> AppError.Authentication(message)
                ResourceErrorKind.NOT_FOUND -> AppError.NotFound(message)
                ResourceErrorKind.RATE_LIMITED -> AppError.RateLimited(message)
                ResourceErrorKind.ACCESS_DENIED -> AppError.AccessDenied(message)
                ResourceErrorKind.SERVER -> AppError.Server(message)
                else -> AppError.Unknown(message)
            }

            // Offline-first fallback: check if local catalog cache has files before failing
            val cachedEntries = catalogRepository?.getChildren(folderId).orEmpty()
            val cachedVideos = cachedEntries
                .filter { entry ->
                    val lower = entry.name.lowercase()
                    val isVideoMime = entry.mimeType.startsWith("video/") || entry.mimeType == "application/x-matroska"
                    val isVideoExt = lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".avi")
                    isVideoMime || isVideoExt
                }
                .map { entry ->
                    DriveFile(
                        id = entry.id,
                        name = entry.name,
                        size = null,
                        mimeType = entry.mimeType,
                        iconLink = null,
                        thumbnailLink = entry.posterUrl,
                        posterUrl = entry.posterUrl,
                        shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
                        starred = zechs.drive.stream.data.model.Starred.UNSTARRED
                    )
                }

            if (cachedVideos.isNotEmpty()) {
                return DriveVideoFetch(cachedVideos, isIncomplete = true)
            }

            return DriveVideoFetch(emptyList(), isIncomplete = true, appError = appError, errorMessage = message)
        }

        var isIncomplete = !directCollection.isComplete || directRes is Resource.Error
        val items = directCollection.files.map { it.toDriveFile() }
        val videoFiles = items.filter { it.isVideoFile || (it.isShortcut && it.isShortcutVideo) }.toMutableList()
        val subfolders = items.filter { it.isFolder || it.isShortcutFolder }

        // If folder has subfolders (e.g. Kajitsu, Meikyuu, Rakuen) and few direct videos, fetch subfolders
        if (subfolders.isNotEmpty() && videoFiles.size <= 2) {
            val subResults = coroutineScope {
                subfolders.map { sub ->
                    async {
                        val subId = if (sub.isShortcut) sub.shortcutDetails.targetId ?: sub.id else sub.id
                        driveRepository.getAllFilesDetailed(
                            query = "'$subId' in parents and trashed = false",
                            pageSize = 100
                        )
                    }
                }.awaitAll()
            }
            for (subRes in subResults) {
                val subCollection = when (subRes) {
                    is Resource.Success -> subRes.data
                    is Resource.Error -> subRes.data
                    is Resource.Loading -> null
                }
                if (subCollection == null) {
                    isIncomplete = true
                    continue
                }
                if (!subCollection.isComplete || subRes is Resource.Error) isIncomplete = true
                if (subCollection.files.isNotEmpty()) {
                    val subVideos = subCollection.files.map { it.toDriveFile() }
                        .filter { it.isVideoFile || (it.isShortcut && it.isShortcutVideo) }
                    videoFiles.addAll(subVideos)
                }
            }
        }

        val deduplicatedVideos = EpisodeDeduplicationPolicy.removeDuplicates(
            videoFiles.map { file ->
                EpisodeDeduplicationPolicy.Candidate(
                    id = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id,
                    title = file.name,
                    sizeBytes = file.size,
                    durationMillis = file.durationMillis,
                    checksum = file.md5Checksum
                )
            }
        ).mapNotNull { candidate ->
            videoFiles.firstOrNull { file ->
                (if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id) == candidate.id &&
                    file.name == candidate.title
            }
        }

        if (deduplicatedVideos.isNotEmpty()) {
            try {
                catalogRepository?.cacheFolderContents(
                    parentId = folderId,
                    entries = deduplicatedVideos.map {
                        CatalogEntry(
                            id = it.id,
                            name = it.name,
                            mimeType = it.mimeType,
                            parentId = folderId,
                            posterUrl = it.thumbnailLink ?: it.posterUrl
                        )
                    }
                )
            } catch (_: Exception) {}
        }

        return DriveVideoFetch(deduplicatedVideos.sortedWith { a, b ->
            SeasonEpisodeGrouper.compareItems(
                a.name, EpisodeParser.parse(a.name),
                b.name, EpisodeParser.parse(b.name)
            )
        }, isIncomplete = isIncomplete)
    }

    private fun isExplicitMovieTitle(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("kizumonogatari") || lower.contains("movie") || lower.contains("filme")
    }

    private fun mapToEpisodeItem(
        file: DriveFile,
        watch: WatchList?,
        arcs: List<TenraiAnimeService.TenraiFranchiseArc>,
        isMovie: Boolean = false,
        canonicalMovieTitle: String? = null,
        fallbackThumbnail: String? = null,
        resolvedEpisodes: List<EpisodeDetails> = emptyList(),
        seriesAgeRating: String? = null,
        fallbackRuntimeMinutes: Int? = null
    ): SeriesEpisodeItem {
        val parsed = EpisodeParser.parse(file.name)
        // OP/ED and other extras can contain a number (for example OP4a), but that
        // number is a variant index, not a regular episode and must never be mapped
        // to a franchise episode title or shown with an EP badge.
        val epNum = parsed.episode?.toInt()?.takeUnless { parsed.isSpecial || isMovie }

        // Match canonical episode title and synopsis from franchise arcs
        var canonName: String? = null
        var arcSynopsis: String? = null
        if (isMovie) {
            canonName = canonicalMovieTitle
        } else if (epNum != null) {
            for (arc in arcs) {
                if (arc.episodes.containsKey(epNum)) {
                    canonName = arc.episodes[epNum]
                    arcSynopsis = arc.episodeSynopses[epNum]
                    break
                }
            }
        }

        val epMeta = if (isMovie) null else {
            episodeMetadataResolver.findEpisode(
                episodes = resolvedEpisodes,
                season = parsed.season?.toInt() ?: 1,
                episodeNumber = epNum,
                canonicalName = canonName
            )
        }

        val resolvedTitle = epMeta?.name ?: canonName
        val epSynopsis = epMeta?.synopsis ?: arcSynopsis ?: file.description
        val epThumbnail = epMeta?.previewImageUrl ?: fallbackThumbnail

        val presentation = presentSeriesEpisode(file.name, resolvedTitle, isMovie = isMovie)

        val progress = watch?.watchProgress() ?: 0
        val durationMillis = watch?.totalDuration?.takeIf { it > 0 } ?: file.durationMillis ?: 0L
        val durationText = if (durationMillis > 0) {
            formatDurationMillis(durationMillis)
        } else if (fallbackRuntimeMinutes != null && fallbackRuntimeMinutes > 0) {
            formatDurationMinutes(fallbackRuntimeMinutes)
        } else {
            ""
        }

        val audioText = SeriesMediaMetadataPolicy.audioLabelForFile(file.name).orEmpty()
        val badge = if (isMovie) "FILME" else null
        val resolvedAge = formatRating(epMeta?.ageRating) ?: formatRating(seriesAgeRating) ?: if (isMovie) null else "14+"

        return SeriesEpisodeItem(
            file = file,
            displayTitle = presentation.displayTitle,
            episodeNumber = if (isMovie) null else presentation.episodeNumber,
            durationText = durationText,
            audioBadgeText = audioText,
            progressPercent = progress,
            watchedDuration = watch?.watchedDuration ?: 0L,
            totalDuration = durationMillis,
            badgeText = badge,
            fallbackThumbnailUrl = epThumbnail,
            synopsis = epSynopsis,
            ageRating = resolvedAge
        )
    }

    fun selectSeasonTab(tab: SeasonTab) {
        observeDownloads(tab.id)
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return

        val hub = currentState.franchiseHub
        if (hub != null && hub.members.size > 1) {
            val cachedEps = folderEpisodesCache[tab.id]
            if (cachedEps != null) {
                val categoryCounts = mutableMapOf<SeriesMediaCategory, Int>()
                cachedEps.forEach { ep ->
                    val parsed = EpisodeParser.parse(ep.file.name)
                    val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
                    val cat = SeriesMediaClassifier.mapCategory(domainCat)
                    categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
                }
                val defaultCategory = if ((categoryCounts[SeriesMediaCategory.EPISODES] ?: 0) > 0) {
                    SeriesMediaCategory.EPISODES
                } else {
                    categoryCounts.keys.firstOrNull() ?: SeriesMediaCategory.EPISODES
                }
                val chips = SeriesMediaClassifier.buildChips(categoryCounts, defaultCategory)
                val filtered = cachedEps.filter { ep ->
                    val parsed = EpisodeParser.parse(ep.file.name)
                    val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
                    SeriesMediaClassifier.mapCategory(domainCat) == defaultCategory
                }
                val updatedTabs = currentState.seasonTabs.map {
                    if (it.id == tab.id) {
                        it.copy(isSelected = true, chips = chips, selectedCategory = defaultCategory)
                    } else {
                        it.copy(isSelected = false)
                    }
                }
                selectedSeasonPosition = updatedTabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)

                _uiState.value = currentState.copy(
                    seasonTabs = updatedTabs,
                    currentEpisodes = filtered,
                    allCurrentArcEpisodes = cachedEps,
                    activeMemberFolderId = tab.id,
                    activeArcId = tab.id,
                    activeArcTitle = tab.title,
                    activeArcYear = tab.releaseYear ?: currentState.activeArcYear,
                    activeArcScore = if (currentState.isFranchiseHub) tab.score else tab.score ?: currentState.activeArcScore,
                    activeArcScoreSource = tab.scoreSource,
                    categoryChips = chips,
                    selectedCategory = defaultCategory
                )
            } else {
                val updatedTabs = currentState.seasonTabs.map {
                    it.copy(isSelected = it.id == tab.id)
                }
                selectedSeasonPosition = updatedTabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)
                _uiState.value = currentState.copy(
                    seasonTabs = updatedTabs,
                    activeMemberFolderId = tab.id,
                    activeArcId = tab.id,
                    activeArcTitle = tab.title,
                    activeArcYear = tab.releaseYear ?: currentState.activeArcYear,
                    activeArcScore = if (currentState.isFranchiseHub) tab.score else tab.score ?: currentState.activeArcScore,
                    activeArcScoreSource = tab.scoreSource
                )
                viewModelScope.launch(Dispatchers.IO) {
                    val driveResult = fetchAllDriveVideos(tab.id)
                    val videoIds = driveResult.files.map { it.id }
                    val watchList = watchListRepository.getWatches(videoIds)
                    val watchMap = watchList.associateBy { it.videoId }
                    val resolved = try {
                        episodeMetadataResolver.fetchShowEpisodes(tab.title)
                    } catch (_: Exception) {
                        currentResolvedEpisodes
                    }.ifEmpty { currentResolvedEpisodes }
                    val isMovieTab = tab.subtitle?.contains("Filme", true) == true
                    val arcMeta = folderMetadataRepository.getMetadata(tab.id)?.toAnimeMetadata()
                        ?: try {
                            animePosterResolver.resolveMetadata(tab.title)
                        } catch (_: Exception) { null }
                    val arcRating = formatRating(arcMeta?.ageRating)
                        ?: formatRating(currentState.aniListMetadata?.ageRating)
                        ?: formatRating(animeEntry?.rating)
                    val fallbackThumb = arcMeta?.bannerUrl
                        ?: arcMeta?.posterUrl
                        ?: currentState.aniListMetadata?.bannerUrl
                        ?: currentState.fallbackPosterUrl
                    val newEpisodes = driveResult.files.map { file ->
                        mapToEpisodeItem(
                            file = file,
                            watch = watchMap[file.id],
                            arcs = franchiseArcs,
                            isMovie = isMovieTab,
                            canonicalMovieTitle = if (isMovieTab) tab.title else null,
                            fallbackThumbnail = fallbackThumb,
                            resolvedEpisodes = resolved,
                            seriesAgeRating = arcRating,
                            fallbackRuntimeMinutes = arcMeta?.runtime ?: currentState.aniListMetadata?.runtime
                        )
                    }
                    folderEpisodesCache[tab.id] = newEpisodes

                    val categoryCounts = mutableMapOf<SeriesMediaCategory, Int>()
                    newEpisodes.forEach { ep ->
                        val parsed = EpisodeParser.parse(ep.file.name)
                        val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
                        val cat = SeriesMediaClassifier.mapCategory(domainCat)
                        categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
                    }
                    val defaultCategory = if ((categoryCounts[SeriesMediaCategory.EPISODES] ?: 0) > 0) {
                        SeriesMediaCategory.EPISODES
                    } else {
                        categoryCounts.keys.firstOrNull() ?: SeriesMediaCategory.EPISODES
                    }
                    val chips = SeriesMediaClassifier.buildChips(categoryCounts, defaultCategory)
                    val filtered = newEpisodes.filter { ep ->
                        val parsed = EpisodeParser.parse(ep.file.name)
                        val domainCat = EpisodeCategorizer.categorize(ep.file.name, parsed)
                        SeriesMediaClassifier.mapCategory(domainCat) == defaultCategory
                    }

                    withContext(Dispatchers.Main) {
                        val finalTabs = currentState.seasonTabs.map {
                            if (it.id == tab.id) {
                                it.copy(isSelected = true, chips = chips, selectedCategory = defaultCategory)
                            } else {
                                it.copy(isSelected = false)
                            }
                        }
                        _uiState.value = currentState.copy(
                            seasonTabs = finalTabs,
                            currentEpisodes = filtered,
                            allCurrentArcEpisodes = newEpisodes,
                            activeMemberFolderId = tab.id,
                            activeArcId = tab.id,
                            activeArcTitle = tab.title,
                            activeArcYear = tab.releaseYear ?: currentState.activeArcYear,
                            activeArcScore = if (currentState.isFranchiseHub) tab.score else tab.score ?: currentState.activeArcScore,
                            activeArcScoreSource = tab.scoreSource,
                            categoryChips = chips,
                            selectedCategory = defaultCategory
                        )
                    }
                }
            }
        } else {
            val targetGroup = allSeasonGroups.firstOrNull { it.id == tab.id }
            val filteredEpisodes = getEpisodesForGroup(targetGroup)

            val updatedTabs = currentState.seasonTabs.map {
                it.copy(isSelected = it.id == tab.id)
            }
            selectedSeasonPosition = updatedTabs.indexOfFirst { it.isSelected }.coerceAtLeast(0)

            _uiState.value = currentState.copy(
                seasonTabs = updatedTabs,
                currentEpisodes = filteredEpisodes,
                activeArcId = tab.id,
                activeArcTitle = tab.title,
                activeArcYear = tab.releaseYear ?: currentState.activeArcYear,
                activeArcScore = if (currentState.isFranchiseHub) tab.score else tab.score ?: currentState.activeArcScore,
                activeArcScoreSource = tab.scoreSource
            )
        }
    }

    private fun getEpisodesForGroup(group: SeasonGroup?): List<SeriesEpisodeItem> {
        if (group == null || group.id == "season_all") {
            val groupFileIds = group?.fileItems.orEmpty().mapNotNull {
                (it as? FilesDataModel.File)?.driveFile?.id
            }.toSet()
            return SeriesDetailEpisodeSelection.allEpisodes(allEpisodeItems, groupFileIds)
        }
        val groupFileIds = group.fileItems.mapNotNull {
            (it as? FilesDataModel.File)?.driveFile?.id
        }.toSet()

        return allEpisodeItems.filter { groupFileIds.contains(it.file.id) }
    }

    fun toggleStar() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return@launch
        val newStarred = !isFolderStarred
        isFolderStarred = newStarred
        try {
            favoriteRepository.set(
                folderId = currentFolderId,
                folderName = currentState.seriesTitle,
                isFavorite = newStarred
            )
            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(isStarred = newStarred)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error toggling local favorite", e)
        }
    }

    fun toggleFollow() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return@launch
        val newFollowed = !isFolderFollowed
        isFolderFollowed = newFollowed
        try {
            if (newFollowed) {
                followedFolderRepository.follow(currentFolderId, currentState.seriesTitle)
            } else {
                followedFolderRepository.unfollow(currentFolderId)
            }
            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(isFollowed = newFollowed)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            isFolderFollowed = !newFollowed
            AppLog.e(TAG, "Error toggling followed folder", e)
        }
    }

    fun addToQueue(file: DriveFile) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (!watchQueueRepository.isInQueue(file.id)) {
                watchQueueRepository.enqueue(
                    fileId = file.id,
                    name = file.name,
                    posterUrl = file.thumbnailLarge ?: file.thumbnailLink ?: file.posterUrl
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error adding episode to queue", e)
        }
    }

    fun markSeasonWatched() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return@launch

        currentState.currentEpisodes.forEach { item ->
            val resolvedDuration = PlaybackProgressPolicy.resolveWatchedDuration(item.totalDuration)
            val watch = WatchList(
                name = item.file.name,
                videoId = item.file.id,
                watchedDuration = resolvedDuration,
                totalDuration = resolvedDuration,
                thumbnailLink = item.file.thumbnailLarge ?: item.file.thumbnailLink
            )
            watchListRepository.insertWatch(watch)
        }

        // Reload to update progress bars
        loadSeriesDetails(currentFolderId, currentSeriesTitle)
    }

    fun setEpisodeWatched(file: DriveFile, watched: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return@launch
        try {
            val existingEpisode = allEpisodeItems.firstOrNull { it.file.id == file.id }
            val resolvedDuration = PlaybackProgressPolicy.resolveWatchedDuration(existingEpisode?.totalDuration)

            if (watched) {
                val watch = WatchList(
                    name = file.name,
                    videoId = file.id,
                    watchedDuration = resolvedDuration,
                    totalDuration = resolvedDuration,
                    thumbnailLink = file.thumbnailLarge ?: file.thumbnailLink ?: file.posterUrl
                )
                watchListRepository.insertWatch(watch)
            } else {
                watchListRepository.deleteWatchByVideoId(file.id)
            }

            val updatedAll = allEpisodeItems.map { ep ->
                if (ep.file.id == file.id) {
                    if (watched) {
                        ep.copy(
                            progressPercent = 100,
                            watchedDuration = resolvedDuration,
                            totalDuration = resolvedDuration
                        )
                    } else {
                        ep.copy(progressPercent = 0, watchedDuration = 0L, totalDuration = 0L)
                    }
                } else ep
            }
            allEpisodeItems = updatedAll

            val selectedTabId = currentState.seasonTabs.firstOrNull { it.isSelected }?.id
            val targetGroup = allSeasonGroups.firstOrNull { it.id == selectedTabId }
            val filtered = getEpisodesForGroup(targetGroup)
            val continueItem = PrimaryEpisodeSelection.select(allEpisodeItems)
            val continueSubtitle = PrimaryEpisodeSelection.formatContinueSubtitle(continueItem, currentState.isMovie)

            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(
                    currentEpisodes = filtered,
                    continueWatchingItem = continueItem,
                    continueWatchingSubtitle = continueSubtitle
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Error setting episode watched state", e)
        }
    }

    fun resetEpisodeProgress(file: DriveFile) = viewModelScope.launch(Dispatchers.IO) {
        setEpisodeWatched(file, false)
    }

    private var downloadObserverJob: kotlinx.coroutines.Job? = null

    fun observeDownloads(folderId: String) {
        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch(Dispatchers.Main) {
            downloadRepository?.getDownloadsForFolderFlow(folderId)?.collect { downloads ->
                updateEpisodesWithDownloads(downloads)
            }
        }
    }

    private fun updateEpisodesWithDownloads(downloads: List<zechs.drive.stream.data.model.DownloadItem>) {
        val downloadMap = downloads.associateBy { it.fileId }

        allEpisodeItems = allEpisodeItems.map { ep ->
            val dl = downloadMap[ep.file.id]
            if (dl != null) {
                ep.copy(
                    downloadStatus = dl.downloadStatus,
                    downloadProgress = dl.progressPercent
                )
            } else {
                ep.copy(
                    downloadStatus = null,
                    downloadProgress = 0
                )
            }
        }

        val currentSuccess = _uiState.value as? SeriesDetailUiState.Success ?: return
        val updatedCurrentEpisodes = currentSuccess.currentEpisodes.map { ep ->
            val dl = downloadMap[ep.file.id]
            if (dl != null) {
                ep.copy(
                    downloadStatus = dl.downloadStatus,
                    downloadProgress = dl.progressPercent
                )
            } else {
                ep.copy(
                    downloadStatus = null,
                    downloadProgress = 0
                )
            }
        }
        _uiState.value = currentSuccess.copy(currentEpisodes = updatedCurrentEpisodes)
    }

    fun getCompletedDownloadPath(fileId: String): String? {
        val download = kotlinx.coroutines.runBlocking { downloadRepository?.getDownload(fileId) }
        return if (download != null && download.isComplete && java.io.File(download.localPath).exists()) {
            download.localPath
        } else null
    }

    fun isEpisodeDownloaded(fileId: String): Boolean {
        return getCompletedDownloadPath(fileId) != null
    }

    fun isEpisodeDownloading(fileId: String): Boolean {
        val download = kotlinx.coroutines.runBlocking { downloadRepository?.getDownload(fileId) }
        return download?.downloadStatus == zechs.drive.stream.data.model.DownloadStatus.DOWNLOADING ||
            download?.downloadStatus == zechs.drive.stream.data.model.DownloadStatus.QUEUED
    }

    fun startDownload(episode: SeriesEpisodeItem, seriesTitle: String, folderId: String) {
        // Optimistic UI update: immediate visual feedback on click (Netflix style)
        val currentSuccess = _uiState.value as? SeriesDetailUiState.Success
        if (currentSuccess != null) {
            val updated = currentSuccess.currentEpisodes.map { ep ->
                if (ep.file.id == episode.file.id) {
                    ep.copy(
                        downloadStatus = zechs.drive.stream.data.model.DownloadStatus.QUEUED,
                        downloadProgress = 0
                    )
                } else ep
            }
            _uiState.value = currentSuccess.copy(currentEpisodes = updated)
        }

        viewModelScope.launch {
            downloadRepository?.enqueueDownload(
                fileId = episode.file.id,
                folderId = folderId,
                seriesTitle = seriesTitle,
                name = episode.displayTitle,
                seasonNumber = null,
                episodeNumber = episode.episodeNumber,
                thumbnailUrl = episode.file.thumbnailLarge ?: episode.file.thumbnailLink ?: episode.fallbackThumbnailUrl,
                expectedSize = episode.file.size ?: 0L
            )
        }
    }

    fun deleteDownload(fileId: String) {
        // Optimistic UI update
        val currentSuccess = _uiState.value as? SeriesDetailUiState.Success
        if (currentSuccess != null) {
            val updated = currentSuccess.currentEpisodes.map { ep ->
                if (ep.file.id == fileId) {
                    ep.copy(
                        downloadStatus = null,
                        downloadProgress = 0
                    )
                } else ep
            }
            _uiState.value = currentSuccess.copy(currentEpisodes = updated)
        }

        viewModelScope.launch {
            downloadRepository?.deleteDownload(fileId)
        }
    }

}
