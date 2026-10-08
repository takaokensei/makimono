package zechs.drive.stream.ui.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.remote.AnimeMetadata
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.series.adapter.SeasonTab
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.PlaybackProgressPolicy
import zechs.drive.stream.utils.SeasonEpisodeGrouper
import zechs.drive.stream.utils.SeasonGroup
import zechs.drive.stream.utils.state.Resource
import java.util.Locale
import javax.inject.Inject

sealed class SeriesDetailUiState {
    data class Loading(
        val folderId: String,
        val seriesTitle: String,
        val posterUrl: String? = null
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
        val isMovie: Boolean = false
    ) : SeriesDetailUiState()
    data class Error(val message: String) : SeriesDetailUiState()
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
    private val watchQueueRepository: WatchQueueRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SeriesDetailVM"
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

    fun loadSeriesDetails(folderId: String, seriesTitle: String, initialPoster: String? = null) {
        currentFolderId = folderId
        currentSeriesTitle = seriesTitle
        _uiState.value = SeriesDetailUiState.Loading(folderId, seriesTitle, initialPoster)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Record folder opened for Home screen Recents / Continue
                folderMetadataRepository.recordFolderOpened(folderId, seriesTitle)

                // 1. Concurrently fetch Tenrai metadata & AniList high-res metadata
                val tenraiDeferred = async {
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
                        Log.w(TAG, "Tenrai fetch error: ${e.message}")
                        null to emptyList()
                    }
                }

