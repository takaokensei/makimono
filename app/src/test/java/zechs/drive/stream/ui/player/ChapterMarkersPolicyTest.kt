package zechs.drive.stream.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
