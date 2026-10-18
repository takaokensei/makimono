package zechs.drive.stream.ui.profile

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.ProfileArt
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.data.remote.ProfileArtCatalogPolicy
import zechs.drive.stream.databinding.DialogProfileEditorBinding
import zechs.drive.stream.databinding.ItemProfileCatalogCardBinding
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.ShimmerController
import java.util.UUID

/**
 * Fullscreen Crunchyroll TV split-screen profile preferences dialog.
 * Left 42%: Full-height live preview with wallpaper, centered circular avatar, and name.
 * Right 58%: Clean preferences menu and categorized franchise catalogs (including user's library).
 */
class ProfileEditorDialog(
    private val fragment: Fragment,
    private val manager: ProfileManager,
    private val resolver: ProfileArtCatalog
) {
    fun show(profile: UserProfile?) {
        val context = fragment.requireContext()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val b = DialogProfileEditorBinding.inflate(LayoutInflater.from(context))

        var currentName = profile?.name.orEmpty().ifBlank {
            context.getString(R.string.profile_name_hint)
        }
        var selectedAvatarUrl = profile?.avatarUrl.orEmpty().ifBlank {
            resolver.defaultAvatarForProfile(profile?.id ?: UUID.randomUUID().toString())?.url.orEmpty()
        }
        var selectedBackgroundUrl = profile?.backgroundUrl.orEmpty().ifBlank {
            resolver.getPresetWallpapers().firstOrNull()?.url.orEmpty()
        }

        val themedContext = ContextThemeWrapper(context, R.style.Theme_DriveStream_Obsidian)
        val dialog = Dialog(themedContext, R.style.Theme_DriveStream_Obsidian).apply {
            setContentView(b.root)
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
                decorView.setPadding(0, 0, 0, 0)
            }
        }

        val transparentDrawable = ColorDrawable(Color.TRANSPARENT)
        val glideAvatarOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(transparentDrawable)
            .error(transparentDrawable)
            .circleCrop()

        val glideWallpaperOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(transparentDrawable)
            .error(transparentDrawable)
            .centerCrop()

        var previewDebounceJob: Job? = null
        var activeBgIsA = true
        var activeAvatarIsA = true

        fun refreshPreview(smoothTransition: Boolean = true) {
            b.tvPreviewName.text = currentName.ifBlank { context.getString(R.string.profile_name_hint) }
            b.tvProfileNameValue.text = currentName.ifBlank { context.getString(R.string.profile_name_hint) }

            // Double-buffered avatar transition: old avatar stays visible until new avatar crossfades in
            val currentAvatar = if (activeAvatarIsA) b.ivPreviewAvatarA else b.ivPreviewAvatarB
            val nextAvatar = if (activeAvatarIsA) b.ivPreviewAvatarB else b.ivPreviewAvatarA
            val avatarTarget: Any = selectedAvatarUrl.takeIf { it.isNotBlank() }
                ?: if (profile?.avatarResName == "avatar_caua") R.drawable.avatar_caua else R.drawable.avatar_caua

            if (!smoothTransition) {
                Glide.with(context)
                    .load(avatarTarget)
                    .apply(glideAvatarOptions)
                    .into(currentAvatar)
                currentAvatar.alpha = 1f
                nextAvatar.alpha = 0f
            } else {
                Glide.with(context)
                    .load(avatarTarget)
                    .apply(glideAvatarOptions)
                    .into(nextAvatar)
                nextAvatar.animate().cancel()
                currentAvatar.animate().cancel()
                nextAvatar.alpha = 0f
                nextAvatar.animate().alpha(1f).setDuration(160).start()
                currentAvatar.animate().alpha(0f).setDuration(160).withEndAction {
                    currentAvatar.alpha = 0f
                }.start()
                activeAvatarIsA = !activeAvatarIsA
            }

            // Double-buffered wallpaper transition: fixes Glide CrossFadeDrawable CENTER_CROP squishing bug
            val currentBg = if (activeBgIsA) b.ivPreviewBgA else b.ivPreviewBgB
            val nextBg = if (activeBgIsA) b.ivPreviewBgB else b.ivPreviewBgA
            val bgTarget = selectedBackgroundUrl.takeIf { it.isNotBlank() } ?: R.drawable.bg_profile_fantasy

            if (!smoothTransition) {
                Glide.with(context)
                    .load(bgTarget)
                    .apply(glideWallpaperOptions)
                    .into(currentBg)
                currentBg.alpha = 1f
                nextBg.alpha = 0f
            } else {
                Glide.with(context)
                    .load(bgTarget)
                    .apply(glideWallpaperOptions)
                    .into(nextBg)
                nextBg.animate().cancel()
                currentBg.animate().cancel()
                nextBg.alpha = 0f
                nextBg.animate().alpha(1f).setDuration(180).start()
                currentBg.animate().alpha(0f).setDuration(180).withEndAction {
                    currentBg.alpha = 0f
                }.start()
                activeBgIsA = !activeBgIsA
            }
        }

        refreshPreview(smoothTransition = false)

        fun showMainMenu(focusedTarget: View? = null) {
            b.panelAvatarCatalog.isVisible = false
            b.panelWallpaperCatalog.isVisible = false
            b.panelMainMenu.isVisible = true
            (focusedTarget ?: b.rowProfileName).requestFocus()
        }

        // Row 1: Profile Name edit dialog
        b.rowProfileName.setOnClickListener {
            val input = EditText(context).apply {
                setText(currentName)
                setSelection(currentName.length)
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(context, R.drawable.glass_input_bg)
                val pad = dp(14)
                setPadding(pad, pad, pad, pad)
                setSingleLine(true)
                maxLines = 1
                hint = context.getString(R.string.profile_name_hint)
            }
            MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.profile_name_hint)
                .setView(input)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotBlank()) {
                        currentName = newName
                        refreshPreview(smoothTransition = false)
                    }
                }
                .setNegativeButton(R.string.profile_cancel_action, null)
                .show()
        }

        fun renderCatalogSections(
            sections: List<Pair<String, List<ProfileArt>>>,
            container: LinearLayout,
            isWallpaper: Boolean
        ) {
            container.removeAllViews()
            var firstFocusableCard: View? = null

            sections.forEach { (sectionTitleText, arts) ->
                if (arts.isEmpty()) return@forEach

                val sectionTitle = TextView(context).apply {
                    text = "$sectionTitleText (${arts.size})"
                    androidx.core.widget.TextViewCompat.setTextAppearance(
                        this,
                        R.style.TextAppearance_Makimono_TV_Body
                    )
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    val topPad = dp(16)
                    val botPad = dp(10)
                    setPadding(0, topPad, 0, botPad)
                }
                container.addView(sectionTitle)

                val hScroll = HorizontalScrollView(context).apply {
                    clipChildren = false
                    clipToPadding = false
                    isFillViewport = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                }

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    setPadding(0, dp(4), dp(16), dp(8))
                }

                val cardWidth: Int
                val cardHeight: Int
                val radius: Float
                val selectorBg: Int

                if (isWallpaper) {
                    cardWidth = dp(192)
                    cardHeight = dp(108) // 16:9
                    radius = dp(10).toFloat()
                    selectorBg = R.drawable.bg_profile_wallpaper_selector
                } else {
                    cardWidth = dp(84)
                    cardHeight = dp(84)
                    radius = cardWidth / 2f
                    selectorBg = R.drawable.bg_profile_avatar_selector
                }
                val cardMargin = dp(16)

                arts.forEach { art ->
                    val cardBinding = ItemProfileCatalogCardBinding.inflate(LayoutInflater.from(context), row, false)
                    cardBinding.root.layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                        marginEnd = cardMargin
                    }
                    val isCurrentlySelected = if (isWallpaper) (art.url == selectedBackgroundUrl) else (art.url == selectedAvatarUrl)

                    cardBinding.cardArtRoot.apply {
                        this.radius = radius
                        strokeWidth = 0
                        setCardBackgroundColor(Color.TRANSPARENT)
                        foreground = ContextCompat.getDrawable(context, selectorBg)
                        isFocusable = true
                        isClickable = true
                        isSelected = isCurrentlySelected

                        setOnFocusChangeListener { v, hasFocus ->
                            if (hasFocus) {
                                val scale = if (isWallpaper) 1.08f else 1.12f
                                v.animate().scaleX(scale).scaleY(scale).translationZ(dp(8).toFloat()).setDuration(160).start()
                                previewDebounceJob?.cancel()
                                previewDebounceJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
                                    delay(70L)
                                    if (isWallpaper) {
                                        selectedBackgroundUrl = art.url
                                    } else {
                                        selectedAvatarUrl = art.url
                                    }
                                    refreshPreview(smoothTransition = true)
                                }
                            } else {
                                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(160).start()
                            }
                        }

                        setOnClickListener {
                            previewDebounceJob?.cancel()
                            if (isWallpaper) {
                                selectedBackgroundUrl = art.url
                                refreshPreview(smoothTransition = true)
                                showMainMenu(b.btnChangeBackground)
                            } else {
                                selectedAvatarUrl = art.url
                                refreshPreview(smoothTransition = true)
                                showMainMenu(b.btnChangeAvatar)
                            }
                        }

                        if (firstFocusableCard == null) {
                            firstFocusableCard = this
                        }
                    }

                    cardBinding.ivArtSelected.isVisible = isCurrentlySelected
                    Glide.with(context)
                        .load(art.url)
                        .apply(if (isWallpaper) glideWallpaperOptions else glideAvatarOptions)
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: com.bumptech.glide.load.engine.GlideException?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                cardBinding.root.visibility = View.GONE
                                return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                model: Any,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                dataSource: com.bumptech.glide.load.DataSource,
                                isFirstResource: Boolean
                            ): Boolean = false
                        })
                        .into(cardBinding.ivArtImage)

                    row.addView(cardBinding.root)
                }

                hScroll.addView(row)
                container.addView(hScroll)
            }

            firstFocusableCard?.post { firstFocusableCard?.requestFocus() }
        }

        var avatarCatalogPopulated = false
        fun populateAvatarCatalog() {
            if (avatarCatalogPopulated) return
            avatarCatalogPopulated = true

            b.scrollAvatarCatalog.isVisible = false
            b.layoutAvatarSkeleton.isVisible = true
            val shimmerAnim = ShimmerController.start(b.layoutAvatarSkeleton)

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val (libAvatarSections, _) = resolver.getLibraryArtSections()
                val presetAvatars = resolver.getPresetAvatars()

                val allSections = mutableListOf<Pair<String, List<ProfileArt>>>()
                val seenFranchises = mutableSetOf<String>()

                // 1. Each anime from the user's library gets its OWN individual section with character avatars
                libAvatarSections.forEach { (animeTitle, avatars) ->
                    if (avatars.isNotEmpty()) {
                        val descriptor = ProfileArtCatalogPolicy.resolveFranchise(animeTitle)
                        seenFranchises.add(descriptor.franchiseKey)
                        allSections.add(Pair(animeTitle, avatars))
                    }
                }

                // 2. Preset anime franchise sections that weren't already in the library
                val groupedPresets = presetAvatars.groupBy { art ->
                    art.label.substringAfterLast("(", "").substringBefore(")", "").ifBlank { "Destaques" }
                }
                groupedPresets.forEach { (franchise, arts) ->
                    val descriptor = ProfileArtCatalogPolicy.resolveFranchise(franchise)
                    if (seenFranchises.add(descriptor.franchiseKey)) {
                        allSections.add(Pair(franchise, arts))
                    }
                }

                val relevantTitles = resolver.getRelevantTitles()
                val organizedSections = ProfileArtCatalogPolicy.organizeSections(
                    rawSections = allSections,
                    relevantTitles = relevantTitles,
                    maxRelevant = 3
                )

                shimmerAnim?.cancel()
                b.layoutAvatarSkeleton.isVisible = false
                b.scrollAvatarCatalog.isVisible = true

                renderCatalogSections(
                    sections = organizedSections,
                    container = b.layoutAvatarSections,
                    isWallpaper = false
                )
            }
        }

        var wallpaperCatalogPopulated = false
        fun populateWallpaperCatalog() {
            if (wallpaperCatalogPopulated) return
            wallpaperCatalogPopulated = true

            b.scrollWallpaperCatalog.isVisible = false
            b.layoutWallpaperSkeleton.isVisible = true
            val shimmerAnim = ShimmerController.start(b.layoutWallpaperSkeleton)

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val (_, libWallpaperSections) = resolver.getLibraryArtSections()
                val presetWallpapers = resolver.getPresetWallpapers()

                val allSections = mutableListOf<Pair<String, List<ProfileArt>>>()
                val seenFranchises = mutableSetOf<String>()

                // 1. Each anime from the user's library gets its OWN wallpaper section
                libWallpaperSections.forEach { (animeTitle, wallpapers) ->
                    if (wallpapers.isNotEmpty()) {
                        val descriptor = ProfileArtCatalogPolicy.resolveFranchise(animeTitle)
                        seenFranchises.add(descriptor.franchiseKey)
                        allSections.add(Pair(animeTitle, wallpapers))
                    }
                }

                // 2. Preset wallpapers that were not already in the library
                val groupedPresets = presetWallpapers.groupBy { art ->
                    art.label.substringAfter("· ", "").ifBlank { "Destaques" }
                }
                groupedPresets.forEach { (franchise, arts) ->
                    val descriptor = ProfileArtCatalogPolicy.resolveFranchise(franchise)
                    if (seenFranchises.add(descriptor.franchiseKey)) {
                        allSections.add(Pair(franchise, arts))
                    }
                }

                val relevantTitles = resolver.getRelevantTitles()
                val organizedSections = ProfileArtCatalogPolicy.organizeSections(
                    rawSections = allSections,
                    relevantTitles = relevantTitles,
                    maxRelevant = 3
                )

                shimmerAnim?.cancel()
                b.layoutWallpaperSkeleton.isVisible = false
                b.scrollWallpaperCatalog.isVisible = true

                renderCatalogSections(
                    sections = organizedSections,
                    container = b.layoutWallpaperSections,
                    isWallpaper = true
                )
            }
        }

        // Row 2: Change Avatar
        b.btnChangeAvatar.setOnClickListener {
            b.panelMainMenu.isVisible = false
            b.panelWallpaperCatalog.isVisible = false
            b.panelAvatarCatalog.isVisible = true
            populateAvatarCatalog()
        }

        // Row 3: Change Wallpaper
        b.btnChangeBackground.setOnClickListener {
            b.panelMainMenu.isVisible = false
            b.panelAvatarCatalog.isVisible = false
            b.panelWallpaperCatalog.isVisible = true
            populateWallpaperCatalog()
        }

        // Delete button (visible when editing an existing profile and more than 1 profile exists)
        val canDelete = profile != null && manager.getProfiles().size > 1
        b.btnDeleteProfile.isVisible = canDelete
        if (canDelete) {
            b.btnDeleteProfile.setOnClickListener {
                MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                    .setTitle(context.getString(R.string.profile_delete_title, profile!!.name))
                    .setMessage(R.string.profile_delete_message)
                    .setPositiveButton(R.string.profile_delete_action) { _, _ ->
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                manager.deleteProfile(profile.id)
                                dialog.dismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, R.string.profile_delete_error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton(R.string.profile_cancel_action, null)
                    .show()
            }
        }

        // Row 4: Done button
        b.btnDone.setOnClickListener {
            val name = currentName.trim()
            if (name.isBlank()) {
                Toast.makeText(context, R.string.profile_name_required, Toast.LENGTH_SHORT).show()
                b.rowProfileName.requestFocus()
                return@setOnClickListener
            }
            b.btnDone.isEnabled = false
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val targetProfile = (profile ?: UserProfile(UUID.randomUUID().toString(), name, "avatar_anime"))
                        .copy(
                            name = name,
                            avatarUrl = selectedAvatarUrl.ifBlank { null },
                            backgroundUrl = selectedBackgroundUrl.ifBlank { null },
                            isAdmin = profile?.isAdmin ?: true,
                            isKids = profile?.isKids ?: false
                        )
                    manager.saveProfile(targetProfile)
                    dialog.dismiss()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    b.btnDone.isEnabled = true
                    Toast.makeText(context, R.string.profile_save_error, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Handle Back key navigation inside catalog screens
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (b.panelAvatarCatalog.isVisible) {
                    showMainMenu(b.btnChangeAvatar)
                    return@setOnKeyListener true
                }
                if (b.panelWallpaperCatalog.isVisible) {
                    showMainMenu(b.btnChangeBackground)
                    return@setOnKeyListener true
                }
            }
            false
        }

        // Lifecycle observer to prevent leak
        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                previewDebounceJob?.cancel()
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            previewDebounceJob?.cancel()
            lifecycle.removeObserver(observer)
        }

        dialog.show()
        b.rowProfileName.post { b.rowProfileName.requestFocus() }
    }

    companion object {
        fun validImageUrl(value: String?): Boolean = value == null || runCatching {
            java.net.URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }
        }.getOrDefault(false)
    }
}
