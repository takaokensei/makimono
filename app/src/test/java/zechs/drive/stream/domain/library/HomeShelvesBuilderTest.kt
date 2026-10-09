package zechs.drive.stream.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeShelvesBuilderTest {

    private lateinit var builder: HomeShelvesBuilder

    @Before
    fun setup() {
        builder = HomeShelvesBuilder()
    }

    @Test
    fun `empty inputs return empty shelves`() {
        val shelves = builder.build()
        assertTrue(shelves.isEmpty())
    }

    @Test
    fun `continue watching shelf is first and ordered by lastAccessed descending`() {
        val item1 = LibraryMediaItem(
            id = "1",
            title = "Item 1",
            lastAccessedTimestamp = 100L,
            progressPercentage = 50f
        )
        val item2 = LibraryMediaItem(
            id = "2",
            title = "Item 2",
            lastAccessedTimestamp = 200L,
            progressPercentage = 30f
        )
        val itemFinished = LibraryMediaItem(
            id = "3",
            title = "Finished Item",
            lastAccessedTimestamp = 300L,
            progressPercentage = 98f // finished > 95%
        )

        val shelves = builder.build(
            continueWatching = listOf(item1, item2, itemFinished)
        )

        assertEquals(1, shelves.size)
        val continueShelf = shelves[0]
        assertEquals("shelf_continue_watching", continueShelf.id)
        assertEquals(2, continueShelf.items.size)
        assertEquals("2", continueShelf.items[0].id)
        assertEquals("1", continueShelf.items[1].id)
    }

    @Test
    fun `all populated shelves are placed in canonical sequence`() {
        val inProgress = listOf(
            LibraryMediaItem("cw-1", "CW 1", progressPercentage = 40f, lastAccessedTimestamp = 1000L)
        )
        val starred = listOf(LibraryMediaItem("st-1", "Starred 1"))
        val followed = listOf(LibraryMediaItem("fl-1", "Followed 1"))
        val recent = listOf(LibraryMediaItem("rc-1", "Recent 1"))
        val catalog = listOf(LibraryMediaItem("ct-1", "Catalog 1"))

        val shelves = builder.build(
            continueWatching = inProgress,
            starred = starred,
            followed = followed,
            recentlyAdded = recent,
            catalog = catalog
        )

        assertEquals(5, shelves.size)
        assertEquals("shelf_continue_watching", shelves[0].id)
        assertEquals("shelf_starred", shelves[1].id)
        assertEquals("shelf_followed", shelves[2].id)
        assertEquals("shelf_recently_added", shelves[3].id)
        assertEquals("shelf_catalog", shelves[4].id)
    }
}
