package zechs.drive.stream.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BuildLibraryShelvesUseCaseTest {

    private lateinit var useCase: BuildLibraryShelvesUseCase

    @Before
    fun setUp() {
        useCase = BuildLibraryShelvesUseCase()
    }

    @Test
    fun buildsContinueWatchingShelfOnlyWhenItemsHaveValidProgress() {
        val continueWatching = listOf(
            LibraryMediaItem("cw_1", "Show A", progressPercentage = 50f, lastAccessedTimestamp = 200L),
            LibraryMediaItem("cw_2", "Show B", progressPercentage = 98f, lastAccessedTimestamp = 100L), // > 95% = finished, excluded
            LibraryMediaItem("cw_3", "Show C", progressPercentage = 0.5f, lastAccessedTimestamp = 50L)  // < 1% = unwatched, excluded
        )

        val shelves = useCase(allItems = emptyList(), continueWatchingItems = continueWatching)
        val cwShelf = shelves.firstOrNull { it.id == "shelf_continue_watching" }

        assertNotNull(cwShelf)
        assertEquals(1, cwShelf?.items?.size)
        assertEquals("cw_1", cwShelf?.items?.first()?.id)
    }

    @Test
    fun sortsContinueWatchingByLastAccessedTimestampDescending() {
        val continueWatching = listOf(
            LibraryMediaItem("cw_1", "Show A", progressPercentage = 30f, lastAccessedTimestamp = 100L),
            LibraryMediaItem("cw_2", "Show B", progressPercentage = 40f, lastAccessedTimestamp = 300L),
            LibraryMediaItem("cw_3", "Show C", progressPercentage = 50f, lastAccessedTimestamp = 200L)
        )

        val shelves = useCase(allItems = emptyList(), continueWatchingItems = continueWatching)
        val cwShelf = shelves.first { it.id == "shelf_continue_watching" }

        assertEquals(listOf("cw_2", "cw_3", "cw_1"), cwShelf.items.map { it.id })
    }

    @Test
    fun buildsRecentlyAddedShelfCappedAt20Items() {
        val allItems = (1..30).map { i ->
            LibraryMediaItem(
                id = "item_$i",
                title = "Title $i",
                lastAccessedTimestamp = i.toLong()
            )
        }

        val shelves = useCase(allItems = allItems)
        val recentShelf = shelves.firstOrNull { it.id == "shelf_recently_added" }

        assertNotNull(recentShelf)
        assertEquals(20, recentShelf?.items?.size)
        // Most recent first (item_30 down to item_11)
        assertEquals("item_30", recentShelf?.items?.first()?.id)
    }

    @Test
    fun separatesSeriesAndMoviesShelves() {
        val allItems = listOf(
            LibraryMediaItem("s_1", "Series 1", mediaType = MediaType.SERIES),
            LibraryMediaItem("m_1", "Movie 1", mediaType = MediaType.MOVIE),
            LibraryMediaItem("s_2", "Series 2", mediaType = MediaType.SERIES),
            LibraryMediaItem("m_2", "Movie 2", mediaType = MediaType.MOVIE)
        )

        val shelves = useCase(allItems = allItems)
        val seriesShelf = shelves.firstOrNull { it.id == "shelf_series" }
        val moviesShelf = shelves.firstOrNull { it.id == "shelf_movies" }

        assertNotNull(seriesShelf)
        assertNotNull(moviesShelf)
        assertEquals(2, seriesShelf?.items?.size)
        assertEquals(2, moviesShelf?.items?.size)
    }

    @Test
    fun omitsShelvesWhenItemsAreEmpty() {
        val shelves = useCase(allItems = emptyList(), continueWatchingItems = emptyList())
        assertEquals(0, shelves.size)
    }
}
