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

    @Test
    fun animatorDurationScaleZeroReducesMotionWhileOneAllowsNormalMotion() {
        assertTrue(MotionPolicy.isReducedForAnimatorScale(MotionPreference.SYSTEM, 0f))
        assertFalse(MotionPolicy.isReducedForAnimatorScale(MotionPreference.SYSTEM, 1f))
        assertTrue(MotionPolicy.isReducedForAnimatorScale(MotionPreference.REDUCED, 1f))
    }

    @Test
    fun resolveDurationReturnsZeroWhenReducedMotionIsActive() {
        org.junit.Assert.assertEquals(0L, MotionPolicy.resolveDuration(220L, reducedMotion = true))
        org.junit.Assert.assertEquals(220L, MotionPolicy.resolveDuration(220L, reducedMotion = false))
    }

    @Test
    fun motionSpecTokensEnforceEnterLongerThanExitRule() {
        val candidates = listOf(
            java.io.File("src/main/res/values/motion_spec.xml"),
            java.io.File("app/src/main/res/values/motion_spec.xml")
        )
        val xmlFile = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate motion_spec.xml from working directory")
        val content = xmlFile.readText()

        fun extractInt(name: String): Int {
            val regex = Regex("""<item\s+name="$name"\s+type="integer">(\d+)</item>""")
            return regex.find(content)?.groupValues?.get(1)?.toInt()
                ?: error("Missing integer token $name in motion_spec.xml")
        }

        val osdShow = extractInt("motion_osd_show")
        val osdHide = extractInt("motion_osd_hide")
        val pillEnter = extractInt("motion_pill_enter")
        val pillExit = extractInt("motion_pill_exit")

        assertTrue("Expected motion_osd_show ($osdShow) > motion_osd_hide ($osdHide)", osdShow > osdHide)
        assertTrue("Expected motion_pill_enter ($pillEnter) > motion_pill_exit ($pillExit)", pillEnter > pillExit)
    }
}
