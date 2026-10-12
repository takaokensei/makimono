package zechs.drive.stream.playback.session

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.playback.engine.EnginePlaybackState
import zechs.drive.stream.playback.engine.PlayerEngine
import zechs.drive.stream.playback.osd.OsdAction
import zechs.drive.stream.playback.osd.OsdState
import zechs.drive.stream.utils.MatroskaChapterParser.ChapterType
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter

class PlaybackControllerTest {

    private lateinit var fakeEngine: RecordingPlayerEngine
    private lateinit var watchListRepository: WatchListRepository
    private lateinit var progressRecorder: ProgressRecorder
    private lateinit var controller: PlaybackController

    private val ep1 = PlaylistItem("ep-1", "Frieren - S01E01.mkv")
    private val ep2 = PlaylistItem("ep-2", "Frieren - S01E02.mkv")
    private val ep3 = PlaylistItem("ep-3", "Frieren - S01E03.mkv")
    private val ep4 = PlaylistItem("ep-4", "Frieren - S01E04.mkv")
    private val ep5 = PlaylistItem("ep-5", "Frieren - S01E05.mkv")

    @Before
    fun setUp() {
        fakeEngine = RecordingPlayerEngine()
        watchListRepository = mockk(relaxed = true)
        progressRecorder = ProgressRecorder(watchListRepository, "profile-1")
        controller = PlaybackController(
            engine = fakeEngine,
            progressRecorder = progressRecorder
        )
    }

    @Test
    fun `release is called exactly once across finish, rotation, and PiP`() {
        controller.loadAndPlay(ep1, 0L)
        assertTrue(fakeEngine.playbackState.value.isPlaying)

        // Entering PiP hides OSD without releasing engine
        controller.showOsd()
        controller.onEnterPictureInPicture()
        assertEquals(0, fakeEngine.releaseCallCount)
        assertEquals(OsdState.HIDDEN, controller.osdStateMachine.state)

        // First release (e.g. finish / onDestroy)
        controller.release()
        assertEquals(1, fakeEngine.releaseCallCount)
        assertTrue(controller.released)

        // Subsequent release calls (e.g. rotation or duplicate lifecycle callback) are no-ops
        controller.release()
        controller.release()
        assertEquals(1, fakeEngine.releaseCallCount)
    }

    @Test
    fun `saveProgress persists current engine position and duration via ProgressRecorder`() = runTest {
        io.mockk.coEvery { watchListRepository.getWatch("ep-1", "profile-1") } returns null
        controller.loadAndPlay(ep1, 300_000L)
        fakeEngine.setDuration(1_440_000L)

        val saved = controller.saveProgress(
            audioDelayMs = 100L,
            subtitleDelayMs = -200L
        )

        assertTrue(saved)
        coVerify(exactly = 1) {
            watchListRepository.insertWatch(
                match {
                    it.videoId == "ep-1" &&
                        it.watchedDuration == 300_000L &&
                        it.totalDuration == 1_440_000L &&
                        it.audioDelayMs == 100L &&
                        it.subtitleDelayMs == -200L
                },
                "profile-1"
            )
        }
    }

    @Test
    fun `performSkipIntroOrCredits seeks engine to end of active opening chapter`() {
        controller.loadAndPlay(ep1, 15_000L)
        controller.setChapters(
            listOf(
                ParsedChapter(index = 1, title = "Opening", startTimeMs = 10_000L, endTimeMs = 100_000L, type = ChapterType.OPENING)
            )
        )

        val target = controller.performSkipIntroOrCredits()
        assertEquals(100_000L, target)
        assertEquals(100_000L, fakeEngine.playbackState.value.currentPositionMs)
    }

    @Test
    fun `onPlaybackTick with autoSkipEnabled automatically seeks engine past opening`() {
        controller.loadAndPlay(ep1, 11_000L)
        fakeEngine.setDuration(1_440_000L)
        controller.setChapters(
            listOf(
                ParsedChapter(index = 1, title = "OP", startTimeMs = 10_000L, endTimeMs = 100_000L, type = ChapterType.OPENING)
            )
        )

        val outcome = controller.onPlaybackTick(autoSkipEnabled = true, controlsLocked = false)
        assertTrue(outcome.skipAction is SkipController.SkipAction.AutoSkip)
        assertEquals(100_000L, fakeEngine.playbackState.value.currentPositionMs)
    }

