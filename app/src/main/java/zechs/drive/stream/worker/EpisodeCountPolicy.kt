package zechs.drive.stream.worker

import zechs.drive.stream.data.model.FileCollectionResult
import zechs.drive.stream.utils.state.Resource

/** Pure decision logic for the followed-folder monitor. Counts unique playable videos only. */
internal object EpisodeCountPolicy {

    sealed interface Decision {
        data class Complete(val videoCount: Int, val newEpisodeCount: Int) : Decision
        data object Retry : Decision
    }

    fun evaluate(
        previousVideoCount: Int,
        result: Resource<FileCollectionResult>
    ): Decision {
        val collection = (result as? Resource.Success)?.data ?: return Decision.Retry
        // A continuation token means the count is not authoritative, even if a caller
        // accidentally marks the page collection complete.
        if (!collection.isComplete || !collection.nextPageToken.isNullOrBlank()) {
            return Decision.Retry
        }

        val videoCount = collection.files.asSequence()
            .distinctBy { it.id }
            .map { it.toDriveFile() }
            .count { it.isVideoFile || it.isShortcutVideo }

        val newEpisodeCount = if (previousVideoCount > 0) {
            (videoCount - previousVideoCount).coerceAtLeast(0)
        } else {
            0
        }
        return Decision.Complete(videoCount, newEpisodeCount)
    }
}
