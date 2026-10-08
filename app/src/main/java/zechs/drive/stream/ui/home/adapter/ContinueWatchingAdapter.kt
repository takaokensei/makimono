package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.thumbnailLarge
import zechs.drive.stream.databinding.ItemContinueWatchingShelfBinding
import zechs.drive.stream.ui.home.ContinueWatchingProgressPolicy
import zechs.drive.stream.ui.home.ContinueWatchingInputPolicy
import zechs.drive.stream.utils.MediaImageLoader

/**
 * Backs the horizontal "Continuar assistindo" shelf on Home (Crunchyroll-style).
 * Each card shows the Google Drive-generated video-frame thumbnail as a 16:9 preview,
 * with a cyan progress bar and "X min restantes" badge — matching the cyan glass aesthetic.
 */
class ContinueWatchingAdapter(
    private val onClick: (WatchList) -> Unit
) : ListAdapter<WatchList, ContinueWatchingAdapter.ViewHolder>(DiffCallback()) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).videoId.hashCode().toLong()
    }

    var onDpadLeftListener: ((android.view.View) -> Boolean)? = null
    var onDpadVerticalListener: ((android.view.View, Int) -> Boolean)? = null
    var onFocusItemListener: ((android.view.View) -> Unit)? = null
    var onLongClickListener: ((WatchList, android.view.View) -> Unit)? = null

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

            val context = binding.root.context
            val remainingLabel = when (
                val remaining = ContinueWatchingProgressPolicy.remaining(
                    watchItem.watchedDuration,
                    watchItem.totalDuration
                )
            ) {
                is ContinueWatchingProgressPolicy.Remaining.Minutes -> context.resources.getQuantityString(
                    R.plurals.home_continue_remaining_minutes,
                    remaining.count,
                    remaining.count
                )
                ContinueWatchingProgressPolicy.Remaining.LessThanMinute ->
                    context.getString(R.string.home_continue_less_than_minute)
                ContinueWatchingProgressPolicy.Remaining.Unknown ->
                    context.getString(R.string.home_continue_progress_saved)
                ContinueWatchingProgressPolicy.Remaining.Complete ->
                    context.getString(R.string.home_continue_completed)
            }

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

            binding.tvShelfItemRemaining.text = remainingLabel
            binding.pbShelfItemProgress.progress = progressPct

            MediaImageLoader.card(
                binding.ivShelfItemThumb,
                watchItem.thumbnailLarge ?: watchItem.thumbnailLink
            )

            binding.cardShelfItem.setOnClickListener { onClick(watchItem) }

            binding.cardShelfItem.setOnLongClickListener {
                adapter.onLongClickListener?.let { listener ->
                    listener(watchItem, binding.cardShelfItem)
                    true
                } ?: false
            }

            zechs.drive.stream.utils.FocusEngine.attach(
                binding.cardShelfItem,
                zechs.drive.stream.utils.FocusEngine.FocusKind.CARD
            ) { v, hasFocus ->
                if (hasFocus) {
                    adapter.onFocusItemListener?.invoke(v)
                }
            }

            binding.cardShelfItem.setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_MENU -> {
                            if (ContinueWatchingInputPolicy.shouldOpenContextMenu(
                                    keyCode = keyCode,
                                    action = event.action,
                                    repeatCount = event.repeatCount,
                                    hasHandler = adapter.onLongClickListener != null
                                )
                            ) {
                                adapter.onLongClickListener?.invoke(watchItem, binding.cardShelfItem)
                                return@setOnKeyListener true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (bindingAdapterPosition == 0 && adapter.onDpadLeftListener?.invoke(v) == true) {
                                return@setOnKeyListener true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (adapter.onDpadVerticalListener?.invoke(v, keyCode) == true) {
                                return@setOnKeyListener true
                            }
                        }
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
