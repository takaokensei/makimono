package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShimmerPolicyTest {

    @Test
    fun standardMotion_usesBreathingRangeAnd1200ms() {
        val spec = ShimmerController.resolveSpec(reducedMotion = false)
        assertEquals(0.06f, spec.minAlpha, 0.001f)
        assertEquals(0.12f, spec.maxAlpha, 0.001f)
        assertEquals(1200L, spec.durationMs)
        assertTrue(spec.isAnimated)
    }

    @Test
    fun reducedMotion_usesFixedAlphaAndNoAnimation() {
        val spec = ShimmerController.resolveSpec(reducedMotion = true)
        assertEquals(0.08f, spec.minAlpha, 0.001f)
        assertEquals(0.08f, spec.maxAlpha, 0.001f)
        assertEquals(0L, spec.durationMs)
        assertFalse(spec.isAnimated)
    }
}
