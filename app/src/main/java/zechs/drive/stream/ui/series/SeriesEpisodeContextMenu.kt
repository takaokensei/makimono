package zechs.drive.stream.ui.series

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R

/** Context actions for an episode, with a predictable D-pad focus return on cancellation. */
internal object SeriesEpisodeContextMenu {
    fun show(
        context: Context,
        focusAnchor: View,
        title: String,
        options: List<String>,
        onAction: (Int) -> Unit
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(title)
            .setItems(options.toTypedArray()) { dialogInterface, which ->
                dialogInterface.dismiss()
                onAction(which)
            }
            .create()

        dialog.setOnCancelListener {
            zechs.drive.stream.utils.LifecycleDelays.postDelayed(focusAnchor, FOCUS_RESTORE_DELAY_MS) {
                if (focusAnchor.isShown) {
                    if (focusAnchor.isInTouchMode) {
                        focusAnchor.requestFocusFromTouch()
                    } else {
                        focusAnchor.requestFocus()
                    }
                }
            }
        }
        dialog.show()
        return dialog
    }

    private const val FOCUS_RESTORE_DELAY_MS = 150L
}
