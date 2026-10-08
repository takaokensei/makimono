package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.utils.SeasonEpisodeGrouper

class SeriesDetailEpisodeSelectionTest {

    @Test
    fun `trailer action finds the earliest trailer outside narrative episode list`() {
        val items = listOf(
            episode("episode", "Show - 01.mkv"),
            episode("trailer2", "Show PV2.mkv"),
            episode("trailer1", "Show PV1.mkv")
        )

        assertEquals("trailer1", SeriesDetailEpisodeSelection.firstTrailer(items)?.file?.id)
    }

    @Test
    fun `all episodes follows the grouped narrative ids and excludes extras`() {
        val items = listOf(
            episode("op", "Show - OP1.mkv"),
            episode("ep2", "Show - 02.mkv"),
            episode("ova", "Show OVA 01.mkv"),
            episode("pv", "Show PV1.mkv"),
            episode("ep1", "Show - 01.mkv")
        )

        val group = SeasonEpisodeGrouper.groupFiles(
            "Show",
            items.map { FilesDataModel.File(it.file) }
        ).first { it.id == "season_all" }
        val groupFileIds = group.fileItems.mapNotNull {
            (it as? FilesDataModel.File)?.driveFile?.id
        }.toSet()

        val selected = SeriesDetailEpisodeSelection.allEpisodes(items, groupFileIds)

        assertEquals(listOf("ep1", "ep2"), selected.map { it.file.id })
    }

    @Test
    fun `missing group ids falls back to story episodes when available`() {
        val items = listOf(
            episode("opening", "Show - OP1.mkv"),
            episode("episode", "Show - 01.mkv")
        )

        val selected = SeriesDetailEpisodeSelection.allEpisodes(items, emptySet())

        assertEquals(listOf("episode"), selected.map { it.file.id })
    }

    @Test
    fun `folder with only extras remains accessible`() {
        val items = listOf(
            episode("opening", "Show - OP1.mkv"),
            episode("ova", "Show OVA 01.mkv")
        )

        val selected = SeriesDetailEpisodeSelection.allEpisodes(items, emptySet())

        assertEquals(listOf("ova", "opening"), selected.map { it.file.id })
    }

    private fun episode(id: String, name: String) = SeriesEpisodeItem(
        file = DriveFile(
            id = id,
            name = name,
            size = null,
            mimeType = "video/mp4",
            iconLink = null,
            shortcutDetails = ShortcutDetails(),
            starred = Starred.UNSTARRED
        ),
        displayTitle = name,
        episodeNumber = null,
        durationText = "",
        audioBadgeText = "",
        progressPercent = 0,
        watchedDuration = 0L,
        totalDuration = 0L
    )
}
