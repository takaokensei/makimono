package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.ItemStarredShelfBinding
import zechs.drive.stream.utils.MediaImageLoader

class StarredShelfAdapter(
    private val onClick: (DriveFile) -> Unit
) : ListAdapter<DriveFile, StarredShelfAdapter.ViewHolder>(DiffCallback()) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    var onFocusItemListener: ((View) -> Unit)? = null
    var onDpadLeftListener: ((View) -> Boolean)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStarredShelfBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(
        private val binding: ItemStarredShelfBinding,
        private val adapter: StarredShelfAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: DriveFile, onClick: (DriveFile) -> Unit) {
            binding.tvStarredTitle.text = file.name

            val isFolder = file.isFolder || file.isShortcutFolder
            val isVideo = file.isVideoFile || file.isShortcutVideo

            binding.tvStarredTypeBadge.text = when {
                isFolder -> "PASTA"
                isVideo -> "VÍDEO"
                file.isSubtitleFile -> "LEGENDA"
                else -> "ARQUIVO"
            }

            binding.tvStarredSubtitle.text = when {
                isFolder -> "Pasta favoritada • Abrir"
                isVideo -> file.humanSize?.let { "$it • Reproduzir" } ?: "Vídeo favoritado"
                else -> file.humanSize ?: "Favoritado"
            }

            val displayImage = file.posterUrl ?: file.thumbnailLarge
            if (!displayImage.isNullOrBlank()) {
                binding.folderBgView.visibility = View.GONE
                binding.ivWatermarkIcon.visibility = View.GONE
                binding.ivStarredThumb.visibility = View.VISIBLE

                MediaImageLoader.poster(binding.ivStarredThumb, displayImage)
            } else if (isFolder) {
                binding.folderBgView.visibility = View.VISIBLE
                binding.ivWatermarkIcon.setImageResource(R.drawable.ic_folder_24)
                binding.ivWatermarkIcon.visibility = View.VISIBLE
                binding.ivStarredThumb.visibility = View.GONE
            } else {
                binding.folderBgView.visibility = View.GONE
                binding.ivWatermarkIcon.setImageResource(R.drawable.ic_play_24)
                binding.ivWatermarkIcon.visibility = View.VISIBLE
                binding.ivStarredThumb.visibility = View.GONE
            }

            binding.cardStarredItem.setOnClickListener { onClick(file) }

            binding.cardStarredItem.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    adapter.onFocusItemListener?.invoke(v)
                    v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(12f).setDuration(150L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150L).start()
                }
            }

            binding.cardStarredItem.setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (bindingAdapterPosition == 0 && adapter.onDpadLeftListener?.invoke(v) == true) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DriveFile>() {
        override fun areItemsTheSame(oldItem: DriveFile, newItem: DriveFile): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DriveFile, newItem: DriveFile): Boolean =
            oldItem == newItem
    }
}
