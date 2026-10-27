package zechs.drive.stream.playback.ambient

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.utils.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
@OptIn(UnstableApi::class)
class AmbientSoundscapeController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "AmbientSoundscape"
        const val DEFAULT_GAIN_DB = -22.0f // Gentle nocturnal volume (~0.08 linear amplitude)
    }

    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fadeJob: Job? = null

    private val _nowPlaying = MutableStateFlow<AmbientTrack?>(null)
    val nowPlaying: StateFlow<AmbientTrack?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var targetVolume = dbToLinear(DEFAULT_GAIN_DB)
    private var currentPlaylist = AmbientTrackCatalog.OPUS_CANONICAL_CHAPTERS
    private var currentTrackIndex = 0

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (player != null) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            player = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
                .build()
                .apply {
                    volume = targetVolume
                    repeatMode = Player.REPEAT_MODE_ALL
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                playNextTrack()
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                        }
                    })
                }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed initializing Ambient ExoPlayer", e)
        }
    }

    fun playTrack(track: AmbientTrack, mediaUri: String? = track.sourceUri) {
        val p = player ?: return
        val uriToPlay = mediaUri ?: track.sourceUri
        if (uriToPlay.isNullOrBlank()) {
            AppLog.w(TAG, "No URI to play for track: ${track.title}")
            return
        }

        try {
            _nowPlaying.value = track
            val mediaItem = MediaItem.fromUri(uriToPlay)
            p.setMediaItem(mediaItem)
            if (track.startMs > 0L) {
                p.seekTo(track.startMs)
            }
            p.prepare()
            p.playWhenReady = true
            fadeIn()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed playing ambient track: ${track.title}", e)
        }
    }

    fun playNextTrack() {
        if (currentPlaylist.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % currentPlaylist.size
        val next = currentPlaylist[currentTrackIndex]
        playTrack(next)
    }

    fun playPreviousTrack() {
        if (currentPlaylist.isEmpty()) return
        currentTrackIndex = if (currentTrackIndex - 1 < 0) currentPlaylist.size - 1 else currentTrackIndex - 1
        val prev = currentPlaylist[currentTrackIndex]
        playTrack(prev)
    }

    fun setPlaylist(tracks: List<AmbientTrack>, startIdx: Int = 0) {
        if (tracks.isEmpty()) return
        currentPlaylist = tracks
        currentTrackIndex = startIdx.coerceIn(0, tracks.size - 1)
        _nowPlaying.value = currentPlaylist[currentTrackIndex]
    }

    fun setGainDb(gainDb: Float) {
        targetVolume = dbToLinear(gainDb)
        player?.volume = targetVolume
    }

    fun fadeOut(durationMs: Long = 700L, onComplete: () -> Unit = {}) {
        val p = player ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startVol = p.volume
            val steps = 14
            val stepDelay = durationMs / steps
            for (i in steps downTo 0) {
                p.volume = startVol * (i.toFloat() / steps)
                delay(stepDelay)
            }
            p.pause()
            onComplete()
        }
    }

    fun fadeIn(durationMs: Long = 1500L) {
        val p = player ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            p.volume = 0f
            p.play()
            val steps = 15
            val stepDelay = durationMs / steps
            for (i in 1..steps) {
                p.volume = targetVolume * (i.toFloat() / steps)
                delay(stepDelay)
            }
            p.volume = targetVolume
        }
    }

    fun stop() {
        fadeJob?.cancel()
        player?.pause()
        _isPlaying.value = false
    }

    fun release() {
        fadeJob?.cancel()
        player?.release()
        player = null
    }

    private fun dbToLinear(db: Float): Float {
        // V = 10^(dB/20) clamped to [0.001, 1.0]
        return 10.0.pow(db.toDouble() / 20.0).toFloat().coerceIn(0.001f, 1.0f)
    }
}
