package makimono.dossie.provas.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MakimonoPlayerOsdSkipCoordinatorTest {

    private lateinit var coordinator: MakimonoPlayerOsdSkipCoordinator

    private val testIntro = SkipInterval(
        type = SkipType.INTRO,
        startMs = 90_000L,
        endMs = 180_000L,
        label = "Pular Abertura"
    )

    @Before
    fun setUp() {
        coordinator = MakimonoPlayerOsdSkipCoordinator(autoHideTimeoutMs = 3000L, skipBufferOffsetMs = 100L)
    }

    @Test
    fun controlsAreInitiallyHidden() {
        assertFalse(coordinator.isControlsVisible)
        assertEquals(SkipButtonState.Hidden, coordinator.skipButtonState)
    }

    @Test
    fun userActivityShowsControlsAndAutoHidesAfter3000ms() {
        coordinator.onUserActivity(currentTimeMs = 10_000L)
        assertTrue(coordinator.isControlsVisible)

        // 2 segundos depois -> ainda visível
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 5000L, intervals = emptyList(), clockTimeMs = 12_000L)
        assertTrue(coordinator.isControlsVisible)

        // 3.5 segundos depois -> auto-hide acionado
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 6500L, intervals = emptyList(), clockTimeMs = 13_500L)
        assertFalse(coordinator.isControlsVisible)
    }

    @Test
    fun enteringSkipIntervalShowsSkipButton() {
        // Posição antes da intro (80s)
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 80_000L, intervals = listOf(testIntro), clockTimeMs = 1000L)
        assertEquals(SkipButtonState.Hidden, coordinator.skipButtonState)

        // Posição dentro da intro (95s)
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 95_000L, intervals = listOf(testIntro), clockTimeMs = 2000L)
        assertTrue(coordinator.skipButtonState is SkipButtonState.Visible)

        val visible = coordinator.skipButtonState as SkipButtonState.Visible
        assertEquals(180_100L, visible.targetSeekPositionMs) // 180_000 + 100ms buffer
    }

    @Test
    fun performingSkipReturnsSeekTargetAndHidesButton() {
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 100_000L, intervals = listOf(testIntro), clockTimeMs = 1000L)
        val seekTarget = coordinator.performSkip()

        assertEquals(180_100L, seekTarget)
        assertEquals(SkipButtonState.Hidden, coordinator.skipButtonState)
    }

    @Test
    fun leavingIntervalAutoHidesButtonWithoutUserAction() {
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 150_000L, intervals = listOf(testIntro), clockTimeMs = 1000L)
        assertTrue(coordinator.skipButtonState is SkipButtonState.Visible)

        // Playback passou do fim da intro (181s)
        coordinator.onPlaybackTimeTick(currentPlaybackPositionMs = 181_000L, intervals = listOf(testIntro), clockTimeMs = 2000L)
        assertEquals(SkipButtonState.Hidden, coordinator.skipButtonState)
    }
}
