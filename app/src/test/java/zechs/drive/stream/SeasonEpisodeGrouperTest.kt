package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.utils.SeasonEpisodeGrouper

class SeasonEpisodeGrouperTest {

    @Test
    fun groupsMultiSeasonPlaylistCorrectly() {
        val items = listOf(
            PlaylistItem("id1", "My Hero Academia S01E01.mkv", null),
            PlaylistItem("id2", "My Hero Academia S01E02.mkv", null),
            PlaylistItem("id3", "My Hero Academia S02E01.mkv", null),
            PlaylistItem("id4", "My Hero Academia S02E02.mkv", null)
        )

        val groups = SeasonEpisodeGrouper.groupPlaylist("My Hero Academia", items)

        assertTrue(groups.size >= 2)
        val season1 = groups.firstOrNull { it.id == "season_1" }
        val season2 = groups.firstOrNull { it.id == "season_2" }

        assertEquals(2, season1?.playlistItems?.size)
        assertEquals(2, season2?.playlistItems?.size)
    }

    @Test
    fun groupsCanonicalArcsForOnePiece() {
        val items = listOf(
            PlaylistItem("id1", "One Piece - 001.mkv", null),
            PlaylistItem("id2", "One Piece - 050.mkv", null),
            PlaylistItem("id3", "One Piece - 070.mkv", null),
            PlaylistItem("id4", "One Piece - 100.mkv", null)
        )

        val groups = SeasonEpisodeGrouper.groupPlaylist("One Piece", items)

        assertTrue(groups.isNotEmpty())
        // Episodes 1 and 50 belong to Romance Dawn & East Blue (1-61)
        val eastBlue = groups.firstOrNull { it.name.contains("East Blue", ignoreCase = true) }
        assertEquals(2, eastBlue?.playlistItems?.size)

        // Episodes 70 and 100 belong to Alabasta (62-135)
        val alabasta = groups.firstOrNull { it.name.contains("Alabasta", ignoreCase = true) }
        assertEquals(2, alabasta?.playlistItems?.size)
    }

    @Test
    fun handlesSingleSeasonFlatList() {
        val items = listOf(
            PlaylistItem("id1", "Frieren - 01.mkv", null),
            PlaylistItem("id2", "Frieren - 02.mkv", null),
            PlaylistItem("id3", "Frieren - 03.mkv", null)
        )

        val groups = SeasonEpisodeGrouper.groupPlaylist("Frieren", items)

        assertEquals(1, groups.size)
        assertEquals(3, groups[0].playlistItems.size)
    }

    @Test
    fun sortsEpisodesNumericallyWithinGroup() {
        val items = listOf(
            PlaylistItem("id3", "Show - 03.mkv", null),
            PlaylistItem("id1", "Show - 01.mkv", null),
            PlaylistItem("id2", "Show - 02.mkv", null)
        )

        val groups = SeasonEpisodeGrouper.groupPlaylist("Show", items)
        val sortedList = groups[0].playlistItems

        assertEquals("Show - 01.mkv", sortedList[0].title)
        assertEquals("Show - 02.mkv", sortedList[1].title)
        assertEquals("Show - 03.mkv", sortedList[2].title)
    }
}
