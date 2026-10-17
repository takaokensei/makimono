package zechs.drive.stream.ui.player2

import java.util.Locale

/**
 * Unified technical telemetry formatter for both Media3 (ExoPlayer) and libmpv engines (P-17).
 * Presents properties as reported; unavailable values remain explicit.
 */
object MpvTechnicalInfoDisplay {
    data class Row(val id: String, val title: String, val value: String)

    fun rows(
        videoCodec: String?,
        width: Int?,
        height: Int?,
        containerFps: Double?,
        activeDecoder: String?,
        audioCodec: String?,
        channels: String?,
        sampleRateHz: Int?,
        subtitleTrack: String?,
        cacheDurationSeconds: Double?,
        engineName: String? = null,
        hdrSummary: String? = null,
        afrSummary: String? = null,
        colorSpaceAndPixelFormat: String? = null,
        debandSummary: String? = null
    ): List<Row> = buildList {
        if (!engineName.isNullOrBlank()) {
            add(Row("engine", "Motor", engineName.trim()))
        }
        add(Row("video", "Vídeo", present(videoCodec)))
        add(Row("resolution", "Resolução", dimensions(width, height)))
        add(Row("fps", "FPS do contêiner", fps(containerFps)))
        add(Row("decoder", "Decodificador ativo", decoder(activeDecoder)))
        if (!colorSpaceAndPixelFormat.isNullOrBlank()) {
            add(Row("colorspace", "Formato e Cores", colorSpaceAndPixelFormat.trim()))
        }
        if (!debandSummary.isNullOrBlank()) {
            add(Row("deband", "Deband / Dither", debandSummary.trim()))
        }
        add(Row("audio", "Áudio", audio(audioCodec, channels, sampleRateHz)))
        add(Row("subtitle", "Legenda", subtitleTrack?.takeIf(String::isNotBlank) ?: "Indisponível"))
        add(Row("buffer", "Cache do demuxer", cache(cacheDurationSeconds)))
        if (!hdrSummary.isNullOrBlank()) {
            add(Row("hdr", "Alcance Dinâmico", hdrSummary.trim()))
        }
        if (!afrSummary.isNullOrBlank()) {
            add(Row("afr", "Taxa de Quadros (AFR)", afrSummary.trim()))
        }
    }

    /**
     * Builds unified technical info rows from Media3 (ExoPlayer) format properties (P-17).
     */
    fun rowsForMedia3(
        videoMimeType: String?,
        width: Int?,
        height: Int?,
        frameRate: Float?,
        activeDecoder: String?,
        audioMimeType: String?,
        channelCount: Int?,
        sampleRateHz: Int?,
        subtitleLabel: String?,
        bufferedDurationSeconds: Double?,
        hdrSummary: String? = null,
        afrSummary: String? = null
    ): List<Row> {
        val videoCodec = zechs.drive.stream.ui.player.MediaInfoDisplay.videoCodec(videoMimeType)
            .takeUnless { it == "Codec desconhecido" }
        val audioCodec = when {
            audioMimeType.isNullOrBlank() -> null
            audioMimeType.contains("mp4a", true) || audioMimeType.contains("aac", true) -> "AAC"
            audioMimeType.contains("opus", true) -> "Opus"
            audioMimeType.contains("flac", true) -> "FLAC"
            audioMimeType.contains("ac3", true) && !audioMimeType.contains("eac3", true) -> "Dolby Digital (AC-3)"
            audioMimeType.contains("eac3", true) -> "Dolby Digital Plus (E-AC-3)"
            else -> audioMimeType.substringAfterLast('/').takeIf(String::isNotBlank)
        }
        val channels = when {
            channelCount == 1 -> "Mono (1.0)"
            channelCount == 2 -> "Estéreo (2.0)"
            channelCount == 6 -> "Surround (5.1)"
            channelCount == 8 -> "Surround (7.1)"
            channelCount != null && channelCount > 0 -> "$channelCount canais"
            else -> null
        }
        return rows(
            videoCodec = videoCodec,
            width = width,
            height = height,
            containerFps = frameRate?.takeIf { it.isFinite() && it > 0f }?.toDouble(),
            activeDecoder = activeDecoder,
            audioCodec = audioCodec,
            channels = channels,
            sampleRateHz = sampleRateHz,
            subtitleTrack = subtitleLabel?.takeIf(String::isNotBlank) ?: "Desativadas",
            cacheDurationSeconds = bufferedDurationSeconds,
            engineName = "ExoPlayer (Media3)",
            hdrSummary = hdrSummary,
            afrSummary = afrSummary
        )
    }

    fun subtitleSelection(trackIdProperty: String?, selectedTrackLabel: String?): String {
        val trackId = trackIdProperty?.trim()?.lowercase(Locale.ROOT)
        val label = selectedTrackLabel?.trim()?.takeIf(String::isNotEmpty)
        return when {
            trackId == "no" -> "Desativadas"
            trackId == "auto" -> label ?: "Automática"
            trackId?.toIntOrNull()?.let { it > 0 } == true -> label ?: "Faixa $trackId"
            else -> "Indisponível"
        }
    }

    private fun present(value: String?): String = value?.trim()?.takeIf(String::isNotEmpty) ?: "Indisponível"

    private fun dimensions(width: Int?, height: Int?): String =
        if (width != null && width > 0 && height != null && height > 0) "${width}×${height}" else "Indisponível"

    private fun fps(value: Double?): String = if (value != null && value.isFinite() && value > 0.0) {
        String.format(Locale.US, "%.3f fps", value)
    } else "Indisponível"

    private fun decoder(value: String?): String = when (value?.trim()?.lowercase(Locale.ROOT)) {
        null, "" -> "Indisponível"
        "no" -> "Software"
        else -> value
    }

    private fun audio(codec: String?, channels: String?, sampleRateHz: Int?): String {
        val rate = sampleRateHz?.takeIf { it > 0 }?.let {
            String.format(Locale.US, "%.1f kHz", it / 1000.0)
        } ?: "frequência indisponível"
        return "${present(codec)} • ${present(channels)} • $rate"
    }

    private fun cache(seconds: Double?): String = if (seconds != null && seconds.isFinite() && seconds >= 0.0) {
        String.format(Locale.US, "%.1f s (estimativa)", seconds)
    } else "Indisponível"
}
