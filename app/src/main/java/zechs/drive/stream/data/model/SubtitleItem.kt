package zechs.drive.stream.data.model

import androidx.annotation.Keep
import zechs.drive.stream.domain.library.EpisodeParser
import java.io.Serializable

@Keep
data class SubtitleItem(
    val id: String,
    val name: String,
    val ext: String = name.substringAfterLast(".", "ass").lowercase(),
    val languageLabel: String = parseLanguageLabel(name),
    val languageCode: String = parseLanguageCode(name),
    val source: MediaSource = MediaSource.DRIVE,
    val uri: String? = null
) : Serializable {

    fun matchesVideo(videoTitle: String): Boolean {
        val cleanVideo = videoTitle.lowercase().substringBeforeLast(".").trim()
        val cleanSub = name.lowercase().trim()
        if (cleanVideo.isNotEmpty() && cleanSub.startsWith(cleanVideo)) return true

        val normalizedVideo = normalizeTitleForMatch(cleanVideo)
        val normalizedSub = normalizeTitleForMatch(cleanSub.substringBeforeLast("."))
        if (normalizedVideo.length >= 4 && normalizedSub.startsWith(normalizedVideo)) {
            return true
        }

        // Episode number matching fallback (strip trailing subtitle language tags like .pt-BR / .en first)
        val videoParsed = EpisodeParser.parse(videoTitle)
        val subParsed = EpisodeParser.parse(stripSubtitleLanguageAndExt(name))
        val videoEp = videoParsed.episode
        val subEp = subParsed.episode
        if (videoEp != null && subEp != null && videoEp == subEp) {
            val vSeason = videoParsed.season
            val sSeason = subParsed.season
            if (vSeason == null || sSeason == null || vSeason == sSeason) {
                return true
            }
        }

        return false
    }

    /**
     * Extended matching used during episode sidecar download and single-video folders:
     * matches against both the display title and raw filename (if available), or accepts
     * standalone language subtitles (e.g. `pt-BR.ass`, `por.srt`) when the folder contains
     * only a single video file.
     */
    fun matchesEpisodeOrSingleVideo(
        videoTitle: String,
        rawVideoFileName: String? = null,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
        isSingleVideoFolder: Boolean = false
    ): Boolean {
        if (matchesVideo(videoTitle)) return true
        if (!rawVideoFileName.isNullOrBlank() && matchesVideo(rawVideoFileName)) return true

        val subParsed = EpisodeParser.parse(stripSubtitleLanguageAndExt(name))
        if (episodeNumber != null && subParsed.episode != null && episodeNumber == subParsed.episode.toInt()) {
            val sSeason = subParsed.season?.toInt()
            if (seasonNumber == null || sSeason == null || seasonNumber == sSeason) {
                return true
            }
        }

        if (isSingleVideoFolder && subParsed.episode == null) {
            return true
        }

        return false
    }

    /**
     * Lower number = higher priority when auto-selecting a default subtitle track.
     * Prioritizes Portuguese (BR) > English > others, and ASS/SSA > SRT/VTT.
     */
    fun sortPriority(): Int {
        val langRank = when (languageCode) {
            "por" -> 0
            "eng" -> 10
            "spa" -> 20
            "jpn" -> 30
            else -> 40
        }
        val extRank = when (ext) {
            "ass" -> 0
            "ssa" -> 1
            "srt" -> 2
            "vtt" -> 3
            else -> 4
        }
        return langRank + extRank
    }

    companion object {
        private val NORMALIZE_REGEX = Regex("[\\s._\\-\\[\\]()]+")
        private val SUBTITLE_EXT_REGEX = Regex("\\.(?:ass|ssa|srt|vtt|sub)$", RegexOption.IGNORE_CASE)
        private val TRAILING_LANG_TAGS_REGEX = Regex(
            "(?:[._\\-](?:pt[-_]?br|pob|por|pt|eng|en|spa|es|fra|fre|fr|deu|ger|de|ita|it|jpn|ja|forced|sdh|default|full|signs))+$",
            RegexOption.IGNORE_CASE
        )

        private fun stripSubtitleLanguageAndExt(raw: String): String {
            val withoutExt = raw.trim().replace(SUBTITLE_EXT_REGEX, "")
            return withoutExt.replace(TRAILING_LANG_TAGS_REGEX, "").trim()
        }

        private fun normalizeTitleForMatch(raw: String): String {
            return stripSubtitleLanguageAndExt(raw).lowercase().replace(NORMALIZE_REGEX, " ").trim()
        }

        fun parseLanguageCode(fileName: String): String {
            val lower = fileName.lowercase()
            return when {
                lower.contains("pt-br") || lower.contains("pt_br") || lower.contains(".por.") || lower.contains(".pt.") ||
                lower.contains("[pt") || lower.contains("(pt") || lower.contains("portugu") -> "por"
                lower.contains("eng") || lower.contains(".en.") || lower.contains("[en") || lower.contains("english") -> "eng"
                lower.contains("spa") || lower.contains(".es.") || lower.contains("[es") || lower.contains("spanish") ||
                lower.contains("español") || lower.contains("espanol") -> "spa"
                lower.contains("fre") || lower.contains("fra") || lower.contains(".fr.") || lower.contains("french") ||
                lower.contains("français") || lower.contains("francais") -> "fra"
                lower.contains("ger") || lower.contains("deu") || lower.contains(".de.") || lower.contains("german") ||
                lower.contains("deutsch") -> "deu"
                lower.contains("ita") || lower.contains(".it.") || lower.contains("italian") -> "ita"
                lower.contains("jpn") || lower.contains(".ja.") || lower.contains("japanese") -> "jpn"
                else -> "und"
            }
        }

        fun parseLanguageLabel(fileName: String): String {
            val lower = fileName.lowercase()
            val ext = fileName.substringAfterLast(".", "").lowercase()
            val formatLabel = ext.uppercase().takeIf { it.isNotBlank() } ?: "ASS"
            val lang = when {
                lower.contains("pt-br") || lower.contains("pt_br") || lower.contains(".por.") || lower.contains(".pt.") ||
                lower.contains("[pt") || lower.contains("(pt") || lower.contains("portugu") -> "Português (BR)"
                lower.contains("eng") || lower.contains(".en.") || lower.contains("[en") || lower.contains("english") -> "English"
                lower.contains("spa") || lower.contains(".es.") || lower.contains("[es") || lower.contains("spanish") ||
                lower.contains("español") || lower.contains("espanol") -> "Español"
                lower.contains("fre") || lower.contains("fra") || lower.contains(".fr.") || lower.contains("french") ||
                lower.contains("français") || lower.contains("francais") -> "Français"
                lower.contains("ger") || lower.contains("deu") || lower.contains(".de.") || lower.contains("german") ||
                lower.contains("deutsch") -> "Deutsch"
                lower.contains("ita") || lower.contains(".it.") || lower.contains("italian") -> "Italiano"
                lower.contains("jpn") || lower.contains(".ja.") || lower.contains("japanese") -> "Japanese"
                else -> "Legenda"
            }
            return "$lang ($formatLabel)"
        }
    }
}
