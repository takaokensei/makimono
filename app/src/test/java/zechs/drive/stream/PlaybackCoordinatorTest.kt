package zechs.drive.stream

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.ui.player.engine.EnginePlaybackState
import zechs.drive.stream.ui.player.engine.PlaybackCoordinator
import zechs.drive.stream.ui.player.engine.PlayerEngine
import zechs.drive.stream.utils.PlaybackProgressPolicy

class PlaybackCoordinatorTest {

    private lateinit var coordinator: PlaybackCoordinator

    private val item1 = PlaylistItem("id-1", "Episode 1", null)
    private val item2 = PlaylistItem("id-2", "Episode 2", null)
    private val item3 = PlaylistItem("id-3", "Episode 3", null)
    private val playlist = listOf(item1, item2, item3)

    @Before
    fun setUp() {
        coordinator = PlaybackCoordinator(PlaybackProgressPolicy)
    }

    @Test
    fun setPlaylist_resolvesCurrentNextAndPrev() = runTest {
        coordinator.setPlaylist(playlist, "id-2")
        val state = coordinator.state.value

        assertEquals(item2, state.currentItem)
        assertEquals(item3, state.nextEpisode)
        assertEquals(item1, state.prevEpisode)
    }

    @Test
    fun setPlaylist_firstItem_hasNullPrev() = runTest {
        coordinator.setPlaylist(playlist, "id-1")
        val state = coordinator.state.value

        assertEquals(item1, state.currentItem)
        assertEquals(item2, state.nextEpisode)
        assertNull(state.prevEpisode)
    }

    @Test
    fun setPlaylist_lastItem_hasNullNext() = runTest {
        coordinator.setPlaylist(playlist, "id-3")
        val state = coordinator.state.value

        assertEquals(item3, state.currentItem)
        assertNull(state.nextEpisode)
        assertEquals(item2, state.prevEpisode)
    }

    @Test
    fun advanceToNext_advancesPlaylistPosition() = runTest {
        coordinator.setPlaylist(playlist, "id-1")
        val next = coordinator.advanceToNext()

        assertEquals(item2, next)
        assertEquals(item2, coordinator.state.value.currentItem)
        assertEquals(item3, coordinator.getNextEpisode())
        assertEquals(item1, coordinator.getPreviousEpisode())
    }

    @Test
    fun advanceToPrevious_recedesPlaylistPosition() = runTest {
        coordinator.setPlaylist(playlist, "id-2")
        val prev = coordinator.advanceToPrevious()

        assertEquals(item1, prev)
        assertEquals(item1, coordinator.state.value.currentItem)
        assertEquals(item2, coordinator.getNextEpisode())
        assertNull(coordinator.getPreviousEpisode())
    }

    @Test
    fun onPlaybackTick_triggersCountdownWithinWindow() = runTest {
        coordinator.setPlaylist(playlist, "id-1")
        val durationMs = 1_440_000L // 24 min
        val positionMs = durationMs - 5_000L // 5 sec before end

        val decision = coordinator.onPlaybackTick(positionMs, durationMs)
        assertTrue(decision is PlaybackProgressPolicy.AutoplayDecision.ShowCountdown)

        val uiState = coordinator.state.value.autoplayUiState
        assertTrue(uiState is PlaybackCoordinator.AutoplayUiState.Countdown)
        assertEquals(5, (uiState as PlaybackCoordinator.AutoplayUiState.Countdown).secondsRemaining)
        assertEquals(item2, uiState.nextItem)
    }

    @Test
    fun onPlaybackTick_triggersAdvanceNearEnd() = runTest {
        coordinator.setPlaylist(playlist, "id-1")
        val durationMs = 1_440_000L
        val positionMs = durationMs - 500L // within 1 second

        val decision = coordinator.onPlaybackTick(positionMs, durationMs)
        assertTrue(decision is PlaybackProgressPolicy.AutoplayDecision.PlayNext)

        val uiState = coordinator.state.value.autoplayUiState
        assertTrue(uiState is PlaybackCoordinator.AutoplayUiState.Advance)
        assertEquals(item2, (uiState as PlaybackCoordinator.AutoplayUiState.Advance).nextItem)
    }

