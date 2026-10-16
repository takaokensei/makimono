package makimono.dossie.audit

import makimono.dossie.provas.rail.MakimonoRailCoordinator
import makimono.dossie.provas.rail.RailFocusState
import makimono.dossie.provas.focus.MakimonoFocusMemoryManager
import makimono.dossie.provas.player.MakimonoPlayerOsdSkipCoordinator
import makimono.dossie.provas.player.SkipButtonState
import makimono.dossie.provas.player.SkipInterval
import makimono.dossie.provas.player.SkipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit requirements for integration; these are not claims about Crunchyroll.
 * Failures document limits of the existing prototypes, without modifying them.
 */
class ContractRegressionAuditTest {
    @Test
    fun cancellingExitRestoresThePreviouslyFocusedRailItem() {
        val coordinator = MakimonoRailCoordinator()
        coordinator.onRailFocused(3)
        coordinator.handleBackPress()
        coordinator.handleBackPress()
        assertEquals(RailFocusState.ExpandedInRail(3), coordinator.state)
    }

    @Test
    fun updatingIndexWithoutIdentityDoesNotKeepAnUnrelatedOldIdentity() {
        val memory = MakimonoFocusMemoryManager()
        memory.remember("shelf", 0, "old-item")
        memory.remember("shelf", 1)
        assertNull(memory.getRememberedId("shelf"))
    }

    @Test
    fun restoringAfterReorderFindsTheSameItemRatherThanTheSameIndex() {
        val memory = MakimonoFocusMemoryManager()
        memory.remember("shelf", 1, "b")

        // Permutações variadas sem amarra a uma lista fixa
        val permutation1 = listOf("c", "a", "b")
        val restoredIndex1 = memory.resolveTargetIndex("shelf", permutation1)
        assertEquals("b", permutation1[restoredIndex1])
        assertEquals(2, restoredIndex1)

        val permutation2 = listOf("b", "x", "y", "z")
        val restoredIndex2 = memory.resolveTargetIndex("shelf", permutation2)
        assertEquals("b", permutation2[restoredIndex2])
        assertEquals(0, restoredIndex2)

        val permutation3 = listOf("alpha", "beta", "gamma", "b")
        val restoredIndex3 = memory.resolveTargetIndex("shelf", permutation3)
        assertEquals("b", permutation3[restoredIndex3])
        assertEquals(3, restoredIndex3)
    }

    @Test
    fun restoringWhenIdMissingFallsBackToClampedIndex() {
        val memory = MakimonoFocusMemoryManager()
        memory.remember("shelf", 3, "removed-item")

        val currentItems = listOf("item0", "item1")
        val fallbackIndex = memory.resolveTargetIndex("shelf", currentItems)
        assertEquals(1, fallbackIndex) // Clamped para último item válido (size - 1)

        val emptyListFallback = memory.resolveTargetIndex("shelf", emptyList<String>())
        assertEquals(0, emptyListFallback)
    }

    @Test
    fun anAcceptedSkipDoesNotReappearOnTheStalePositionBeforeSeekCompletes() {
        val coordinator = MakimonoPlayerOsdSkipCoordinator()
        val intervals = listOf(SkipInterval(SkipType.INTRO, 0L, 10_000L))
        coordinator.onPlaybackTimeTick(5_000L, intervals, 1_000L)
        coordinator.performSkip()
        coordinator.onPlaybackTimeTick(5_000L, intervals, 1_010L)
        assertEquals(SkipButtonState.Hidden, coordinator.skipButtonState)
    }
}
