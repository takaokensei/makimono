package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.databinding.ItemContinueWatchingShelfBinding
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.MediaImageLoader

class WatchQueueShelfAdapter(
    private val onClick: (WatchQueueItem) -> Unit
) : ListAdapter<WatchQueueItem, WatchQueueShelfAdapter.ViewHolder>(DiffCallback()) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).fileId.hashCode().toLong()
    }

    var onDpadLeftListener: ((android.view.View) -> Boolean)? = null
    var onFocusItemListener: ((android.view.View) -> Unit)? = null
    var onLongClickListener: ((WatchQueueItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContinueWatchingShelfBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, onClick, this)
    }

    class ViewHolder(
        private val binding: ItemContinueWatchingShelfBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: WatchQueueItem,
            position: Int,
            onClick: (WatchQueueItem) -> Unit,
            adapter: WatchQueueShelfAdapter
        ) {
            val parsed = EpisodeParser.parse(item.name)
            binding.tvShelfItemTitle.text = parsed.showTitle.ifBlank { parsed.cleanTitle }
            binding.tvEpisodeTopBadge.text = "FILA • ${position + 1}"
            binding.tvShelfItemEpisode.visibility = android.view.View.GONE
            binding.tvShelfItemDot.visibility = android.view.View.GONE
            binding.tvShelfItemRemaining.text = parsed.episodeLabel.ifBlank { "Assistir depois" }
            binding.pbShelfItemProgress.progress = 0

            MediaImageLoader.card(binding.ivShelfItemThumb, item.posterUrl)

            binding.cardShelfItem.setOnClickListener { onClick(item) }
            binding.cardShelfItem.setOnLongClickListener {
                adapter.onLongClickListener?.invoke(item)
                true
            }
            binding.cardShelfItem.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    adapter.onFocusItemListener?.invoke(v)
                    v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(12f).setDuration(150L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150L).start()
                }
            }
            binding.cardShelfItem.setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT &&
                    bindingAdapterPosition == 0 &&
                    adapter.onDpadLeftListener?.invoke(v) == true
                ) {
                    return@setOnKeyListener true
                }
                false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WatchQueueItem>() {
        override fun areItemsTheSame(oldItem: WatchQueueItem, newItem: WatchQueueItem): Boolean =
            oldItem.fileId == newItem.fileId

        override fun areContentsTheSame(oldItem: WatchQueueItem, newItem: WatchQueueItem): Boolean =
            oldItem == newItem
    }
}
