package zechs.drive.stream.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.DriveFolderListing
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.ui.player.PlayerActivity.Companion.TAG
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val watchListRepository: WatchListRepository,
    private val driveRepository: Lazy<DriveRepository>,
    savedStateHandle: SavedStateHandle,
    profileManager: ProfileManager
) : ViewModel() {

    /** A playback session belongs to the profile that started it, even if the active profile changes (for example in PiP). */
    private val playbackProfileId = PlaybackProfileSessionPolicy.resolve(
        savedStateHandle.get(PlayerLauncher.EXTRA_PROFILE_ID),
        profileManager.getActiveProfile().id
    )
    val playbackProfileIdForHandoff: String get() = playbackProfileId

    private val _startDuration = Channel<Long>(Channel.CONFLATED)
    val startDuration = _startDuration

    // Each event is a page delta. CONFLATED would silently discard intermediate pages.
    private val _playlistChannel = Channel<DriveFolderListing<PlaylistItem>>(Channel.BUFFERED)
    val playlistChannel = _playlistChannel

    private val _subtitlesChannel = Channel<DriveFolderListing<SubtitleItem>>(Channel.BUFFERED)
    val subtitlesChannel = _subtitlesChannel

    private var siblingsJob: Job? = null
    private var subtitlesJob: Job? = null

    fun fetchSiblings(fileId: String, parentFolderId: String? = null) {
        siblingsJob?.cancel()
        siblingsJob = viewModelScope.launch(Dispatchers.IO) {
            val repository = driveRepository.get()
            var page = repository.getFileSiblings(fileId, parentFolderId)
            val requestedTokens = mutableSetOf<String>()
            while (true) {
                _playlistChannel.send(page)
                val folderId = page.sourceFolderId ?: break
                val token = page.nextPageToken?.takeIf(String::isNotBlank) ?: break
                if (!requestedTokens.add(token)) {
                    _playlistChannel.send(page.copy(
                        isComplete = false,
                        message = "O Drive retornou uma página repetida; a playlist foi interrompida para evitar duplicatas."
                    ))
                    break
                }
                page = repository.getNextFileSiblingPage(folderId, token)
                if (page.message != null && page.items.isEmpty()) {
                    _playlistChannel.send(page)
                    break
                }
            }
        }
    }

    fun fetchSubtitles(fileId: String, parentFolderId: String? = null) {
        subtitlesJob?.cancel()
        subtitlesJob = viewModelScope.launch(Dispatchers.IO) {
            val repository = driveRepository.get()
            var page = repository.getFolderSubtitles(fileId, parentFolderId)
            val requestedTokens = mutableSetOf<String>()
            while (true) {
                _subtitlesChannel.send(page)
                val folderId = page.sourceFolderId ?: break
                val token = page.nextPageToken?.takeIf(String::isNotBlank) ?: break
                if (!requestedTokens.add(token)) {
                    _subtitlesChannel.send(page.copy(
                        isComplete = false,
                        message = "O Drive retornou uma página repetida; a busca por legendas foi interrompida."
                    ))
                    break
                }
                page = repository.getNextFolderSubtitlePage(folderId, token)
                if (page.message != null && page.items.isEmpty()) {
                    _subtitlesChannel.send(page)
                    break
                }
            }
        }
    }

    suspend fun downloadSubtitle(sub: SubtitleItem, cacheDir: java.io.File): java.io.File? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            driveRepository.get().downloadSubtitle(sub, cacheDir)
        }
    }

    val progressRecorder by lazy {
        zechs.drive.stream.playback.session.ProgressRecorder(watchListRepository, playbackProfileId)
    }

    fun getWatch(videoId: String) = viewModelScope.launch(Dispatchers.IO) {
        val resumePos = progressRecorder.getResumePosition(videoId)
        if (resumePos > 0L) {
            Log.d(TAG, "Starting at $resumePos")
            _startDuration.send(resumePos)
        } else {
            Log.d(TAG, "Video not found or already finished in database")
        }
    }

    suspend fun resumePosition(videoId: String): Long = progressRecorder.getResumePosition(videoId)

    fun getWatchPosition(videoId: String, onResult: (Long) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val pos = progressRecorder.getResumePosition(videoId)
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(pos)
        }
    }

    suspend fun getDelays(videoId: String): Pair<Long, Long> = progressRecorder.getDelays(videoId)

    fun saveDelays(videoId: String, audioDelayMs: Long, subtitleDelayMs: Long) = viewModelScope.launch(Dispatchers.IO) {
        progressRecorder.updateDelays(videoId, audioDelayMs, subtitleDelayMs)
    }

    fun saveWatch(
        name: String,
        videoId: String,
        watchedDuration: Long,
        totalDuration: Long,
        thumbnailLink: String? = null,
        audioDelayMs: Long? = null,
        subtitleDelayMs: Long? = null
    ) = viewModelScope.launch(Dispatchers.IO) {
        progressRecorder.recordProgress(
            fileId = videoId,
            title = name,
            watchedDuration = watchedDuration,
            totalDuration = totalDuration,
            thumbnailLink = thumbnailLink,
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs
        )
    }
}
