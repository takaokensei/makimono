package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import zechs.drive.stream.R
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.databinding.ItemContinueWatchingShelfBinding
import zechs.drive.stream.utils.GlideApp
import zechs.mpv.utils.Utils

/**
 * Backs the horizontal "Continuar assistindo" shelf on Home (Kodi Estuary's
 * "In progress" row). Each card shows the Google Drive-generated video-frame
 * thumbnail ([WatchList.thumbnailLink]) as a 16:9 preview photo, the same way
 * Estuary shows scraped fanart - no local scraping needed since Drive
 * generates the preview frame for us server-side.
 */
class ContinueWatchingAdapter(
    private val onClick: (WatchList) -> Unit
) : ListAdapter<WatchList, ContinueWatchingAdapter.ViewHolder>(DiffCallback()) {

    var onDpadLeftListener: ((android.view.View) -> Boolean)? = null
    var onFocusItemListener: ((android.view.View) -> Unit)? = null

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
            val currSec = (watchItem.watchedDuration / 1000).toInt()
            val totalSec = (watchItem.totalDuration / 1000).toInt()

            binding.tvShelfItemTitle.text = watchItem.name
            binding.tvShelfItemProgress.text = "${Utils.prettyTime(currSec)} / " +
                    "${Utils.prettyTime(totalSec)} ($progressPct%)"
            binding.pbShelfItemProgress.progress = progressPct

            GlideApp.with(binding.ivShelfItemThumb)
                .load(watchItem.thumbnailLink)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .placeholder(R.drawable.glass_card_bg)
                .error(R.drawable.glass_card_bg)
                .into(binding.ivShelfItemThumb)

            binding.cardShelfItem.setOnClickListener { onClick(watchItem) }

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
