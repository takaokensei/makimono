package zechs.drive.stream.ui.files.adapter

import android.graphics.Color
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.color.MaterialColors
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.databinding.ItemDriveFileBinding
import zechs.drive.stream.databinding.ItemDriveFileGridBinding
import zechs.drive.stream.databinding.ItemLoadingBinding
import zechs.drive.stream.utils.GlideApp

sealed class FilesViewHolder(
    binding: ViewBinding
) : RecyclerView.ViewHolder(binding.root) {

    class DriveFileViewHolder(
        private val itemBinding: ItemDriveFileBinding,
        val filesAdapter: FilesAdapter
    ) : FilesViewHolder(itemBinding) {

        private fun setStarred(file: DriveFile, starredState: Starred) {
            when (starredState) {
                Starred.UNSTARRED -> {
                    itemBinding.apply {
                        btnStar.isInvisible = false
                        starLoading.isGone = true
                        btnStar.setImageResource(R.drawable.ic_star_outline_24)
                        btnStar.imageTintList = android.content.res.ColorStateList.valueOf(
                            MaterialColors.getColor(root, R.attr.colorTextSecondary)
                        )
                        btnStar.setOnClickListener {
                            filesAdapter.onStarClickListener.invoke(file, true)
                        }
                    }
                }

                Starred.STARRED -> {
                    itemBinding.apply {
                        btnStar.isInvisible = false
                        starLoading.isGone = true
                        btnStar.setImageResource(R.drawable.ic_star_filled_24)
                        btnStar.imageTintList = android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#FFD700")
                        )
                        btnStar.setOnClickListener {
                            filesAdapter.onStarClickListener.invoke(file, false)
                        }
                    }
                }

                Starred.LOADING -> {
                    itemBinding.apply {
                        btnStar.isInvisible = true
                        starLoading.isGone = false
                    }
                }

                Starred.UNKNOWN -> {
                    itemBinding.apply {
                        btnStar.isInvisible = false
                        starLoading.isGone = true
                        btnStar.setImageResource(R.drawable.ic_star_outline_24)
                        btnStar.imageTintList = android.content.res.ColorStateList.valueOf(
                            MaterialColors.getColor(root, R.attr.colorTextSecondary)
                        )
                        btnStar.setOnClickListener {
                            filesAdapter.onStarClickListener.invoke(file, true)
                        }
                    }
                }
            }
        }

        fun bind(file: FilesDataModel.File) {
            val item = file.driveFile
            itemBinding.apply {

                val displayThumb = item.posterUrl ?: item.thumbnailLarge ?: item.thumbnailLink
                val isVideo = item.isVideoFile || item.isShortcutVideo
                val hasCover = !item.posterUrl.isNullOrBlank()
                if ((isVideo || hasCover) && !displayThumb.isNullOrBlank()) {
                    cardVideoThumb.visibility = View.VISIBLE
                    iconContainer.visibility = View.GONE
                    ivListPlayOverlay.isVisible = isVideo

                    GlideApp.with(ivVideoThumb)
                        .load(displayThumb)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.drawable.home_hero_gradient)
                        .error(R.drawable.home_hero_gradient)
                        .into(ivVideoThumb)
                } else {
                    cardVideoThumb.visibility = View.GONE
                    iconContainer.visibility = View.VISIBLE

                    if (item.isSubtitleFile) {
                        ivFileType.setImageResource(R.drawable.ic_subtitles_24)
                    } else {
                        val iconLink = item.iconLink128 ?: R.drawable.ic_my_drive_24
                        GlideApp.with(ivFileType)
                            .load(iconLink)
                            .apply(RequestOptions().override(48, 48))
                            .into(ivFileType)
                    }
                }

                ivSharedBadge.isGone = !(item.isShortcut || item.isShortcutFolder || item.isShortcutVideo)

                tvFileName.text = item.name

                val tvFileSizeTAG = "tvFileSize"

                tvFileSize.apply {
                    tag = if (item.size == null) {
                        tvFileSizeTAG
                    } else null

                    isGone = tag == tvFileSizeTAG
                    text = item.humanSize
                }

                root.setOnClickListener {
                    filesAdapter.onClickListener.invoke(item)
                }

                root.setOnLongClickListener {
                    filesAdapter.onLongClickListener.invoke(item)
                    return@setOnLongClickListener true
                }

                root.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(6f).setDuration(120L).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                    }
                }

                setStarred(item, item.starred)
            }
        }
    }

    class DriveFileGridViewHolder(
        private val itemBinding: ItemDriveFileGridBinding,
        val filesAdapter: FilesAdapter
    ) : FilesViewHolder(itemBinding) {

        private fun setStarred(file: DriveFile, starredState: Starred) {
            when (starredState) {
                Starred.UNSTARRED -> {
                    itemBinding.apply {
                        btnGridStar.isInvisible = false
                        gridStarLoading.isGone = true
                        btnGridStar.setImageResource(R.drawable.ic_star_outline_24)
                        btnGridStar.imageTintList = android.content.res.ColorStateList.valueOf(
                            MaterialColors.getColor(root, R.attr.colorTextSecondary)
                        )
                        btnGridStar.setOnClickListener {
                            filesAdapter.onStarClickListener.invoke(file, true)
                        }
                    }
                }

                Starred.STARRED -> {
                    itemBinding.apply {
                        btnGridStar.isInvisible = false
                        gridStarLoading.isGone = true
                        btnGridStar.setImageResource(R.drawable.ic_star_filled_24)
                        btnGridStar.imageTintList = android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#FFD700")
                        )
                        btnGridStar.setOnClickListener {
                            filesAdapter.onStarClickListener.invoke(file, false)
                        }
                    }
                }

                Starred.LOADING -> {
                    itemBinding.apply {
                        btnGridStar.isInvisible = true
                        gridStarLoading.isGone = false
                    }
                }

                Starred.UNKNOWN -> {
                    itemBinding.apply {
                        btnGridStar.isInvisible = false
                        gridStarLoading.isGone = true
                        btnGridStar.setImageResource(R.drawable.ic_star_outline_24)
                        btnGridStar.imageTintList = android.content.res.ColorStateList.valueOf(
                            MaterialColors.getColor(root, R.attr.colorTextSecondary)
                        )
                        btnGridStar.setOnClickListener {
                            filesAdapter.onStarClickListener.invoke(file, true)
                        }
                    }
                }
            }
        }

        fun bind(file: FilesDataModel.File) {
            val item = file.driveFile
            itemBinding.apply {
                tvGridFileName.text = item.name
                tvGridFileSize.text = item.humanSize ?: if (item.isFolder || item.isShortcutFolder) "Pasta" else ""

                val isFolder = item.isFolder || item.isShortcutFolder
                val isVideo = item.isVideoFile || item.isShortcutVideo

                tvGridTypeBadge.text = when {
                    isFolder -> "PASTA"
                    isVideo -> "VÍDEO"
                    item.isSubtitleFile -> "LEGENDA"
                    else -> "ARQUIVO"
                }

                ivPlayOverlay.isVisible = isVideo

                val displayThumb = item.posterUrl ?: item.thumbnailLarge ?: item.thumbnailLink
                if (!displayThumb.isNullOrBlank()) {
                    gridBgFallback.isGone = true
                    ivGridFallbackIcon.isGone = true
                    ivGridThumb.isGone = false

                    GlideApp.with(ivGridThumb)
                        .load(displayThumb)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .placeholder(R.drawable.home_hero_gradient)
                        .error(R.drawable.home_hero_gradient)
                        .into(ivGridThumb)
                } else {
                    ivGridThumb.isGone = true
                    gridBgFallback.isGone = false
                    ivGridFallbackIcon.isGone = false

                    val iconRes = when {
                        isFolder -> R.drawable.ic_folder_24
                        item.isSubtitleFile -> R.drawable.ic_subtitles_24
                        isVideo -> R.drawable.ic_play_24
                        else -> R.drawable.ic_my_drive_24
                    }
                    ivGridFallbackIcon.setImageResource(iconRes)
                }

                root.setOnClickListener {
                    filesAdapter.onClickListener.invoke(item)
                }

                root.setOnLongClickListener {
                    filesAdapter.onLongClickListener.invoke(item)
                    return@setOnLongClickListener true
                }

                root.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(10f).setDuration(140L).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(140L).start()
                    }
                }

                setStarred(item, item.starred)
            }
        }
    }

    class LoadingViewHolder(
        itemBinding: ItemLoadingBinding
    ) : FilesViewHolder(itemBinding)

}