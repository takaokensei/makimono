package zechs.drive.stream.ui.player2

import java.util.Locale

/** Presents MPV properties as reported; unavailable values remain explicit. */
internal object MpvTechnicalInfoDisplay {
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
        cacheDurationSeconds: Double?
    ): List<Row> = listOf(
        Row("video", "Vídeo", present(videoCodec)),
        Row("resolution", "Resolução", dimensions(width, height)),
        Row("fps", "FPS do contêiner", fps(containerFps)),
        Row("decoder", "Decodificador ativo", decoder(activeDecoder)),
        Row("audio", "Áudio", audio(audioCodec, channels, sampleRateHz)),
        Row("subtitle", "Legenda", subtitleTrack?.takeIf(String::isNotBlank) ?: "Indisponível"),
        Row("buffer", "Cache do demuxer", cache(cacheDurationSeconds))
    )

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
