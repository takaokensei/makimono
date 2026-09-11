package zechs.drive.stream.ui.player.utils

class BufferConfig {

    companion object {
        // Min buffer ExoPlayer tries to keep once playback is going (25 secs)
        const val MIN_BUFFER_DURATION = 25_000

        // Max buffer while playing (90 secs)
        const val MAX_BUFFER_DURATION = 90_000

        // Min buffer before starting playback (3 secs)
        const val MIN_PLAYBACK_START_BUFFER = 3_000

        // Min buffer when video is resumed after rebuffering (4 secs)
        const val MIN_PLAYBACK_RESUME_BUFFER = 4_000

        // Back buffer to retain in memory for instant small rewinds (20 secs)
        const val BACK_BUFFER_DURATION = 20_000
    }

}