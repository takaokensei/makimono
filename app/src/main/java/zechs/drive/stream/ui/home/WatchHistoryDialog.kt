package zechs.drive.stream.ui.home

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.GridLayoutManager
import zechs.drive.stream.R
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.databinding.DialogWatchHistoryBinding
import zechs.drive.stream.ui.home.adapter.WatchHistoryAdapter
import zechs.drive.stream.utils.ext.resolveThemeColor

class WatchHistoryDialog(
    context: Context,
    history: List<WatchList>,
    private val onPlay: (WatchList) -> Unit
) {
    private val dialog = Dialog(context, R.style.ThemeOverlay_DriveStream_Dialog)
    private val binding = DialogWatchHistoryBinding.inflate(dialog.layoutInflater)
    private val allItems = history.sortedByDescending { it.id ?: 0 }
    private val adapter = WatchHistoryAdapter { item ->
        dialog.dismiss()
        onPlay(item)
    }
    private var filter = Filter.ALL

    init {
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.72f)
        }

        val density = context.resources.displayMetrics.density
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / density).toInt()
        val columns = ((screenWidthDp - 96) / 172).coerceIn(2, 6)
        binding.rvWatchHistory.layoutManager = GridLayoutManager(context, columns)
        binding.rvWatchHistory.adapter = adapter
        binding.rvWatchHistory.itemAnimator = null
        binding.btnHistoryBack.setOnClickListener { dialog.dismiss() }

        binding.btnHistoryAll.setOnClickListener { setFilter(Filter.ALL) }
        binding.btnHistoryInProgress.setOnClickListener { setFilter(Filter.IN_PROGRESS) }
        binding.btnHistoryCompleted.setOnClickListener { setFilter(Filter.COMPLETED) }
        updateFilterButtons()
        updateItems(requestCardFocus = false)
        binding.tvHistoryCount.text = context.resources.getQuantityString(
            R.plurals.history_item_count,
            allItems.size,
            allItems.size
        )
        binding.tvHistoryCount.visibility = View.VISIBLE

        dialog.setOnShowListener {
            dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            if (allItems.isNotEmpty()) {
                binding.rvWatchHistory.post { binding.rvWatchHistory.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
            } else {
                binding.btnHistoryAll.requestFocus()
            }
        }
    }

    fun show() = dialog.show()

    private fun setFilter(next: Filter) {
        if (filter == next) return
        filter = next
        updateFilterButtons()
        updateItems(requestCardFocus = true)
    }

    private fun updateItems(requestCardFocus: Boolean) {
        val visibleItems = when (filter) {
            Filter.ALL -> allItems
            Filter.IN_PROGRESS -> allItems.filterNot(WatchList::hasFinished)
            Filter.COMPLETED -> allItems.filter(WatchList::hasFinished)
        }
        adapter.submitList(visibleItems) {
            binding.tvHistoryEmpty.visibility = if (visibleItems.isEmpty()) View.VISIBLE else View.GONE
            binding.tvHistoryEmpty.text = when {
                allItems.isEmpty() -> binding.root.context.getString(R.string.history_empty_all)
                filter == Filter.IN_PROGRESS -> binding.root.context.getString(R.string.history_empty_in_progress)
                filter == Filter.COMPLETED -> binding.root.context.getString(R.string.history_empty_completed)
                else -> binding.root.context.getString(R.string.history_empty_all)
            }
            if (requestCardFocus && visibleItems.isNotEmpty()) {
                binding.rvWatchHistory.post { binding.rvWatchHistory.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
            }
        }
    }

    private fun updateFilterButtons() {
        val accent = binding.root.context.resolveThemeColor(R.attr.colorAccentPrimary)
        val onAccent = binding.root.context.resolveThemeColor(R.attr.colorOnPrimary)
        listOf(
            Filter.ALL to binding.btnHistoryAll,
            Filter.IN_PROGRESS to binding.btnHistoryInProgress,
            Filter.COMPLETED to binding.btnHistoryCompleted
        ).forEach { (itemFilter, button) ->
            val selected = filter == itemFilter
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) accent else binding.root.context.resolveThemeColor(R.attr.colorBgGlass)
            )
            button.setTextColor(if (selected) onAccent else binding.root.context.resolveThemeColor(R.attr.colorTextPrimary))
            button.isSelected = selected
        }
    }

    private enum class Filter { ALL, IN_PROGRESS, COMPLETED }
}
