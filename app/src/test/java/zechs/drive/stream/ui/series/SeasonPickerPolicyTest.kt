package zechs.drive.stream.ui.series

import org.junit.Assert.*
import org.junit.Test

class SeasonPickerPolicyTest {
    @Test fun emptyCatalogHasNoSelectionOrCompactPicker() {
        val result = SeasonPickerPolicy.present(emptyList(), null)
        assertFalse(result.compact)
        assertEquals(-1, result.selectedIndex)
        assertTrue(result.visibleIndices.isEmpty())
    }

    @Test fun upToFourPartsKeepEveryPartVisible() {
        for (count in 1..4) {
            val ids = (1..count).map(Int::toString)
            val result = SeasonPickerPolicy.present(ids, ids.last())
            assertFalse(result.compact)
            assertEquals(ids.lastIndex, result.selectedIndex)
            assertEquals(ids.indices.toList(), result.visibleIndices)
        }
    }

    @Test fun largeCatalogKeepsOnlySelectedPartAndItsCategoriesVisible() {
        val result = SeasonPickerPolicy.present(listOf("all", "s1", "s2", "s3", "s4"), "s3")
        assertTrue(result.compact)
        assertEquals(3, result.selectedIndex)
        assertEquals(listOf(3), result.visibleIndices)
    }

    @Test fun missingSelectionFallsBackToFirstPart() {
        val ids = (1..20).map(Int::toString)
        for (selection in listOf(null, "removed")) {
            val result = SeasonPickerPolicy.present(ids, selection)
            assertEquals(0, result.selectedIndex)
            assertEquals(listOf(0), result.visibleIndices)
        }
    }

    @Test fun reorderingPreservesSelectionByIdentity() {
        val ids = listOf("s4", "s3", "s2", "s1", "specials")
        val result = SeasonPickerPolicy.present(ids, "s1")
        assertEquals("s1", ids[result.selectedIndex])
        assertEquals(listOf(3), result.visibleIndices)
    }

    @Test fun catalogShrinkingRestoresFullListAndCurrentSelection() {
        val result = SeasonPickerPolicy.present(listOf("s2", "s3"), "s3")
        assertFalse(result.compact)
        assertEquals(1, result.selectedIndex)
        assertEquals(listOf(0, 1), result.visibleIndices)
    }
}
