package zechs.drive.stream.ui.series

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.ui.series.adapter.SeasonTab
import zechs.drive.stream.ui.series.adapter.SeriesDetailSeasonAdapter
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.FocusEngine

/** Compact selector shares the existing season adapter and preserves the active category chips. */
internal class SeasonPickerController(
    private val trigger: TextView,
    private val adapter: SeriesDetailSeasonAdapter,
    private val onSelected: (SeasonTab) -> Unit
) {
    private var seasons: List<SeasonTab> = emptyList()
    private var dialog: AlertDialog? = null
    var isCompact: Boolean = false
        private set

    init {
        FocusEngine.attach(trigger, FocusEngine.FocusKind.BUTTON)
        trigger.setOnClickListener { showPicker() }
    }

    fun bind(tabs: List<SeasonTab>) {
        seasons = tabs
        val presentation = SeasonPickerPolicy.present(tabs.map { it.id }, tabs.firstOrNull { it.isSelected }?.id)
        isCompact = presentation.compact
        trigger.visibility = if (isCompact) View.VISIBLE else View.GONE
        val selected = tabs.getOrNull(presentation.selectedIndex)
        trigger.text = trigger.context.getString(R.string.series_season_picker_current, selected?.title.orEmpty())
        trigger.contentDescription = trigger.context.getString(
            R.string.series_season_picker_accessibility, selected?.title.orEmpty()
        )
        adapter.submitList(presentation.visibleIndices.map { index ->
            tabs[index].copy(isSelected = index == presentation.selectedIndex)
        })
    }

    private fun showPicker() {
        if (dialog?.isShowing == true || seasons.isEmpty()) return
        val snapshot = seasons
        val selectedIndex = SeasonPickerPolicy.present(
            snapshot.map { it.id }, snapshot.firstOrNull { it.isSelected }?.id
        ).selectedIndex
        val picker = MaterialAlertDialogBuilder(trigger.context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(R.string.series_season_picker_title)
            .setSingleChoiceItems(snapshot.map { it.title }.toTypedArray(), selectedIndex) { openedDialog, position ->
                val selectedId = snapshot.getOrNull(position)?.id
                val current = seasons.firstOrNull { it.id == selectedId }
                if (current != null) onSelected(current)
                openedDialog.dismiss()
            }
            .setNegativeButton(R.string.series_action_cancel, null)
            .create()
        dialog = picker
        picker.setOnDismissListener {
            dialog = null
            if (trigger.isAttachedToWindow && trigger.isShown) trigger.requestFocus()
        }
        picker.show()
        constrainToSafeWindow(picker)
        picker.listView?.apply {
            setSelection(selectedIndex)
            requestFocus()
        }
    }

    private fun constrainToSafeWindow(picker: AlertDialog) {
        val window = picker.window ?: return
        val density = trigger.resources.displayMetrics.density
        val tv = DeviceUi.isTenFootExperience(trigger.context)
        val horizontalInset = ((if (tv) 48 else 16) * density).toInt()
        val verticalInset = ((if (tv) 27 else 16) * density).toInt()
        val root = trigger.rootView
        val availableWidth = (root.width - 2 * horizontalInset).coerceAtLeast(1)
        val availableHeight = (root.height - 2 * verticalInset).coerceAtLeast(1)
        val width = minOf((560 * density).toInt(), availableWidth)
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST)
        )
        window.setLayout(width, minOf(window.decorView.measuredHeight, availableHeight))
    }

    fun close() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        seasons = emptyList()
        trigger.setOnClickListener(null)
    }
}
