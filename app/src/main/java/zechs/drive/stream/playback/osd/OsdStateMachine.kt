package zechs.drive.stream.playback.osd

/**
 * Pure state machine representing player On-Screen Display (OSD) lifecycle.
 *
 * Guarantees:
 * - Idempotency: hide() while already HIDING or HIDDEN produces no duplicate transition (erradicates H2).
 * - Interruptibility: show() while HIDING reverses towards SHOWN from current progress without jumps.
 * - Single animation lifecycle: prevents overlapping animators and duplicate calls (erradicates H1 & H4).
 */
enum class OsdState {
    HIDDEN,
    SHOWING,
    SHOWN,
    HIDING
}

sealed class OsdAction {
    object StartShow : OsdAction()
    object StartHide : OsdAction()
    object ReverseToShow : OsdAction()
    object ReverseToHide : OsdAction()
    object ResetInactivityTimer : OsdAction()
    object None : OsdAction()
}

class OsdStateMachine(
    initialState: OsdState = OsdState.HIDDEN
) {
    var state: OsdState = initialState
        private set

    /**
     * Called when a request to show the OSD is issued (e.g. user tap, D-pad, media key).
     * Returns the appropriate transition action.
     */
    fun onShowRequested(): OsdAction {
        return when (state) {
            OsdState.HIDDEN -> {
                state = OsdState.SHOWING
                OsdAction.StartShow
            }
            OsdState.HIDING -> {
                state = OsdState.SHOWING
                OsdAction.ReverseToShow
            }
            OsdState.SHOWN, OsdState.SHOWING -> {
                OsdAction.ResetInactivityTimer
            }
        }
    }

    /**
     * Called when a request to hide the OSD is issued (e.g. user tap, Back button, timeout).
     * Returns the appropriate transition action.
     */
    fun onHideRequested(): OsdAction {
        return when (state) {
            OsdState.SHOWN -> {
                state = OsdState.HIDING
                OsdAction.StartHide
            }
            OsdState.SHOWING -> {
                state = OsdState.HIDING
                OsdAction.ReverseToHide
            }
            OsdState.HIDDEN, OsdState.HIDING -> {
                // Idempotent: ignore reentrant hide calls
                OsdAction.None
            }
        }
    }

    /**
     * Called when the show animation finishes.
     */
    fun onShowAnimationCompleted() {
        if (state == OsdState.SHOWING) {
            state = OsdState.SHOWN
        }
    }

    /**
     * Called when the hide animation finishes.
     */
    fun onHideAnimationCompleted() {
        if (state == OsdState.HIDING) {
            state = OsdState.HIDDEN
        }
    }

    /**
     * Immediate reset (e.g., when entering PiP or finishing activity).
     */
    fun forceHidden() {
        state = OsdState.HIDDEN
    }

    /**
     * Immediate set (e.g. initial setup or reduce motion instant show).
     */
    fun forceShown() {
        state = OsdState.SHOWN
    }

    val isVisible: Boolean
        get() = state == OsdState.SHOWN || state == OsdState.SHOWING

    val isHiding: Boolean
        get() = state == OsdState.HIDING

    val shouldConsumeBack: Boolean
        get() = state != OsdState.HIDDEN
}
