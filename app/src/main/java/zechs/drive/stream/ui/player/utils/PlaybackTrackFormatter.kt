package zechs.drive.stream.ui.player.utils

import java.util.Locale

/**
 * Formatador canônico de faixas de áudio e legenda no padrão Kodi / VLC.
 * Converte metadados brutos de contêineres (MKV, MP4, TS) em descrições humanas e profissionais.
 */
object PlaybackTrackFormatter {

    data class FormattedTrack(
        val primaryTitle: String,
        val secondaryDetails: String,
        val isDefaultOrForced: Boolean = false
    ) {
        val displayName: String get() = primaryTitle
        val secondaryDetail: String get() = secondaryDetails
    }

    fun formatSubtitle(
        format: androidx.media3.common.Format,
        trackIndex: Int = 0
    ): FormattedTrack {
        return formatSubtitle(
            rawLabel = format.label,
            rawLanguage = format.language,
            mimeType = format.sampleMimeType,
            isEmbedded = true
        )
    }

    fun formatAudio(
        format: androidx.media3.common.Format,
        trackIndex: Int = 0
    ): FormattedTrack {
        return formatAudio(
            rawLabel = format.label,
            rawLanguage = format.language,
            channelCount = format.channelCount,
            mimeType = format.sampleMimeType,
            bitrate = format.bitrate.takeIf { it > 0 }
        )
    }

    fun formatSubtitle(
        rawLabel: String?,
        rawLanguage: String?,
        mimeType: String?,
        isEmbedded: Boolean = true,
        isDefault: Boolean = false,
        isForced: Boolean = false
    ): FormattedTrack {
        val cleanLang = resolveLanguageName(rawLanguage, rawLabel)
        val formatBadge = when {
            mimeType?.contains("x-subrip", ignoreCase = true) == true ||
                rawLabel?.contains("srt", ignoreCase = true) == true -> "SRT"
            mimeType?.contains("ssa", ignoreCase = true) == true ||
                mimeType?.contains("ass", ignoreCase = true) == true ||
                rawLabel?.contains("ass", ignoreCase = true) == true ||
                rawLabel?.contains("ssa", ignoreCase = true) == true -> "ASS/SSA Estilizada"
            mimeType?.contains("vtt", ignoreCase = true) == true -> "WebVTT"
            mimeType?.contains("pgs", ignoreCase = true) == true -> "PGS"
            else -> "Legenda"
        }

        val sourceBadge = if (isEmbedded) "Embutida" else "Nuvem / Arquivo"
        val flags = buildList {
            add(sourceBadge)
            add(formatBadge)
            if (isForced) add("Forçada")
            if (isDefault) add("Padrão")
        }.joinToString(" • ")

        return FormattedTrack(
            primaryTitle = cleanLang,
            secondaryDetails = flags,
            isDefaultOrForced = isDefault || isForced
        )
    }

    fun formatAudio(
        rawLabel: String?,
        rawLanguage: String?,
        channelCount: Int?,
        mimeType: String?,
        bitrate: Int? = null,
        isDefault: Boolean = false
    ): FormattedTrack {
        val cleanLang = resolveLanguageName(rawLanguage, rawLabel)
        val channelBadge = when (channelCount) {
            1 -> "Mono 1.0"
            2 -> "Estéreo 2.0"
            6 -> "Surround 5.1"
            8 -> "Surround 7.1"
            else -> if (rawLabel?.contains("5.1", ignoreCase = true) == true) "5.1 Surround" else "Estéreo"
        }

        val codecBadge = when {
            mimeType?.contains("flac", ignoreCase = true) == true ||
                rawLabel?.contains("flac", ignoreCase = true) == true -> "FLAC Lossless"
            mimeType?.contains("opus", ignoreCase = true) == true ||
                rawLabel?.contains("opus", ignoreCase = true) == true -> "Opus"
            mimeType?.contains("eac3", ignoreCase = true) == true ||
                rawLabel?.contains("e-ac-3", ignoreCase = true) == true ||
                rawLabel?.contains("eac3", ignoreCase = true) == true -> "Dolby Digital Plus (E-AC3)"
            mimeType?.contains("ac3", ignoreCase = true) == true ||
                rawLabel?.contains("ac3", ignoreCase = true) == true -> "Dolby Digital (AC3)"
            mimeType?.contains("dts", ignoreCase = true) == true ||
                rawLabel?.contains("dts", ignoreCase = true) == true -> "DTS"
            rawLabel?.contains("truehd", ignoreCase = true) == true -> "Dolby TrueHD"
            else -> "AAC"
        }

        val isDubbed = cleanLang.contains("Português", ignoreCase = true)
        val roleHint = if (isDubbed) "Dublado" else if (cleanLang.contains("Japonês", ignoreCase = true)) "Áudio Original" else ""

        val details = buildList {
            if (roleHint.isNotBlank()) add(roleHint)
            add(channelBadge)
            add(codecBadge)
            if (isDefault) add("Padrão")
        }.joinToString(" • ")

        return FormattedTrack(
            primaryTitle = cleanLang,
            secondaryDetails = details,
            isDefaultOrForced = isDefault
        )
    }

    private fun resolveLanguageName(code: String?, label: String?): String {
        val raw = (label ?: "").lowercase(Locale.ROOT)
        val normCode = (code ?: "").lowercase(Locale.ROOT).trim()

        return when {
            raw.contains("brazilian") || raw.contains("pt-br") || raw.contains("pt_br") || normCode == "pt-br" -> "Português (Brasil)"
            raw.contains("portugu") || normCode == "pt" || normCode == "por" -> "Português"
            raw.contains("japan") || raw.contains("jap") || normCode == "ja" || normCode == "jpn" -> "Japonês"
            raw.contains("english") || raw.contains("ingl") || normCode == "en" || normCode == "eng" -> "Inglês"
            raw.contains("latin") || raw.contains("es-la") || raw.contains("es_la") -> "Espanhol (América Latina)"
            raw.contains("span") || raw.contains("espan") || normCode == "es" || normCode == "spa" -> "Espanhol"
            raw.contains("french") || raw.contains("franc") || normCode == "fr" || normCode == "fra" || normCode == "fre" -> "Francês"
            raw.contains("german") || raw.contains("alem") || normCode == "de" || normCode == "deu" || normCode == "ger" -> "Alemão"
            raw.contains("italian") || raw.contains("ital") || normCode == "it" || normCode == "ita" -> "Italiano"
            raw.contains("korean") || raw.contains("corean") || normCode == "ko" || normCode == "kor" -> "Coreano"
            raw.contains("chinese") || raw.contains("chin") || normCode == "zh" || normCode == "zho" || normCode == "chi" -> "Chinês"
            raw.contains("russian") || raw.contains("russ") || normCode == "ru" || normCode == "rus" -> "Russo"
            raw.contains("arabic") || raw.contains("arab") || normCode == "ar" || normCode == "ara" -> "Árabe"
            label?.isNotBlank() == true -> cleanRawLabel(label)
            code?.isNotBlank() == true -> code.uppercase(Locale.ROOT)
            else -> "Indefinido"
        }
    }

    private fun cleanRawLabel(label: String): String {
        return label
            .replace(Regex("""^\[[^\]]+\]_?"""), "")
            .replace(Regex("""^(\([^)]+\))_?"""), "")
            .replace(Regex("""_CR$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""_AAC$""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { label }
    }
}
