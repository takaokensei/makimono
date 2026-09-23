package zechs.drive.stream.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.databinding.ItemProfileCardBinding

sealed class ProfileUiModel {
    data class ProfileItem(val profile: UserProfile, val isActive: Boolean) : ProfileUiModel()
    object AddProfileItem : ProfileUiModel()
}

class ProfilesAdapter(
    private val onProfileSelected: (UserProfile) -> Unit,
    private val onAddProfileClicked: () -> Unit,
    private val onEditProfileClicked: (UserProfile) -> Unit,
    private val onProfileFocused: (UserProfile) -> Unit = {}
) : ListAdapter<ProfileUiModel, ProfilesAdapter.ProfileViewHolder>(DiffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is ProfileUiModel.ProfileItem -> item.profile.id.hashCode().toLong()
            is ProfileUiModel.AddProfileItem -> -1L
        }
    }

    var isManageMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    object DiffCallback : DiffUtil.ItemCallback<ProfileUiModel>() {
        override fun areItemsTheSame(oldItem: ProfileUiModel, newItem: ProfileUiModel): Boolean {
            return when {
                oldItem is ProfileUiModel.ProfileItem && newItem is ProfileUiModel.ProfileItem ->
                    oldItem.profile.id == newItem.profile.id
                oldItem is ProfileUiModel.AddProfileItem && newItem is ProfileUiModel.AddProfileItem -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ProfileUiModel, newItem: ProfileUiModel): Boolean {
            return oldItem == newItem
        }
    }

    inner class ProfileViewHolder(
        val binding: ItemProfileCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            if (android.os.Build.VERSION.SDK_INT >= 26) binding.cardProfileRoot.defaultFocusHighlightEnabled = false
            binding.cardProfileRoot.setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.035f else 1.0f
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(180L)
                    .start()
                binding.cardProfileRoot.isSelected = hasFocus
                if (hasFocus) (currentList.getOrNull(bindingAdapterPosition) as? ProfileUiModel.ProfileItem)
                    ?.profile?.let(onProfileFocused)
            }
        }

        fun bind(item: ProfileUiModel) {
            when (item) {
                is ProfileUiModel.ProfileItem -> {
                    val profile = item.profile
                    binding.tvProfileName.text = profile.name

                    val avatarRes = when (profile.avatarResName) {
                        "avatar_anime" -> R.drawable.avatar_anime
                        "avatar_caua" -> R.drawable.avatar_caua
                        else -> R.drawable.avatar_caua
                    }
                    com.bumptech.glide.Glide.with(binding.ivProfileAvatar)
                        .load(profile.avatarUrl).placeholder(avatarRes).error(avatarRes)
                        .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .into(binding.ivProfileAvatar)
                    binding.tvProfileRole.text = listOfNotNull(
                        "Admin".takeIf { profile.isAdmin }, "Kids".takeIf { profile.isKids }).joinToString(" · ")
                    binding.tvProfileRole.visibility = if (profile.isAdmin || profile.isKids)
                        android.view.View.VISIBLE else android.view.View.INVISIBLE
                    binding.ivProfileAvatar.isVisible = true
                    binding.ivAddIcon.isVisible = false

                    binding.cardProfileRoot.background = ContextCompat.getDrawable(
                        itemView.context,
                        R.drawable.bg_profile_surface
                    )

                    // Tick indicator
                    binding.badgeStatusTick.isVisible = false
                    binding.cardProfileRoot.contentDescription = "${profile.name}${if (item.isActive) ", perfil ativo" else ""}"

                    // Manage mode edit icon
                    binding.ivEditBadge.isVisible = isManageMode

                    binding.cardProfileRoot.setOnClickListener {
                        if (isManageMode) {
                            onEditProfileClicked(profile)
                        } else {
                            onProfileSelected(profile)
                        }
                    }
                }
                is ProfileUiModel.AddProfileItem -> {
                    binding.tvProfileRole.visibility = android.view.View.INVISIBLE
                    com.bumptech.glide.Glide.with(binding.ivProfileAvatar).clear(binding.ivProfileAvatar)
                    binding.tvProfileName.text = "Adicionar perfil"
                    binding.ivProfileAvatar.setImageDrawable(null)
                    binding.ivProfileAvatar.isVisible = false
                    binding.ivAddIcon.isVisible = true
                    binding.badgeStatusTick.isVisible = false
                    binding.ivEditBadge.isVisible = false

                    binding.cardProfileRoot.background = ContextCompat.getDrawable(
                        itemView.context,
                        R.drawable.bg_profile_surface
                    )

                    binding.cardProfileRoot.setOnClickListener {
                        onAddProfileClicked()
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemProfileCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
