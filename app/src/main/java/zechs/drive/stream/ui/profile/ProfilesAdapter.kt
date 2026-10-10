package zechs.drive.stream.ui.profile

import android.view.LayoutInflater
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.remote.ProfileArtCatalog
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
    private val onProfileFocused: (UserProfile) -> Unit = {},
    private val profileArtCatalog: ProfileArtCatalog? = null
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

    var reduceMotion: Boolean = false
        set(value) {
            field = value
            if (value) {
                for (index in 0 until itemCount) {
                    (recyclerView?.findViewHolderForAdapterPosition(index) as? ProfileViewHolder)
                        ?.binding?.cardProfileRoot?.let { card ->
                            card.animate().cancel()
                            card.scaleX = 1f
                            card.scaleY = 1f
                            card.alpha = 1f
                            card.translationY = 0f
                            card.setTag(R.id.profile_entry_animation_played, true)
                        }
                }
            }
        }

    private var recyclerView: RecyclerView? = null

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
            // Touch should activate the selected profile immediately, even when the card had no
            // prior keyboard focus. Keep parent interception enabled so the profile carousel can
            // still scroll horizontally when a gesture moves beyond touch slop.
            zechs.drive.stream.utils.SingleTapActivation.install(
                binding.cardProfileRoot,
                disallowParentIntercept = false
            )
            zechs.drive.stream.utils.FocusEngine.attach(
                binding.cardProfileRoot,
                zechs.drive.stream.utils.FocusEngine.FocusKind.CARD
            ) { view, hasFocus ->
                val focusedItem = currentList.getOrNull(bindingAdapterPosition) as? ProfileUiModel.ProfileItem
                binding.avatarContainer.isSelected = hasFocus || focusedItem?.isActive == true
                val nameHighlighted = hasFocus || focusedItem?.isActive == true
                binding.tvProfileName.isSelected = nameHighlighted
                binding.tvProfileName.setTypeface(
                    binding.tvProfileName.typeface,
                    if (nameHighlighted) Typeface.BOLD else Typeface.NORMAL
                )
                if (hasFocus) {
                    focusedItem?.profile?.let(onProfileFocused)
                }
            }
        }

        fun bind(item: ProfileUiModel) {
            when (item) {
                is ProfileUiModel.ProfileItem -> {
                    val profile = item.profile
                    binding.tvProfileName.text = profile.name

                    val avatarSource: Any? = profile.avatarUrl
                        ?: if (profile.avatarResName == "avatar_caua") R.drawable.avatar_caua
                        else profileArtCatalog?.defaultAvatarForProfile(profile.id)?.url
                    com.bumptech.glide.Glide.with(binding.ivProfileAvatar)
                        .load(avatarSource).placeholder(R.drawable.avatar_caua).error(R.drawable.avatar_caua)
                        .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .into(binding.ivProfileAvatar)
                    binding.tvProfileRole.text = listOfNotNull(
                        itemView.context.getString(R.string.profile_role_admin).takeIf { profile.isAdmin },
                        itemView.context.getString(R.string.profile_role_kids).takeIf { profile.isKids }
                    ).joinToString(" · ")
                    binding.tvProfileRole.visibility = if (profile.isAdmin || profile.isKids)
                        android.view.View.VISIBLE else android.view.View.GONE
                    binding.ivProfileAvatar.isVisible = true
                    binding.ivAddIcon.isVisible = false
                    binding.avatarContainer.setBackgroundResource(R.drawable.item_profile_avatar_ring)
                    // Let the portrait fill the circular focus ring; the picker follows the
                    // clean, avatar-first layout from the reference instead of a backing tile.
                    val ringInset = (1 * itemView.resources.displayMetrics.density).toInt()
                    binding.avatarContainer.setPadding(ringInset, ringInset, ringInset, ringInset)

                    binding.cardProfileRoot.background = null
                    binding.cardProfileRoot.isSelected = item.isActive
                    binding.avatarContainer.isSelected = item.isActive
                    binding.tvProfileName.isSelected = item.isActive
                    binding.tvProfileName.setTypeface(
                        binding.tvProfileName.typeface,
                        if (item.isActive) Typeface.BOLD else Typeface.NORMAL
                    )

                    // Tick indicator
                    binding.badgeStatusTick.isVisible = false
                    binding.cardProfileRoot.contentDescription = buildList {
                        add(profile.name)
                        if (profile.isAdmin) add(itemView.context.getString(R.string.profile_role_admin_accessibility))
                        if (profile.isKids) add(itemView.context.getString(R.string.profile_role_kids_accessibility))
                        if (item.isActive) add(itemView.context.getString(R.string.profile_active_accessibility))
                    }.joinToString(", ")

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
                    binding.cardProfileRoot.contentDescription =
                        itemView.context.getString(R.string.profile_add_accessibility)
                    com.bumptech.glide.Glide.with(binding.ivProfileAvatar).clear(binding.ivProfileAvatar)
                    binding.tvProfileName.setText(R.string.profile_add_accessibility)
                    binding.tvProfileName.isSelected = false
                    binding.tvProfileName.setTypeface(binding.tvProfileName.typeface, Typeface.NORMAL)
                    binding.ivProfileAvatar.setImageDrawable(null)
                    binding.ivProfileAvatar.isVisible = false
                    binding.ivAddIcon.isVisible = true
                    binding.avatarContainer.setBackgroundResource(R.drawable.item_profile_add_ring)
                    binding.avatarContainer.setPadding(0, 0, 0, 0)
                    binding.avatarContainer.isSelected = false
                    binding.badgeStatusTick.isVisible = false
                    binding.ivEditBadge.isVisible = false

                    binding.cardProfileRoot.background = null
                    binding.cardProfileRoot.isSelected = false

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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
        prepareEntrance(holder.itemView)
    }

    private fun prepareEntrance(view: View) {
        if (view.getTag(R.id.profile_entry_animation_played) == true) return
        if (reduceMotion) {
            view.alpha = 1f
            view.translationY = 0f
            view.setTag(R.id.profile_entry_animation_played, true)
        } else {
            view.alpha = 0f
            view.translationY = 10f * view.resources.displayMetrics.density
        }
    }

    override fun onViewAttachedToWindow(holder: ProfileViewHolder) {
        super.onViewAttachedToWindow(holder)
        val card = holder.itemView
        if (card.getTag(R.id.profile_entry_animation_played) == true) return
        card.setTag(R.id.profile_entry_animation_played, true)
        if (reduceMotion) {
            card.alpha = 1f
            card.translationY = 0f
            return
        }
        val position = holder.bindingAdapterPosition.coerceAtLeast(0)
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(position * 40L)
            .setDuration(card.resources.getInteger(R.integer.motion_card).toLong())
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onViewDetachedFromWindow(holder: ProfileViewHolder) {
        val card = holder.itemView
        if (card.getTag(R.id.profile_entry_animation_played) == true &&
            (card.alpha < 1f || card.translationY != 0f)
        ) {
            card.animate().cancel()
            card.alpha = 1f
            card.translationY = 0f
            card.scaleX = 1f
            card.scaleY = 1f
        }
        super.onViewDetachedFromWindow(holder)
    }
}
