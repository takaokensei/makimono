package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesAiringStatusPolicyTest {
    @Test
    fun mapsKnownAniListAndMalStatuses() {
        assertEquals(SeriesAiringStatus.COMPLETED, SeriesAiringStatusPolicy.classify("FINISHED"))
        assertEquals(SeriesAiringStatus.COMPLETED, SeriesAiringStatusPolicy.classify("Finished Airing"))
        assertEquals(SeriesAiringStatus.AIRING, SeriesAiringStatusPolicy.classify("RELEASING"))
        assertEquals(SeriesAiringStatus.AIRING, SeriesAiringStatusPolicy.classify("Currently Airing"))
        assertEquals(SeriesAiringStatus.UPCOMING, SeriesAiringStatusPolicy.classify("NOT_YET_RELEASED"))
        assertEquals(SeriesAiringStatus.UPCOMING, SeriesAiringStatusPolicy.classify("Not yet aired"))
        assertEquals(SeriesAiringStatus.HIATUS, SeriesAiringStatusPolicy.classify("HIATUS"))
        assertEquals(SeriesAiringStatus.CANCELLED, SeriesAiringStatusPolicy.classify("CANCELLED"))
        assertEquals(SeriesAiringStatus.CANCELLED, SeriesAiringStatusPolicy.classify("canceled"))
    }

    @Test
    fun blankAndUnknownStatusesRemainUnknown() {
        assertNull(SeriesAiringStatusPolicy.classify(null))
        assertNull(SeriesAiringStatusPolicy.classify("  "))
        assertNull(SeriesAiringStatusPolicy.classify("SPECIAL"))
        assertNull(SeriesAiringStatusPolicy.classify("currently airing soon"))
    }
}
