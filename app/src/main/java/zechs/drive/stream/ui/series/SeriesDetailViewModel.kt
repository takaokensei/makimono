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
    object Loading : SeriesDetailUiState()
    data class Success(
        val folderId: String,
        val seriesTitle: String,
        val animeEntry: TenraiAnimeService.TenraiAnimeEntry?,
        val seasonTabs: List<SeasonTab>,
        val currentEpisodes: List<SeriesEpisodeItem>,
        val continueWatchingItem: SeriesEpisodeItem?,
        val continueWatchingSubtitle: String,
        val isStarred: Boolean,
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

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading)
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
        _uiState.value = SeriesDetailUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Record folder opened for Home screen Recents / Continue
                folderMetadataRepository.recordFolderOpened(folderId, seriesTitle)

                // 1. Concurrently fetch Tenrai metadata
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

                // 2. Fetch Drive files in folder
                val driveFiles = fetchAllDriveVideos(folderId)
                if (driveFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = SeriesDetailUiState.Error("Nenhum arquivo de vídeo encontrado na pasta.")
                    }
                    return@launch
                }

                val (fetchedAnime, fetchedArcs) = tenraiDeferred.await()
                animeEntry = fetchedAnime
                franchiseArcs = fetchedArcs

                // If poster URL exists in Tenrai and not recorded yet, update local cache
                val finalPoster = fetchedAnime?.imageUrl ?: initialPoster
                if (finalPoster != null) {
                    folderMetadataRepository.updatePosterUrl(folderId, seriesTitle, finalPoster)
                }

                // 3. Batch query watch history for progress
                val videoIds = driveFiles.map { it.id }
                val watchList = watchListDao.getWatches(videoIds)
                val watchMap = watchList.associateBy { it.videoId }

                // Check folder star status
                isFolderStarred = driveFiles.firstOrNull()?.starred == zechs.drive.stream.data.model.Starred.STARRED

                // 4. Map DriveFiles to SeriesEpisodeItem with canonical names & progress
                allEpisodeItems = driveFiles.map { file ->
                    mapToEpisodeItem(file, watchMap[file.id], fetchedArcs)
                }

                // 5. Group using SeasonEpisodeGrouper
                val fileModels = driveFiles.map { FilesDataModel.File(it) }
                allSeasonGroups = SeasonEpisodeGrouper.groupFiles(seriesTitle, fileModels)

                // 6. Build SeasonTab list
                val tabs = mutableListOf<SeasonTab>()
                allSeasonGroups.forEachIndexed { index, group ->
                    val icon = when {
                        group.id == "season_all" -> R.drawable.ic_list_view_24
                        group.id.startsWith("season_") -> R.drawable.ic_play_24
                        group.id.contains("special", true) -> R.drawable.ic_star_filled_24
                        group.id.contains("music", true) || group.id.contains("opening", true) -> R.drawable.ic_audio_24
                        else -> null
                    }
                    tabs.add(
                        SeasonTab(
                            id = group.id,
                            title = group.name,
                            iconRes = icon,
                            isSelected = index == 0
                        )
                    )
                }

                // 7. Calculate "Continuar Assistindo" item & remaining time
                val continueItem = determineContinueWatchingItem(allEpisodeItems)
                val continueSubtitle = continueItem?.let { item ->
                    val epText = item.episodeNumber?.let { "Ep. $it" } ?: "Ep. 01"
                    if (item.watchedDuration > 0 && item.totalDuration > item.watchedDuration) {
                        val remainingSec = (item.totalDuration - item.watchedDuration) / 1000
                        val min = remainingSec / 60
                        val sec = remainingSec % 60
                        "$epText • ${String.format(Locale.ROOT, "%02d:%02d", min, sec)} restante"
                    } else {
                        "$epText • Iniciar reprodução"
                    }
                } ?: "Ep. 01 • Iniciar reprodução"

                // Filter initial episodes for first tab
                val initialEpisodes = getEpisodesForGroup(allSeasonGroups.firstOrNull())

                withContext(Dispatchers.Main) {
                    _uiState.value = SeriesDetailUiState.Success(
                        folderId = folderId,
                        seriesTitle = seriesTitle,
                        animeEntry = fetchedAnime,
                        seasonTabs = tabs,
                        currentEpisodes = initialEpisodes,
                        continueWatchingItem = continueItem,
                        continueWatchingSubtitle = continueSubtitle,
                        isStarred = isFolderStarred,
                        qualityBadge = detectQuality(driveFiles),
                        audioBadge = detectAudio(driveFiles),
                        subtitleBadge = "Multi Subs"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading series details", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = SeriesDetailUiState.Error(e.message ?: "Erro ao carregar detalhes da série.")
                }
            }
        }
    }

    private suspend fun fetchAllDriveVideos(folderId: String): List<DriveFile> {
        val directRes = driveRepository.getFiles(
            query = "'$folderId' in parents and trashed = false",
            pageToken = null,
            pageSize = 100
        )
        if (directRes !is Resource.Success || directRes.data?.files.isNullOrEmpty()) {
            return emptyList()
        }

        val items = directRes.data!!.files.map { it.toDriveFile() }
        val videoFiles = items.filter { it.isVideoFile || (it.isShortcut && it.isShortcutVideo) }.toMutableList()
        val subfolders = items.filter { it.isFolder || it.isShortcutFolder }

        // If folder has subfolders (e.g. Kajitsu, Meikyuu, Rakuen) and few direct videos, fetch subfolders
        if (subfolders.isNotEmpty() && videoFiles.size <= 2) {
            for (sub in subfolders) {
                val subId = if (sub.isShortcut) sub.shortcutDetails.targetId ?: sub.id else sub.id
                val subRes = driveRepository.getFiles(
                    query = "'$subId' in parents and trashed = false",
                    pageToken = null,
                    pageSize = 100
                )
                if (subRes is Resource.Success && !subRes.data?.files.isNullOrEmpty()) {
                    val subVideos = subRes.data!!.files.map { it.toDriveFile() }
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
            return allEpisodeItems
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
        val now = System.currentTimeMillis()

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
