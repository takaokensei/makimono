package zechs.drive.stream.ui.downloads

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.databinding.ItemDownloadCardBinding
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.MediaImageLoader

class DownloadsAdapter(
    private val onItemClick: (DownloadItem) -> Unit,
    private val onDeleteClick: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadsAdapter.ViewHolder>(DiffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).fileId.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_PROGRESS_UPDATE)) {
            holder.bindProgress(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: ItemDownloadCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.cardDownloadItem.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.cardDownloadItem.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                    true
                } else false
            }

            binding.btnDownloadPlay.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.btnDownloadDelete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }

            FocusEngine.attach(
                binding.cardDownloadItem,
                FocusEngine.FocusKind.CARD
            ) { _, _ -> }
        }

        fun bind(item: DownloadItem) {
            binding.tvDownloadTitle.text = item.name
            binding.tvDownloadSeries.text = item.seriesTitle.orEmpty()
            binding.tvDownloadSeries.visibility = if (item.seriesTitle.isNullOrBlank()) View.GONE else View.VISIBLE

            MediaImageLoader.card(binding.ivDownloadThumb, item.thumbnailUrl)
            bindProgress(item)
        }

        fun bindProgress(item: DownloadItem) {
            val context = binding.root.context
            when (item.downloadStatus) {
                DownloadStatus.DOWNLOADING -> {
                    binding.pbDownloadProgress.visibility = View.VISIBLE
                    binding.pbDownloadProgress.isIndeterminate = false
                    binding.pbDownloadProgress.progress = item.progressPercent

                    val downloadedStr = Formatter.formatFileSize(context, item.downloadedBytes)
                    val totalStr = Formatter.formatFileSize(context, item.totalBytes)
                    binding.tvDownloadSize.text = context.getString(
                        R.string.download_status_downloading_progress,
                        item.progressPercent,
                        downloadedStr,
                        totalStr
                    )
                    binding.tvDownloadSize.setTextColor(ContextCompat.getColor(context, R.color.obsidian_accent))

                    binding.btnDownloadPlay.visibility = View.GONE
                    binding.btnDownloadDelete.visibility = View.VISIBLE
                    binding.btnDownloadDelete.setImageResource(R.drawable.ic_close_24)
                    binding.btnDownloadDelete.contentDescription = context.getString(R.string.download_cancel)
                }
                DownloadStatus.QUEUED -> {
                    binding.pbDownloadProgress.visibility = View.VISIBLE
                    binding.pbDownloadProgress.isIndeterminate = true

                    binding.tvDownloadSize.text = context.getString(R.string.download_status_queued)
                    binding.tvDownloadSize.setTextColor(ContextCompat.getColor(context, R.color.obsidian_accent))

                    binding.btnDownloadPlay.visibility = View.GONE
                    binding.btnDownloadDelete.visibility = View.VISIBLE
                    binding.btnDownloadDelete.setImageResource(R.drawable.ic_close_24)
                    binding.btnDownloadDelete.contentDescription = context.getString(R.string.download_cancel)
                }
                DownloadStatus.COMPLETED -> {
                    binding.pbDownloadProgress.visibility = View.GONE

                    val sizeStr = Formatter.formatFileSize(context, item.downloadedBytes.takeIf { it > 0 } ?: item.totalBytes)
                    val statusStr = context.getString(R.string.download_status_completed)
                    binding.tvDownloadSize.text = "$sizeStr • $statusStr"
                    binding.tvDownloadSize.setTextColor(ContextCompat.getColor(context, R.color.obsidian_text_secondary))

                    binding.btnDownloadPlay.visibility = View.VISIBLE
                    binding.btnDownloadDelete.visibility = View.VISIBLE
                    binding.btnDownloadDelete.setImageResource(R.drawable.ic_delete_24)
                    binding.btnDownloadDelete.contentDescription = context.getString(R.string.download_delete)
                }
                DownloadStatus.FAILED -> {
                    binding.pbDownloadProgress.visibility = View.GONE

                    binding.tvDownloadSize.text = item.errorMessage?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.download_status_failed)
                    binding.tvDownloadSize.setTextColor(ContextCompat.getColor(context, R.color.obsidian_danger))

                    binding.btnDownloadPlay.visibility = View.GONE
                    binding.btnDownloadDelete.visibility = View.VISIBLE
                    binding.btnDownloadDelete.setImageResource(R.drawable.ic_delete_24)
                    binding.btnDownloadDelete.contentDescription = context.getString(R.string.download_delete)
                }
                DownloadStatus.PAUSED -> {
                    binding.pbDownloadProgress.visibility = View.VISIBLE
                    binding.pbDownloadProgress.isIndeterminate = false
                    binding.pbDownloadProgress.progress = item.progressPercent

                    binding.tvDownloadSize.text = context.getString(R.string.download_status_paused_format, item.progressPercent)
                    binding.tvDownloadSize.setTextColor(ContextCompat.getColor(context, R.color.obsidian_text_secondary))

                    binding.btnDownloadPlay.visibility = View.GONE
                    binding.btnDownloadDelete.visibility = View.VISIBLE
                    binding.btnDownloadDelete.setImageResource(R.drawable.ic_delete_24)
                    binding.btnDownloadDelete.contentDescription = context.getString(R.string.download_delete)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        const val PAYLOAD_PROGRESS_UPDATE = "payload_progress_update"

        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean =
            oldItem.fileId == newItem.fileId

        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: DownloadItem, newItem: DownloadItem): Any? {
            val isOnlyProgressOrStatusChanged = oldItem.copy(
                downloadedBytes = newItem.downloadedBytes,
                totalBytes = newItem.totalBytes,
                status = newItem.status,
                completedAt = newItem.completedAt,
                errorMessage = newItem.errorMessage
            ) == newItem

            return if (isOnlyProgressOrStatusChanged) {
                PAYLOAD_PROGRESS_UPDATE
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }
}
