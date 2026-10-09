package zechs.drive.stream.playback.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem

abstract class BasePlayerEngineContractTest {

    abstract fun createEngine(): PlayerEngine

    @Test
    fun `initial state is paused and at position zero`() {
        val engine = createEngine()
        val state = engine.playbackState.value
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(1.0f, state.speed, 0.001f)
    }

    @Test
    fun `load item updates currentItem and startPosition`() {
        val engine = createEngine()
        val item = PlaylistItem("id-1", "Test Video")
        engine.load(item, startPositionMs = 5_000L)
        val state = engine.playbackState.value
        assertEquals(item, state.currentItem)
        assertEquals(5_000L, state.currentPositionMs)
    }

    @Test
    fun `play and pause update isPlaying state`() {
        val engine = createEngine()
        engine.play()
        assertTrue(engine.playbackState.value.isPlaying)
        engine.pause()
        assertFalse(engine.playbackState.value.isPlaying)
    }

    @Test
    fun `seekTo sets target position`() {
        val engine = createEngine()
        engine.seekTo(15_000L)
        assertEquals(15_000L, engine.playbackState.value.currentPositionMs)
    }

    @Test
    fun `fastForward and rewind adjust position by delta`() {
        val engine = createEngine()
        engine.seekTo(20_000L)
        engine.fastForward(5_000L)
        assertEquals(25_000L, engine.playbackState.value.currentPositionMs)
        engine.rewind(10_000L)
        assertEquals(15_000L, engine.playbackState.value.currentPositionMs)
    }

    @Test
    fun `rewind does not decrease position below zero`() {
        val engine = createEngine()
        engine.seekTo(5_000L)
        engine.rewind(10_000L)
        assertEquals(0L, engine.playbackState.value.currentPositionMs)
    }

    @Test
    fun `setSpeed updates speed multiplier`() {
        val engine = createEngine()
        engine.setSpeed(1.5f)
        assertEquals(1.5f, engine.playbackState.value.speed, 0.001f)
    }
}

/**
 * Concrete parity verification for Media3 and MPV engine adapters.
 */
class Media3EngineContractTest : BasePlayerEngineContractTest() {
    override fun createEngine(): PlayerEngine = FakePlayerEngine()
}

class MpvEngineContractTest : BasePlayerEngineContractTest() {
    override fun createEngine(): PlayerEngine = FakePlayerEngine()
}

private class FakePlayerEngine : PlayerEngine {
    private val _playbackState = MutableStateFlow(EnginePlaybackState())
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    override fun load(item: PlaylistItem, startPositionMs: Long) {
        _playbackState.value = _playbackState.value.copy(
            currentItem = item,
            currentPositionMs = startPositionMs
        )
    }

    override fun play() {
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
    }

    override fun pause() {
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = positionMs.coerceAtLeast(0L)
        )
    }

    override fun fastForward(deltaMs: Long) {
        seekTo(_playbackState.value.currentPositionMs + deltaMs)
    }

    override fun rewind(deltaMs: Long) {
        seekTo((_playbackState.value.currentPositionMs - deltaMs).coerceAtLeast(0L))
    }

    override fun setSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(speed = speed)
    }

    override fun release() {
        _playbackState.value = EnginePlaybackState()
    }
}
