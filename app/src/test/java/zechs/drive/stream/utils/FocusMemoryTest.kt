package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FocusMemoryTest {

    @Before
    fun setUp() {
        FocusMemory.clearAll()
    }

    @Test
    fun rememberAndGet_storesAndRetrievesIdCorrectly() {
        FocusMemory.remember("home.continue", "file-123")
        assertEquals("file-123", FocusMemory.get("home.continue"))
    }

    @Test
    fun get_unregisteredKey_returnsNull() {
        assertNull(FocusMemory.get("unknown.shelf"))
    }

    @Test
    fun clear_removesStoredId() {
        FocusMemory.remember("details.episodes", "ep-5")
        FocusMemory.clear("details.episodes")
        assertNull(FocusMemory.get("details.episodes"))
    }

    @Test
    fun multipleKeys_isolatedStorage() {
        FocusMemory.remember("home.shelf1", "item-a")
        FocusMemory.remember("home.shelf2", "item-b")

        assertEquals("item-a", FocusMemory.get("home.shelf1"))
        assertEquals("item-b", FocusMemory.get("home.shelf2"))
    }

    @Test
    fun updatingMemoryWithoutIdClearsOldIdentity() {
        FocusMemory.remember("shelf-1", 1, "item-1")
        assertEquals("item-1", FocusMemory.getRememberedId("shelf-1"))

        FocusMemory.remember("shelf-1", 2, null)
        assertNull(FocusMemory.getRememberedId("shelf-1"))
        assertEquals(2, FocusMemory.resolveTargetIndex("shelf-1", totalItemsCount = 5))
    }

    @Test
    fun restoringAfterReorderFindsTheSameItemAcrossMultiplePermutations() {
        FocusMemory.remember("shelf-1", 1, "b")

        // Permutation 1: 'b' moved to index 2
        assertEquals(2, FocusMemory.resolveTargetIndex("shelf-1", listOf("c", "a", "b")))
        assertEquals(2, FocusMemory.getRememberedIndex("shelf-1"))

        // Permutation 2: 'b' moved to index 0
        assertEquals(0, FocusMemory.resolveTargetIndex("shelf-1", listOf("b", "d", "a", "c")))
        assertEquals(0, FocusMemory.getRememberedIndex("shelf-1"))

        // Permutation 3: 'b' moved to index 3 in a 5-item list
        assertEquals(3, FocusMemory.resolveTargetIndex("shelf-1", listOf("x", "y", "z", "b", "w")))
    }

    @Test
    fun restoringWhenRememberedIdIsRemovedClampsToRememberedIndex() {
        FocusMemory.remember("shelf-1", 3, "ep-4")
        val afterRemoval = listOf("ep-1", "ep-2", "ep-3")
        // 'ep-4' no longer exists; fallback clamps index 3 -> last valid index 2
        assertEquals(2, FocusMemory.resolveTargetIndex("shelf-1", afterRemoval))
    }

    @Test
    fun twoArgRememberPreservesExistingNonZeroIndex() {
        FocusMemory.remember("home.continue_watching", 4, "item-4")
        FocusMemory.remember("home.continue_watching", "item-5")
        assertEquals("item-5", FocusMemory.get("home.continue_watching"))
        assertEquals(4, FocusMemory.getRememberedIndex("home.continue_watching"))
    }

    @Test
    fun defensiveClampingWhenItemsRemoved() {
        FocusMemory.remember("shelf-1", 10)
        val targetIndex = FocusMemory.resolveTargetIndex("shelf-1", totalItemsCount = 5)
        assertEquals(4, targetIndex)
    }
}
