package zechs.drive.stream.playback.session

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.WatchListRepository

class ProgressRecorderTest {

    private lateinit var watchListRepository: WatchListRepository
    private lateinit var recorder: ProgressRecorder
    private val profileId = "profile-1"

    @Before
    fun setup() {
        watchListRepository = mockk(relaxed = true)
        recorder = ProgressRecorder(watchListRepository, profileId)
    }

    @Test
    fun `shouldSave returns false when progress is below minimum percentage`() {
        assertFalse(recorder.shouldSave(positionMs = 5_000L, durationMs = 100_000L))
    }

    @Test
    fun `shouldSave returns true when position is within valid range`() {
        assertTrue(recorder.shouldSave(positionMs = 30_000L, durationMs = 100_000L))
    }

    @Test
    fun `isFinished returns true when position is 95 percent of duration`() {
        assertTrue(recorder.isFinished(positionMs = 95_000L, durationMs = 100_000L))
    }

    @Test
    fun `recordProgress returns false and does not save when below threshold`() = runBlocking {
        val result = recorder.recordProgress(
            fileId = "file-1",
            title = "Test",
            watchedDuration = 5_000L,
            totalDuration = 100_000L
        )
        assertFalse(result)
        coVerify(exactly = 0) { watchListRepository.insertWatch(any(), any()) }
    }

    @Test
    fun `recordProgress saves and normalizes watchedDuration when finished`() = runBlocking {
        coEvery { watchListRepository.getWatch("file-1", profileId) } returns null

        val result = recorder.recordProgress(
            fileId = "file-1",
            title = "Episode 1",
            watchedDuration = 95_000L,
            totalDuration = 100_000L
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            watchListRepository.insertWatch(
                match { it.videoId == "file-1" && it.watchedDuration == 100_000L && it.totalDuration == 100_000L },
                profileId
            )
        }
    }

    @Test
    fun `recordProgress preserves existing delays when not provided`() = runBlocking {
        val existing = WatchList(
            name = "Old Title",
            videoId = "file-1",
            watchedDuration = 20_000L,
            totalDuration = 100_000L,
            audioDelayMs = 250L,
            subtitleDelayMs = -100L
        )
        coEvery { watchListRepository.getWatch("file-1", profileId) } returns existing

        val result = recorder.recordProgress(
            fileId = "file-1",
            title = "New Title",
            watchedDuration = 40_000L,
            totalDuration = 100_000L
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            watchListRepository.insertWatch(
                match { it.audioDelayMs == 250L && it.subtitleDelayMs == -100L && it.watchedDuration == 40_000L },
                profileId
            )
        }
    }

    @Test
    fun `getResumePosition returns 0 when item does not exist or was finished`() = runBlocking {
        coEvery { watchListRepository.getWatch("file-1", profileId) } returns null
        assertEquals(0L, recorder.getResumePosition("file-1"))

        val finished = WatchList(
            name = "Finished",
            videoId = "file-1",
            watchedDuration = 100_000L,
            totalDuration = 100_000L
        )
        coEvery { watchListRepository.getWatch("file-1", profileId) } returns finished
        assertEquals(0L, recorder.getResumePosition("file-1"))
    }

    @Test
    fun `getResumePosition returns position for in-progress video`() = runBlocking {
        val inProgress = WatchList(
            name = "In Progress",
            videoId = "file-1",
            watchedDuration = 50_000L,
            totalDuration = 100_000L
        )
        coEvery { watchListRepository.getWatch("file-1", profileId) } returns inProgress
        assertEquals(50_000L, recorder.getResumePosition("file-1"))
    }

    @Test
    fun `updateDelays calls repository updateDelays`() = runBlocking {
        recorder.updateDelays("file-1", 150L, -50L)
        coVerify(exactly = 1) {
            watchListRepository.updateDelays("file-1", 150L, -50L, profileId)
        }
    }
}