    @Test
    fun `advanceByAutoplay advances 2 times and prompts still watching on 3rd consecutive episode`() {
        controller.setPlaylist(listOf(ep1, ep2, ep3, ep4, ep5), ep1.fileId)
        controller.loadAndPlay(ep1, 0L)

        // 1st autoplay: ep1 -> ep2
        val r1 = controller.advanceByAutoplay()
        assertTrue(r1 is PlaybackController.AutoplayTransitionResult.Advanced)
        assertEquals(ep2, (r1 as PlaybackController.AutoplayTransitionResult.Advanced).nextItem)
        assertEquals(1, controller.consecutiveAutoplayedCount)

        // 2nd autoplay: ep2 -> ep3
        val r2 = controller.advanceByAutoplay()
        assertTrue(r2 is PlaybackController.AutoplayTransitionResult.Advanced)
        assertEquals(ep3, (r2 as PlaybackController.AutoplayTransitionResult.Advanced).nextItem)
        assertEquals(2, controller.consecutiveAutoplayedCount)

        // 3rd autoplay without user interaction: pauses and prompts "Ainda está assistindo?"
        val r3 = controller.advanceByAutoplay()
        assertEquals(PlaybackController.AutoplayTransitionResult.PromptStillWatching, r3)
        assertFalse(fakeEngine.playbackState.value.isPlaying)
        assertTrue(controller.stillWatchingPromptVisible.value)

        // User confirms still watching -> resets counter and advances to ep4
        val confirmed = controller.confirmStillWatching()
        assertTrue(confirmed is PlaybackController.AutoplayTransitionResult.Advanced)
        assertEquals(ep4, (confirmed as PlaybackController.AutoplayTransitionResult.Advanced).nextItem)
        assertEquals(0, controller.consecutiveAutoplayedCount)
        assertFalse(controller.stillWatchingPromptVisible.value)
        assertTrue(fakeEngine.playbackState.value.isPlaying)
    }

    @Test
    fun `onUserInteraction resets consecutiveAutoplayedCount and resets OSD timer when visible`() {
        controller.setPlaylist(listOf(ep1, ep2, ep3, ep4), ep1.fileId)
        controller.advanceByAutoplay()
        assertEquals(1, controller.consecutiveAutoplayedCount)

        controller.showOsd()
        controller.osdStateMachine.onShowAnimationCompleted()

        val action = controller.onUserInteraction()
        assertEquals(0, controller.consecutiveAutoplayedCount)
        assertEquals(OsdAction.ResetInactivityTimer, action)
    }

    private class RecordingPlayerEngine : PlayerEngine {
        private val _state = MutableStateFlow(EnginePlaybackState())
        override val playbackState: StateFlow<EnginePlaybackState> = _state.asStateFlow()
        var releaseCallCount = 0
            private set

        fun setDuration(durationMs: Long) {
            _state.value = _state.value.copy(durationMs = durationMs)
        }

        override fun load(item: PlaylistItem, startPositionMs: Long) {
            _state.value = _state.value.copy(
                currentItem = item,
                currentPositionMs = startPositionMs
            )
        }

        override fun play() {
            _state.value = _state.value.copy(isPlaying = true)
        }

        override fun pause() {
            _state.value = _state.value.copy(isPlaying = false)
        }

        override fun seekTo(positionMs: Long) {
            _state.value = _state.value.copy(currentPositionMs = positionMs.coerceAtLeast(0L))
        }

        override fun fastForward(deltaMs: Long) {
            seekTo(_state.value.currentPositionMs + deltaMs)
        }

        override fun rewind(deltaMs: Long) {
            seekTo((_state.value.currentPositionMs - deltaMs).coerceAtLeast(0L))
        }

        override fun setSpeed(speed: Float) {
            _state.value = _state.value.copy(speed = speed)
        }

        override fun release() {
            releaseCallCount++
            _state.value = EnginePlaybackState()
        }
    }
}
