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
            val cleanName = fileName
                .substringBeforeLast('.')
                .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
                .trim()
            "Episódio ${String.format(Locale.ROOT, "%02d", episodeNumber)} - $cleanName"
        }
        else -> EpisodeParser.cleanEpisodeFileName(fileName)
    }
    return SeriesEpisodePresentation(displayTitle, episodeNumber)
}
