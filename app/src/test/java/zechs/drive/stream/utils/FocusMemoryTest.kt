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
}
