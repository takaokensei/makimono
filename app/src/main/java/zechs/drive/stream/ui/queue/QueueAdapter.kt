package zechs.drive.stream.ui.queue

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.databinding.ItemQueueBinding

class QueueAdapter(
    private val onQueueItemClick: (WatchQueueItem) -> Unit,
    private val onQueueItemRemove: (WatchQueueItem) -> Unit,
    private val onQueueItemMove: (Int, Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private var items = listOf<WatchQueueItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<WatchQueueItem>) {
        val diffCallback = QueueDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    inner class QueueViewHolder(
        private val binding: ItemQueueBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchQueueItem, position: Int) {
            binding.tvQueueTitle.text = item.name
            binding.tvQueuePosition.text = "${position + 1}"
            
            // Load poster if available
            if (item.posterUrl != null) {
                // Use your image loading library here
                // Glide.with(binding.ivQueuePoster.context)
                //     .load(item.posterUrl)
                //     .into(binding.ivQueuePoster)
            }

            binding.root.setOnClickListener {
                onQueueItemClick(item)
            }

            binding.btnRemove.setOnClickListener {
                onQueueItemRemove(item)
            }

            // Drag handle for reordering (could be implemented with ItemTouchHelper)
            binding.btnDrag.setOnLongClickListener {
                // Trigger drag start
                true
            }
        }
    }

    private class QueueDiffCallback(
        private val oldList: List<WatchQueueItem>,
        private val newList: List<WatchQueueItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].fileId == newList[newItemPosition].fileId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}