package zechs.drive.stream.ui.series

import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.utils.SeasonEpisodeGrouper

/** Chooses a story episode for the primary Play/Continue action, independent of Drive order. */
internal object PrimaryEpisodeSelection {

    fun select(episodes: List<SeriesEpisodeItem>): SeriesEpisodeItem? {
        if (episodes.isEmpty()) return null

        val storyEpisodes = episodes
            .filter { it.category() == SeasonEpisodeGrouper.ItemCategory.STORY }
            .sortedWith(::compareEpisodes)

        // The primary action follows the narrative. OPs, EDs, trailers and OVAs
        // are available in their own shelves and should not hijack Continue.
        return chooseByProgress(storyEpisodes)
            ?: chooseByProgress(episodes.sortedWith(::compareEpisodes))
    }

    private fun chooseByProgress(episodes: List<SeriesEpisodeItem>): SeriesEpisodeItem? {
        return episodes.firstOrNull { it.progressPercent in 1..94 }
            ?: episodes.firstOrNull { it.progressPercent == 0 }
            ?: episodes.firstOrNull()
    }

    private fun SeriesEpisodeItem.category(): SeasonEpisodeGrouper.ItemCategory =
        SeasonEpisodeGrouper.categorize(file.name, EpisodeParser.parse(file.name))

    private fun compareEpisodes(left: SeriesEpisodeItem, right: SeriesEpisodeItem): Int =
        SeasonEpisodeGrouper.compareItems(
            left.file.name,
            EpisodeParser.parse(left.file.name),
            right.file.name,
            EpisodeParser.parse(right.file.name)
        )

    /**
     * Formats the primary CTA button label per P-10 ("Continuar E5 · faltam 12 min").
     */
    fun formatContinueCta(item: SeriesEpisodeItem?, isMovie: Boolean = false): String {
        if (item == null) {
            return if (isMovie) "Assistir filme" else "Assistir E1"
        }
        val canResume = item.progressPercent in 1..94 &&
            item.watchedDuration > 0L &&
            item.totalDuration > item.watchedDuration
        val remainingMinutes = if (canResume) {
            ((item.totalDuration - item.watchedDuration) / 60_000L).coerceAtLeast(1L)
        } else {
            0L
        }
        return if (isMovie) {
            if (canResume) {
                "Continuar · faltam $remainingMinutes min"
            } else {
                "Assistir filme"
            }
        } else {
            val epNum = item.episodeNumber?.takeIf { it > 0 } ?: 1
            if (canResume) {
                "Continuar E$epNum · faltam $remainingMinutes min"
            } else {
                "Assistir E$epNum"
            }
        }
    }

    /**
     * Formats the secondary subtitle line under the primary action button.
     */
    fun formatContinueSubtitle(item: SeriesEpisodeItem?, isMovie: Boolean = false): String {
        if (item == null) {
            return if (isMovie) "Filme" else "Ep. 01 • Iniciar reprodução"
        }
        val hasRemaining = item.watchedDuration > 0L && item.totalDuration > item.watchedDuration
        return if (isMovie) {
            if (hasRemaining) {
                val remainingSec = (item.totalDuration - item.watchedDuration) / 1000L
                "Filme • ${String.format(java.util.Locale.ROOT, "%02d:%02d", remainingSec / 60, remainingSec % 60)} restante"
            } else {
                "Filme"
            }
        } else {
            val epPrefix = item.episodeNumber?.let { "Ep. $it" } ?: "Ep. 01"
            if (hasRemaining) {
                val remainingSec = (item.totalDuration - item.watchedDuration) / 1000L
                "$epPrefix • ${String.format(java.util.Locale.ROOT, "%02d:%02d", remainingSec / 60, remainingSec % 60)} restante"
            } else {
                "$epPrefix • Iniciar reprodução"
            }
        }
    }
}
