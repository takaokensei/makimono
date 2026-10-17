package zechs.drive.stream.ui.player

/** Formats only media properties reported by the active Media3 formats. */
internal object MediaInfoDisplay {
    fun videoCodec(mimeType: String?): String = when {
        mimeType.isNullOrBlank() -> "Codec desconhecido"
        mimeType.contains("hevc", ignoreCase = true) -> "HEVC (H.265)"
        mimeType.contains("avc", ignoreCase = true) -> "AVC (H.264)"
        mimeType.contains("vp9", ignoreCase = true) -> "VP9"
        mimeType.contains("av01", ignoreCase = true) -> "AV1"
        mimeType.contains("mp2t", ignoreCase = true) -> "MPEG-2 TS"
        mimeType.contains("mpeg2", ignoreCase = true) || mimeType.contains("mp2v", ignoreCase = true) -> "MPEG-2"
        mimeType.contains("vc1", ignoreCase = true) || mimeType.contains("wvc1", ignoreCase = true) -> "VC-1"
        else -> mimeType.substringAfterLast('/').takeIf(String::isNotBlank) ?: "Codec desconhecido"
    }

    fun videoDimensions(width: Int?, height: Int?): String =
        if (width != null && height != null && width > 0 && height > 0) {
            "${width}x${height}"
        } else {
            "Resolução desconhecida"
        }

    fun frameRate(frameRate: Float?): String =
        if (frameRate != null && frameRate.isFinite() && frameRate > 0f) {
            String.format(java.util.Locale.US, "%.3f fps", frameRate)
        } else {
            "FPS desconhecido"
        }

    fun audioSummary(mimeType: String?, channelCount: Int?, sampleRateHz: Int?): String {
        val codec = when {
            mimeType.isNullOrBlank() -> "Codec desconhecido"
            mimeType.contains("mp4a", true) || mimeType.contains("aac", true) -> "AAC"
            mimeType.contains("opus", true) -> "Opus"
            mimeType.contains("flac", true) -> "FLAC"
            mimeType.contains("ac3", true) && !mimeType.contains("eac3", true) -> "Dolby Digital (AC-3)"
            mimeType.contains("eac3", true) -> "Dolby Digital Plus (E-AC-3)"
            mimeType.contains("dts", true) -> "DTS"
            mimeType.contains("mpeg-l2", true) || mimeType.endsWith("/mp2", true) -> "MP2"
            mimeType.contains("mpeg", true) || mimeType.endsWith("/mp3", true) -> "MP3"
            else -> mimeType.substringAfterLast('/').takeIf(String::isNotBlank) ?: "Codec desconhecido"
        }
        val channels = when {
            channelCount == 1 -> "Mono (1.0)"
            channelCount == 2 -> "Estéreo (2.0)"
            channelCount == 6 -> "Surround (5.1)"
            channelCount == 8 -> "Surround (7.1)"
            channelCount != null && channelCount in 3..5 -> "$channelCount canais"
            channelCount != null && channelCount >= 7 -> "$channelCount canais"
            else -> "Canais desconhecidos"
        }
        val sampleRate = sampleRateHz
            ?.takeIf { it > 0 }
            ?.let { String.format(java.util.Locale.US, "%.1f kHz", it / 1000f) }
            ?: "Frequência desconhecida"
        return "$codec • $channels • $sampleRate"
    }
}
