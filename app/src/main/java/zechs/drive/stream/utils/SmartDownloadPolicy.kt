package zechs.drive.stream.utils

import zechs.drive.stream.data.model.PlaylistItem

/**
 * Netflix-style Smart Downloads Policy.
 *
 * Automatically manages storage and continuity:
 * 1. Deletes watched episodes once completed (reclaiming disk space).
 * 2. Enqueues the next episode in the series for download.
 *
 * Rules:
 * - Smart Downloads must be enabled by user preference.
 * - Current episode must be completed (playback reached completion threshold).
 * - Current episode must have been a downloaded file.
 * - Next episode must not already be downloaded.
 */
object SmartDownloadPolicy {

    sealed class Action {
        object None : Action()
        data class Execute(
            val deleteFileId: String?,
            val enqueueNextEpisode: PlaylistItem?
        ) : Action()
    }

    fun evaluate(
        isSmartDownloadsEnabled: Boolean,
        isPlaybackFinished: Boolean,
        currentEpisodeFileId: String,
        isCurrentEpisodeDownloaded: Boolean,
        nextEpisode: PlaylistItem?,
        isNextEpisodeDownloaded: Boolean
    ): Action {
        if (!isSmartDownloadsEnabled) {
            return Action.None
        }
        if (!isPlaybackFinished) {
            return Action.None
        }
        // Only trigger smart downloads if the watched episode was actually downloaded
        if (!isCurrentEpisodeDownloaded) {
            return Action.None
        }

        val deleteId = currentEpisodeFileId.takeIf { it.isNotBlank() }
        val nextToDownload = if (nextEpisode != null && !isNextEpisodeDownloaded && nextEpisode.fileId.isNotBlank()) {
            nextEpisode
        } else {
            null
        }

        if (deleteId == null && nextToDownload == null) {
            return Action.None
        }

        return Action.Execute(
            deleteFileId = deleteId,
            enqueueNextEpisode = nextToDownload
        )
    }
}
