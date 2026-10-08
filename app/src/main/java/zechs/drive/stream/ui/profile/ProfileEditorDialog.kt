package zechs.drive.stream.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.ProfileArt
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.databinding.DialogProfileEditorBinding
import zechs.drive.stream.databinding.ItemProfileCatalogCardBinding
import com.bumptech.glide.Glide
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.TvFocusRing
import java.net.URI
import java.util.UUID

class ProfileEditorDialog(
    private val fragment: Fragment,
    private val manager: ProfileManager,
    private val resolver: ProfileArtCatalog
) {
    private enum class CatalogTab { AVATARS, WALLPAPERS }

    fun show(profile: UserProfile?) {
        val context = fragment.requireContext()
        val b = DialogProfileEditorBinding.inflate(LayoutInflater.from(context))
        adaptForLargeText(b)
        b.profileName.setText(profile?.name.orEmpty())
        b.avatarUrl.setText(profile?.avatarUrl.orEmpty())
        b.backgroundUrl.setText(profile?.backgroundUrl.orEmpty())
        var selectedAvatarUrl = profile?.avatarUrl.orEmpty()
        var selectedBackgroundUrl = profile?.backgroundUrl.orEmpty()
        b.isAdmin.isChecked = profile?.isAdmin == true
        b.isKids.isChecked = profile?.isKids == true
        var search: Job? = null
        val catalogFocusTargets = mutableListOf<android.view.View>()

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setBackground(ContextCompat.getDrawable(context, R.drawable.bg_profile_editor_dialog))
            .setTitle(if (profile == null) R.string.profile_editor_new_title else R.string.profile_editor_edit_title)
            .setView(b.root)
            .setPositiveButton(R.string.profile_save_action, null)
            .setNegativeButton(R.string.profile_cancel_action, null)
            .create()

        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { dialog.dismiss() }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            search?.cancel()
            lifecycle.removeObserver(observer)
        }

        fun populateArtGrid(items: List<ProfileArt>) {
            b.catalogImages.removeAllViews()
            b.profileArtCarousel.scrollTo(0, 0)
            val density = context.resources.displayMetrics.density
            val avatarDiameter = (112 * density).toInt()
            val wallpaperWidth = (198 * density).toInt()
            val avatarRowHeight = (112 * density).toInt()
            b.catalogImages.columnCount = items.size.coerceAtLeast(1)

            val glideOptions = RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_image_placeholder_24)
                .error(R.drawable.ic_image_placeholder_24)
                .override(480, 270)
                .centerCrop()

            val inflater = LayoutInflater.from(context)
            val catalogSelectionTargets = mutableListOf<Triple<ProfileArt, android.view.View, android.widget.ImageView>>()
            catalogFocusTargets.clear()

            fun isSelected(art: ProfileArt): Boolean = if (art.isWallpaper) {
                art.url == selectedBackgroundUrl
            } else {
                art.url == selectedAvatarUrl
            }

            fun updateSelection(art: ProfileArt, card: android.view.View, indicator: android.widget.ImageView) {
                val selected = isSelected(art)
                card.isSelected = selected
                indicator.isVisible = selected
                val label = if (art.isWallpaper) {
                    context.getString(R.string.profile_wallpaper_accessibility, art.label)
                } else {
                    context.getString(R.string.profile_avatar_accessibility, art.label)
                }
                card.contentDescription = if (!selected) label else {
                    context.getString(R.string.profile_art_selected_accessibility, label)
                }
            }

            items.forEachIndexed { index, art ->
                val cardBinding = ItemProfileCatalogCardBinding.inflate(inflater, b.catalogImages, false)
                val itemHeight = if (art.isWallpaper) avatarRowHeight else avatarDiameter
                val itemWidth = if (art.isWallpaper) wallpaperWidth else avatarDiameter
                cardBinding.root.layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = itemWidth
                    height = itemHeight
                    columnSpec = android.widget.GridLayout.spec(index)
                    setMargins((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
                }
                cardBinding.cardArtRoot.layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                cardBinding.frameArtContainer.layoutParams = cardBinding.frameArtContainer.layoutParams.apply {
                    width = itemWidth
                    height = itemHeight
                }
                cardBinding.cardArtRoot.radius = if (art.isWallpaper) {
                    12 * density
                } else {
                    itemHeight / 2f
                }
                if (art.isWallpaper) {
                    cardBinding.cardArtRoot.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.layout_literal_1f2335)
                    )
                    cardBinding.cardArtRoot.strokeWidth = (1 * density).toInt()
                } else {
                    // The circle image itself is the selectable surface. A gray MaterialCard
                    // tile around it reduces its apparent size and conflicts with the reference.
                    cardBinding.cardArtRoot.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    cardBinding.cardArtRoot.strokeWidth = 0
                }
                cardBinding.cardArtRoot.apply {
                    isFocusable = true
                    isClickable = true
                    setOnClickListener {
                        if (art.isWallpaper) {
                            selectedBackgroundUrl = art.url
                            b.backgroundUrl.setText(art.url)
                            b.catalogStatus.text = context.getString(R.string.profile_wallpaper_selected, art.label)
                        } else {
                            selectedAvatarUrl = art.url
                            b.avatarUrl.setText(art.url)
                            b.catalogStatus.text = context.getString(R.string.profile_avatar_selected, art.label)
                        }
                        catalogSelectionTargets.forEach { (targetArt, card, indicator) ->
                            updateSelection(targetArt, card, indicator)
                        }
                    }
                    setOnFocusChangeListener { focusedView, hasFocus ->
                        if (hasFocus) {
                            b.scrollEditor.post {
                                val viewLocation = IntArray(2)
                                val scrollLocation = IntArray(2)
                                focusedView.getLocationOnScreen(viewLocation)
                                b.scrollEditor.getLocationOnScreen(scrollLocation)
                                val viewportTop = scrollLocation[1] + b.scrollEditor.paddingTop
                                val viewportBottom = scrollLocation[1] + b.scrollEditor.height - b.scrollEditor.paddingBottom
                                val focusedTop = viewLocation[1]
                                val focusedBottom = focusedTop + focusedView.height
                                var scrollDelta = when {
                                    focusedBottom > viewportBottom -> focusedBottom - viewportBottom
                                    focusedTop < viewportTop -> focusedTop - viewportTop
                                    else -> 0
                                }

                                // A small remnant of the gallery header against the fixed dialog
                                // title looks like a layout seam. Once scrolling passes the tabs,
                                // move both tabs and status label fully out of view.
                                val tabsLocation = IntArray(2)
                                b.profileArtTabs.getLocationOnScreen(tabsLocation)
                                val tabsTopAfterScroll = tabsLocation[1] - scrollDelta
                                val tabsBottomAfterScroll = tabsTopAfterScroll + b.profileArtTabs.height
                                if (tabsTopAfterScroll < viewportTop && tabsBottomAfterScroll > viewportTop) {
                                    scrollDelta += tabsBottomAfterScroll - viewportTop + (8 * density).toInt()
                                }

                                val statusLocation = IntArray(2)
                                b.catalogStatus.getLocationOnScreen(statusLocation)
                                val statusTopAfterScroll = statusLocation[1] - scrollDelta
                                val statusBottomAfterScroll = statusTopAfterScroll + b.catalogStatus.height
                                if (statusTopAfterScroll < viewportTop && statusBottomAfterScroll > viewportTop) {
                                    scrollDelta += statusBottomAfterScroll - viewportTop + (8 * density).toInt()
                                }
                                if (scrollDelta != 0) b.scrollEditor.smoothScrollBy(0, scrollDelta)
                                focusedView.post {
                                    focusedView.requestRectangleOnScreen(
                                        android.graphics.Rect(0, 0, focusedView.width, focusedView.height),
                                        true
                                    )
                                }
                            }
                        }
                    }
                    catalogFocusTargets += this
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                // Traverse image choices in visual order. At row edges this
                                // avoids spatial focus jumping down to Cancel/Save on TV.
                                catalogFocusTargets.getOrNull(index + 1)?.requestFocus() ?: true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (index > 0) {
                                    catalogFocusTargets[index - 1].requestFocus()
                                } else {
                                    if (art.isWallpaper) b.btnTabWallpapers.requestFocus()
                                    else b.btnTabAvatars.requestFocus()
                                }
                            }
                            else -> false
                        }
                    }
                }

                catalogSelectionTargets += Triple(art, cardBinding.cardArtRoot, cardBinding.ivArtSelected)
                updateSelection(art, cardBinding.cardArtRoot, cardBinding.ivArtSelected)

                Glide.with(fragment)
                    .load(art.url)
                    .apply(glideOptions)
                    .into(cardBinding.ivArtImage)

                b.catalogImages.addView(cardBinding.root)
            }
        }

        fun switchTab(tab: CatalogTab) {
            if (tab == CatalogTab.AVATARS) {
                b.btnTabAvatars.setBackgroundResource(R.drawable.glass_pill_accent_bg)
                b.btnTabAvatars.setTextColor(android.graphics.Color.parseColor("#040C16"))
                b.btnTabWallpapers.setBackgroundResource(R.drawable.glass_pill_button_bg)
                b.btnTabWallpapers.setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                b.catalogStatus.setText(R.string.profile_avatar_catalog_hint)
                populateArtGrid(resolver.getPresetAvatars())
            } else {
                b.btnTabAvatars.setBackgroundResource(R.drawable.glass_pill_button_bg)
                b.btnTabAvatars.setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                b.btnTabWallpapers.setBackgroundResource(R.drawable.glass_pill_accent_bg)
                b.btnTabWallpapers.setTextColor(android.graphics.Color.parseColor("#040C16"))
                b.catalogStatus.setText(R.string.profile_wallpaper_catalog_hint)
                populateArtGrid(resolver.getPresetWallpapers())
            }
        }

        b.btnTabAvatars.setOnClickListener { switchTab(CatalogTab.AVATARS) }
        b.btnTabWallpapers.setOnClickListener { switchTab(CatalogTab.WALLPAPERS) }
        listOf(b.btnTabAvatars, b.btnTabWallpapers).forEach { tab ->
            tab.setOnKeyListener { _, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN || keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    return@setOnKeyListener false
                }
                catalogFocusTargets.firstOrNull()?.requestFocus() ?: false
            }
        }
        b.toggleCustomUrls.setOnClickListener {
            val expanded = !b.customUrlsGroup.isVisible
            b.customUrlsGroup.isVisible = expanded
            b.toggleCustomUrls.setText(
                if (expanded) R.string.profile_custom_urls_hide else R.string.profile_custom_urls_show
            )
        }

        b.searchCatalog.setOnClickListener {
            val query = b.catalogQuery.text.toString().trim()
            if (query.isBlank()) {
                b.catalogQuery.error = context.getString(R.string.profile_catalog_query_required)
                return@setOnClickListener
            }
            search?.cancel()
            search = fragment.viewLifecycleOwner.lifecycleScope.launch {
                b.catalogStatus.setText(R.string.profile_catalog_loading)
                b.catalogImages.removeAllViews()
                try {
                    val choices = resolver.search(query)
                    b.catalogStatus.text = if (choices.isEmpty()) {
                        context.getString(R.string.profile_catalog_empty)
                    } else {
                        context.getString(R.string.profile_catalog_results, query)
                    }
                    populateArtGrid(choices)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    b.catalogStatus.setText(R.string.profile_catalog_error)
                }
            }
        }

        dialog.setOnShowListener {
            TvFocusRing.install(dialog.window!!.decorView)
            val verticalDpadNavigation = android.view.View.OnKeyListener { focusedView, keyCode, event ->
                if (event.action != android.view.KeyEvent.ACTION_DOWN) return@OnKeyListener false
                val direction = when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> android.view.View.FOCUS_DOWN
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> android.view.View.FOCUS_UP
                    else -> return@OnKeyListener false
                }
                val next = focusedView.focusSearch(direction) ?: return@OnKeyListener false
                if (next === focusedView || !next.isFocusable) return@OnKeyListener false
                next.requestFocus()
            }
            listOf(b.profileName, b.catalogQuery, b.avatarUrl, b.backgroundUrl)
                .forEach { it.setOnKeyListener(verticalDpadNavigation) }

            // The catalog width only exists after the dialog has been measured. Populate then so
            // portrait columns and circles are sized correctly on narrow mobile/TV windows.
            b.catalogImages.post { switchTab(CatalogTab.AVATARS) }

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = b.profileName.text.toString().trim()
                if (name.isBlank()) {
                    b.profileName.error = context.getString(R.string.profile_name_required)
                    b.profileName.requestFocus()
                    return@setOnClickListener
                }
                val avatar = b.avatarUrl.text.toString().trim().ifBlank { null }
                val background = b.backgroundUrl.text.toString().trim().ifBlank { null }
                for ((field, value) in listOf(b.avatarUrl to avatar, b.backgroundUrl to background)) {
                    if (!validImageUrl(value)) {
                        field.error = context.getString(R.string.profile_image_url_invalid)
                        field.requestFocus()
                        return@setOnClickListener
                    }
                }
                val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        manager.saveProfile(
                            (profile ?: UserProfile(UUID.randomUUID().toString(), name, "avatar_anime"))
                                .copy(
                                    name = name,
                                    avatarUrl = avatar,
                                    backgroundUrl = background,
                                    isAdmin = b.isAdmin.isChecked,
                                    isKids = b.isKids.isChecked
                                )
                        )
                        dialog.dismiss()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: zechs.drive.stream.utils.LastAdministratorRequiredException) {
                        b.isAdmin.error = fragment.getString(R.string.profile_last_admin_required)
                        button.isEnabled = true
                    } catch (_: Exception) {
                        b.profileName.error = context.getString(R.string.profile_save_error)
                        button.isEnabled = true
                    }
                }
            }
            b.profileName.requestFocus()
        }
        dialog.show()
    }

    companion object {
        fun validImageUrl(value: String?): Boolean = value == null || runCatching {
            URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }
        }.getOrDefault(false)
    }

    private fun adaptForLargeText(binding: DialogProfileEditorBinding) {
        val context = fragment.requireContext()
        val fontScale = context.resources.configuration.fontScale
        if (fontScale < 1.3f) return

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val controlHeight = dp(if (fontScale >= 1.8f) 64 else 56)

        binding.profileArtTabs.orientation = LinearLayout.VERTICAL
        listOf(binding.btnTabAvatars, binding.btnTabWallpapers).forEach { button ->
            val params = button.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.marginStart = 0
            params.marginEnd = 0
            params.topMargin = dp(6)
            button.layoutParams = params
            button.minHeight = controlHeight
            button.isSingleLine = false
            button.maxLines = 2
        }

        binding.profileArtSearchRow.orientation = LinearLayout.VERTICAL
        binding.catalogQuery.apply {
            layoutParams = (layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            minHeight = controlHeight
            if (fontScale >= 1.8f) hint = context.getString(R.string.profile_catalog_search_hint)
        }
        binding.searchCatalog.apply {
            layoutParams = (layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
                marginStart = 0
                topMargin = dp(6)
            }
            minHeight = controlHeight
        }

        listOf(binding.profileName, binding.avatarUrl, binding.backgroundUrl).forEach { field ->
            field.layoutParams = field.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            field.minHeight = controlHeight
            field.setPadding(field.paddingLeft, dp(8), field.paddingRight, dp(8))
        }
    }
}
