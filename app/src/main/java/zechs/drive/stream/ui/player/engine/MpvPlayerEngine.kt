package zechs.drive.stream.ui.player.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.model.PlaylistItem
import zechs.mpv.MPVLib

/**
 * ARCH-01: MPV adapter implementing [PlayerEngine].
 */
class MpvPlayerEngine(
    private val onLoadItem: (item: PlaylistItem, startPositionMs: Long) -> Unit = { _, _ -> }
) : PlayerEngine {

    private val _playbackState = MutableStateFlow(EnginePlaybackState())
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private inline fun updateState(crossinline transform: (EnginePlaybackState) -> EnginePlaybackState) {
        _playbackState.value = transform(_playbackState.value)
    }

    /**
     * Called by MPV event observers when playback properties change.
     */
    fun onMpvPropertyChange(name: String, value: Any?) {
        when (name) {
            "pause" -> {
                val paused = value as? Boolean ?: false
                updateState { it.copy(isPlaying = !paused) }
            }
            "time-pos" -> {
                val seconds = (value as? Number)?.toDouble() ?: 0.0
                updateState { it.copy(currentPositionMs = (seconds * 1000).toLong()) }
            }
            "duration" -> {
                val seconds = (value as? Number)?.toDouble() ?: 0.0
                updateState { it.copy(durationMs = (seconds * 1000).toLong()) }
            }
            "speed" -> {
                val speed = (value as? Number)?.toFloat() ?: 1.0f
                updateState { it.copy(speed = speed) }
            }
            "paused-for-cache" -> {
                val buffering = value as? Boolean ?: false
                updateState { it.copy(isBuffering = buffering) }
            }
            "eof-reached" -> {
                val eof = value as? Boolean ?: false
                updateState { it.copy(isEnded = eof) }
            }
        }
    }

    override fun load(item: PlaylistItem, startPositionMs: Long) {
        updateState {
            it.copy(
                currentItem = item,
                currentPositionMs = startPositionMs,
                errorMessage = null,
                isEnded = false
            )
        }
        onLoadItem(item, startPositionMs)
    }

    override fun play() {
        MPVLib.setPropertyBoolean("pause", false)
        updateState { it.copy(isPlaying = true) }
    }

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
        updateState { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs.toDouble() / 1000.0
        MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))
        updateState { it.copy(currentPositionMs = positionMs) }
    }

    override fun fastForward(deltaMs: Long) {
        val current = _playbackState.value.currentPositionMs
        val duration = _playbackState.value.durationMs
        val target = (current + deltaMs).coerceAtMost(duration.coerceAtLeast(0L))
        seekTo(target)
    }

    override fun rewind(deltaMs: Long) {
        val current = _playbackState.value.currentPositionMs
        val target = (current - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble())
        updateState { it.copy(speed = speed) }
    }

    override fun release() {
        MPVLib.destroy()
    }
}
