package zechs.drive.stream.playback.osd

/**
 * Pure state policy for HUD pills and transient cards (Brightness, Volume, Seek, Speed,
 * Notification, Next Episode, AniSkip).
 *
 * Solves the rapid re-entry / double animation problem (Plano v4 Fase I, item I-4):
 * when an exit animation is running and the user interacts again, the exit animation
 * must be cancelled and its withEndAction MUST NOT set visibility to GONE.
 */
class PillVisibilityPolicy {

    enum class State {
        HIDDEN,
        VISIBLE,
        PENDING_HIDE,
        HIDING
    }

    var state: State = State.HIDDEN
        private set

    /**
     * Called when the pill should be shown or kept visible (e.g. user touch / gesture progress).
     *
     * @return true if an in-flight exit animation or pending hide timer must be cancelled immediately.
     */
    fun requestShow(): Boolean {
        val wasHidingOrPending = state == State.HIDING || state == State.PENDING_HIDE
        state = State.VISIBLE
        return wasHidingOrPending
    }

    /**
     * Called when a delayed hide is scheduled (e.g. finger lifted from screen).
     */
    fun requestDelayedHide() {
        if (state == State.VISIBLE) {
            state = State.PENDING_HIDE
        }
    }

    /**
     * Called when the exit animation actually begins.
     */
    fun onHideAnimationStarted() {
        if (state == State.PENDING_HIDE || state == State.VISIBLE) {
            state = State.HIDING
        }
    }

    /**
     * Called inside withEndAction when the exit animation completes.
     *
     * @return true if the view should be marked GONE.
     *         Returns false if requestShow() was called while hiding, preserving visibility!
     */
    fun onHideAnimationEnded(): Boolean {
        return if (state == State.HIDING) {
            state = State.HIDDEN
            true
        } else {
            false
        }
    }

    /**
     * Resets the policy to HIDDEN state and cancels any in-flight transitions.
     *
     * @return true if an active transition was cancelled.
     */
    fun cancel(): Boolean {
        val wasActive = state != State.HIDDEN
        state = State.HIDDEN
        return wasActive
    }
}
