package zechs.drive.stream.playback.osd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillVisibilityPolicyTest {

    @Test
    fun initialStateIsHidden() {
        val policy = PillVisibilityPolicy()
        assertEquals(PillVisibilityPolicy.State.HIDDEN, policy.state)
    }

    @Test
    fun requestShowTransitionsToVisible() {
        val policy = PillVisibilityPolicy()
        val cancelled = policy.requestShow()

        assertEquals(PillVisibilityPolicy.State.VISIBLE, policy.state)
        assertFalse(cancelled)
    }

    @Test
    fun requestDelayedHideTransitionsToPendingHide() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()
        policy.requestDelayedHide()

        assertEquals(PillVisibilityPolicy.State.PENDING_HIDE, policy.state)
    }

    @Test
    fun onHideAnimationStartedTransitionsToHiding() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()
        policy.requestDelayedHide()
        policy.onHideAnimationStarted()

        assertEquals(PillVisibilityPolicy.State.HIDING, policy.state)
    }

    @Test
    fun normalHideLifecycleCompletesToHidden() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()
        policy.requestDelayedHide()
        policy.onHideAnimationStarted()

        val shouldHideView = policy.onHideAnimationEnded()

        assertEquals(PillVisibilityPolicy.State.HIDDEN, policy.state)
        assertTrue(shouldHideView)
    }

    @Test
    fun rapidShowDuringHidingCancelsExitAnimationAndRemainsVisible() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()
        policy.requestDelayedHide()
        policy.onHideAnimationStarted()
        assertEquals(PillVisibilityPolicy.State.HIDING, policy.state)

        // Rapid user interaction during exit animation
        val cancelled = policy.requestShow()

        assertTrue(cancelled)
        assertEquals(PillVisibilityPolicy.State.VISIBLE, policy.state)
    }

    @Test
    fun endActionAfterRapidShowDoesNotHideView() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()
        policy.requestDelayedHide()
        policy.onHideAnimationStarted()

        // User touches again before animation ends
        policy.requestShow()

        // Stale animation completion callback fires
        val shouldHideView = policy.onHideAnimationEnded()

        assertFalse("Stale endAction must not hide the view after re-show", shouldHideView)
        assertEquals(PillVisibilityPolicy.State.VISIBLE, policy.state)
    }

    @Test
    fun rapidShowDuringPendingHideCancelsScheduledHide() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()
        policy.requestDelayedHide()
        assertEquals(PillVisibilityPolicy.State.PENDING_HIDE, policy.state)

        val cancelled = policy.requestShow()

        assertTrue(cancelled)
        assertEquals(PillVisibilityPolicy.State.VISIBLE, policy.state)
    }

    @Test
    fun multipleShowRequestsAreIdempotent() {
        val policy = PillVisibilityPolicy()
        assertFalse(policy.requestShow())
        assertFalse(policy.requestShow())
        assertFalse(policy.requestShow())

        assertEquals(PillVisibilityPolicy.State.VISIBLE, policy.state)
    }

    @Test
    fun cancelResetsStateToHidden() {
        val policy = PillVisibilityPolicy()
        policy.requestShow()

        val wasActive = policy.cancel()

        assertTrue(wasActive)
        assertEquals(PillVisibilityPolicy.State.HIDDEN, policy.state)
    }

    @Test
    fun delayedHideWhenAlreadyHiddenIsNoOp() {
        val policy = PillVisibilityPolicy()
        policy.requestDelayedHide()

        assertEquals(PillVisibilityPolicy.State.HIDDEN, policy.state)
    }
}
