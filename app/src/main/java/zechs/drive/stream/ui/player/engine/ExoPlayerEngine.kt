package zechs.drive.stream.ui.player.engine

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.model.PlaylistItem

/**
 * ARCH-01: ExoPlayer adapter implementing [PlayerEngine].
 */
class ExoPlayerEngine(
    private val exoPlayer: ExoPlayer,
    private val onLoadItem: (item: PlaylistItem, startPositionMs: Long) -> Unit = { _, _ -> }
) : PlayerEngine {

    private val _playbackState = MutableStateFlow(EnginePlaybackState())
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val isEnded = playbackState == Player.STATE_ENDED
            val isBuffering = playbackState == Player.STATE_BUFFERING
            updateState {
                it.copy(
                    isBuffering = isBuffering,
                    isEnded = isEnded,
                    currentPositionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState {
                it.copy(
                    isPlaying = isPlaying,
                    currentPositionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            updateState {
                it.copy(
                    errorMessage = error.localizedMessage ?: "Erro de reprodução no ExoPlayer"
                )
            }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
    }

    private inline fun updateState(crossinline transform: (EnginePlaybackState) -> EnginePlaybackState) {
        _playbackState.value = transform(_playbackState.value)
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
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updateState { it.copy(currentPositionMs = positionMs) }
    }

    override fun fastForward(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    override fun rewind(deltaMs: Long) {
        val target = (exoPlayer.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        updateState { it.copy(speed = speed) }
    }

    override fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
