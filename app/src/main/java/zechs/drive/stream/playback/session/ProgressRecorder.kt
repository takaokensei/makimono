package zechs.drive.stream.playback.session

import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.utils.PlaybackProgressPolicy

/**
 * ADR-002: ProgressRecorder
 * Centralizes playback progress calculation, completion threshold evaluation,
 * and persistence to the local WatchList database per profile.
 */
class ProgressRecorder(
    private val watchListRepository: WatchListRepository,
    private val playbackProfileId: String,
    private val progressPolicy: PlaybackProgressPolicy = PlaybackProgressPolicy
) {
    companion object {
        private const val TAG = "ProgressRecorder"
    }

    /**
     * Determines whether progress should be saved based on duration and position.
     */
    fun shouldSave(positionMs: Long, durationMs: Long): Boolean {
        return progressPolicy.shouldSave(positionMs, durationMs)
    }

    /**
     * Checks if playback has crossed the completion threshold.
     */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        return progressPolicy.isFinished(positionMs, durationMs)
    }

    /**
     * Records progress in the local database.
     * Returns true if saved, or false if skipped (e.g. below minimum threshold).
     */
    suspend fun recordProgress(
        fileId: String,
        title: String,
        watchedDuration: Long,
        totalDuration: Long,
        thumbnailLink: String? = null,
        audioDelayMs: Long? = null,
        subtitleDelayMs: Long? = null
    ): Boolean {
        if (!shouldSave(watchedDuration, totalDuration)) {
            AppLog.d(TAG, "Playback below minimum threshold or invalid ($watchedDuration / $totalDuration), skipping save")
            return false
        }

        val finished = isFinished(watchedDuration, totalDuration)
        val finalWatched = if (finished && totalDuration > 0L) totalDuration else watchedDuration

        val existing = watchListRepository.getWatch(fileId, playbackProfileId)
        val finalAudioDelay = audioDelayMs ?: existing?.audioDelayMs ?: 0L
        val finalSubtitleDelay = subtitleDelayMs ?: existing?.subtitleDelayMs ?: 0L

        val watch = existing?.copy(
            name = title,
            videoId = fileId,
            watchedDuration = finalWatched,
            totalDuration = totalDuration,
            thumbnailLink = thumbnailLink ?: existing.thumbnailLink,
            audioDelayMs = finalAudioDelay,
            subtitleDelayMs = finalSubtitleDelay
        ) ?: WatchList(
            name = title,
            videoId = fileId,
            watchedDuration = finalWatched,
            totalDuration = totalDuration,
            thumbnailLink = thumbnailLink,
            audioDelayMs = finalAudioDelay,
            subtitleDelayMs = finalSubtitleDelay
        )

        AppLog.d(TAG, "Saving progress: $title ($finalWatched/$totalDuration, finished=$finished)")
        watchListRepository.insertWatch(watch, playbackProfileId)
        return true
    }

    /**
     * Retrieves resume position in milliseconds for a given video ID.
     */
    suspend fun getResumePosition(fileId: String): Long {
        val watch = watchListRepository.getWatch(fileId, playbackProfileId) ?: return 0L
        return progressPolicy.resumePosition(watch.watchedDuration, watch.totalDuration)
    }

    /**
     * Retrieves saved audio and subtitle sync delays for a given video ID.
     */
    suspend fun getDelays(fileId: String): Pair<Long, Long> {
        val watch = watchListRepository.getWatch(fileId, playbackProfileId)
        return Pair(watch?.audioDelayMs ?: 0L, watch?.subtitleDelayMs ?: 0L)
    }

    /**
     * Updates audio and subtitle delays without modifying playback timestamps.
     */
    suspend fun updateDelays(fileId: String, audioDelayMs: Long, subtitleDelayMs: Long) {
        watchListRepository.updateDelays(fileId, audioDelayMs, subtitleDelayMs, playbackProfileId)
    }
}
