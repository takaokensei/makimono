package zechs.drive.stream.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.ui.player.PlayerActivity.Companion.TAG
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val watchListRepository: WatchListRepository,
    private val driveRepository: Lazy<DriveRepository>
) : ViewModel() {

    private val _startDuration = Channel<Long>(Channel.CONFLATED)
    val startDuration = _startDuration

    private val _playlistChannel = Channel<List<PlaylistItem>>(Channel.CONFLATED)
    val playlistChannel = _playlistChannel

    private val _subtitlesChannel = Channel<List<SubtitleItem>>(Channel.CONFLATED)
    val subtitlesChannel = _subtitlesChannel

    fun fetchSiblings(fileId: String) = viewModelScope.launch(Dispatchers.IO) {
        val siblings = driveRepository.get().getFileSiblings(fileId)
        if (siblings.isNotEmpty()) {
            _playlistChannel.send(siblings)
        }
    }

    fun fetchSubtitles(fileId: String) = viewModelScope.launch(Dispatchers.IO) {
        val subs = driveRepository.get().getFolderSubtitles(fileId)
        if (subs.isNotEmpty()) {
            _subtitlesChannel.send(subs)
        }
    }

    suspend fun downloadSubtitle(sub: SubtitleItem, cacheDir: java.io.File): java.io.File? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            driveRepository.get().downloadSubtitle(sub, cacheDir)
        }
    }

    fun getWatch(videoId: String) = viewModelScope.launch(Dispatchers.IO) {
        val video = watchListRepository.getWatch(videoId)
        if (video == null) {
            Log.d(TAG, "Video not found in database")
        } else {
            Log.d(TAG, "Starting at ${video.watchedDuration}")
            _startDuration.send(video.watchedDuration)
        }
    }

    fun getWatchPosition(videoId: String, onResult: (Long) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val video = watchListRepository.getWatch(videoId)
        val pos = video?.watchedDuration ?: 0L
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(pos)
        }
    }

    fun saveWatch(
        name: String,
        videoId: String,
        watchedDuration: Long,
        totalDuration: Long,
        thumbnailLink: String? = null,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val lookUpWatched = watchListRepository.getWatch(videoId)

        val watch = lookUpWatched?.copy(
            name = name,
            videoId = videoId,
            watchedDuration = watchedDuration,
            totalDuration = totalDuration,
            // Keep whatever thumbnail we already had saved if this particular
            // save-progress tick didn't come with a fresh one (e.g. resumed
            // from a deep link without the Drive file object on hand).
            thumbnailLink = thumbnailLink ?: lookUpWatched.thumbnailLink
        ) ?: WatchList(
            name, videoId,
            watchedDuration, totalDuration,
            thumbnailLink = thumbnailLink
        )

        if (watch.hasFinished()) {
            Log.d(TAG, "Video has finished, removing from database")
            watchListRepository.deleteWatch(watch)
        } else {
            Log.d(TAG, "Saving video at ${watch.watchedDuration}")
            watchListRepository.insertWatch(watch)
        }
    }

}