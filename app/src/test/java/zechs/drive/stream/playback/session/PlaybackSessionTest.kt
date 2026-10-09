package zechs.drive.stream.playback.session

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.utils.MatroskaChapterParser.ChapterType
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter

class PlaybackSessionTest {

    private lateinit var progressRecorder: ProgressRecorder
    private lateinit var session: PlaybackSession

    private val playlistItems = listOf(
        PlaylistItem(fileId = "f1", title = "Show S01E01.mkv"),
        PlaylistItem(fileId = "f2", title = "Show S01E02.mkv")
    )

    @Before
    fun setup() {
        progressRecorder = mockk(relaxed = true)
        session = PlaybackSession(progressRecorder)
    }

    @Test
    fun `initial state is empty`() {
        val state = session.sessionState.value
        assertEquals(null, state.currentItem)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `setting playlist updates current and next episode in state`() {
        session.setPlaylist(playlistItems, currentFileId = "f1")

        val state = session.sessionState.value
        assertEquals("f1", state.currentItem?.fileId)
        assertEquals("f2", state.nextEpisode?.fileId)
    }

    @Test
    fun `playback tick updates position, duration, and skip info`() {
        val chapter = ParsedChapter(
            index = 1,
            title = "Opening",
            startTimeMs = 30_000L,
            endTimeMs = 90_000L,
            type = ChapterType.OPENING
        )
        session.setChapters(listOf(chapter))
        session.setPlaylist(playlistItems, currentFileId = "f1")

        val tickResult = session.onPlaybackTick(
            positionMs = 45_000L,
            durationMs = 1_000_000L,
            autoSkipEnabled = false,
            controllerVisible = false
        )

        val state = session.sessionState.value
        assertEquals(45_000L, state.positionMs)
        assertEquals(1_000_000L, state.durationMs)
        assertEquals(chapter, state.activeChapter)
        assertTrue(state.skipState.buttonUi is SkipController.SkipButtonUi.Visible)
        assertNotNull(tickResult)
    }

    @Test
    fun `performSkip delegates to skipController`() {
        val chapter = ParsedChapter(
            index = 1,
            title = "Opening",
            startTimeMs = 30_000L,
            endTimeMs = 90_000L,
            type = ChapterType.OPENING
        )
        session.setChapters(listOf(chapter))
        session.onPlaybackTick(positionMs = 45_000L, durationMs = 1_000_000L)

        val target = session.performSkip(45_000L)
        assertEquals(90_000L, target)
    }

    @Test
    fun `recordProgress delegates to progressRecorder`() = runBlocking {
        coEvery { progressRecorder.recordProgress(any(), any(), any(), any(), any(), any(), any()) } returns true

        val recorded = session.recordProgress(
            fileId = "f1",
            title = "Show S01E01.mkv",
            watchedDuration = 50_000L,
            totalDuration = 100_000L
        )

        assertTrue(recorded)
        coVerify(exactly = 1) {
            progressRecorder.recordProgress(
                fileId = "f1",
                title = "Show S01E01.mkv",
                watchedDuration = 50_000L,
                totalDuration = 100_000L,
                thumbnailLink = null,
                audioDelayMs = null,
                subtitleDelayMs = null
            )
        }
    }

    @Test
    fun `advancing playlist advances coordinator and updates state`() {
        session.setPlaylist(playlistItems, currentFileId = "f1")
        val advanced = session.advanceToNext()

        assertEquals("f2", advanced?.fileId)
        val state = session.sessionState.value
        assertEquals("f2", state.currentItem?.fileId)
    }
}
