package zechs.drive.stream.ui.common

import android.view.View
import android.view.ViewGroup

/** Stops the covered activity from displaying a second focus while a modal owns input. */
internal class ModalFocusGuard(private val anchor: View) {
    private val host = anchor.rootView as? ViewGroup
    private val previousFocusability = host?.descendantFocusability

    init {
        host?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        host?.findFocus()?.clearFocus()
    }

    fun restore(requestFocus: Boolean = true) {
        previousFocusability?.let { host?.descendantFocusability = it }
        if (requestFocus && anchor.isAttachedToWindow && anchor.isShown) anchor.requestFocus()
    }
}
