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
    val ruleId: String

    data class Media3(
        override val reason: String,
        override val ruleId: String = "default_media3"
    ) : EngineChoice

    data class Mpv(
        override val reason: String,
        override val ruleId: String = "default_mpv"
    ) : EngineChoice
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
 * Regra declarativa de roteamento de motor inspirada no PlayerCoreFactory do Kodi (T-309).
 */
data class EngineRoutingRule(
    val id: String,
    val summary: String,
    val matches: (MediaInfo, DeviceCaps) -> Boolean,
    val resolve: (MediaInfo, DeviceCaps) -> EngineChoice
)

/**
 * ADR-002 / T-309: Seletor declarativo de motor de reprodução inspirado no PlayerCoreFactory (Kodi).
 * Avalia regras ordenadas para escolher entre Media3 (ExoPlayer) para decodificação por hardware,
 * HDR e baixo consumo de memória, ou MPV (libmpv/libass) para legendas ASS/SSA estilizadas e codecs exóticos.
 */
object EngineSelector {

    val declarativeRules: List<EngineRoutingRule> = listOf(
        EngineRoutingRule(
            id = "rule_ass_libass",
            summary = "ASS/SSA → MPV (renderização libass)",
            matches = { media, device -> media.hasAssSubtitles && !device.supportsHardwareAss },
            resolve = { _, _ ->
                EngineChoice.Mpv(
                    reason = "ASS/SSA stylized subtitles require libass rendering",
                    ruleId = "rule_ass_libass"
                )
            }
        ),
        EngineRoutingRule(
            id = "rule_pgs_subtitles",
            summary = "PGS (Blu-ray) → MPV",
            matches = { media, _ -> media.hasPgsSubtitles },
            resolve = { _, _ ->
                EngineChoice.Mpv(
                    reason = "PGS bitmap subtitles routed to MPV",
                    ruleId = "rule_pgs_subtitles"
                )
            }
        ),
        EngineRoutingRule(
            id = "rule_audio_codec",
            summary = "Áudio sem codec HW (TrueHD/DTS-HD) → MPV (FFmpeg)",
            matches = { media, device ->
                !media.audioCodec.isNullOrBlank() &&
                    device.supportedAudioCodecs.isNotEmpty() &&
                    !device.supportedAudioCodecs.map { it.lowercase().trim() }
                        .contains(media.audioCodec.lowercase().trim())
            },
            resolve = { media, _ ->
                EngineChoice.Mpv(
                    reason = "Audio codec '${media.audioCodec}' not supported by hardware/MediaCodec",
                    ruleId = "rule_audio_codec"
                )
            }
        ),
        EngineRoutingRule(
            id = "rule_audio_channels",
            summary = "Canais de áudio acima do limite HW → MPV (downmix)",
            matches = { media, device ->
                media.audioChannels != null && media.audioChannels > device.maxSupportedAudioChannels
            },
            resolve = { media, device ->
                EngineChoice.Mpv(
                    reason = "Audio channels (${media.audioChannels}) exceed device maximum (${device.maxSupportedAudioChannels})",
                    ruleId = "rule_audio_channels"
                )
            }
        ),
        EngineRoutingRule(
            id = "rule_video_codec",
            summary = "Vídeo sem codec HW (VC-1/Hi10P) → MPV (FFmpeg)",
            matches = { media, device ->
                !media.videoCodec.isNullOrBlank() &&
                    device.supportedVideoCodecs.isNotEmpty() &&
                    !device.supportedVideoCodecs.map { it.lowercase().trim() }
                        .contains(media.videoCodec.lowercase().trim())
            },
            resolve = { media, _ ->
                EngineChoice.Mpv(
                    reason = "Video codec '${media.videoCodec}' not supported by hardware/MediaCodec",
                    ruleId = "rule_video_codec"
                )
            }
        ),
        EngineRoutingRule(
            id = "rule_hdr_media3",
            summary = "HDR10 / Dolby Vision → ExoPlayer (Media3 surface passthrough)",
            matches = { media, _ -> media.isHdr },
            resolve = { _, _ ->
                EngineChoice.Media3(
                    reason = "HDR content routed to Media3 hardware surface",
                    ruleId = "rule_hdr_media3"
                )
            }
        ),
        EngineRoutingRule(
            id = "rule_low_ram",
            summary = "Pouca RAM → ExoPlayer (MediaCodec zero-copy)",
            matches = { _, device -> device.isLowRamDevice },
            resolve = { _, _ ->
                EngineChoice.Media3(
                    reason = "Low-RAM device benefits from Media3 lighter memory footprint",
                    ruleId = "rule_low_ram"
                )
            }
        )
    )

    /**
     * Retorna a lista legível das regras de roteamento automático para exibição nas Configurações.
     */
    fun describeActiveRules(): List<String> = declarativeRules.map { it.summary }

    fun selectEngine(
        media: MediaInfo,
        device: DeviceCaps,
        preference: UserEnginePreference = UserEnginePreference.AUTO
    ): EngineChoice {
        when (preference) {
            UserEnginePreference.FORCE_MEDIA3 ->
                return EngineChoice.Media3("Forced by user preference", ruleId = "force_media3")
            UserEnginePreference.FORCE_MPV ->
                return EngineChoice.Mpv("Forced by user preference", ruleId = "force_mpv")
            UserEnginePreference.AUTO -> { /* avalia regras declarativas */ }
        }

        for (rule in declarativeRules) {
            if (rule.matches(media, device)) {
                return rule.resolve(media, device)
            }
        }

        return EngineChoice.Media3(
            reason = "Default primary hardware engine (Media3)",
            ruleId = "default_media3"
        )
    }
}
