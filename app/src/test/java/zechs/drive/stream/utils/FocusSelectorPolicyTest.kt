package zechs.drive.stream.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSelectorPolicyTest {
    private val focused = 41
    private val pressed = 42

    @Test
    fun pressedAndDefaultStatesDoNotClaimFocusVisuals() {
        assertFalse(
            FocusSelectorPolicy.hasFocusedState(
                stateSpecs = listOf(intArrayOf(pressed), intArrayOf()),
                focusedStateAttribute = focused
            )
        )
    }

    @Test
    fun explicitFocusedStateClaimsFocusVisuals() {
        assertTrue(
            FocusSelectorPolicy.hasFocusedState(
                stateSpecs = listOf(intArrayOf(focused), intArrayOf()),
                focusedStateAttribute = focused
            )
        )
    }

    @Test
    fun negatedFocusedStateDoesNotClaimFocusedVisuals() {
        assertFalse(
            FocusSelectorPolicy.hasFocusedState(
                stateSpecs = listOf(intArrayOf(-focused), intArrayOf()),
                focusedStateAttribute = focused
            )
        )
    }
}
