package makimono.dossie.provas.focus

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MakimonoFocusMemoryManagerTest {

    private lateinit var focusManager: MakimonoFocusMemoryManager

    @Before
    fun setUp() {
        focusManager = MakimonoFocusMemoryManager()
    }

    @Test
    fun unvisitedShelfReturnsZeroIndex() {
        val target = focusManager.resolveTargetIndex("shelf_trending", 10)
        assertEquals(0, target)
    }

    @Test
    fun rememberedIndexIsRestoredExactly() {
        focusManager.remember("shelf_trending", 3, "anime-black-clover")
        val target = focusManager.resolveTargetIndex("shelf_trending", 10)
        assertEquals(3, target)
        assertEquals("anime-black-clover", focusManager.getRememberedId("shelf_trending"))
    }

    @Test
    fun rememberedIndexClampsWhenItemsReduced() {
        focusManager.remember("shelf_trending", 8, "anime-solo-leveling")

        // Lista foi filtrada ou reduziu de 10 para 5 itens
        val target = focusManager.resolveTargetIndex("shelf_trending", 5)
        assertEquals(4, target) // Último índice válido (5 - 1)
    }

    @Test
    fun multipleShelvesMaintainIndependentMemory() {
        focusManager.remember("shelf_trending", 2)
        focusManager.remember("shelf_continue", 5)

        assertEquals(2, focusManager.resolveTargetIndex("shelf_trending", 10))
        assertEquals(5, focusManager.resolveTargetIndex("shelf_continue", 10))
    }
}
