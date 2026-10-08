package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.ui.files.adapter.FilesDataModel
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
    fun assignsOnePieceBoundaryEpisodesToExactlyOneArc() {
        val items = listOf(
            PlaylistItem("ep195", "One Piece - 195.mkv"),
            PlaylistItem("ep196", "One Piece - 196.mkv"),
            PlaylistItem("ep206", "One Piece - 206.mkv"),
            PlaylistItem("ep207", "One Piece - 207.mkv")
        )

        val groups = SeasonEpisodeGrouper.groupPlaylist("One Piece", items)
        val arcs = groups.filterNot { it.id == "season_all" }
        val assigned = arcs.flatMap { it.playlistItems }

        assertEquals("Every source episode should occur in one arc only", items.size, assigned.size)
        assertEquals(items.map { it.fileId }.toSet(), assigned.map { it.fileId }.toSet())
        assertEquals(
            listOf("ep195"),
            arcs.first { it.name == "Skypiea" }.playlistItems.map { it.fileId }
        )
        assertEquals(
            listOf("ep196", "ep206"),
            arcs.first { it.name == "G-8 / The Naval Fortress" }.playlistItems.map { it.fileId }
        )
        assertEquals(
            listOf("ep207"),
            arcs.first { it.name == "The Foxy Pirate Crew" }.playlistItems.map { it.fileId }
        )
    }

    @Test
    fun canonicalArcGroupingKeepsSpecialsMusicAndPromosOutOfStoryArcs() {
        val titles = listOf(
            "One Piece - 001.mkv",
            "One Piece - OP 01.mkv",
            "One Piece - OVA 01.mkv",
            "One Piece - PV 01.mkv"
        )
        val playlist = titles.mapIndexed { index, title ->
            PlaylistItem("id-$index", title)
        }

        val allPlaylistGroups = SeasonEpisodeGrouper.groupPlaylist("One Piece", playlist)
        val allPlaylistEpisodes = allPlaylistGroups.first { it.id == "season_all" }
        assertEquals(listOf("id-0"), allPlaylistEpisodes.playlistItems.map { it.fileId })
        val playlistGroups = allPlaylistGroups.filterNot { it.id == "season_all" }
        assertEquals(titles.size, playlistGroups.sumOf { it.playlistItems.size })
        val eastBlue = playlistGroups.first { it.name == "Romance Dawn & East Blue" }
        assertEquals(listOf("id-0"), eastBlue.playlistItems.map { it.fileId })
        assertEquals(listOf("id-1"), playlistGroups.first { it.id == "openings_endings" }.playlistItems.map { it.fileId })
        assertEquals(listOf("id-2"), playlistGroups.first { it.id == "specials_ovas" }.playlistItems.map { it.fileId })
        assertEquals(listOf("id-3"), playlistGroups.first { it.id == "promos_trailers" }.playlistItems.map { it.fileId })

        val files = titles.mapIndexed { index, title ->
            FilesDataModel.File(
                DriveFile(
                    id = "id-$index",
                    name = title,
                    size = null,
                    mimeType = "video/mp4",
                    iconLink = null,
                    shortcutDetails = ShortcutDetails(),
                    starred = Starred.UNSTARRED
                )
            )
        }
        val allFileGroups = SeasonEpisodeGrouper.groupFiles("One Piece", files)
        val allFileEpisodes = allFileGroups.first { it.id == "season_all" }
        assertEquals(listOf("id-0"), allFileEpisodes.fileItems.map { (it as FilesDataModel.File).driveFile.id })
        val fileGroups = allFileGroups.filterNot { it.id == "season_all" }
        assertEquals(titles.size, fileGroups.sumOf { it.fileItems.size })
        assertEquals(listOf("id-0"), fileGroups.first { it.name == "Romance Dawn & East Blue" }
            .fileItems.map { (it as FilesDataModel.File).driveFile.id })
        assertEquals(listOf("id-1"), fileGroups.first { it.id == "openings_endings" }
            .fileItems.map { (it as FilesDataModel.File).driveFile.id })
        assertEquals(listOf("id-2"), fileGroups.first { it.id == "specials_ovas" }
            .fileItems.map { (it as FilesDataModel.File).driveFile.id })
        assertEquals(listOf("id-3"), fileGroups.first { it.id == "promos_trailers" }
            .fileItems.map { (it as FilesDataModel.File).driveFile.id })

        val extrasOnly = playlist.drop(1)
        assertEquals(
            extrasOnly.map { it.fileId }.toSet(),
            SeasonEpisodeGrouper.groupPlaylist("One Piece", extrasOnly)
                .first { it.id == "season_all" }.playlistItems.map { it.fileId }.toSet()
        )
        val extraFilesOnly = files.drop(1)
        assertEquals(
            extraFilesOnly.map { it.driveFile.id }.toSet(),
            SeasonEpisodeGrouper.groupFiles("One Piece", extraFilesOnly)
                .first { it.id == "season_all" }.fileItems.map { (it as FilesDataModel.File).driveFile.id }.toSet()
        )
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
