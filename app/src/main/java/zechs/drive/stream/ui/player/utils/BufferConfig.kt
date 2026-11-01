package zechs.drive.stream.ui.player.utils

class BufferConfig {

    companion object {
        // Min buffer ExoPlayer tries to keep once playback is going (15s keeps memory bounded on high-bitrate Blu-Ray MKVs)
        const val MIN_BUFFER_DURATION = 15_000

        // Max buffer while playing (45s prevents 180s OOM heap spikes on 80+ Mbps streams)
        const val MAX_BUFFER_DURATION = 45_000

        // Min buffer before starting playback (1.8s for instant TTFF start matching Netflix/Crunchyroll)
        const val MIN_PLAYBACK_START_BUFFER = 1_800

        // Min buffer when video is resumed after rebuffering (3s)
        const val MIN_PLAYBACK_RESUME_BUFFER = 3_000

        // Back buffer to retain in memory for instant small rewinds (10s)
        const val BACK_BUFFER_DURATION = 10_000

        // Strict byte thresholds so DefaultLoadControl never overflows the JVM heap
        const val TARGET_BUFFER_BYTES_STANDARD = 48 * 1024 * 1024 // 48 MB
        const val TARGET_BUFFER_BYTES_LOW_RAM = 24 * 1024 * 1024  // 24 MB
    }

}