    @Test
    fun cancelAutoplay_suppressesCountdownAndAdvance() = runTest {
        coordinator.setPlaylist(playlist, "id-1")
        val durationMs = 1_440_000L
        coordinator.cancelAutoplay()

        val decision = coordinator.onPlaybackTick(durationMs - 5_000L, durationMs)
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.None, decision)
        assertEquals(PlaybackCoordinator.AutoplayUiState.Hidden, coordinator.state.value.autoplayUiState)
    }

    @Test
    fun controlsLocked_dismissesAutoplay() = runTest {
        coordinator.setPlaylist(playlist, "id-1")
        val durationMs = 1_440_000L

        val decision = coordinator.onPlaybackTick(durationMs - 5_000L, durationMs, controlsLocked = true)
        assertEquals(PlaybackProgressPolicy.AutoplayDecision.DismissCard, decision)
        assertEquals(PlaybackCoordinator.AutoplayUiState.Hidden, coordinator.state.value.autoplayUiState)
    }

    @Test
    fun progressEvaluation_calculatesWatchedAndSave() = runTest {
        val durationMs = 1_440_000L // 24 min

        // 5% -> neither save nor watched
        assertFalse(coordinator.shouldSaveProgress(72_000L, durationMs))

        // 15% -> should save, not watched
        assertTrue(coordinator.shouldSaveProgress(216_000L, durationMs))

        // 96% -> should save and is watched
        coordinator.onPlaybackTick(1_390_000L, durationMs)
        assertTrue(coordinator.state.value.isWatched)
        assertEquals(96, coordinator.state.value.watchProgressPercent)
    }

    @Test
    fun playerEngineContract_fakeImplementation() = runTest {
        val fakeEngine = object : PlayerEngine {
            private val _state = MutableStateFlow(EnginePlaybackState())
            override val playbackState: StateFlow<EnginePlaybackState> = _state.asStateFlow()

            override fun load(item: PlaylistItem, startPositionMs: Long) {
                _state.value = _state.value.copy(currentItem = item, currentPositionMs = startPositionMs)
            }
            override fun play() {
                _state.value = _state.value.copy(isPlaying = true)
            }
            override fun pause() {
                _state.value = _state.value.copy(isPlaying = false)
            }
            override fun seekTo(positionMs: Long) {
                _state.value = _state.value.copy(currentPositionMs = positionMs)
            }
            override fun fastForward(deltaMs: Long) {
                _state.value = _state.value.copy(currentPositionMs = _state.value.currentPositionMs + deltaMs)
            }
            override fun rewind(deltaMs: Long) {
                _state.value = _state.value.copy(currentPositionMs = (_state.value.currentPositionMs - deltaMs).coerceAtLeast(0L))
            }
            override fun setSpeed(speed: Float) {
                _state.value = _state.value.copy(speed = speed)
            }
            override fun release() {
                _state.value = EnginePlaybackState()
            }
        }

        fakeEngine.playbackState.test {
            assertEquals(EnginePlaybackState(), awaitItem())

            fakeEngine.load(item1, 5000L)
            val loadedState = awaitItem()
            assertEquals(item1, loadedState.currentItem)
            assertEquals(5000L, loadedState.currentPositionMs)

            fakeEngine.play()
            assertTrue(awaitItem().isPlaying)

            fakeEngine.fastForward(10_000L)
            assertEquals(15_000L, awaitItem().currentPositionMs)

            fakeEngine.rewind(5_000L)
            assertEquals(10_000L, awaitItem().currentPositionMs)

            fakeEngine.pause()
            assertFalse(awaitItem().isPlaying)

            fakeEngine.setSpeed(1.5f)
            assertEquals(1.5f, awaitItem().speed)

            fakeEngine.release()
            assertEquals(EnginePlaybackState(), awaitItem())
        }
    }
}
