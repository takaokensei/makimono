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

internal class WatchHistoryDialog(
    context: Context,
    history: List<WatchList>,
    private val focusAnchor: View? = null,
    private val presentation: WatchHistoryPresentation = WatchHistoryPresentation(),
    private val onContextAction: (WatchList, ContinueWatchingContextMenu.Action) -> Unit = { _, _ -> },
    private val onRestore: (WatchList) -> Unit = {},
    private val onPlay: (WatchList) -> Unit
) {
    private val dialog = Dialog(context, R.style.ThemeOverlay_DriveStream_Dialog)
    private val binding = DialogWatchHistoryBinding.inflate(dialog.layoutInflater)
    private val allItems = sortWatchHistory(history).toMutableList()
    private val adapter = WatchHistoryAdapter(onLongClick = { item, anchor ->
        ContinueWatchingContextMenu.show(context, item, anchor) { action ->
            if (action == ContinueWatchingContextMenu.Action.REMOVE) {
                allItems.removeAll { it.videoId == item.videoId }
                onContextAction(item, action)
                updateItems(requestCardFocus = true)
                com.google.android.material.snackbar.Snackbar.make(binding.root, R.string.home_continue_removed,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) {
                        allItems.add(item)
                        onRestore(item)
                        updateItems(requestCardFocus = true)
                    }.show()
            } else {
                dialog.dismiss()
                onContextAction(item, action)
            }
        }
    }) { item ->
        dialog.dismiss()
        onPlay(item)
    }
    private var filter = presentation.filter

    init {
        dialog.setContentView(binding.root)
        val tv = zechs.drive.stream.utils.DeviceUi.isTenFootExperience(context)
        val scale = context.resources.displayMetrics.density
        val horizontal = ((if (tv) 48 else 16) * scale).toInt()
        val vertical = ((if (tv) 27 else 16) * scale).toInt()
        binding.root.setPadding(horizontal, vertical, horizontal, vertical)
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

        binding.btnHistoryAll.setOnClickListener { setFilter(WatchHistoryFilter.ALL) }
        binding.btnHistoryInProgress.setOnClickListener { setFilter(WatchHistoryFilter.IN_PROGRESS) }
        binding.btnHistoryCompleted.setOnClickListener { setFilter(WatchHistoryFilter.COMPLETED) }
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
                binding.rvWatchHistory.post {
                    if (!zechs.drive.stream.utils.FocusMemory.restore(binding.rvWatchHistory, "home.history") { pos ->
                            adapter.currentList.getOrNull(pos)?.videoId }) {
                        binding.rvWatchHistory.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            } else {
                binding.btnHistoryAll.requestFocus()
            }
        }
    }

    fun show() {
        val guard = focusAnchor?.let { zechs.drive.stream.ui.common.ModalFocusGuard(it) }
        dialog.setOnDismissListener { guard?.restore() }
        dialog.show()
    }

    private fun setFilter(next: WatchHistoryFilter) {
        if (filter == next) return
        filter = next
        presentation.filter = next
        updateFilterButtons()
        updateItems(requestCardFocus = true)
    }

    private fun updateItems(requestCardFocus: Boolean) {
        binding.tvHistoryCount.text = binding.root.resources.getQuantityString(R.plurals.history_item_count, allItems.size, allItems.size)
        val visibleItems = filterWatchHistory(allItems, filter)
        adapter.submitList(visibleItems) {
            binding.tvHistoryEmpty.visibility = if (visibleItems.isEmpty()) View.VISIBLE else View.GONE
            binding.tvHistoryEmpty.text = when {
                allItems.isEmpty() -> binding.root.context.getString(R.string.history_empty_all)
                filter == WatchHistoryFilter.IN_PROGRESS -> binding.root.context.getString(R.string.history_empty_in_progress)
                filter == WatchHistoryFilter.COMPLETED -> binding.root.context.getString(R.string.history_empty_completed)
                else -> binding.root.context.getString(R.string.history_empty_all)
            }
            if (requestCardFocus && visibleItems.isNotEmpty()) {
                binding.rvWatchHistory.post {
                    if (!zechs.drive.stream.utils.FocusMemory.restore(binding.rvWatchHistory, "home.history") { pos ->
                            adapter.currentList.getOrNull(pos)?.videoId }) {
                        binding.rvWatchHistory.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            } else if (requestCardFocus && visibleItems.isEmpty()) {
                binding.btnHistoryAll.requestFocus()
            }
        }
    }

    private fun updateFilterButtons() {
        listOf(
            WatchHistoryFilter.ALL to binding.btnHistoryAll,
            WatchHistoryFilter.IN_PROGRESS to binding.btnHistoryInProgress,
            WatchHistoryFilter.COMPLETED to binding.btnHistoryCompleted
        ).forEach { (itemFilter, button) ->
            val selected = filter == itemFilter
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) binding.root.context.resolveThemeColor(R.attr.colorFocusFill) else binding.root.context.resolveThemeColor(R.attr.colorBgGlass)
            )
            button.setTextColor(binding.root.context.resolveThemeColor(R.attr.colorTextPrimary))
            button.isSelected = selected
        }
    }

}
