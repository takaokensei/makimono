package zechs.drive.stream.ui.series

import java.util.Locale
import zechs.drive.stream.utils.EpisodeParser

internal data class SeriesEpisodePresentation(
    val displayTitle: String,
    val episodeNumber: Int?
)

/** Turns release filenames into a concise card title without treating extras as episodes. */
internal fun presentSeriesEpisode(fileName: String, canonicalTitle: String? = null): SeriesEpisodePresentation {
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
