package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import zechs.drive.stream.R
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.thumbnailLarge
import zechs.drive.stream.databinding.ItemWatchHistoryBinding
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ext.resolveThemeColor

class WatchHistoryAdapter(
    private val onClick: (WatchList) -> Unit
) : ListAdapter<WatchList, WatchHistoryAdapter.ViewHolder>(DiffCallback()) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).videoId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemWatchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onClick)

    class ViewHolder(private val binding: ItemWatchHistoryBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchList, onClick: (WatchList) -> Unit) {
            val context = binding.root.context
            val parsed = EpisodeParser.parse(item.name)
            val progress = item.watchProgress()
            val complete = item.hasFinished()

            binding.tvHistorySeriesTitle.text = parsed.showTitle.ifBlank { parsed.cleanTitle }
            binding.tvHistoryEpisodeTitle.text = when {
                parsed.episodeLabel.isNotBlank() -> parsed.episodeLabel
                parsed.episode != null -> context.getString(
                    R.string.history_episode_number,
                    String.format(java.util.Locale.ROOT, "%02d", parsed.episode.toInt())
                )
                else -> parsed.cleanTitle.takeIf { it != parsed.showTitle }.orEmpty()
            }
            binding.tvHistoryEpisodeTitle.visibility =
                if (binding.tvHistoryEpisodeTitle.text.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.pbHistoryProgress.progress = progress
            binding.pbHistoryProgress.visibility = if (complete) android.view.View.GONE else android.view.View.VISIBLE
            binding.tvHistoryProgressBadge.text = if (complete) {
                context.getString(R.string.history_completed)
            } else {
                context.getString(R.string.history_progress_percent, progress)
            }
            binding.tvHistoryStatus.text = if (complete) {
                context.getString(R.string.history_completed)
            } else {
                val remaining = ((item.totalDuration - item.watchedDuration).coerceAtLeast(0L) / 60_000L).toInt()
                if (remaining > 0) context.resources.getQuantityString(
                    R.plurals.home_continue_remaining_minutes,
                    remaining,
                    remaining
                ) else context.getString(R.string.home_continue_progress_saved)
            }
            MediaImageLoader.card(binding.ivHistoryThumbnail, item.thumbnailLarge ?: item.thumbnailLink)
            binding.cardHistoryItem.contentDescription = context.getString(
                R.string.history_card_accessibility,
                binding.tvHistorySeriesTitle.text,
                binding.tvHistoryEpisodeTitle.text,
                binding.tvHistoryProgressBadge.text
            )
            binding.cardHistoryItem.setOnClickListener { onClick(item) }
            FocusEngine.attach(binding.cardHistoryItem, FocusEngine.FocusKind.CARD) { view, focused ->
                val card = view as? com.google.android.material.card.MaterialCardView ?: return@attach
                card.strokeColor = context.resolveThemeColor(
                    if (focused) R.attr.colorAccentPrimary else R.attr.colorBorderGlass
                )
                card.strokeWidth =
                    ((if (focused) 3f else 1f) * context.resources.displayMetrics.density).toInt()
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WatchList>() {
        override fun areItemsTheSame(oldItem: WatchList, newItem: WatchList): Boolean =
            oldItem.videoId == newItem.videoId

        override fun areContentsTheSame(oldItem: WatchList, newItem: WatchList): Boolean = oldItem == newItem
    }
}
