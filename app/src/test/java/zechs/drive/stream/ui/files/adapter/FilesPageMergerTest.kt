package zechs.drive.stream.ui.files.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred

class FilesPageMergerTest {
    @Test
    fun mergeKeepsOrderRefreshesRepeatedIdsAndDoesNotMutateSubmittedList() {
        val first = file("a", "A")
        val oldB = file("b", "B old", posterUrl = "old")
        val loading = FilesDataModel.Loading
        val alreadySubmitted = listOf(first, oldB, loading)
        val refreshedB = file("b", "B updated", posterUrl = "new")
        val c = file("c", "C")

        val merged = FilesPageMerger.merge(
            previous = alreadySubmitted,
            incoming = listOf(refreshedB, c, file("c", "C duplicate"))
        )

        assertNotSame(alreadySubmitted, merged)
        assertEquals(listOf("a", "b", "c"), merged.map { (it as FilesDataModel.File).driveFile.id })
        assertSame(first, merged[0])
        assertSame(refreshedB, merged[1])
        assertEquals("C duplicate", (merged[2] as FilesDataModel.File).driveFile.name)
        assertEquals(listOf(first, oldB, loading), alreadySubmitted)
    }

    @Test
    fun mergeWithoutPreviousPageProducesUniqueFirstPage() {
        val merged = FilesPageMerger.merge(
            previous = null,
            incoming = listOf(file("a", "first"), file("a", "latest"), file("b", "B"))
        )

        assertEquals(listOf("a", "b"), merged.map { (it as FilesDataModel.File).driveFile.id })
        assertEquals("latest", (merged.first() as FilesDataModel.File).driveFile.name)
    }

    private fun file(id: String, name: String, posterUrl: String? = null) =
        FilesDataModel.File(
            DriveFile(
                id = id,
                name = name,
                size = 1L,
                mimeType = "video/mp4",
                iconLink = null,
                shortcutDetails = ShortcutDetails(),
                starred = Starred.UNSTARRED,
                posterUrl = posterUrl
            )
        )
}
