package zechs.drive.stream.ui.profile

import zechs.drive.stream.domain.profile.ProfileSetupPolicy

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val resolver: ProfileArtCatalog,
    private val setupSettings: zechs.drive.stream.utils.AppSettings? = null
) {
    fun show(profile: UserProfile?, required: Boolean = false, onSaved: (UserProfile) -> Unit = {}) {
        val context = fragment.requireContext()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val b = DialogProfileEditorBinding.inflate(LayoutInflater.from(context))

        var currentName = profile?.name.orEmpty()
        var selectedAvatarUrl = profile?.avatarUrl.orEmpty()
        var selectedBackgroundUrl = profile?.backgroundUrl.orEmpty()

        val themedContext = ContextThemeWrapper(context, R.style.Theme_DriveStream_Obsidian)
        val dialog = Dialog(themedContext, R.style.Theme_DriveStream_Obsidian).apply {
            setContentView(b.root)
            setCancelable(!required)
            setCanceledOnTouchOutside(!required)
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
                decorView.setPadding(0, 0, 0, 0)
                setWindowAnimations(R.style.Animation_Makimono_DialogFade)
            }
        }

        val placeholderAvatar = ContextCompat.getDrawable(context, R.drawable.bg_profile_avatar_placeholder)
        val placeholderWallpaper = ContextCompat.getDrawable(context, R.drawable.bg_profile_card_placeholder)

        val glideAvatarOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(placeholderAvatar)
            .error(placeholderAvatar)
            .circleCrop()

        val glideWallpaperOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(placeholderWallpaper)
            .error(placeholderWallpaper)
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
                ?: manager.getAvatarDrawableRes(profile?.avatarResName ?: "profile_placeholder")

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
            android.transition.TransitionManager.beginDelayedTransition(
                b.root as? android.view.ViewGroup ?: b.root,
                android.transition.AutoTransition().apply {
                    duration = context.resources.getInteger(R.integer.motion_focus).toLong()
                }
            )
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

        fun appendSection(
            sectionTitleText: String,
            arts: List<ProfileArt>,
            container: LinearLayout,
            isWallpaper: Boolean,
            onCardCreated: ((View) -> Unit)? = null
        ) {
            if (arts.isEmpty()) return

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

                    onCardCreated?.invoke(this)
                }

                cardBinding.ivArtSelected.isVisible = isCurrentlySelected
                Glide.with(context)
                    .load(art.url)
                    .apply(if (isWallpaper) glideWallpaperOptions else glideAvatarOptions)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(150))
                    .into(cardBinding.ivArtImage)

                row.addView(cardBinding.root)
            }

            hScroll.addView(row)
            container.addView(hScroll)
        }

        var avatarCatalogPopulated = false
        var avatarRenderJob: kotlinx.coroutines.Job? = null
        fun populateAvatarCatalog() {
            if (avatarCatalogPopulated && b.layoutAvatarSections.childCount > 0) {
                b.layoutAvatarSkeleton.isVisible = false
                b.scrollAvatarCatalog.isVisible = true
                b.layoutAvatarSections.findFocus()?.requestFocus()
                    ?: (b.layoutAvatarSections.getChildAt(1) as? ViewGroup)?.getChildAt(0)?.requestFocus()
                return
            }

            b.scrollAvatarCatalog.isVisible = false
            b.layoutAvatarSkeleton.isVisible = true
            val shimmerAnim = ShimmerController.start(b.layoutAvatarSkeleton, 0.40f, 0.85f)

            // Tier 1: Instant display of presets (< 2ms)
            val presetAvatars = resolver.getPresetAvatars()
            val groupedPresets = presetAvatars.groupBy { art ->
                art.label.substringAfterLast("(", "").substringBefore(")", "").ifBlank { "Destaques" }
            }
            val presetSections = mutableListOf<Pair<String, List<ProfileArt>>>()
            val seenFranchises = mutableSetOf<String>()
            groupedPresets.forEach { (franchise, arts) ->
                val descriptor = ProfileArtCatalogPolicy.resolveFranchise(franchise)
                if (seenFranchises.add(descriptor.franchiseKey)) {
                    presetSections.add(Pair(franchise, arts))
                }
            }

            b.layoutAvatarSections.removeAllViews()
            var firstFocusableCard: View? = null
            val initialBatch = presetSections.take(2)
            val remainingPresets = presetSections.drop(2)

            initialBatch.forEach { (title, arts) ->
                appendSection(
                    sectionTitleText = title,
                    arts = arts,
                    container = b.layoutAvatarSections,
                    isWallpaper = false
                ) { card ->
                    if (firstFocusableCard == null) firstFocusableCard = card
                }
            }

            avatarCatalogPopulated = true
            shimmerAnim?.cancel()
            b.layoutAvatarSkeleton.isVisible = false
            b.scrollAvatarCatalog.isVisible = true
            firstFocusableCard?.post { firstFocusableCard?.requestFocus() }

            // Tier 2: Progressive on-demand background streaming
            avatarRenderJob?.cancel()
            avatarRenderJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
                val libAvatarSections: List<Pair<String, List<ProfileArt>>> = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(2000L) {
                        try { resolver.getLibraryArtSections().first } catch (_: Exception) { emptyList() }
                    } ?: emptyList()
                }

                if (libAvatarSections.isNotEmpty()) {
                    libAvatarSections.forEach { (animeTitle, avatars) ->
                        if (!isActive) return@forEach
                        val descriptor = ProfileArtCatalogPolicy.resolveFranchise(animeTitle)
                        if (seenFranchises.add(descriptor.franchiseKey)) {
                            kotlinx.coroutines.delay(16L)
                            appendSection(animeTitle, avatars, b.layoutAvatarSections, isWallpaper = false)
                        }
                    }
                }

                for (section in remainingPresets) {
                    if (!isActive) break
                    kotlinx.coroutines.delay(16L)
                    appendSection(section.first, section.second, b.layoutAvatarSections, isWallpaper = false)
                }
            }
        }

        var wallpaperCatalogPopulated = false
        var wallpaperRenderJob: kotlinx.coroutines.Job? = null
        fun populateWallpaperCatalog() {
            if (wallpaperCatalogPopulated && b.layoutWallpaperSections.childCount > 0) {
                b.layoutWallpaperSkeleton.isVisible = false
                b.scrollWallpaperCatalog.isVisible = true
                b.layoutWallpaperSections.findFocus()?.requestFocus()
                    ?: (b.layoutWallpaperSections.getChildAt(1) as? ViewGroup)?.getChildAt(0)?.requestFocus()
                return
            }

            b.scrollWallpaperCatalog.isVisible = false
            b.layoutWallpaperSkeleton.isVisible = true
            val shimmerAnim = ShimmerController.start(b.layoutWallpaperSkeleton, 0.40f, 0.85f)

            // Tier 1: Instant display of presets (< 2ms)
            val presetWallpapers = resolver.getPresetWallpapers()
            val groupedPresets = presetWallpapers.groupBy { art ->
                art.label.substringAfter("· ", "").ifBlank { "Destaques" }
            }
            val presetSections = mutableListOf<Pair<String, List<ProfileArt>>>()
            val seenFranchises = mutableSetOf<String>()
            groupedPresets.forEach { (franchise, arts) ->
                val descriptor = ProfileArtCatalogPolicy.resolveFranchise(franchise)
                if (seenFranchises.add(descriptor.franchiseKey)) {
                    presetSections.add(Pair(franchise, arts))
                }
            }

            b.layoutWallpaperSections.removeAllViews()
            var firstFocusableCard: View? = null
            val initialBatch = presetSections.take(2)
            val remainingPresets = presetSections.drop(2)

            initialBatch.forEach { (title, arts) ->
                appendSection(
                    sectionTitleText = title,
                    arts = arts,
                    container = b.layoutWallpaperSections,
                    isWallpaper = true
                ) { card ->
                    if (firstFocusableCard == null) firstFocusableCard = card
                }
            }

            wallpaperCatalogPopulated = true
            shimmerAnim?.cancel()
            b.layoutWallpaperSkeleton.isVisible = false
            b.scrollWallpaperCatalog.isVisible = true
            firstFocusableCard?.post { firstFocusableCard?.requestFocus() }

            // Tier 2: Progressive on-demand background streaming
            wallpaperRenderJob?.cancel()
            wallpaperRenderJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
                val libWallpaperSections: List<Pair<String, List<ProfileArt>>> = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(2000L) {
                        try { resolver.getLibraryArtSections().second } catch (_: Exception) { emptyList() }
                    } ?: emptyList()
                }

                if (libWallpaperSections.isNotEmpty()) {
                    libWallpaperSections.forEach { (animeTitle, wallpapers) ->
                        if (!isActive) return@forEach
                        val descriptor = ProfileArtCatalogPolicy.resolveFranchise(animeTitle)
                        if (seenFranchises.add(descriptor.franchiseKey)) {
                            kotlinx.coroutines.delay(16L)
                            appendSection(animeTitle, wallpapers, b.layoutWallpaperSections, isWallpaper = true)
                        }
                    }
                }

                for (section in remainingPresets) {
                    if (!isActive) break
                    kotlinx.coroutines.delay(16L)
                    appendSection(section.first, section.second, b.layoutWallpaperSections, isWallpaper = true)
                }
            }
        }

        // Row 2: Change Avatar
        b.btnChangeAvatar.setOnClickListener {
            android.transition.TransitionManager.beginDelayedTransition(
                b.root as? android.view.ViewGroup ?: b.root,
                android.transition.AutoTransition().apply {
                    duration = context.resources.getInteger(R.integer.motion_focus).toLong()
                }
            )
            b.panelMainMenu.isVisible = false
            b.panelWallpaperCatalog.isVisible = false
            b.panelAvatarCatalog.isVisible = true
            populateAvatarCatalog()
        }

        // Row 3: Change Wallpaper
        b.btnChangeBackground.setOnClickListener {
            android.transition.TransitionManager.beginDelayedTransition(
                b.root as? android.view.ViewGroup ?: b.root,
                android.transition.AutoTransition().apply {
                    duration = context.resources.getInteger(R.integer.motion_focus).toLong()
                }
            )
            b.panelMainMenu.isVisible = false
            b.panelAvatarCatalog.isVisible = false
            b.panelWallpaperCatalog.isVisible = true
            populateWallpaperCatalog()
        }

        zechs.drive.stream.utils.FocusEngine.attach(b.rowProfileName, zechs.drive.stream.utils.FocusEngine.FocusKind.BUTTON)
        zechs.drive.stream.utils.FocusEngine.attach(b.btnChangeAvatar, zechs.drive.stream.utils.FocusEngine.FocusKind.BUTTON)
        zechs.drive.stream.utils.FocusEngine.attach(b.btnChangeBackground, zechs.drive.stream.utils.FocusEngine.FocusKind.BUTTON)
        zechs.drive.stream.utils.FocusEngine.attach(b.btnDeleteProfile, zechs.drive.stream.utils.FocusEngine.FocusKind.BUTTON)
        zechs.drive.stream.utils.FocusEngine.attach(b.btnDone, zechs.drive.stream.utils.FocusEngine.FocusKind.BUTTON)

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
                    val targetProfile = (profile ?: UserProfile(UUID.randomUUID().toString(), name, "profile_placeholder"))
                        .copy(
                            name = name,
                            avatarUrl = selectedAvatarUrl.ifBlank { null },
                            backgroundUrl = selectedBackgroundUrl.ifBlank { null },
                            isAdmin = profile?.isAdmin ?: true,
                            isKids = profile?.isKids ?: false
                        )
                    if (profile == null || required)
                        setupSettings?.saveProfileSetupStep(targetProfile.id, ProfileSetupPolicy.LANGUAGE)
                    manager.saveProfile(targetProfile)
                    val saved = manager.getProfiles().first { it.id == targetProfile.id }
                    dialog.dismiss()
                    onSaved(saved)
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
                avatarRenderJob?.cancel()
                wallpaperRenderJob?.cancel()
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            previewDebounceJob?.cancel()
            avatarRenderJob?.cancel()
            wallpaperRenderJob?.cancel()
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
