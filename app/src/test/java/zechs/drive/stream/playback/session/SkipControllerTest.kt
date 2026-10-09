package zechs.drive.stream.playback.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.utils.MatroskaChapterParser.ChapterType
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter

class SkipControllerTest {

    private lateinit var skipController: SkipController

    private val sampleOpening = ParsedChapter(
        index = 1,
        title = "Opening",
        startTimeMs = 60_000L,
        endTimeMs = 150_000L,
        type = ChapterType.OPENING
    )

    private val sampleEnding = ParsedChapter(
        index = 2,
        title = "Ending",
        startTimeMs = 1_200_000L,
        endTimeMs = 1_290_000L,
        type = ChapterType.ENDING
    )

    private val sampleRecap = ParsedChapter(
        index = 0,
        title = "Recap",
        startTimeMs = 0L,
        endTimeMs = 30_000L,
        type = ChapterType.RECAP
    )

    @Before
    fun setup() {
        skipController = SkipController()
    }

    @Test
    fun `detects opening chapter and provides skip button with label Pular Abertura`() {
        skipController.setChapters(listOf(sampleOpening))
        val (state, action) = skipController.onPlaybackTick(
            positionMs = 80_000L,
            autoSkipEnabled = false,
            controllerVisible = false
        )

        assertEquals(sampleOpening, state.activeChapter)
        assertTrue(state.buttonUi is SkipController.SkipButtonUi.Visible)
        val visibleUi = state.buttonUi as SkipController.SkipButtonUi.Visible
        assertEquals("Pular Abertura", visibleUi.label)
        assertEquals(150_000L, visibleUi.targetPositionMs)
        assertTrue(visibleUi.isOverlay)
        assertEquals(SkipController.SkipAction.None, action)
    }

    @Test
    fun `detects ending chapter and provides skip button with label Pular Encerramento`() {
        skipController.setChapters(listOf(sampleEnding))
        val (state, _) = skipController.onPlaybackTick(
            positionMs = 1_250_000L,
            autoSkipEnabled = false,
            controllerVisible = true
        )

        assertEquals(sampleEnding, state.activeChapter)
        val visibleUi = state.buttonUi as SkipController.SkipButtonUi.Visible
        assertEquals("Pular Encerramento", visibleUi.label)
        assertEquals(1_290_000L, visibleUi.targetPositionMs)
    }

    @Test
    fun `detects recap chapter and provides skip button with label Pular Recap`() {
        skipController.setChapters(listOf(sampleRecap))
        val (state, _) = skipController.onPlaybackTick(
            positionMs = 15_000L,
            autoSkipEnabled = false,
            controllerVisible = true
        )

        assertEquals(sampleRecap, state.activeChapter)
        val visibleUi = state.buttonUi as SkipController.SkipButtonUi.Visible
        assertEquals("Pular Recap", visibleUi.label)
        assertEquals(30_000L, visibleUi.targetPositionMs)
    }

    @Test
    fun `auto-skips opening when autoSkipEnabled and within grace period`() {
        skipController.setChapters(listOf(sampleOpening))
        val (_, action) = skipController.onPlaybackTick(
            positionMs = 61_000L,
            autoSkipEnabled = true,
            controllerVisible = false
        )

        assertTrue(action is SkipController.SkipAction.AutoSkip)
        val autoSkip = action as SkipController.SkipAction.AutoSkip
        assertEquals(150_000L, autoSkip.targetPositionMs)
        assertTrue(autoSkip.message.contains("Abertura"))
    }

    @Test
    fun `does not auto-skip twice in same chapter interval`() {
        skipController.setChapters(listOf(sampleOpening))
        // First tick triggers auto-skip
        skipController.onPlaybackTick(
            positionMs = 61_000L,
            autoSkipEnabled = true
        )

        // Second tick within interval should not trigger again
        val (_, secondAction) = skipController.onPlaybackTick(
            positionMs = 62_000L,
            autoSkipEnabled = true
        )
        assertEquals(SkipController.SkipAction.None, secondAction)
    }

    @Test
    fun `provides fallback 90s intro skip button when no chapters exist and position is within 140s`() {
        skipController.setChapters(emptyList())
        val (state, _) = skipController.onPlaybackTick(
            positionMs = 45_000L,
            autoSkipEnabled = false,
            controllerVisible = true
        )

        assertNull(state.activeChapter)
        assertTrue(state.buttonUi is SkipController.SkipButtonUi.Visible)
        val visibleUi = state.buttonUi as SkipController.SkipButtonUi.Visible
        assertEquals("Pular Abertura (+90s)", visibleUi.label)
        assertEquals(135_000L, visibleUi.targetPositionMs)
    }

    @Test
    fun `hides fallback intro skip when position exceeds 140s`() {
        skipController.setChapters(emptyList())
        val (state, _) = skipController.onPlaybackTick(
            positionMs = 150_000L,
            autoSkipEnabled = false,
            controllerVisible = true
        )

        assertTrue(state.buttonUi is SkipController.SkipButtonUi.Hidden)
    }

    @Test
    fun `mergeAniSkipChapters does not overwrite existing MKV opening`() {
        skipController.setChapters(listOf(sampleOpening))
        val aniOpening = ParsedChapter(
            index = 10,
            title = "AniSkip OP",
            startTimeMs = 65_000L,
            endTimeMs = 155_000L,
            type = ChapterType.OPENING
        )
        skipController.mergeAniSkipChapters(listOf(aniOpening))

        val chapters = skipController.getChapters()
        assertEquals(1, chapters.size)
        assertEquals(sampleOpening.startTimeMs, chapters.first().startTimeMs)
    }

    @Test
    fun `mergeAniSkipChapters adds missing ending and recap`() {
        skipController.setChapters(listOf(sampleOpening))
        val aniEnding = ParsedChapter(
            index = 11,
            title = "AniSkip ED",
            startTimeMs = 1_200_000L,
            endTimeMs = 1_290_000L,
            type = ChapterType.ENDING
        )
        skipController.mergeAniSkipChapters(listOf(aniEnding))

        val chapters = skipController.getChapters()
        assertEquals(2, chapters.size)
        assertEquals(ChapterType.ENDING, chapters[1].type)
    }

    @Test
    fun `performSkip returns chapter end time when inside chapter or 90s forward when fallback`() {
        skipController.setChapters(listOf(sampleOpening))
        skipController.onPlaybackTick(80_000L)
        assertEquals(150_000L, skipController.performSkip(80_000L))

        skipController.setChapters(emptyList())
        skipController.onPlaybackTick(20_000L, controllerVisible = true)
        assertEquals(110_000L, skipController.performSkip(20_000L))
    }
}
