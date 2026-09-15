package zechs.drive.stream.ui.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.data.local.WatchListDao
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.remote.AnimeMetadata
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.series.adapter.SeasonTab
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.utils.EpisodeParser
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
        val continueWatchingSubtitle: String,
        val isStarred: Boolean,
        val fallbackPosterUrl: String? = null,
        val qualityBadge: String = "1080p",
        val audioBadge: String = "Dual Áudio PT-BR / JA",
        val subtitleBadge: String = "Multi Subs"
    ) : SeriesDetailUiState()
    data class Error(val message: String) : SeriesDetailUiState()
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val tenraiAnimeService: TenraiAnimeService,
    private val watchListDao: WatchListDao,
    private val folderMetadataRepository: FolderMetadataRepository,
    private val animePosterResolver: AnimePosterResolver
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
                        Log.w(TAG, "Tenrai fetch error: ${e.message}")
                        null to emptyList()
                    }
                }

                val aniListDeferred = async {
                    try {
                        animePosterResolver.resolveMetadata(seriesTitle)
                    } catch (e: Exception) {
                        Log.w(TAG, "AniList metadata fetch error: ${e.message}")
                        null
                    }
                }

                // 2. Fetch Drive files in folder
                val driveFiles = fetchAllDriveVideos(folderId)
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
                val watchList = watchListDao.getWatches(videoIds)
                val watchMap = watchList.associateBy { it.videoId }

                // Check folder star status
                isFolderStarred = driveFiles.firstOrNull()?.starred == zechs.drive.stream.data.model.Starred.STARRED

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
                    fallbackPosterUrl = cachedPoster
                )
                withContext(Dispatchers.Main) {
                    _uiState.value = shellState
                }

                val (fetchedAnime, fetchedArcs) = tenraiDeferred.await()
                val aniListMeta = aniListDeferred.await()
                animeEntry = fetchedAnime
                franchiseArcs = fetchedArcs

                // Prioritize AniList high-res poster (extraLarge 460x650), then Tenrai/MAL
                val finalPoster = aniListMeta?.posterUrl
                    ?: fetchedAnime?.imageUrl
                    ?: initialPoster
                    ?: cachedPoster
                if (!finalPoster.isNullOrBlank()) {
                    folderMetadataRepository.updatePosterUrl(folderId, seriesTitle, finalPoster)
                }

                val enrichedState = buildSuccessState(
                    folderId = folderId,
                    seriesTitle = seriesTitle,
                    driveFiles = driveFiles,
                    watchMap = watchMap,
                    fetchedAnime = fetchedAnime,
                    aniListMeta = aniListMeta,
                    arcs = fetchedArcs,
                    fallbackPosterUrl = finalPoster
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
        fallbackPosterUrl: String?
    ): SeriesDetailUiState.Success {
        allEpisodeItems = driveFiles.map { file ->
            mapToEpisodeItem(file, watchMap[file.id], arcs)
        }

        val fileModels = driveFiles.map { FilesDataModel.File(it) }
        allSeasonGroups = SeasonEpisodeGrouper.groupFiles(seriesTitle, fileModels)
        val tabs = allSeasonGroups.mapIndexed { index, group ->
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

        val continueItem = determineContinueWatchingItem(allEpisodeItems)
        val continueSubtitle = continueItem?.let { item ->
            val epText = item.episodeNumber?.let { "Ep. $it" } ?: "Ep. 01"
            if (item.watchedDuration > 0 && item.totalDuration > item.watchedDuration) {
                val remainingSec = (item.totalDuration - item.watchedDuration) / 1000
                "$epText • ${String.format(Locale.ROOT, "%02d:%02d", remainingSec / 60, remainingSec % 60)} restante"
            } else {
                "$epText • Iniciar reprodução"
            }
        } ?: "Ep. 01 • Iniciar reprodução"

        return SeriesDetailUiState.Success(
            folderId = folderId,
            seriesTitle = seriesTitle,
            animeEntry = fetchedAnime,
            aniListMetadata = aniListMeta,
            seasonTabs = tabs,
            currentEpisodes = getEpisodesForGroup(allSeasonGroups.firstOrNull()),
            continueWatchingItem = continueItem,
            continueWatchingSubtitle = continueSubtitle,
            isStarred = isFolderStarred,
            fallbackPosterUrl = fallbackPosterUrl,
            qualityBadge = detectQuality(driveFiles),
            audioBadge = detectAudio(driveFiles),
            subtitleBadge = "Multi Subs"
        )
    }

    private suspend fun fetchAllDriveVideos(folderId: String): List<DriveFile> {
        val directRes = driveRepository.getAllFiles(
            query = "'$folderId' in parents and trashed = false",
            pageSize = 100
        )
        if (directRes !is Resource.Success || directRes.data.isNullOrEmpty()) {
            return emptyList()
        }

        val items = directRes.data.map { it.toDriveFile() }
        val videoFiles = items.filter { it.isVideoFile || (it.isShortcut && it.isShortcutVideo) }.toMutableList()
        val subfolders = items.filter { it.isFolder || it.isShortcutFolder }

        // If folder has subfolders (e.g. Kajitsu, Meikyuu, Rakuen) and few direct videos, fetch subfolders
        if (subfolders.isNotEmpty() && videoFiles.size <= 2) {
            for (sub in subfolders) {
                val subId = if (sub.isShortcut) sub.shortcutDetails.targetId ?: sub.id else sub.id
                val subRes = driveRepository.getAllFiles(
                    query = "'$subId' in parents and trashed = false",
                    pageSize = 100
                )
                if (subRes is Resource.Success && !subRes.data.isNullOrEmpty()) {
                    val subVideos = subRes.data.map { it.toDriveFile() }
                        .filter { it.isVideoFile || (it.isShortcut && it.isShortcutVideo) }
                    videoFiles.addAll(subVideos)
                }
            }
        }

        return videoFiles.sortedWith { a, b ->
            SeasonEpisodeGrouper.compareItems(
                a.name, EpisodeParser.parse(a.name),
                b.name, EpisodeParser.parse(b.name)
            )
        }
    }

    private fun mapToEpisodeItem(
        file: DriveFile,
        watch: WatchList?,
        arcs: List<TenraiAnimeService.TenraiFranchiseArc>
    ): SeriesEpisodeItem {
        val parsed = EpisodeParser.parse(file.name)
        val epNum = parsed.episode?.toInt()

        // Match canonical episode title from franchise arcs
        var canonName: String? = null
        if (epNum != null) {
            for (arc in arcs) {
                if (arc.episodes.containsKey(epNum)) {
                    canonName = arc.episodes[epNum]
                    break
                }
            }
        }

        val cleanRawName = file.name
            .substringBeforeLast('.')
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .trim()

        val displayTitle = when {
            canonName != null && epNum != null -> "Episódio ${String.format(Locale.ROOT, "%02d", epNum)} - $canonName"
            epNum != null -> "Episódio ${String.format(Locale.ROOT, "%02d", epNum)} - $cleanRawName"
            else -> cleanRawName
        }

        val progress = watch?.watchProgress() ?: 0
        val durationText = if (watch != null && watch.totalDuration > 0) {
            val totalMin = (watch.totalDuration / 1000) / 60
            "$totalMin min"
        } else {
            "24 min"
        }

        val audioText = if (file.name.contains("dual", ignoreCase = true) || file.name.contains("multi", ignoreCase = true)) {
            "Dual Áudio"
        } else {
            "Japonês"
        }

        return SeriesEpisodeItem(
            file = file,
            displayTitle = displayTitle,
            episodeNumber = epNum,
            durationText = durationText,
            audioBadgeText = audioText,
            progressPercent = progress,
            watchedDuration = watch?.watchedDuration ?: 0L,
            totalDuration = watch?.totalDuration ?: 0L
        )
    }

    private fun determineContinueWatchingItem(episodes: List<SeriesEpisodeItem>): SeriesEpisodeItem? {
        // 1. Check for partially watched episode (progress in 1..94)
        val inProgress = episodes.firstOrNull { it.progressPercent in 1..94 }
        if (inProgress != null) return inProgress

        // 2. Check for first unwatched episode
        val firstUnwatched = episodes.firstOrNull { it.progressPercent == 0 }
        if (firstUnwatched != null) return firstUnwatched

        // 3. Default to first episode
        return episodes.firstOrNull()
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
            val nonMusicEpisodes = allEpisodeItems.filter { item ->
                val parsed = EpisodeParser.parse(item.file.name)
                SeasonEpisodeGrouper.categorize(item.file.name, parsed) != SeasonEpisodeGrouper.ItemCategory.MUSIC
            }
            return if (nonMusicEpisodes.isNotEmpty()) nonMusicEpisodes else allEpisodeItems
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
            driveRepository.updateFile(currentFolderId, newStarred)
            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(isStarred = newStarred)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling star", e)
        }
    }

    fun markSeasonWatched() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _uiState.value as? SeriesDetailUiState.Success ?: return@launch

        currentState.currentEpisodes.forEach { item ->
            val watch = WatchList(
                name = item.file.name,
                videoId = item.file.id,
                watchedDuration = 24 * 60 * 1000L,
                totalDuration = 24 * 60 * 1000L,
                thumbnailLink = item.file.thumbnailLarge ?: item.file.thumbnailLink
            )
            watchListDao.upsertWatch(watch)
        }

        // Reload to update progress bars
        loadSeriesDetails(currentFolderId, currentSeriesTitle)
    }

    private fun detectQuality(files: List<DriveFile>): String {
        return when {
            files.any { it.name.contains("2160p", ignoreCase = true) || it.name.contains("4k", ignoreCase = true) } -> "4K UHD"
            files.any { it.name.contains("1080p", ignoreCase = true) } -> "1080p"
            files.any { it.name.contains("720p", ignoreCase = true) } -> "720p"
            else -> "1080p"
        }
    }

    private fun detectAudio(files: List<DriveFile>): String {
        return if (files.any { it.name.contains("dual", ignoreCase = true) || it.name.contains("pt-br", ignoreCase = true) }) {
            "Dual Áudio PT-BR / JA"
        } else {
            "Áudio Original (JA)"
        }
    }
}
