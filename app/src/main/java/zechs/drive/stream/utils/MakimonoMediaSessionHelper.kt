package zechs.drive.stream.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log

/**
 * FEAT-08: MediaSession helper providing platform-native media controls
 * for Bluetooth headsets, Android Auto, lockscreen controls, and Android TV remotes.
 * Compatible with both ExoPlayer and MPV engines.
 */
class MakimonoMediaSessionHelper(
    context: Context,
    tag: String = "MakimonoMediaSession",
    private val callback: Callback
) {

    companion object {
        private const val TAG = "MakimonoMediaSession"
    }

    interface Callback {
        fun onPlay()
        fun onPause()
        fun onSkipToNext()
        fun onSkipToPrevious()
        fun onSeekTo(positionMs: Long)
        fun onStop()
        fun onFastForward() {}
        fun onRewind() {}
    }

    private var mediaSession: MediaSession? = null

    init {
        try {
            mediaSession = MediaSession(context, tag).apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        Log.d(TAG, "MediaSession callback: onPlay")
                        callback.onPlay()
                    }

                    override fun onPause() {
                        Log.d(TAG, "MediaSession callback: onPause")
                        callback.onPause()
                    }

                    override fun onSkipToNext() {
                        Log.d(TAG, "MediaSession callback: onSkipToNext")
                        callback.onSkipToNext()
                    }

                    override fun onSkipToPrevious() {
                        Log.d(TAG, "MediaSession callback: onSkipToPrevious")
                        callback.onSkipToPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        Log.d(TAG, "MediaSession callback: onSeekTo $pos")
                        callback.onSeekTo(pos)
                    }

                    override fun onFastForward() {
                        callback.onFastForward()
                    }

                    override fun onRewind() {
                        callback.onRewind()
                    }

                    override fun onStop() {
                        Log.d(TAG, "MediaSession callback: onStop")
                        callback.onStop()
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaSession", e)
        }
    }

    fun updatePlaybackState(
        isPlaying: Boolean,
        positionMs: Long,
        speed: Float = 1.0f,
        canSkipNext: Boolean = true,
        canSkipPrevious: Boolean = true
    ) {
        val session = mediaSession ?: return
        try {
            var actions = PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND

            if (canSkipNext) {
                actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
            }
            if (canSkipPrevious) {
                actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            }

            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED

            val playbackState = PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs.coerceAtLeast(0L), speed)
                .build()

            session.setPlaybackState(playbackState)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating PlaybackState", e)
        }
    }

    fun updateMetadata(
        title: String,
        seriesName: String? = null,
        durationMs: Long = 0L,
        artwork: Bitmap? = null
    ) {
        val session = mediaSession ?: return
        try {
            val builder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)

            if (!seriesName.isNullOrBlank()) {
                builder.putString(MediaMetadata.METADATA_KEY_ALBUM, seriesName)
                builder.putString(MediaMetadata.METADATA_KEY_ARTIST, seriesName)
                builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, seriesName)
            }

            if (durationMs > 0L) {
                builder.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            }

            if (artwork != null) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            }

            session.setMetadata(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating MediaMetadata", e)
        }
    }

    fun release() {
        try {
            mediaSession?.let {
                it.isActive = false
                it.release()
            }
            mediaSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaSession", e)
        }
    }
}
