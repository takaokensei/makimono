package zechs.drive.stream.ui.series

import java.util.Locale
import zechs.drive.stream.domain.library.EpisodeParser

internal data class SeriesEpisodePresentation(
    val displayTitle: String,
    val episodeNumber: Int?
)

/** Turns release filenames into a concise card title without treating extras as episodes. */
internal fun presentSeriesEpisode(
    fileName: String,
    canonicalTitle: String? = null,
    isMovie: Boolean = false
): SeriesEpisodePresentation {
    if (isMovie) {
        val cleanName = EpisodeParser.cleanEpisodeFileName(fileName)
        val title = if (!canonicalTitle.isNullOrBlank()) {
            canonicalTitle
        } else if (cleanName.isNotBlank() && !cleanName.contains("Episódio", ignoreCase = true)) {
            cleanName
        } else {
            "Filme Completo"
        }
        return SeriesEpisodePresentation(title, null)
    }

    val parsed = EpisodeParser.parse(fileName)
    val episodeNumber = parsed.episode?.toInt()?.takeUnless { parsed.isSpecial }
    val displayTitle = when {
        parsed.isSpecial && parsed.episodeLabel.isNotBlank() -> parsed.episodeLabel
        !canonicalTitle.isNullOrBlank() && episodeNumber != null ->
            "Episódio ${String.format(Locale.ROOT, "%02d", episodeNumber)} - $canonicalTitle"
        episodeNumber != null -> {
            val epStr = String.format(Locale.ROOT, "%02d", episodeNumber)
            if (!parsed.episodeTitle.isNullOrBlank()) {
                "Episódio $epStr - ${parsed.episodeTitle}"
            } else {
                "Episódio $epStr"
            }
        }
        else -> EpisodeParser.cleanEpisodeFileName(fileName)
    }
    return SeriesEpisodePresentation(displayTitle, episodeNumber)
}

/** Formats item count respecting movies and singular/plural: '1 Episódio', '15 Episódios', 'Filme'. */
internal fun formatCountLabel(count: Int, isMovie: Boolean = false): String {
    return if (isMovie) {
        if (count <= 1) "Filme" else "$count Filmes"
    } else {
        if (count == 1) "1 Episódio" else "$count Episódios"
    }
}

/** Formats age rating code to standard display badge (e.g. '16+', '14+', 'L'). */
internal fun formatRating(rating: String?): String? {
    if (rating.isNullOrBlank()) return null
    val lower = rating.lowercase(Locale.ROOT).trim()
    return when {
        lower.contains("18+") || lower.contains("rx") || lower.contains("r+") ||
            lower.contains("tv-ma") || lower == "18" || lower == "a18" || lower.contains("nc-17") -> "18+"
        lower.contains("17+") || lower.contains("r -") || lower == "r" ||
            lower.contains("16+") || lower == "16" || lower == "a16" -> "16+"
        lower.contains("13+") || lower.contains("pg-13") || lower.contains("tv-14") ||
            lower.contains("14+") || lower == "14" || lower == "a14" -> "14+"
        lower.contains("10+") || lower.contains("tv-pg") || lower.contains("pg") ||
            lower == "10" || lower == "a10" || lower.contains("12+") || lower == "12" -> "10+"
        lower.contains("g") || lower.contains("tv-g") || lower.contains("tv-y") ||
            lower == "l" || lower.contains("livre") -> "L"
        else -> null
    }
}
