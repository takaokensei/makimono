package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.ItemStarredShelfBinding
import zechs.drive.stream.utils.GlideApp

class StarredShelfAdapter(
    private val onClick: (DriveFile) -> Unit
) : ListAdapter<DriveFile, StarredShelfAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStarredShelfBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(
        private val binding: ItemStarredShelfBinding
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

                GlideApp.with(binding.ivStarredThumb)
                    .load(displayImage)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .placeholder(R.drawable.home_hero_gradient)
                    .error(R.drawable.home_hero_gradient)
                    .into(binding.ivStarredThumb)
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
                    v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(12f).setDuration(150L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150L).start()
                }
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
