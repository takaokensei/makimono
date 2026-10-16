package zechs.drive.stream.ui.series.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.ItemSeriesEpisodeCardBinding
import zechs.drive.stream.ui.series.EpisodeCardAccessibilityLabel
import zechs.drive.stream.utils.MediaImageLoader

data class SeriesEpisodeItem(
    val file: DriveFile,
    val displayTitle: String,
    val episodeNumber: Int?,
    val durationText: String,
    val audioBadgeText: String,
    val progressPercent: Int,
    val watchedDuration: Long,
    val totalDuration: Long,
    val badgeText: String? = null,
    val fallbackThumbnailUrl: String? = null
)

class SeriesDetailEpisodeAdapter(
    private val onEpisodeClick: (SeriesEpisodeItem) -> Unit,
    private val onEpisodeLongClick: ((SeriesEpisodeItem, View) -> Unit)? = null
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
                    onEpisodeLongClick.invoke(getItem(position), binding.cardEpisodeItem)
                    true
                } else {
                    false
                }
            }

            zechs.drive.stream.utils.FocusEngine.attach(
                binding.cardEpisodeItem,
                zechs.drive.stream.utils.FocusEngine.FocusKind.CARD
            ) { _, hasFocus ->
                binding.ivPlayOverlay.visibility = if (hasFocus) View.VISIBLE else View.GONE
                updateSynopsisVisibility(hasFocus)
            }
        }

        fun bind(item: SeriesEpisodeItem) {
            binding.tvEpisodeTitle.text = item.displayTitle
            val hasDuration = item.durationText.isNotBlank()
            binding.tvEpisodeDuration.text = item.durationText
            binding.tvEpisodeDuration.visibility = if (hasDuration) View.VISIBLE else View.GONE
            val hasAudio = item.audioBadgeText.isNotBlank()
            binding.tvAudioBadge.text = item.audioBadgeText
            binding.tvAudioBadge.visibility = if (hasAudio) View.VISIBLE else View.GONE
            binding.tvEpisodeDot1.visibility = if (hasDuration && hasAudio) View.VISIBLE else View.GONE

            // Google Drive descriptions are optional and user-authored. Reveal them only
            // on the focused episode so the rail remains scannable at rest.
            binding.tvEpisodeSynopsis.text = item.file.description
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(MAX_DESCRIPTION_LENGTH)
                .orEmpty()
            updateSynopsisVisibility(binding.cardEpisodeItem.hasFocus())

            val badge = item.badgeText ?: if (item.episodeNumber != null && item.episodeNumber > 0) {
                String.format("EP %02d", item.episodeNumber)
            } else null

            if (badge != null) {
                binding.tvEpNumberBadge.visibility = View.VISIBLE
                binding.tvEpNumberBadge.text = badge
            } else {
                binding.tvEpNumberBadge.visibility = View.GONE
            }

            val isWatched = item.progressPercent >= 90
            binding.badgeWatched?.visibility = if (isWatched) View.VISIBLE else View.GONE

            if (item.progressPercent > 0) {
                binding.pbEpisodeProgress.visibility = View.VISIBLE
                binding.pbEpisodeProgress.progress = item.progressPercent
                binding.tvEpisodeProgressPercent.visibility = View.VISIBLE
                binding.tvEpisodeProgressPercent.text = "${item.progressPercent}%"
            } else {
                binding.pbEpisodeProgress.visibility = View.GONE
                binding.tvEpisodeProgressPercent.visibility = View.GONE
            }

            val context = binding.root.context
            val progressDescription = when {
                item.progressPercent >= 95 -> context.getString(R.string.series_episode_accessibility_watched)
                item.progressPercent > 0 -> context.getString(
                    R.string.series_episode_accessibility_in_progress,
                    item.progressPercent
                )
                else -> null
            }
            binding.cardEpisodeItem.contentDescription = EpisodeCardAccessibilityLabel.build(
                actionAndTitle = context.getString(
                    R.string.series_episode_card_accessibility_play,
                    item.displayTitle
                ),
                duration = item.durationText,
                audio = item.audioBadgeText,
                progress = progressDescription
            )

            val thumbUrl = item.file.thumbnailLarge
                ?: item.file.thumbnailLink
                ?: item.fallbackThumbnailUrl
                ?: item.file.posterUrl
            MediaImageLoader.card(binding.ivEpisodeThumb, thumbUrl)
        }

        private fun updateSynopsisVisibility(hasFocus: Boolean) {
            binding.tvEpisodeSynopsis.visibility =
                if (hasFocus && binding.tvEpisodeSynopsis.text.isNotBlank()) View.VISIBLE else View.GONE
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SeriesEpisodeItem>() {
        private const val MAX_DESCRIPTION_LENGTH = 600

        override fun areItemsTheSame(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Boolean =
            oldItem.file.id == newItem.file.id

        override fun areContentsTheSame(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Boolean =
            oldItem == newItem
    }
}
