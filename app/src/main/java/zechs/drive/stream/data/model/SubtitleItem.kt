package zechs.drive.stream.data.model

import androidx.annotation.Keep
import zechs.drive.stream.utils.EpisodeParser
import java.io.Serializable

@Keep
data class SubtitleItem(
    val id: String,
    val name: String,
    val ext: String = name.substringAfterLast(".", "ass").lowercase(),
    val languageLabel: String = parseLanguageLabel(name),
    val languageCode: String = parseLanguageCode(name)
) : Serializable {

    fun matchesVideo(videoTitle: String): Boolean {
        val cleanVideo = videoTitle.lowercase().substringBeforeLast(".")
        val cleanSub = name.lowercase()
        if (cleanSub.startsWith(cleanVideo)) return true

        // Episode number matching fallback
        val videoEp = EpisodeParser.parse(videoTitle).episode
        val subEp = EpisodeParser.parse(name).episode
        if (videoEp != null && subEp != null && videoEp == subEp) {
            return true
        }

        return false
    }

    companion object {
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
