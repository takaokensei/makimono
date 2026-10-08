package zechs.drive.stream.ui.player.utils

class BufferConfig {

    companion object {
        // Min buffer ExoPlayer tries to keep once playback is going (increased to 45 secs for 4K / high-bitrate)
        const val MIN_BUFFER_DURATION = 45_000

        // Max buffer while playing (increased to 180 secs / 3 min to absorb network jitter)
        const val MAX_BUFFER_DURATION = 180_000

        // Min buffer before starting playback (increased to 6 secs)
        const val MIN_PLAYBACK_START_BUFFER = 6_000

        // Min buffer when video is resumed after rebuffering (increased to 8 secs)
        const val MIN_PLAYBACK_RESUME_BUFFER = 8_000

        // Back buffer to retain in memory for instant small rewinds (increased to 30 secs)
        const val BACK_BUFFER_DURATION = 30_000
    }

}