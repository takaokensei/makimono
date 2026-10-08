package zechs.drive.stream.ui.series

import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.SeasonEpisodeGrouper

/** Keeps the detail screen's "all episodes" list aligned with the grouper's playlist contract. */
internal object SeriesDetailEpisodeSelection {

    fun firstTrailer(episodes: List<SeriesEpisodeItem>): SeriesEpisodeItem? = episodes
        .filter { item ->
            item.file.name.contains("PV", ignoreCase = true) ||
                item.file.name.contains("Trailer", ignoreCase = true)
        }
        .sortedWith(::compareEpisodes)
        .firstOrNull()

    fun allEpisodes(
        episodes: List<SeriesEpisodeItem>,
        groupFileIds: Set<String>
    ): List<SeriesEpisodeItem> {
        val groupedEpisodes = episodes
            .filter { it.file.id in groupFileIds }
            .sortedWith(::compareEpisodes)
        if (groupedEpisodes.isNotEmpty()) return groupedEpisodes

        val storyEpisodes = episodes.filter { it.category() == SeasonEpisodeGrouper.ItemCategory.STORY }
            .sortedWith(::compareEpisodes)
        // A folder containing only extras must remain playable and browsable.
        return storyEpisodes.ifEmpty { episodes.sortedWith(::compareEpisodes) }
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
