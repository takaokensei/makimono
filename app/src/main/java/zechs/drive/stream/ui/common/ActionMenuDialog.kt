package zechs.drive.stream.ui.common

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.utils.DeviceUi

/** A single modal action surface shared by TV and touch layouts. */
internal object ActionMenuDialog {
    fun show(context: Context, anchor: View, title: String,
             actions: List<Pair<String, () -> Unit>>): AlertDialog {
        val guard = ModalFocusGuard(anchor)
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { d, index ->
                d.dismiss()
                actions.getOrNull(index)?.second?.invoke()
            }
            .setNegativeButton(R.string.profile_cancel_action, null)
            .create()
        dialog.setOnDismissListener { guard.restore() }
        dialog.setOnShowListener {
            val frame = Rect().also(anchor::getWindowVisibleDisplayFrame)
            val density = context.resources.displayMetrics.density
            val tv = DeviceUi.isTenFootExperience(context)
            val horizontalMargin = ((if (tv) 48 else 16) * density).toInt()
            val verticalMargin = ((if (tv) 27 else 16) * density).toInt()
            val width = minOf((560 * density).toInt(), frame.width() - 2 * horizontalMargin)
            dialog.window?.setLayout(width.coerceAtLeast(1), ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.window?.setWindowAnimations(0)
            dialog.listView?.apply {
                setSelector(R.drawable.bg_modal_list_focus)
                val maxHeight = (frame.height() - 2 * verticalMargin - (180 * density).toInt()).coerceAtLeast(1)
                layoutParams = layoutParams.apply { height = minOf((actions.size * 56 * density).toInt(), maxHeight) }
                requestFocus()
                setSelection(0)
            }
        }
        try { dialog.show() } catch (e: RuntimeException) { guard.restore(); throw e }
        return dialog
    }
}
