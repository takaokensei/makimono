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
    private val onClick: (DriveFile) -> Unit,
    private val folderBadgeTextResId: Int? = null,
    private val folderSubtitleTextResId: Int? = null,
    private val showFavoriteBadge: Boolean = folderBadgeTextResId == null
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
    var onDpadVerticalListener: ((View, Int) -> Boolean)? = null

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
            val isFolder = file.isFolder || file.isShortcutFolder
            val isVideo = file.isVideoFile || file.isShortcutVideo

            val rawName = if (isVideo) file.name.substringBeforeLast('.') else file.name
            binding.tvStarredTitle.text = zechs.drive.stream.data.remote.AnimePosterResolver.cleanAnimeTitle(rawName)

            binding.tvStarredTypeBadge.text = when {
                isFolder -> adapter.folderBadgeTextResId?.let(binding.root.context::getString)
                    ?: binding.root.context.getString(R.string.home_starred_type_folder)
                isVideo -> binding.root.context.getString(R.string.home_starred_type_video)
                file.isSubtitleFile -> binding.root.context.getString(R.string.home_starred_type_subtitle)
                else -> binding.root.context.getString(R.string.home_starred_type_file)
            }

            binding.tvStarredSubtitle.text = when {
                isFolder -> adapter.folderSubtitleTextResId?.let(binding.root.context::getString)
                    ?: binding.root.context.getString(R.string.home_starred_folder_subtitle)
                isVideo -> file.humanSize?.let {
                    binding.root.context.getString(R.string.home_starred_size_and_play, it)
                } ?: binding.root.context.getString(R.string.home_starred_video_subtitle)
                else -> file.humanSize ?: binding.root.context.getString(R.string.home_starred_favorite)
            }
            binding.starredFavoriteBadge.visibility = if (adapter.showFavoriteBadge) View.VISIBLE else View.GONE

            val displayImage = file.posterUrl ?: file.thumbnailLarge
            if (!displayImage.isNullOrBlank()) {
                binding.folderBgView.visibility = View.GONE
                binding.ivWatermarkIcon.visibility = View.GONE
                binding.ivStarredThumb.visibility = View.VISIBLE
                binding.tvStarredTypeBadge.visibility = View.GONE
                binding.tvStarredSubtitle.visibility = View.GONE

                MediaImageLoader.poster(binding.ivStarredThumb, displayImage)
            } else if (isFolder) {
                binding.tvStarredTypeBadge.visibility = View.VISIBLE
                binding.tvStarredSubtitle.visibility = View.VISIBLE
                binding.folderBgView.visibility = View.VISIBLE
                binding.ivWatermarkIcon.setImageResource(R.drawable.ic_folder_24)
                binding.ivWatermarkIcon.visibility = View.VISIBLE
                binding.ivStarredThumb.visibility = View.GONE
            } else {
                binding.tvStarredTypeBadge.visibility = View.VISIBLE
                binding.tvStarredSubtitle.visibility = View.VISIBLE
                binding.folderBgView.visibility = View.GONE
                binding.ivWatermarkIcon.setImageResource(R.drawable.ic_play_24)
                binding.ivWatermarkIcon.visibility = View.VISIBLE
                binding.ivStarredThumb.visibility = View.GONE
            }

            binding.cardStarredItem.setOnClickListener { onClick(file) }

            zechs.drive.stream.utils.FocusEngine.attach(
                binding.cardStarredItem,
                zechs.drive.stream.utils.FocusEngine.FocusKind.CARD
            ) { v, hasFocus ->
                if (hasFocus) {
                    adapter.onFocusItemListener?.invoke(v)
                }
            }

            binding.cardStarredItem.setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
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

    private class DiffCallback : DiffUtil.ItemCallback<DriveFile>() {
        override fun areItemsTheSame(oldItem: DriveFile, newItem: DriveFile): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DriveFile, newItem: DriveFile): Boolean =
            oldItem == newItem
    }
}
