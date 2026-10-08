package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred

class HomeShelfOrderPolicyTest {

    @Test
    fun refreshKeepsExistingOrderUpdatesItemsAndAppendsNewOnes() {
        val previous = listOf(
            folder("a", "A old"),
            folder("b", "B old"),
            folder("c", "C old")
        )
        val incoming = listOf(
            folder("new", "New"),
            folder("c", "C updated"),
            folder("b", "B updated"),
            folder("a", "A updated")
        )

        val result = preserveFocusedShelfOrder(previous, incoming)

        assertEquals(listOf("a", "b", "c", "new"), result.map { it.id })
        assertEquals(listOf("A updated", "B updated", "C updated", "New"), result.map { it.name })
    }

    @Test
    fun refreshDropsEntriesThatAreNoLongerInTheShelf() {
        val result = preserveFocusedShelfOrder(
            listOf(folder("a", "A"), folder("removed", "Removed"), folder("b", "B")),
            listOf(folder("new", "New"), folder("b", "B"), folder("a", "A"))
        )

        assertEquals(listOf("a", "b", "new"), result.map { it.id })
    }

    private fun folder(id: String, name: String) = DriveFile(
        id = id,
        name = name,
        size = null,
        mimeType = "application/vnd.google-apps.folder",
        iconLink = null,
        shortcutDetails = ShortcutDetails(),
        starred = Starred.STARRED
    )
}
