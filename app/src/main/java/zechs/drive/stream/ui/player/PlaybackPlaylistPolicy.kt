package zechs.drive.stream.ui.player

import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.utils.SeasonEpisodeGrouper

/** Merges Drive page deltas without losing the active playlist or duplicating shortcuts. */
object PlaybackPlaylistPolicy {
    fun merge(existing: List<PlaylistItem>, page: List<PlaylistItem>): List<PlaylistItem> {
        if (page.isEmpty()) return existing

        val byId = LinkedHashMap<String, PlaylistItem>(existing.size + page.size)
        existing.forEach { byId[it.fileId] = it }
        page.forEach { byId[it.fileId] = it }
        return byId.values.sortedWith { left, right ->
            SeasonEpisodeGrouper.compareItems(
                left.title, EpisodeParser.parse(left.title),
                right.title, EpisodeParser.parse(right.title)
            )
        }
    }

    fun mergeSubtitles(existing: List<SubtitleItem>, page: List<SubtitleItem>): List<SubtitleItem> =
        (existing + page).distinctBy { it.id }
}
