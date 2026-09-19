package zechs.drive.stream.ui.series.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.ItemSeriesEpisodeCardBinding
import zechs.drive.stream.utils.MediaImageLoader

data class SeriesEpisodeItem(
    val file: DriveFile,
    val displayTitle: String,
    val episodeNumber: Int?,
    val durationText: String,
    val audioBadgeText: String,
    val progressPercent: Int,
    val watchedDuration: Long,
    val totalDuration: Long
)

class SeriesDetailEpisodeAdapter(
    private val onEpisodeClick: (SeriesEpisodeItem) -> Unit,
    private val onEpisodeLongClick: ((SeriesEpisodeItem) -> Unit)? = null
) : ListAdapter<SeriesEpisodeItem, SeriesDetailEpisodeAdapter.EpisodeViewHolder>(DiffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).file.id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemSeriesEpisodeCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EpisodeViewHolder(
        private val binding: ItemSeriesEpisodeCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.cardEpisodeItem.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEpisodeClick(getItem(position))
                }
            }

            binding.cardEpisodeItem.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && onEpisodeLongClick != null) {
                    onEpisodeLongClick.invoke(getItem(position))
                    true
                } else {
                    false
                }
            }

            binding.cardEpisodeItem.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(12f).setDuration(150L).start()
                    binding.ivPlayOverlay.visibility = View.VISIBLE
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150L).start()
                    binding.ivPlayOverlay.visibility = View.GONE
                }
            }
        }

        fun bind(item: SeriesEpisodeItem) {
            binding.tvEpisodeTitle.text = item.displayTitle
            binding.tvEpisodeDuration.text = item.durationText
            binding.tvAudioBadge.text = item.audioBadgeText

            if (item.episodeNumber != null && item.episodeNumber > 0) {
                binding.tvEpNumberBadge.visibility = View.VISIBLE
                binding.tvEpNumberBadge.text = String.format("EP %02d", item.episodeNumber)
            } else {
                binding.tvEpNumberBadge.visibility = View.GONE
            }

            if (item.progressPercent > 0) {
                binding.pbEpisodeProgress.visibility = View.VISIBLE
                binding.pbEpisodeProgress.progress = item.progressPercent
                binding.tvEpisodeProgressPercent.visibility = View.VISIBLE
                binding.tvEpisodeProgressPercent.text = "${item.progressPercent}%"
            } else {
                binding.pbEpisodeProgress.visibility = View.GONE
                binding.tvEpisodeProgressPercent.visibility = View.GONE
            }

            val thumbUrl = item.file.thumbnailLarge ?: item.file.thumbnailLink ?: item.file.posterUrl
            MediaImageLoader.card(binding.ivEpisodeThumb, thumbUrl)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SeriesEpisodeItem>() {
        override fun areItemsTheSame(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Boolean =
            oldItem.file.id == newItem.file.id

        override fun areContentsTheSame(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Boolean =
            oldItem == newItem
    }
}
