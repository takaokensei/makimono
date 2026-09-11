package zechs.drive.stream.utils

import java.util.Locale
import java.util.regex.Pattern

object EpisodeParser {

    data class ParsedEpisode(
        val showTitle: String,
        val season: Int? = null,
        val episode: Double? = null,
        val cleanTitle: String
    )

    private val RELEASE_GROUP_REGEX = Regex("^\\[[^\\]]+\\]\\s*")
    private val BRACKETS_TAGS_REGEX = Regex("\\[[^\\]]*\\]|\\([^\\)]*\\)")
    private val EXTENSION_REGEX = Regex("\\.[a-zA-Z0-9]{2,4}$")

    // S01E07 or s1e7
    private val SEASON_EPISODE_REGEX = Pattern.compile(
        "(?:s|season\\s*)(\\d{1,2})[\\s._-]*(?:e|ep|episode\\s*)(\\d{1,4}(?:\\.\\d)?)",
        Pattern.CASE_INSENSITIVE
    )

    // E07 or EP12
    private val STANDALONE_EP_REGEX = Pattern.compile(
        "(?:\\b|[_.-])(?:e|ep|episode)\\s*(\\d{1,4}(?:\\.\\d)?)(?:\\b|[_.-])",
        Pattern.CASE_INSENSITIVE
    )

    // " - 07" or " - 07v2"
    private val DASH_EPISODE_REGEX = Pattern.compile(
        "\\s+-\\s+(\\d{1,4}(?:\\.\\d)?)(?:v\\d+)?(?:\\s+|$|\\[|\\()",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(filename: String): ParsedEpisode {
        var baseName = filename.replace(EXTENSION_REGEX, "").trim()

        // 1. Check SxxExx
        val seMatcher = SEASON_EPISODE_REGEX.matcher(baseName)
        if (seMatcher.find()) {
            val season = seMatcher.group(1)?.toIntOrNull()
            val episode = seMatcher.group(2)?.toDoubleOrNull()
            val titlePart = baseName.substring(0, seMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)
            val clean = formatDisplay(showTitle, season, episode)
            return ParsedEpisode(showTitle, season, episode, clean)
        }

        // 2. Check " - 07"
        val dashMatcher = DASH_EPISODE_REGEX.matcher(baseName)
        if (dashMatcher.find()) {
            val episode = dashMatcher.group(1)?.toDoubleOrNull()
            val titlePart = baseName.substring(0, dashMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)
            val clean = formatDisplay(showTitle, null, episode)
            return ParsedEpisode(showTitle, null, episode, clean)
        }

        // 3. Check standalone "E07" / "EP 07"
        val epMatcher = STANDALONE_EP_REGEX.matcher(baseName)
        if (epMatcher.find()) {
            val episode = epMatcher.group(1)?.toDoubleOrNull()
            val titlePart = baseName.substring(0, epMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)
            val clean = formatDisplay(showTitle, null, episode)
            return ParsedEpisode(showTitle, null, episode, clean)
        }

        val cleaned = cleanTitleString(baseName)
        return ParsedEpisode(cleaned, null, null, cleaned.ifBlank { filename })
    }

    private fun cleanTitleString(raw: String): String {
        var result = raw.replace(RELEASE_GROUP_REGEX, "")
        result = result.replace(BRACKETS_TAGS_REGEX, "")
        result = result.replace(Regex("[_.]+"), " ")
        result = result.trim()
        return result
    }

    private fun formatDisplay(showTitle: String, season: Int?, episode: Double?): String {
        val epStr = if (episode != null) {
            if (episode % 1.0 == 0.0) {
                String.format(Locale.ROOT, "%02d", episode.toInt())
            } else {
                episode.toString()
            }
        } else null

        return when {
            showTitle.isNotBlank() && season != null && epStr != null ->
                "$showTitle • S${season}E$epStr"
            showTitle.isNotBlank() && epStr != null ->
                "$showTitle • Ep. $epStr"
            epStr != null ->
                "Episódio $epStr"
            else -> showTitle
        }
    }

    /**
     * Natural string comparator so "Episode 2" sorts before "Episode 10".
     */
    fun naturalCompare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        var ca: Char
        var cb: Char

        while (true) {
            var nzaCount = 0
            var nzbCount = 0

            ca = charAt(a, ia)
            cb = charAt(b, ib)

            // skip leading spaces
            while (Character.isSpaceChar(ca)) {
                ia++
                ca = charAt(a, ia)
            }
            while (Character.isSpaceChar(cb)) {
                ib++
                cb = charAt(b, ib)
            }

            // process digit run
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                if (ca == '0') {
                    while (charAt(a, ia) == '0') {
                        nzaCount++
                        ia++
                    }
                }
                if (cb == '0') {
                    while (charAt(b, ib) == '0') {
                        nzbCount++
                        ib++
                    }
                }

                var numStartA = ia
                var numStartB = ib

                while (Character.isDigit(charAt(a, ia))) ia++
                while (Character.isDigit(charAt(b, ib))) ib++

                val lenA = ia - numStartA
                val lenB = ib - numStartB

                if (lenA != lenB) {
                    return lenA - lenB
                }

                while (numStartA < ia) {
                    if (a[numStartA] != b[numStartB]) {
                        return a[numStartA] - b[numStartB]
                    }
                    numStartA++
                    numStartB++
                }

                if (nzaCount != nzbCount) {
                    return nzaCount - nzbCount
                }
            }

            if (ca == '\u0000' && cb == '\u0000') {
                return a.length - b.length
            }

            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) {
                return cmp
            }

            ia++
            ib++
        }
    }

    private fun charAt(s: String, i: Int): Char {
        return if (i >= s.length) '\u0000' else s[i]
    }
}
