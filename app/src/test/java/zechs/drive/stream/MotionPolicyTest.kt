package zechs.drive.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.MotionPreference

class MotionPolicyTest {
    @Test
    fun systemPreferenceFollowsAndroidAnimationSetting() {
        assertFalse(MotionPolicy.shouldReduceMotion(MotionPreference.SYSTEM, systemAnimationsEnabled = true))
        assertTrue(MotionPolicy.shouldReduceMotion(MotionPreference.SYSTEM, systemAnimationsEnabled = false))
    }

    @Test
    fun reducedPreferenceAlwaysReducesMotion() {
        assertTrue(MotionPolicy.shouldReduceMotion(MotionPreference.REDUCED, systemAnimationsEnabled = true))
        assertTrue(MotionPolicy.shouldReduceMotion(MotionPreference.REDUCED, systemAnimationsEnabled = false))
    }

    @Test
    fun activeProfilePreferenceIsUsedBySynchronousViewPolicy() {
        MotionPolicy.updateActiveProfilePreference(MotionPreference.REDUCED)
        try {
            assertTrue(MotionPolicy.isReducedMotion())
        } finally {
            MotionPolicy.updateActiveProfilePreference(MotionPreference.SYSTEM)
        }
    }

    @Test
    fun reducedMotionDisablesBlurEvenWhenSystemAndHardwareAllowIt() {
        assertFalse(
            MotionPolicy.shouldUseBlur(
                MotionPreference.REDUCED,
                systemAnimationsEnabled = true,
                crossWindowBlurEnabled = true,
                lowRamDevice = false
            )
        )
    }

    @Test
    fun lowRamDisablesBlur() {
        assertFalse(
            MotionPolicy.shouldUseBlur(
                MotionPreference.SYSTEM,
                systemAnimationsEnabled = true,
                crossWindowBlurEnabled = true,
                lowRamDevice = true
            )
        )
    }
}
