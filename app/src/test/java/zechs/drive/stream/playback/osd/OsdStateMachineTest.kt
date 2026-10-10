package zechs.drive.stream.playback.osd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OsdStateMachineTest {

    private lateinit var stateMachine: OsdStateMachine

    @Before
    fun setUp() {
        stateMachine = OsdStateMachine()
    }

    @Test
    fun initialStateIsHidden() {
        assertEquals(OsdState.HIDDEN, stateMachine.state)
        assertFalse(stateMachine.isVisible)
        assertFalse(stateMachine.isHiding)
        assertFalse(stateMachine.shouldConsumeBack)
    }

    @Test
    fun showFromHiddenStartsShowAction() {
        val action = stateMachine.onShowRequested()
        assertEquals(OsdAction.StartShow, action)
        assertEquals(OsdState.SHOWING, stateMachine.state)
        assertTrue(stateMachine.isVisible)
        assertFalse(stateMachine.isHiding)
        assertTrue(stateMachine.shouldConsumeBack)
    }

    @Test
    fun completingShowAnimationTransitionsToShown() {
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()
        assertEquals(OsdState.SHOWN, stateMachine.state)
        assertTrue(stateMachine.isVisible)
        assertFalse(stateMachine.isHiding)
        assertTrue(stateMachine.shouldConsumeBack)
    }

    @Test
    fun hideFromShownStartsHideAction() {
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()

        val action = stateMachine.onHideRequested()
        assertEquals(OsdAction.StartHide, action)
        assertEquals(OsdState.HIDING, stateMachine.state)
        assertFalse(stateMachine.isVisible)
        assertTrue(stateMachine.isHiding)
        assertTrue(stateMachine.shouldConsumeBack)
    }

    @Test
    fun completingHideAnimationTransitionsToHidden() {
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()
        stateMachine.onHideRequested()
        stateMachine.onHideAnimationCompleted()

        assertEquals(OsdState.HIDDEN, stateMachine.state)
        assertFalse(stateMachine.isVisible)
        assertFalse(stateMachine.isHiding)
        assertFalse(stateMachine.shouldConsumeBack)
    }

    @Test
    fun hidingTwiceIsIdempotentAndProducesNoDuplicateAction() {
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()

        val action1 = stateMachine.onHideRequested()
        assertEquals(OsdAction.StartHide, action1)
        assertEquals(OsdState.HIDING, stateMachine.state)

        // Second hide call while already HIDING must return None (eradicates H2)
        val action2 = stateMachine.onHideRequested()
        assertEquals(OsdAction.None, action2)
        assertEquals(OsdState.HIDING, stateMachine.state)

        // Third hide call once already HIDDEN must also return None
        stateMachine.onHideAnimationCompleted()
        val action3 = stateMachine.onHideRequested()
        assertEquals(OsdAction.None, action3)
        assertEquals(OsdState.HIDDEN, stateMachine.state)
    }

    @Test
    fun showDuringHidingReversesToShow() {
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()
        stateMachine.onHideRequested()
        assertEquals(OsdState.HIDING, stateMachine.state)

        val action = stateMachine.onShowRequested()
        assertEquals(OsdAction.ReverseToShow, action)
        assertEquals(OsdState.SHOWING, stateMachine.state)
        assertTrue(stateMachine.isVisible)
        assertFalse(stateMachine.isHiding)
    }

    @Test
    fun hideDuringShowingReversesToHide() {
        val showAction = stateMachine.onShowRequested()
        assertEquals(OsdAction.StartShow, showAction)
        assertEquals(OsdState.SHOWING, stateMachine.state)

        val hideAction = stateMachine.onHideRequested()
        assertEquals(OsdAction.ReverseToHide, hideAction)
        assertEquals(OsdState.HIDING, stateMachine.state)
        assertFalse(stateMachine.isVisible)
        assertTrue(stateMachine.isHiding)
    }

    @Test
    fun showWhenAlreadyShownOrShowingOnlyResetsTimer() {
        stateMachine.onShowRequested()
        val secondShow = stateMachine.onShowRequested()
        assertEquals(OsdAction.ResetInactivityTimer, secondShow)

        stateMachine.onShowAnimationCompleted()
        val thirdShow = stateMachine.onShowRequested()
        assertEquals(OsdAction.ResetInactivityTimer, thirdShow)
        assertEquals(OsdState.SHOWN, stateMachine.state)
    }

    @Test
    fun timeoutExpiryDuringHidingIsIgnoredIdempotently() {
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()
        stateMachine.onHideRequested()

        // Simulating inactivity timer or media3 timeout firing while already hiding
        val timerExpiryAction = stateMachine.onHideRequested()
        assertEquals(OsdAction.None, timerExpiryAction)
        assertEquals(OsdState.HIDING, stateMachine.state)
    }

    @Test
    fun modalOpenAndCloseLifecycle() {
        // OSD was shown when modal opened
        stateMachine.onShowRequested()
        stateMachine.onShowAnimationCompleted()

        // Modal opening requests hide
        val modalHide = stateMachine.onHideRequested()
        assertEquals(OsdAction.StartHide, modalHide)
        stateMachine.onHideAnimationCompleted()
        assertEquals(OsdState.HIDDEN, stateMachine.state)

        // Modal closed -> restore OSD
        val modalRestore = stateMachine.onShowRequested()
        assertEquals(OsdAction.StartShow, modalRestore)
        stateMachine.onShowAnimationCompleted()
        assertEquals(OsdState.SHOWN, stateMachine.state)
    }

    @Test
    fun forceHiddenResetsStateImmediately() {
        stateMachine.onShowRequested()
        stateMachine.forceHidden()
        assertEquals(OsdState.HIDDEN, stateMachine.state)
        assertFalse(stateMachine.isVisible)
    }

    @Test
    fun forceShownSetsStateImmediately() {
        assertEquals(OsdState.HIDDEN, stateMachine.state)
        stateMachine.forceShown()
        assertEquals(OsdState.SHOWN, stateMachine.state)
        assertTrue(stateMachine.isVisible)
    }
}
