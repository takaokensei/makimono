package zechs.drive.stream.utils

import java.util.Locale
import java.util.regex.Pattern

object EpisodeParser {

    data class ParsedEpisode(
        val showTitle: String,
        val season: Int? = null,
        val episode: Double? = null,
        val cleanTitle: String,
        val episodeLabel: String = "",
        val episodeTitle: String? = null,
        val isSpecial: Boolean = false
    )

    // Release group at start: [Judas], [Coalgirls]_, (HorribleSubs), [DB]
    private val RELEASE_GROUP_REGEX = Regex("^(?:\\[[^\\]]+\\]|\\([^\\)]+\\))[\\s_.-]*")

    // Hashes: [D82B2A34] or (D82B2A34)
    private val HASH_REGEX = Regex("\\[[0-9A-Fa-f]{8}\\]|\\([0-9A-Fa-f]{8}\\)")

    // Technical metadata tags in brackets or parentheses
    private val BRACKETS_METADATA_REGEX = Regex(
        "(?i)\\[(?:[0-9A-Fa-f]{8}|\\d{3,4}p|hevc|x26[45]|avc|h26[45]|bdrip|webrip|web-dl|bluray|aac|flac|opus|dts|10bit|dual[\\s_-]*audio|multi[\\s_-]*sub(?:title)?s?|remux).*?\\]"
    )
    private val PARENS_METADATA_REGEX = Regex(
        "(?i)\\((?:[0-9A-Fa-f]{8}|\\d{3,4}p|\\d{3,4}x\\d{3,4}|hevc|x26[45]|avc|h26[45]|bdrip|webrip|web-dl|bluray|aac|flac|opus|dts|10bit|dual[\\s_-]*audio|multi[\\s_-]*sub(?:title)?s?|remux).*?\\)"
    )

    // Trailing bare quality tokens like 1080p, WEBRip, etc.
    private val BARE_QUALITY_REGEX = Regex(
        "(?i)\\b(?:1080p|720p|480p|2160p|4k|x264|x265|hevc|avc|h264|h265|webrip|web-dl|bluray|bdrip|aac|flac|opus|10bit)\\b.*$"
    )

    private val EXTENSION_REGEX = Regex("\\.[a-zA-Z0-9]{2,4}$")

    // S01S01 or Season 1 Special 1
    private val SEASON_SPECIAL_REGEX = Pattern.compile(
        "(?:s|season\\s*)(\\d{1,2})[\\s._-]*(?:s|sp|special|ova|oad)\\s*(\\d{1,4})",
        Pattern.CASE_INSENSITIVE
    )

    // S01E07 or s1e7
    private val SEASON_EPISODE_REGEX = Pattern.compile(
        "(?:s|season\\s*)(\\d{1,2})[\\s._-]*(?:e|ep|episode\\s*)(\\d{1,4}(?:\\.\\d)?)",
        Pattern.CASE_INSENSITIVE
    )

    // Standalone Special / OVA
    private val STANDALONE_SPECIAL_REGEX = Pattern.compile(
        "(?:\\b|[_.-])(?:sp|special|ova|oad)\\s*(\\d{1,4})(?:\\b|[_.-])",
        Pattern.CASE_INSENSITIVE
    )

    // " - 07" or " - 07v2"
    private val DASH_EPISODE_REGEX = Pattern.compile(
        "(?:\\s+-\\s+|\\s+-)(\\d{1,4}(?:\\.\\d)?)(?:v\\d+)?\\b",
        Pattern.CASE_INSENSITIVE
    )

