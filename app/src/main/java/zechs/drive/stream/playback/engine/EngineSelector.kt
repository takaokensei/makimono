package zechs.drive.stream.playback.engine

/**
 * Media characteristics relevant for playback engine routing.
 */
data class MediaInfo(
    val name: String,
    val extension: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val hasAssSubtitles: Boolean = false,
    val hasPgsSubtitles: Boolean = false,
    val hasSrtSubtitles: Boolean = false,
    val isHdr: Boolean = false
)

/**
 * Device platform capabilities and hardware decoder limits.
 */
data class DeviceCaps(
    val isTv: Boolean = false,
    val isLowRamDevice: Boolean = false,
    val supportedVideoCodecs: Set<String> = emptySet(),
    val supportedAudioCodecs: Set<String> = emptySet(),
    val maxSupportedAudioChannels: Int = 2,
    val supportsHardwareAss: Boolean = false
)

/**
 * Result of the engine selection decision.
 */
sealed interface EngineChoice {
    val reason: String

    data class Media3(override val reason: String) : EngineChoice
    data class Mpv(override val reason: String) : EngineChoice
}

/**
 * User manual preference override for playback engine.
 */
enum class UserEnginePreference {
    AUTO,
    FORCE_MEDIA3,
    FORCE_MPV
}

/**
 * ADR-002: Intelligent playback engine selector.
 * Chooses between primary Media3 (ExoPlayer) for efficient hardware decoding,
 * MediaSession and PiP support, or specialized MPV (libmpv) for advanced ASS/SSA
 * stylized subtitle rendering and exotic codec fallback.
 */
object EngineSelector {

    fun selectEngine(
        media: MediaInfo,
        device: DeviceCaps,
        preference: UserEnginePreference = UserEnginePreference.AUTO
    ): EngineChoice {
        when (preference) {
            UserEnginePreference.FORCE_MEDIA3 -> return EngineChoice.Media3("Forced by user preference")
            UserEnginePreference.FORCE_MPV -> return EngineChoice.Mpv("Forced by user preference")
            UserEnginePreference.AUTO -> { /* continue */ }
        }

        // Rule 1: Stylized subtitles (.ass/.ssa) require MPV with libass
        if (media.hasAssSubtitles && !device.supportsHardwareAss) {
            return EngineChoice.Mpv("ASS/SSA stylized subtitles require libass rendering")
        }

        // Rule 2: Audio codec compatibility with hardware/MediaCodec
        if (!media.audioCodec.isNullOrBlank() && device.supportedAudioCodecs.isNotEmpty()) {
            val normalizedAudio = media.audioCodec.lowercase().trim()
            val supported = device.supportedAudioCodecs.map { it.lowercase().trim() }
            if (!supported.contains(normalizedAudio)) {
                return EngineChoice.Mpv("Audio codec '${media.audioCodec}' not supported by hardware/MediaCodec")
            }
        }

        // Rule 3: Audio channels exceeding hardware passthrough/downmix limit
        if (media.audioChannels != null && media.audioChannels > device.maxSupportedAudioChannels) {
            return EngineChoice.Mpv("Audio channels (${media.audioChannels}) exceed device maximum (${device.maxSupportedAudioChannels})")
        }

        // Rule 4: Video codec compatibility
        if (!media.videoCodec.isNullOrBlank() && device.supportedVideoCodecs.isNotEmpty()) {
            val normalizedVideo = media.videoCodec.lowercase().trim()
            val supported = device.supportedVideoCodecs.map { it.lowercase().trim() }
            if (!supported.contains(normalizedVideo)) {
                return EngineChoice.Mpv("Video codec '${media.videoCodec}' not supported by hardware/MediaCodec")
            }
        }

        // Rule 5: Low-RAM device favors Media3 (lighter footprint, MediaCodec zero-copy)
        if (device.isLowRamDevice) {
            return EngineChoice.Media3("Low-RAM device benefits from Media3 lighter memory footprint")
        }

        // Default: Media3 primary hardware engine
        return EngineChoice.Media3("Default primary hardware engine (Media3)")
    }
}
