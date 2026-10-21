package zechs.drive.stream.ui.series.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.ItemSeriesEpisodeCardBinding
import zechs.drive.stream.domain.library.MediaItemCategory
import zechs.drive.stream.ui.series.EpisodeAudioBadgePolicy
import zechs.drive.stream.ui.series.EpisodeCardAccessibilityLabel
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.utils.FocusEngine
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
    val fallbackThumbnailUrl: String? = null,
    val category: MediaItemCategory = MediaItemCategory.STORY,
    val synopsis: String? = null,
    val ageRating: String? = null,
    val downloadStatus: DownloadStatus? = null,
    val downloadProgress: Int = 0
)

class SeriesDetailEpisodeAdapter(
    private val onEpisodeClick: (SeriesEpisodeItem) -> Unit,
    private val onEpisodeLongClick: ((SeriesEpisodeItem, View) -> Unit)? = null,
    private val onDownloadClick: ((SeriesEpisodeItem) -> Unit)? = null
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

    override fun onBindViewHolder(
        holder: EpisodeViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_DOWNLOAD_UPDATE)) {
            holder.bindDownloadState(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
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

            FocusEngine.attach(
                binding.cardEpisodeItem,
                FocusEngine.FocusKind.CARD
            ) { _, hasFocus ->
                binding.ivPlayOverlay.visibility = if (hasFocus) View.VISIBLE else View.GONE
                updateSynopsisVisibility(hasFocus)
            }
        }

        fun bind(item: SeriesEpisodeItem) {
            val context = binding.root.context
            binding.tvEpisodeTitle.text = item.displayTitle

            val hasDuration = item.durationText.isNotBlank()
            binding.tvEpisodeDuration.text = item.durationText
            binding.tvEpisodeDuration.visibility = if (hasDuration) View.VISIBLE else View.GONE

            val hasAge = !item.ageRating.isNullOrBlank()
            binding.layoutEpisodeAgeRating.visibility = if (hasAge) View.VISIBLE else View.GONE
            binding.tvEpisodeAgeRating.text = item.ageRating
            binding.tvEpisodeAgeRating.visibility = if (hasAge) View.VISIBLE else View.GONE

            val hasAudio = item.audioBadgeText.isNotBlank()
            binding.layoutAudioBadge.visibility = if (hasAudio) View.VISIBLE else View.GONE
            binding.tvAudioBadge.text = item.audioBadgeText
            binding.tvAudioBadge.visibility = if (hasAudio) View.VISIBLE else View.GONE
            val isDub = EpisodeAudioBadgePolicy.isDubbed(item.audioBadgeText)
            val audioColorRes = if (isDub) R.color.obsidian_accent else R.color.obsidian_link_blue_gray
            binding.tvAudioBadge.setTextColor(ContextCompat.getColor(context, audioColorRes))
            binding.tvEpisodeDot1.visibility = View.GONE

            // Episode description / synopsis (2 lines at rest, expands to 4 lines on focus)
            val desc = item.synopsis?.takeIf { it.isNotBlank() }
                ?: item.file.description?.takeIf { it.isNotBlank() }
            binding.tvEpisodeSynopsis.text = desc
                ?.trim()
                ?.take(MAX_DESCRIPTION_LENGTH)
                .orEmpty()
            updateSynopsisVisibility(binding.cardEpisodeItem.hasFocus())

            // Episode number badge overlay (e.g. "EP 01")
            val badge = item.badgeText ?: if (item.episodeNumber != null && item.episodeNumber > 0) {
                String.format("EP %02d", item.episodeNumber)
            } else null

            if (badge != null) {
                binding.tvEpNumberBadge.visibility = View.VISIBLE
                binding.tvEpNumberBadge.text = badge
            } else {
                binding.tvEpNumberBadge.visibility = View.GONE
            }

            // Watched state & Progress bar
            val isWatched = item.progressPercent >= 90
            if (isWatched) {
                binding.badgeWatched.visibility = View.VISIBLE
                binding.pbEpisodeProgress.visibility = View.VISIBLE
                binding.pbEpisodeProgress.progress = 100
                binding.pbEpisodeProgress.progressTintList = ContextCompat.getColorStateList(
                    context, R.color.obsidian_completed_green
                )
            } else if (item.progressPercent > 0) {
                binding.badgeWatched.visibility = View.GONE
                binding.pbEpisodeProgress.visibility = View.VISIBLE
                binding.pbEpisodeProgress.progress = item.progressPercent
                binding.pbEpisodeProgress.progressTintList = ContextCompat.getColorStateList(
                    context, R.color.obsidian_accent
                )
            } else {
                binding.badgeWatched.visibility = View.GONE
                binding.pbEpisodeProgress.visibility = View.GONE
            }

            // No percentage text shown (per user specification)
            binding.tvEpisodeProgressPercent.visibility = View.GONE

            val progressDescription = when {
                item.progressPercent >= 90 -> context.getString(R.string.series_episode_accessibility_watched)
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
            bindDownloadState(item)
        }

        fun bindDownloadState(item: SeriesEpisodeItem) {
            val context = binding.root.context
            // Download status & action (Netflix & Crunchyroll style)
            when (item.downloadStatus) {
                DownloadStatus.DOWNLOADING -> {
                    binding.btnEpisodeDownload.visibility = View.VISIBLE
                    binding.ivDownloadIcon.visibility = View.GONE
                    binding.pbDownloadProgress.visibility = View.VISIBLE
                    binding.pbDownloadProgress.isIndeterminate = false
                    binding.pbDownloadProgress.progress = item.downloadProgress
                    binding.tvDownloadPercent.visibility = View.VISIBLE
                    binding.tvDownloadPercent.text = "${item.downloadProgress}%"
                    binding.btnEpisodeDownload.contentDescription = context.getString(
                        R.string.download_status_downloading,
                        item.downloadProgress
                    )
                }
                DownloadStatus.QUEUED -> {
                    binding.btnEpisodeDownload.visibility = View.VISIBLE
                    binding.ivDownloadIcon.visibility = View.GONE
                    binding.pbDownloadProgress.visibility = View.VISIBLE
                    binding.pbDownloadProgress.isIndeterminate = true
                    binding.tvDownloadPercent.visibility = View.GONE
                    binding.btnEpisodeDownload.contentDescription = context.getString(R.string.download_status_queued)
                }
                DownloadStatus.COMPLETED -> {
                    binding.btnEpisodeDownload.visibility = View.VISIBLE
                    binding.ivDownloadIcon.visibility = View.VISIBLE
                    binding.pbDownloadProgress.visibility = View.GONE
                    binding.tvDownloadPercent.visibility = View.GONE
                    binding.ivDownloadIcon.setImageResource(R.drawable.ic_download_done_24)
                    binding.ivDownloadIcon.imageTintList = ContextCompat.getColorStateList(
                        context, R.color.obsidian_accent
                    )
                    binding.btnEpisodeDownload.contentDescription = context.getString(R.string.download_status_completed)
                }
                DownloadStatus.FAILED -> {
                    binding.btnEpisodeDownload.visibility = View.VISIBLE
                    binding.ivDownloadIcon.visibility = View.VISIBLE
                    binding.pbDownloadProgress.visibility = View.GONE
                    binding.tvDownloadPercent.visibility = View.GONE
                    binding.ivDownloadIcon.setImageResource(R.drawable.ic_download_24)
                    binding.ivDownloadIcon.imageTintList = ContextCompat.getColorStateList(
                        context, R.color.obsidian_danger
                    )
                    binding.btnEpisodeDownload.contentDescription = context.getString(R.string.download_status_failed)
                }
                else -> {
                    binding.btnEpisodeDownload.visibility = View.VISIBLE
                    binding.ivDownloadIcon.visibility = View.VISIBLE
                    binding.pbDownloadProgress.visibility = View.GONE
                    binding.tvDownloadPercent.visibility = View.GONE
                    binding.ivDownloadIcon.setImageResource(R.drawable.ic_download_24)
                    binding.ivDownloadIcon.imageTintList = ContextCompat.getColorStateList(
                        context, R.color.obsidian_gray
                    )
                    binding.btnEpisodeDownload.contentDescription = context.getString(R.string.download_episode)
                }
            }

            binding.btnEpisodeDownload.setOnClickListener {
                onDownloadClick?.invoke(item)
            }
        }

        private fun updateSynopsisVisibility(hasFocus: Boolean) {
            val hasText = binding.tvEpisodeSynopsis.text.isNotBlank()
            binding.tvEpisodeSynopsis.visibility = if (hasText) View.VISIBLE else View.GONE
            binding.tvEpisodeSynopsis.maxLines = if (hasFocus) 4 else 2
        }
    }

    companion object {
        const val PAYLOAD_DOWNLOAD_UPDATE = "payload_download_update"
        private const val MAX_DESCRIPTION_LENGTH = 600
    }

    object DiffCallback : DiffUtil.ItemCallback<SeriesEpisodeItem>() {

        override fun areItemsTheSame(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Boolean =
            oldItem.file.id == newItem.file.id

        override fun areContentsTheSame(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: SeriesEpisodeItem, newItem: SeriesEpisodeItem): Any? {
            val isOnlyDownloadChanged = oldItem.copy(
                downloadStatus = newItem.downloadStatus,
                downloadProgress = newItem.downloadProgress
            ) == newItem

            return if (isOnlyDownloadChanged) {
                PAYLOAD_DOWNLOAD_UPDATE
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }
    }
}
