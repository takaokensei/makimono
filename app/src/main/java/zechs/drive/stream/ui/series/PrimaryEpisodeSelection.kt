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
}