    // E07 or EP12
    private val STANDALONE_EP_REGEX = Pattern.compile(
        "(?:\\b|[_.-])(?:e|ep|episode)\\s*(\\d{1,4}(?:\\.\\d)?)(?:\\b|[_.-])",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(filename: String): ParsedEpisode {
        var base = filename.replace(EXTENSION_REGEX, "").trim()
        base = base.replace(RELEASE_GROUP_REGEX, "")
        base = base.replace(HASH_REGEX, "")
        base = base.replace(BRACKETS_METADATA_REGEX, "")
        base = base.replace(PARENS_METADATA_REGEX, "")
        base = base.replace(BARE_QUALITY_REGEX, "").trim()

        // Normalize underscores to spaces
        base = base.replace(Regex("_+"), " ").trim()

        // 1. Check SxxSxx (Special / OVA)
        val specMatcher = SEASON_SPECIAL_REGEX.matcher(base)
        if (specMatcher.find()) {
            val season = specMatcher.group(1)?.toIntOrNull()
            val specialNum = specMatcher.group(2)?.toIntOrNull() ?: 1
            val titlePart = base.substring(0, specMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)
            val spStr = String.format(Locale.ROOT, "%02d", specialNum)
            val epLabel = "Especial $spStr"
            val clean = if (showTitle.isNotBlank()) "$showTitle • $epLabel" else epLabel
            return ParsedEpisode(
                showTitle = showTitle,
                season = season,
                episode = specialNum.toDouble(),
                cleanTitle = clean,
                episodeLabel = epLabel,
                isSpecial = true
            )
        }

        // 2. Check SxxExx
        val seMatcher = SEASON_EPISODE_REGEX.matcher(base)
        if (seMatcher.find()) {
            val season = seMatcher.group(1)?.toIntOrNull()
            val episode = seMatcher.group(2)?.toDoubleOrNull()
            val titlePart = base.substring(0, seMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)

            val afterPart = base.substring(seMatcher.end()).trim()
            val epTitle = cleanSubtitleString(afterPart)

            val epStr = formatEpisodeNumber(episode)
            val epLabel = if (season != null && season > 1) {
                if (!epTitle.isNullOrBlank()) "S${String.format(Locale.ROOT, "%02d", season)}E$epStr: $epTitle"
                else "S${String.format(Locale.ROOT, "%02d", season)}E$epStr"
            } else {
                if (!epTitle.isNullOrBlank()) "Ep. $epStr: $epTitle"
                else "Ep. $epStr"
            }

            val clean = if (showTitle.isNotBlank()) "$showTitle • $epLabel" else epLabel
            return ParsedEpisode(
                showTitle = showTitle,
                season = season,
                episode = episode,
                cleanTitle = clean,
                episodeLabel = epLabel,
                episodeTitle = epTitle
            )
        }

        // 3. Check standalone Special / OVA
        val standaloneSpec = STANDALONE_SPECIAL_REGEX.matcher(base)
        if (standaloneSpec.find()) {
            val specialNum = standaloneSpec.group(1)?.toIntOrNull() ?: 1
            val titlePart = base.substring(0, standaloneSpec.start()).trim()
            val showTitle = cleanTitleString(titlePart)
            val spStr = String.format(Locale.ROOT, "%02d", specialNum)
            val epLabel = "Especial $spStr"
            val clean = if (showTitle.isNotBlank()) "$showTitle • $epLabel" else epLabel
            return ParsedEpisode(
                showTitle = showTitle,
                episode = specialNum.toDouble(),
                cleanTitle = clean,
                episodeLabel = epLabel,
                isSpecial = true
            )
        }

        // 4. Check dash episode (" - 07")
        val dashMatcher = DASH_EPISODE_REGEX.matcher(base)
        if (dashMatcher.find()) {
            val episode = dashMatcher.group(1)?.toDoubleOrNull()
            val titlePart = base.substring(0, dashMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)

            val afterPart = base.substring(dashMatcher.end()).trim()
            val epTitle = cleanSubtitleString(afterPart)

            val epStr = formatEpisodeNumber(episode)
            val epLabel = if (!epTitle.isNullOrBlank()) "Ep. $epStr: $epTitle" else "Ep. $epStr"
            val clean = if (showTitle.isNotBlank()) "$showTitle • $epLabel" else epLabel
            return ParsedEpisode(
                showTitle = showTitle,
                episode = episode,
                cleanTitle = clean,
                episodeLabel = epLabel,
                episodeTitle = epTitle
            )
        }

        // 5. Check standalone "E07" / "EP 07"
        val epMatcher = STANDALONE_EP_REGEX.matcher(base)
        if (epMatcher.find()) {
            val episode = epMatcher.group(1)?.toDoubleOrNull()
            val titlePart = base.substring(0, epMatcher.start()).trim()
            val showTitle = cleanTitleString(titlePart)

            val epStr = formatEpisodeNumber(episode)
            val epLabel = "Ep. $epStr"
            val clean = if (showTitle.isNotBlank()) "$showTitle • $epLabel" else epLabel
            return ParsedEpisode(
                showTitle = showTitle,
                episode = episode,
                cleanTitle = clean,
                episodeLabel = epLabel
            )
        }

        // 6. Fallback (Movie, OVA or clean single title)
        val cleaned = cleanTitleString(base)
        return ParsedEpisode(
            showTitle = cleaned.ifBlank { filename },
            cleanTitle = cleaned.ifBlank { filename },
            episodeLabel = ""
        )
    }

    private fun formatEpisodeNumber(episode: Double?): String {
        if (episode == null) return "01"
        return if (episode % 1.0 == 0.0) {
            String.format(Locale.ROOT, "%02d", episode.toInt())
        } else {
            episode.toString()
        }
    }

    private fun cleanTitleString(raw: String): String {
        var result = raw.replace(Regex("\\[[^\\]]*\\]|\\([^\\)]*\\)"), "")
        result = result.replace(Regex("(?i)\\b(?:1080p|720p|480p|2160p|4k|x264|x265|hevc|avc|h264|h265|webrip|web-dl|bluray|bdrip|aac|flac|opus|10bit)\\b"), "")
        result = result.replace(Regex("[_.]+"), " ")
        result = result.trim(' ', '-', '_', '.')
        return result
    }

    private fun cleanSubtitleString(raw: String): String? {
        var result = cleanTitleString(raw)
        if (result.length > 28) {
            result = result.substring(0, 28).trimEnd(' ', '-')
        }
        return result.ifBlank { null }
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
