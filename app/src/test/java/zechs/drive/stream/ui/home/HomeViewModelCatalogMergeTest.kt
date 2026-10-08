package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred

class HomeViewModelCatalogMergeTest {

    @Test
    fun incompleteRefreshKeepsCachedItemsAndUsesRefreshedVersionForMatchingIds() {
        val cached = listOf(folder("a", "Old A"), folder("b", "B"))
        val refreshed = listOf(folder("a", "New A"), folder("c", "C"))

        val result = mergeCatalogSnapshot(refreshed, cached, isComplete = false)

        assertEquals(listOf("New A", "C", "B"), result.map { it.name })
    }

    @Test
    fun completeRefreshReplacesCachedSnapshot() {
        val cached = listOf(folder("a", "A"), folder("b", "B"))
        val refreshed = listOf(folder("c", "C"))

        val result = mergeCatalogSnapshot(refreshed, cached, isComplete = true)

        assertEquals(listOf("C"), result.map { it.name })
    }

    @Test
    fun configuredRootIsNeverPublishedAsCatalogItemOrFeaturedCandidate() {
        val entries = listOf(
            folder("library-root", "oneblacki"),
            folder("bakemonogatari", "Bakemonogatari"),
            DriveFile(
                id = "root-shortcut",
                name = "Biblioteca",
                size = null,
                mimeType = "application/vnd.google-apps.shortcut",
                iconLink = null,
                shortcutDetails = ShortcutDetails(
                    targetId = "library-root",
                    targetMimeType = "application/vnd.google-apps.folder"
                ),
                starred = Starred.UNSTARRED
            )
        )

        val result = excludeLibraryRoot(entries, libraryRootId = "library-root")

        assertEquals(listOf("Bakemonogatari"), result.map { it.name })
    }

    private fun folder(id: String, name: String) = DriveFile(
        id = id,
        name = name,
        size = null,
        mimeType = "application/vnd.google-apps.folder",
        iconLink = null,
        shortcutDetails = ShortcutDetails(),
        starred = Starred.UNSTARRED
    )
}
