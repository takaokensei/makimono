package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.thumbnailLarge
import zechs.drive.stream.databinding.ItemContinueWatchingShelfBinding
import zechs.drive.stream.utils.MediaImageLoader

/**
 * Backs the horizontal "Continuar assistindo" shelf on Home (Crunchyroll-style).
 * Each card shows the Google Drive-generated video-frame thumbnail as a 16:9 preview,
 * with a cyan progress bar and "X min restantes" badge — matching the cyan glass aesthetic.
 */
class ContinueWatchingAdapter(
    private val onClick: (WatchList) -> Unit
) : ListAdapter<WatchList, ContinueWatchingAdapter.ViewHolder>(DiffCallback()) {

    var onDpadLeftListener: ((android.view.View) -> Boolean)? = null
    var onFocusItemListener: ((android.view.View) -> Unit)? = null
    var onLongClickListener: ((WatchList) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContinueWatchingShelfBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, this)
    }

    class ViewHolder(
        private val binding: ItemContinueWatchingShelfBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(watchItem: WatchList, onClick: (WatchList) -> Unit, adapter: ContinueWatchingAdapter) {
            val progressPct = watchItem.watchProgress()

            // Remaining time in minutes (ceil so "1 min restante" not "0 min restante")
            val watchedSec = (watchItem.watchedDuration / 1000)
            val totalSec = (watchItem.totalDuration / 1000)
            val remainingSec = (totalSec - watchedSec).coerceAtLeast(0)
            val remainingMin = ((remainingSec + 59) / 60).toInt()

            val parsed = zechs.drive.stream.utils.EpisodeParser.parse(watchItem.name)
            binding.tvShelfItemTitle.text = parsed.showTitle.ifBlank { parsed.cleanTitle }

            val epBadgeText = when {
                parsed.season != null && parsed.episode != null -> {
                    "T${parsed.season}: Ep. ${String.format(java.util.Locale.ROOT, "%02d", parsed.episode.toInt())}"
                }
                parsed.episode != null -> {
                    "Ep. ${String.format(java.util.Locale.ROOT, "%02d", parsed.episode.toInt())}"
                }
                parsed.isSpecial -> "Especial"
                parsed.episodeLabel.isNotBlank() -> parsed.episodeLabel
                else -> "Assistir"
            }
            binding.tvEpisodeTopBadge.text = epBadgeText

            if (parsed.episodeLabel.isNotBlank()) {
                binding.tvShelfItemEpisode.visibility = android.view.View.VISIBLE
                binding.tvShelfItemEpisode.text = parsed.episodeLabel
                binding.tvShelfItemDot.visibility = android.view.View.VISIBLE
            } else {
                binding.tvShelfItemEpisode.visibility = android.view.View.GONE
                binding.tvShelfItemDot.visibility = android.view.View.GONE
            }

            binding.tvShelfItemRemaining.text = if (remainingMin > 0) {
                "$remainingMin min restante${if (remainingMin > 1) "s" else ""}"
            } else {
                "Quase completo"
            }
            binding.pbShelfItemProgress.progress = progressPct

            MediaImageLoader.card(
                binding.ivShelfItemThumb,
                watchItem.thumbnailLarge ?: watchItem.thumbnailLink
            )

            binding.cardShelfItem.setOnClickListener { onClick(watchItem) }

            binding.cardShelfItem.setOnLongClickListener {
                adapter.onLongClickListener?.invoke(watchItem)
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
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (bindingAdapterPosition == 0 && adapter.onDpadLeftListener?.invoke(v) == true) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WatchList>() {
        override fun areItemsTheSame(oldItem: WatchList, newItem: WatchList): Boolean =
            oldItem.videoId == newItem.videoId

        override fun areContentsTheSame(oldItem: WatchList, newItem: WatchList): Boolean =
            oldItem == newItem
    }
}
