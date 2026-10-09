package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.utils.FocusEngine.FocusKind

class FocusEnginePolicyTest {

    @Test
    fun card_focused_normalMotion_returns106ScaleAnd12Elevation() {
        val target = FocusEngine.targetFor(FocusKind.CARD, focused = true, reducedMotion = false)
        assertEquals(1.06f, target.scale, 0.001f)
        assertEquals(12.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun card_unfocused_returnsResting10ScaleAnd0Elevation() {
        val target = FocusEngine.targetFor(FocusKind.CARD, focused = false, reducedMotion = false)
        assertEquals(1.0f, target.scale, 0.001f)
        assertEquals(0.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun card_focused_reducedMotion_returns10ScaleAnd0Elevation() {
        val target = FocusEngine.targetFor(FocusKind.CARD, focused = true, reducedMotion = true)
        assertEquals(1.0f, target.scale, 0.001f)
        assertEquals(0.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun button_focused_normalMotion_returns104ScaleAnd6Elevation() {
        val target = FocusEngine.targetFor(FocusKind.BUTTON, focused = true, reducedMotion = false)
        assertEquals(1.04f, target.scale, 0.001f)
        assertEquals(6.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun button_unfocused_returnsResting() {
        val target = FocusEngine.targetFor(FocusKind.BUTTON, focused = false, reducedMotion = false)
        assertEquals(1.0f, target.scale, 0.001f)
        assertEquals(0.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun button_focused_reducedMotion_returnsResting() {
        val target = FocusEngine.targetFor(FocusKind.BUTTON, focused = true, reducedMotion = true)
        assertEquals(1.0f, target.scale, 0.001f)
        assertEquals(0.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun listRow_focused_returns10ScaleAnd0Elevation() {
        val target = FocusEngine.targetFor(FocusKind.LIST_ROW, focused = true, reducedMotion = false)
        assertEquals(1.0f, target.scale, 0.001f)
        assertEquals(0.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun none_focused_returns10ScaleAnd0Elevation() {
        val target = FocusEngine.targetFor(FocusKind.NONE, focused = true, reducedMotion = false)
        assertEquals(1.0f, target.scale, 0.001f)
        assertEquals(0.0f, target.elevationDp, 0.001f)
    }

    @Test
    fun unfocused_reducedMotion_returnsRestingAcrossAllKinds() {
        for (kind in FocusKind.values()) {
            val target = FocusEngine.targetFor(kind, focused = false, reducedMotion = true)
            assertEquals(1.0f, target.scale, 0.001f)
            assertEquals(0.0f, target.elevationDp, 0.001f)
        }
    }
}
