package zechs.drive.stream.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.MatroskaChapterParser.ChapterType
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter

class ChapterMarkersPolicyTest {

    @Test
    fun zeroDuration_producesNoMarks() {
        val markers = listOf(ChapterMarker(startMs = 1000L))
        val bounds = ChapterMarkersPolicy.calculateBounds(widthPx = 1000, durationMs = 0L, markers = markers)
        assertTrue(bounds.isEmpty())
    }

    @Test
    fun validMarkers_calculatesCorrectXPositions() {
        val markers = listOf(
            ChapterMarker(startMs = 25000L), // 25% of 100000ms
            ChapterMarker(startMs = 50000L), // 50%
            ChapterMarker(startMs = 80000L, endMs = 90000L) // 80% to 90%
        )
        val bounds = ChapterMarkersPolicy.calculateBounds(widthPx = 1000, durationMs = 100000L, markers = markers)
        assertEquals(3, bounds.size)
        assertEquals(250f, bounds[0].startX, 0.5f)
        assertEquals(500f, bounds[1].startX, 0.5f)
        assertEquals(800f, bounds[2].startX, 0.5f)
        assertEquals(900f, bounds[2].endX, 0.5f)
    }

    @Test
    fun `buildChapterMenuEntries merges chapters and bookmarks chronologically and marks current`() {
        val chapters = listOf(
            ParsedChapter(0, "Prólogo", 0L, 60_000L, ChapterType.OTHER),
            ParsedChapter(1, "Abertura", 60_000L, 150_000L, ChapterType.OPENING),
            ParsedChapter(2, "Parte A", 150_000L, 600_000L, ChapterType.OTHER)
        )
        val bookmarks = listOf(95_000L)
        val entries = ChapterMarkersPolicy.buildChapterMenuEntries(
            chapters = chapters,
            userBookmarksMs = bookmarks,
            currentPositionMs = 100_000L
        )

        assertEquals(4, entries.size)
        assertEquals("Prólogo", entries[0].title)
        assertEquals("Abertura", entries[1].title)
        assertTrue(entries[2].isUserBookmark)
        assertEquals("01:35", entries[2].timestampLabel)
        assertTrue(entries[2].isCurrent)
        assertEquals("Parte A", entries[3].title)
    }

    @Test
    fun `addBookmark deduplicates nearby bookmarks within window and sorts result`() {
        val initial = listOf(10_000L, 60_000L)
        val duplicate = ChapterMarkersPolicy.addBookmark(initial, positionMs = 11_000L, dedupeWindowMs = 2_000L)
        assertEquals(listOf(10_000L, 60_000L), duplicate)

        val added = ChapterMarkersPolicy.addBookmark(initial, positionMs = 30_000L, dedupeWindowMs = 2_000L)
        assertEquals(listOf(10_000L, 30_000L, 60_000L), added)
    }
}
