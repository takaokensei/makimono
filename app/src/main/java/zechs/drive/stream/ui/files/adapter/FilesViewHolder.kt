package zechs.drive.stream.ui.files.adapter

import android.graphics.Color
import android.view.KeyEvent
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
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.databinding.ItemDriveFileBinding
import zechs.drive.stream.databinding.ItemDriveFileGridBinding
import zechs.drive.stream.databinding.ItemLoadingBinding
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.GlideApp
import zechs.drive.stream.utils.MediaImageLoader

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

                    MediaImageLoader.card(ivVideoThumb, displayThumb)
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

                val displayName = if (isVideo || item.isSubtitleFile) {
                    EpisodeParser.cleanEpisodeFileName(item.name)
                } else item.name
                tvFileName.text = displayName

                val tvFileSizeTAG = "tvFileSize"

                tvFileSize.apply {
                    tag = if (item.size == null) {
                        tvFileSizeTAG
                    } else null

                    isGone = tag == tvFileSizeTAG
                    text = item.humanSize
                }

                root.isFocusable = true
                root.isClickable = true
                btnStar.isFocusable = false
                btnStar.isFocusableInTouchMode = false

                root.setOnClickListener {
                    filesAdapter.onClickListener.invoke(item)
                }

                root.setOnLongClickListener {
                    filesAdapter.onLongClickListener.invoke(item)
                    return@setOnLongClickListener true
                }

                root.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        filesAdapter.onFocusItemListener?.invoke(v)
                        v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(6f).setDuration(120L).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                    }
                }

                root.setOnKeyListener { v, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (filesAdapter.onDpadLeftListener?.invoke(v) == true) {
                            return@setOnKeyListener true
                        }
                    }
                    false
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
                val isFolder = item.isFolder || item.isShortcutFolder
                val isVideo = item.isVideoFile || item.isShortcutVideo
                val hasPoster = !item.posterUrl.isNullOrBlank()

                // Dynamically set aspect ratio: 16:9 for video episodes, 2:3 for anime series poster cards
                val params = framePosterContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                if (params != null) {
                    val targetRatio = if (isVideo) "H,16:9" else "H,2:3"
                    if (params.dimensionRatio != targetRatio) {
                        params.dimensionRatio = targetRatio
                        framePosterContainer.layoutParams = params
                    }
                }

                // Clean anime title if folder or has poster, or clean episode filename if video/subtitle
                val cleanTitle = when {
                    hasPoster || isFolder -> AnimePosterResolver.cleanAnimeTitle(item.name)
                    isVideo || item.isSubtitleFile -> EpisodeParser.cleanEpisodeFileName(item.name)
                    else -> item.name
                }

                tvGridFileName.text = cleanTitle

                // Extract episode count or display size
                val epRegex = Regex("""(\d+)\s*(?:eps?|episodes?|cap[íi]tulos?|epis[óo]dios?)""", RegexOption.IGNORE_CASE)
                val epMatch = epRegex.find(item.name)
                val epTag = epMatch?.let { "${it.groupValues[1]} Ep" }

                val sizeText = epTag ?: if (!isFolder) item.humanSize else null
                tvGridFileSize.apply {
                    isVisible = !sizeText.isNullOrBlank()
                    text = sizeText
                }

                // Extract resolution and codec tags from original filename
                val upperName = item.name.uppercase()
                val resTag = when {
                    "2160P" in upperName || "4K" in upperName -> "4K"
                    "1080P" in upperName -> "1080p"
                    "720P" in upperName -> "720p"
                    "480P" in upperName -> "480p"
                    else -> null
                }
                val codecTag = when {
                    "HEVC" in upperName || "X265" in upperName || "H265" in upperName || "H.265" in upperName -> "HEVC"
                    "AVC" in upperName || "X264" in upperName || "H264" in upperName || "H.264" in upperName -> "AVC"
                    else -> null
                }

                tvGridResBadge.apply {
                    isVisible = resTag != null
                    text = resTag
                }

                tvGridCodecBadge.apply {
                    isVisible = codecTag != null
                    text = codecTag
                }

                tvGridTypeBadge.apply {
                    // Show type badge (e.g. ANIME)
                    isVisible = isFolder || (resTag == null && codecTag == null)
                    text = when {
                        isFolder -> "ANIME"
                        isVideo -> "VÍDEO"
                        item.isSubtitleFile -> "LEGENDA"
                        else -> "ARQUIVO"
                    }
                }

                // Quick play overlay: show for videos with clear affordance, hidden for folders
                ivPlayOverlay.isVisible = isVideo

                // Folder button hidden — folder access is via long-press on the card itself
                btnGridFolder.isVisible = false

                val displayThumb = item.posterUrl ?: item.thumbnailLarge ?: item.thumbnailLink
                if (!displayThumb.isNullOrBlank()) {
                    gridBgFallback.isGone = true
                    ivGridFallbackIcon.isGone = true
                    ivGridThumb.isGone = false

                    if (isVideo && !hasPoster) {
                        MediaImageLoader.card(ivGridThumb, displayThumb)
                    } else {
                        MediaImageLoader.poster(ivGridThumb, displayThumb)
                    }
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

                root.isFocusable = true
                root.isClickable = true
                btnGridStar.isFocusable = false
                btnGridStar.isFocusableInTouchMode = false
                ivPlayOverlay.isFocusable = false
                ivPlayOverlay.isFocusableInTouchMode = false

                // Center quick play overlay click
                ivPlayOverlay.setOnClickListener {
                    if (filesAdapter.onPlayClickListener != null) {
                        filesAdapter.onPlayClickListener.invoke(item)
                    } else {
                        filesAdapter.onClickListener.invoke(item)
                    }
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
                        filesAdapter.onFocusItemListener?.invoke(v)
                        v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(12f).setDuration(140L).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(140L).start()
                    }
                }

                root.setOnKeyListener { v, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (filesAdapter.onDpadLeftListener?.invoke(v) == true) {
                            return@setOnKeyListener true
                        }
                    }
                    false
                }

                if (isFolder) {
                    setStarred(item, item.starred)
                } else {
                    btnGridStar.visibility = View.GONE
                    gridStarLoading.visibility = View.GONE
                }
            }
        }
    }

    class LoadingViewHolder(
        itemBinding: ItemLoadingBinding
    ) : FilesViewHolder(itemBinding)

}