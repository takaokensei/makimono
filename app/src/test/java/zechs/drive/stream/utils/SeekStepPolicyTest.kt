package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SeekStepPolicyTest {

    private lateinit var policy: SeekStepPolicy

    @Before
    fun setup() {
        policy = SeekStepPolicy()
    }

    @Test
    fun testFirstPressForward() {
        val delta = policy.onSeekPress(1, nowMs = 1000L)
        assertEquals(10, delta)
        assertEquals(10, policy.cumulativeSeconds)
        assertEquals("+10s", policy.formatCumulative())
    }

    @Test
    fun testProgressiveStepsForward() {
        // 1st press: 10s
        assertEquals(10, policy.onSeekPress(1, nowMs = 1000L))
        assertEquals(10, policy.cumulativeSeconds)

        // 2nd press: 30s -> cumulative 40s
        assertEquals(30, policy.onSeekPress(1, nowMs = 1500L))
        assertEquals(40, policy.cumulativeSeconds)
        assertEquals("+40s", policy.formatCumulative())

        // 3rd press: 60s -> cumulative 100s (1m 40s)
        assertEquals(60, policy.onSeekPress(1, nowMs = 2000L))
        assertEquals(100, policy.cumulativeSeconds)
        assertEquals("+1m 40s", policy.formatCumulative())

        // 4th press: 180s -> cumulative 280s (4m 40s)
        assertEquals(180, policy.onSeekPress(1, nowMs = 2500L))
        assertEquals(280, policy.cumulativeSeconds)
        assertEquals("+4m 40s", policy.formatCumulative())

        // 5th press: 300s -> cumulative 580s (9m 40s)
        assertEquals(300, policy.onSeekPress(1, nowMs = 3000L))
        assertEquals(580, policy.cumulativeSeconds)
        assertEquals("+9m 40s", policy.formatCumulative())
    }

    @Test
    fun testBackwardProgressiveSteps() {
        assertEquals(-10, policy.onSeekPress(-1, nowMs = 1000L))
        assertEquals(-10, policy.cumulativeSeconds)
        assertEquals("-10s", policy.formatCumulative())

        assertEquals(-30, policy.onSeekPress(-1, nowMs = 1500L))
        assertEquals(-40, policy.cumulativeSeconds)
        assertEquals("-40s", policy.formatCumulative())
    }

    @Test
    fun testTimeoutResetsToFirstStep() {
        policy.onSeekPress(1, nowMs = 1000L) // +10s
        policy.onSeekPress(1, nowMs = 1500L) // +30s -> +40s

        // Wait beyond TIMEOUT_MS (1200ms) -> should reset to 1st step (+10s)
        val deltaAfterTimeout = policy.onSeekPress(1, nowMs = 3000L)
        assertEquals(10, deltaAfterTimeout)
        assertEquals(10, policy.cumulativeSeconds)
    }

    @Test
    fun testDirectionChangeResetsAccumulator() {
        policy.onSeekPress(1, nowMs = 1000L) // +10s
        policy.onSeekPress(1, nowMs = 1500L) // +30s -> +40s

        // Reverse direction to backward immediately
        val deltaReversed = policy.onSeekPress(-1, nowMs = 1800L)
        assertEquals(-10, deltaReversed)
        assertEquals(-10, policy.cumulativeSeconds)
    }
}
