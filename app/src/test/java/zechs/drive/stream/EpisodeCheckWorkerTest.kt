package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FileCollectionResult
import zechs.drive.stream.data.model.DriveQueryContext
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.worker.EpisodeCountPolicy

/** Exercises the exact pure policy used by EpisodeCheckWorker. */
class EpisodeCheckWorkerTest {

    @Test
    fun `complete listing counts only unique playable video files`() {
        val listing = complete(
            video("episode-1", "Episode 01.mkv"),
            video("episode-1", "Episode 01.mkv"), // repeated across Drive pages
            video("episode-2", "Episode 02", mimeType = "video/mp4"),
            video("subtitle", "Episode 02.srt", mimeType = "application/x-subrip"),
            video("folder", "Season 2", mimeType = "application/vnd.google-apps.folder"),
            video(
                id = "shortcut",
                name = "Episode 03",
                mimeType = "application/vnd.google-apps.shortcut",
                shortcutDetails = ShortcutDetails(targetMimeType = "video/mp4")
            )
        )

        val decision = EpisodeCountPolicy.evaluate(2, Resource.Success(listing))

        assertEquals(EpisodeCountPolicy.Decision.Complete(videoCount = 3, newEpisodeCount = 1), decision)
    }

    @Test
    fun `first complete check establishes baseline without notification`() {
        val decision = EpisodeCountPolicy.evaluate(
            previousVideoCount = 0,
            result = Resource.Success(complete(video("episode-1", "episode.mkv")))
        )

        assertEquals(EpisodeCountPolicy.Decision.Complete(videoCount = 1, newEpisodeCount = 0), decision)
    }

    @Test
    fun `complete empty listing resets count without notifying`() {
        val decision = EpisodeCountPolicy.evaluate(previousVideoCount = 5, result = Resource.Success(complete()))

        assertEquals(EpisodeCountPolicy.Decision.Complete(videoCount = 0, newEpisodeCount = 0), decision)
    }

    @Test
    fun `incomplete page never updates baseline`() {
        val partial = FileCollectionResult(
            files = listOf(video("episode-9", "episode-9.mkv")),
            isComplete = false,
            nextPageToken = "page-2",
            pagesFetched = 1,
            queryContext = testQueryContext,
            incompleteReason = FileCollectionResult.IncompleteReason.PAGE_LIMIT
        )

        assertEquals(EpisodeCountPolicy.Decision.Retry, EpisodeCountPolicy.evaluate(8, Resource.Success(partial)))
    }

    @Test
    fun `complete flag with continuation token is rejected as inconsistent`() {
        val inconsistent = FileCollectionResult(
            files = listOf(video("episode-9", "episode-9.mkv")),
            isComplete = true,
            nextPageToken = "page-2",
            pagesFetched = 1,
            queryContext = testQueryContext
        )

        assertEquals(EpisodeCountPolicy.Decision.Retry, EpisodeCountPolicy.evaluate(8, Resource.Success(inconsistent)))
    }

    @Test
    fun `request failure with partial files never updates baseline`() {
        val partial = FileCollectionResult(
            files = listOf(video("episode-9", "episode-9.mkv")),
            isComplete = false,
            nextPageToken = "page-2",
            pagesFetched = 1,
            queryContext = testQueryContext,
            incompleteReason = FileCollectionResult.IncompleteReason.REQUEST_FAILED
        )

        assertEquals(EpisodeCountPolicy.Decision.Retry, EpisodeCountPolicy.evaluate(8, Resource.Error("offline", partial)))
    }

    @Test
    fun `a deletion does not emit a negative new episode count`() {
        val decision = EpisodeCountPolicy.evaluate(
            previousVideoCount = 8,
            result = Resource.Success(complete(video("episode-1", "episode-1.mkv")))
        )

        assertEquals(EpisodeCountPolicy.Decision.Complete(videoCount = 1, newEpisodeCount = 0), decision)
    }

    @Test
    fun `errors without partial results are retried`() {
        assertTrue(EpisodeCountPolicy.evaluate(1, Resource.Error("offline")) is EpisodeCountPolicy.Decision.Retry)
    }

    private fun complete(vararg files: File) = FileCollectionResult(
        files = files.toList(),
        isComplete = true,
        nextPageToken = null,
        pagesFetched = 1,
        queryContext = testQueryContext
    )

    private val testQueryContext = DriveQueryContext(
        query = "trashed=false",
        pageSize = 100,
        orderBy = "name"
    )

    private fun video(
        id: String,
        name: String,
        mimeType: String = "video/x-matroska",
        shortcutDetails: ShortcutDetails = ShortcutDetails()
    ) = File(
        id = id,
        name = name,
        size = null,
        iconLink = "",
        mimeType = mimeType,
        shortcutDetails = shortcutDetails
    )
}