                val aniListDeferred = async {
                    try {
                        animePosterResolver.resolveMetadata(seriesTitle)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "AniList metadata fetch error: ${e.message}")
                        null
                    }
                }

                // 2. Fetch Drive files in folder
                val driveResult = fetchAllDriveVideos(folderId)
                val driveFiles = driveResult.files
                if (driveFiles.isEmpty() && driveResult.errorMessage != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = SeriesDetailUiState.Error(driveResult.errorMessage)
                    }
                    return@launch
                }
                if (driveFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = SeriesDetailUiState.Error("Nenhum arquivo de vídeo encontrado na pasta.")
                    }
                    return@launch
                }

                // Publish the Drive-backed shell immediately. Metadata enrichment can
                // arrive later without keeping the detail screen blank.
                val cachedPoster = initialPoster
                    ?: folderMetadataRepository.getMetadata(folderId)?.posterUrl
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
                    aniListMeta = null,
                    arcs = emptyList(),
                    fallbackPosterUrl = cachedPoster,
                    hasIncompleteDriveListing = driveResult.isIncomplete
                )
                withContext(Dispatchers.Main) {
                    _uiState.value = shellState
                }

                val (fetchedAnime, fetchedArcs) = tenraiDeferred.await()
                val aniListMeta = aniListDeferred.await()

                val isMovie = aniListMeta?.isMovie == true
                val isNonAnime = aniListMeta?.isAnime == false
                val mediaType = aniListMeta?.mediaType

                // If this is a movie or non-anime from TMDB, verify fetchedAnime similarity before trusting it
                val cleanTitle = AnimePosterResolver.cleanAnimeTitle(seriesTitle)
                val isLegitAnime = fetchedAnime != null && !isNonAnime && AnimePosterResolver.calculateSimilarity(cleanTitle, fetchedAnime.title) >= 0.45
                val validAnime = if (isLegitAnime) fetchedAnime else null
                val validArcs = if (isLegitAnime) fetchedArcs else emptyList()

                animeEntry = validAnime
                franchiseArcs = validArcs

                // Prioritize AniList high-res poster (extraLarge 460x650), then Tenrai/MAL
                val finalPoster = aniListMeta?.posterUrl
                    ?: validAnime?.imageUrl
                    ?: initialPoster
                    ?: cachedPoster
                if (!finalPoster.isNullOrBlank() || isMovie) {
                    folderMetadataRepository.updateMetadataDetails(
                        folderId = folderId,
                        folderName = seriesTitle,
                        posterUrl = finalPoster ?: "",
                        isMovie = isMovie,
                        mediaType = mediaType
                    )
                }

                val enrichedState = buildSuccessState(
                    folderId = folderId,
                    seriesTitle = seriesTitle,
                    driveFiles = driveFiles,
                    watchMap = watchMap,
                    fetchedAnime = validAnime,
                    aniListMeta = aniListMeta,
                    arcs = validArcs,
                    fallbackPosterUrl = finalPoster,
                    hasIncompleteDriveListing = driveResult.isIncomplete
                )
                withContext(Dispatchers.Main) {
                    val previous = _uiState.value as? SeriesDetailUiState.Success
                    val selectedId = previous?.seasonTabs?.firstOrNull { it.isSelected }?.id
                    if (selectedId == null) {
                        _uiState.value = enrichedState
                    } else {
                        val selectedGroup = allSeasonGroups.firstOrNull { it.id == selectedId }
                        _uiState.value = enrichedState.copy(
                            seasonTabs = enrichedState.seasonTabs.map { it.copy(isSelected = it.id == selectedId) },
                            currentEpisodes = getEpisodesForGroup(selectedGroup)
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error loading series details", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = SeriesDetailUiState.Error(e.message ?: "Erro ao carregar detalhes da série.")
                }
            }
        }
    }

    private fun buildSuccessState(
        folderId: String,
        seriesTitle: String,
        driveFiles: List<DriveFile>,
        watchMap: Map<String, WatchList>,
        fetchedAnime: TenraiAnimeService.TenraiAnimeEntry?,
        aniListMeta: AnimeMetadata?,
        arcs: List<TenraiAnimeService.TenraiFranchiseArc>,
        fallbackPosterUrl: String?,
        hasIncompleteDriveListing: Boolean
    ): SeriesDetailUiState.Success {
        val isMovie = aniListMeta?.isMovie == true || (driveFiles.size == 1 && driveFiles.none { it.name.contains("Ep", ignoreCase = true) || it.name.contains("E0", ignoreCase = true) })
        val movieCanonical = if (isMovie) (aniListMeta?.titleRomaji ?: aniListMeta?.titleEnglish ?: seriesTitle) else null
        val fallbackThumb = if (isMovie) (aniListMeta?.bannerUrl ?: aniListMeta?.posterUrl ?: fallbackPosterUrl) else null
        allEpisodeItems = driveFiles.map { file ->
            mapToEpisodeItem(
                file = file,
                watch = watchMap[file.id],
                arcs = arcs,
                isMovie = isMovie,
                canonicalMovieTitle = movieCanonical,
                fallbackThumbnail = fallbackThumb
            )
        }

        val fileModels = driveFiles.map { FilesDataModel.File(it) }
        allSeasonGroups = SeasonEpisodeGrouper.groupFiles(seriesTitle, fileModels)
        val tabs = if (isMovie) {
            allSeasonGroups.mapIndexed { index, group ->
                val (tabTitle, icon) = when {
                    group.id == "season_all" || group.id.startsWith("season_") -> "Filme" to R.drawable.ic_movie_24
                    group.id.contains("special", true) -> group.name to R.drawable.ic_star_filled_24
                    group.id.contains("music", true) || group.id.contains("opening", true) -> group.name to R.drawable.ic_audio_24
                    else -> group.name to (if (index == 0) R.drawable.ic_movie_24 else null)
                }
                SeasonTab(
                    id = group.id,
                    title = tabTitle,
                    iconRes = icon,
                    isSelected = index == 0
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
                    iconRes = icon,
                    isSelected = index == 0
                )
            }
        }

        val continueItem = PrimaryEpisodeSelection.select(allEpisodeItems)
        val continueSubtitle = continueItem?.let { item ->
            val epPrefix = if (isMovie) "Filme Completo" else (item.episodeNumber?.let { "Ep. $it" } ?: "Ep. 01")
            if (item.watchedDuration > 0 && item.totalDuration > item.watchedDuration) {
                val remainingSec = (item.totalDuration - item.watchedDuration) / 1000
                "$epPrefix • ${String.format(Locale.ROOT, "%02d:%02d", remainingSec / 60, remainingSec % 60)} restante"
            } else {
                "$epPrefix • Iniciar reprodução"
            }
        } ?: if (isMovie) "Filme Completo • Iniciar reprodução" else "Ep. 01 • Iniciar reprodução"

        return SeriesDetailUiState.Success(
            folderId = folderId,
            seriesTitle = seriesTitle,
            animeEntry = fetchedAnime,
            aniListMetadata = aniListMeta,
            seasonTabs = tabs,
            currentEpisodes = getEpisodesForGroup(allSeasonGroups.firstOrNull()),
            continueWatchingItem = continueItem,
            trailerItem = SeriesDetailEpisodeSelection.firstTrailer(allEpisodeItems),
            continueWatchingSubtitle = continueSubtitle,
            isStarred = isFolderStarred,
            isFollowed = isFolderFollowed,
            fallbackPosterUrl = fallbackPosterUrl,
            qualityBadge = SeriesMediaMetadataPolicy.quality(
                driveFiles.map { it.name }, listingComplete = !hasIncompleteDriveListing
            ),
            audioBadge = SeriesMediaMetadataPolicy.audio(
                driveFiles.map { it.name }, listingComplete = !hasIncompleteDriveListing
            ),
            subtitleBadge = SeriesMediaMetadataPolicy.subtitles(
                driveFiles.map { it.name }, listingComplete = !hasIncompleteDriveListing
            ),
            hasIncompleteDriveListing = hasIncompleteDriveListing,
            isMovie = isMovie
        )
    }

    private data class DriveVideoFetch(
        val files: List<DriveFile>,
        val isIncomplete: Boolean,
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
            val message = (directRes as? Resource.Error)?.message
                ?: "Não foi possível carregar os arquivos desta pasta."
            return DriveVideoFetch(emptyList(), isIncomplete = true, errorMessage = message)
        }

        var isIncomplete = !directCollection.isComplete || directRes is Resource.Error
        val items = directCollection.files.map { it.toDriveFile() }
        val videoFiles = items.filter { it.isVideoFile || (it.isShortcut && it.isShortcutVideo) }.toMutableList()
        val subfolders = items.filter { it.isFolder || it.isShortcutFolder }

        // If folder has subfolders (e.g. Kajitsu, Meikyuu, Rakuen) and few direct videos, fetch subfolders
        if (subfolders.isNotEmpty() && videoFiles.size <= 2) {
            for (sub in subfolders) {
                val subId = if (sub.isShortcut) sub.shortcutDetails.targetId ?: sub.id else sub.id
                val subRes = driveRepository.getAllFilesDetailed(
                    query = "'$subId' in parents and trashed = false",
                    pageSize = 100
                )
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

        return DriveVideoFetch(videoFiles.sortedWith { a, b ->
            SeasonEpisodeGrouper.compareItems(
                a.name, EpisodeParser.parse(a.name),
                b.name, EpisodeParser.parse(b.name)
            )
        }, isIncomplete = isIncomplete)
    }

    private fun mapToEpisodeItem(
        file: DriveFile,
        watch: WatchList?,
        arcs: List<TenraiAnimeService.TenraiFranchiseArc>,
        isMovie: Boolean = false,
        canonicalMovieTitle: String? = null,
        fallbackThumbnail: String? = null
    ): SeriesEpisodeItem {
        val parsed = EpisodeParser.parse(file.name)
        // OP/ED and other extras can contain a number (for example OP4a), but that
        // number is a variant index, not a regular episode and must never be mapped
        // to a franchise episode title or shown with an EP badge.
        val epNum = parsed.episode?.toInt()?.takeUnless { parsed.isSpecial || isMovie }

        // Match canonical episode title from franchise arcs
        var canonName: String? = null
        if (isMovie) {
            canonName = canonicalMovieTitle
        } else if (epNum != null) {
            for (arc in arcs) {
                if (arc.episodes.containsKey(epNum)) {
                    canonName = arc.episodes[epNum]
                    break
                }
            }
        }

        val presentation = presentSeriesEpisode(file.name, canonName, isMovie = isMovie)

        val progress = watch?.watchProgress() ?: 0
        val durationText = if (watch != null && watch.totalDuration > 0) {
            val totalMin = (watch.totalDuration / 1000) / 60
            "$totalMin min"
        } else {
            ""
        }

        val audioText = SeriesMediaMetadataPolicy.audioLabelForFile(file.name).orEmpty()
        val badge = if (isMovie) "FILME" else null

        return SeriesEpisodeItem(
            file = file,
            displayTitle = presentation.displayTitle,
            episodeNumber = if (isMovie) null else presentation.episodeNumber,
            durationText = durationText,
            audioBadgeText = audioText,
            progressPercent = progress,
            watchedDuration = watch?.watchedDuration ?: 0L,
            totalDuration = watch?.totalDuration ?: 0L,
            badgeText = badge,
            fallbackThumbnailUrl = fallbackThumbnail
        )
    }

    fun selectSeasonTab(tab: SeasonTab) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return

        val updatedTabs = currentState.seasonTabs.map {
            it.copy(isSelected = it.id == tab.id)
        }

        val targetGroup = allSeasonGroups.firstOrNull { it.id == tab.id }
        val filteredEpisodes = getEpisodesForGroup(targetGroup)

        _uiState.value = currentState.copy(
            seasonTabs = updatedTabs,
            currentEpisodes = filteredEpisodes
        )
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
            Log.e(TAG, "Error toggling local favorite", e)
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
            Log.e(TAG, "Error toggling followed folder", e)
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
            Log.e(TAG, "Error adding episode to queue", e)
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
            val continueSubtitle = continueItem?.let { item ->
                val epText = item.episodeNumber?.let { "Ep. $it" } ?: "Ep. 01"
                if (item.watchedDuration > 0 && item.totalDuration > item.watchedDuration) {
                    val remainingSec = (item.totalDuration - item.watchedDuration) / 1000
                    "$epText • ${String.format(Locale.ROOT, "%02d:%02d", remainingSec / 60, remainingSec % 60)} restante"
                } else {
                    "$epText • Iniciar reprodução"
                }
            } ?: "Ep. 01 • Iniciar reprodução"

            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(
                    currentEpisodes = filtered,
                    continueWatchingItem = continueItem,
                    continueWatchingSubtitle = continueSubtitle
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error setting episode watched state", e)
        }
    }

    fun resetEpisodeProgress(file: DriveFile) = viewModelScope.launch(Dispatchers.IO) {
        setEpisodeWatched(file, false)
    }

}
