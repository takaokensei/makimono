package zechs.drive.stream.ui.player.engine

import kotlinx.coroutines.flow.StateFlow
import zechs.drive.stream.data.model.PlaylistItem

/**
 * ARCH-01: Common player engine contract.
 * Abstracts player operations across ExoPlayer and MPV engines.
 */
interface PlayerEngine {

    /**
     * Observable playback state stream.
     */
    val playbackState: StateFlow<EnginePlaybackState>

    /**
     * Loads a playlist item at an optional start position in milliseconds.
     */
    fun load(item: PlaylistItem, startPositionMs: Long = 0L)

    /**
     * Resumes playback.
     */
    fun play()

    /**
     * Pauses playback.
     */
    fun pause()

    /**
     * Seeks to an absolute timestamp in milliseconds.
     */
    fun seekTo(positionMs: Long)

    /**
     * Fast-forwards by delta milliseconds.
     */
    fun fastForward(deltaMs: Long = 10_000L)

    /**
     * Rewinds by delta milliseconds.
     */
    fun rewind(deltaMs: Long = 10_000L)

    /**
     * Sets playback speed multiplier.
     */
    fun setSpeed(speed: Float)

    /**
     * Releases player resources.
     */
    fun release()
}

/**
 * Engine-agnostic snapshot of playback state.
 */
data class EnginePlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val currentItem: PlaylistItem? = null,
    val isEnded: Boolean = false,
    val errorMessage: String? = null
)
