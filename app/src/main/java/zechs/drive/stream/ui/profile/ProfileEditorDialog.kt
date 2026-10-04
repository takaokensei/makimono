package zechs.drive.stream.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
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
import zechs.drive.stream.utils.GlideApp
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.TvAccessibilityLayout
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
        b.isAdmin.isChecked = profile?.isAdmin == true
        b.isKids.isChecked = profile?.isKids == true
        var search: Job? = null
        var currentTab = CatalogTab.AVATARS

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setBackground(ContextCompat.getDrawable(context, R.drawable.bg_profile_editor_dialog))
            .setTitle(if (profile == null) "Novo perfil" else "Editar perfil")
            .setView(b.root)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
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

        fun populateArtGrid(items: List<ProfileArt>, isWallpaperView: Boolean) {
            b.catalogImages.removeAllViews()
            val density = context.resources.displayMetrics.density
            val itemHeight = if (isWallpaperView) (76 * density).toInt() else (96 * density).toInt()
            val colCount = TvAccessibilityLayout.profileCatalogColumns(
                context.resources.configuration.fontScale,
                isWallpaperView
            )
            b.catalogImages.columnCount = colCount

            val glideOptions = RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_image_placeholder_24)
                .error(R.drawable.ic_image_placeholder_24)
                .override(if (isWallpaperView) 480 else 200, if (isWallpaperView) 270 else 200)
                .centerCrop()

            val inflater = LayoutInflater.from(context)

            items.forEach { art ->
                val cardBinding = ItemProfileCatalogCardBinding.inflate(inflater, b.catalogImages, false)
                cardBinding.frameArtContainer.layoutParams.height = itemHeight
                cardBinding.cardArtRoot.apply {
                    contentDescription = if (art.isWallpaper) art.label else "Avatar de ${art.label}"
                    isFocusable = true
                    isClickable = true
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = 0
                        height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                        columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                        setMargins((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
                    }
                    setOnClickListener {
                        if (art.isWallpaper) {
                            b.backgroundUrl.setText(art.url)
                            b.catalogStatus.text = "Wallpaper: ${art.label}"
                        } else {
                            b.avatarUrl.setText(art.url)
                            b.catalogStatus.text = "Avatar: ${art.label}"
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
                                val scrollDelta = when {
                                    focusedBottom > viewportBottom -> focusedBottom - viewportBottom
                                    focusedTop < viewportTop -> focusedTop - viewportTop
                                    else -> 0
                                }
                                if (scrollDelta != 0) b.scrollEditor.smoothScrollBy(0, scrollDelta)
                            }
                        }
                    }
                }

                GlideApp.with(fragment)
                    .load(art.url)
                    .apply(glideOptions)
                    .into(cardBinding.ivArtImage)

                b.catalogImages.addView(cardBinding.root)
            }
        }

        fun switchTab(tab: CatalogTab) {
            currentTab = tab
            if (tab == CatalogTab.AVATARS) {
                b.btnTabAvatars.setBackgroundResource(R.drawable.glass_pill_accent_bg)
                b.btnTabAvatars.setTextColor(android.graphics.Color.parseColor("#040C16"))
                b.btnTabWallpapers.setBackgroundResource(R.drawable.glass_pill_button_bg)
                b.btnTabWallpapers.setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                b.catalogStatus.text = "Avatares populares selecionáveis:"
                populateArtGrid(resolver.getPresetAvatars(), isWallpaperView = false)
            } else {
                b.btnTabAvatars.setBackgroundResource(R.drawable.glass_pill_button_bg)
                b.btnTabAvatars.setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                b.btnTabWallpapers.setBackgroundResource(R.drawable.glass_pill_accent_bg)
                b.btnTabWallpapers.setTextColor(android.graphics.Color.parseColor("#040C16"))
                b.catalogStatus.text = "Papéis de parede populares selecionáveis:"
                populateArtGrid(resolver.getPresetWallpapers(), isWallpaperView = true)
            }
        }

        b.btnTabAvatars.setOnClickListener { switchTab(CatalogTab.AVATARS) }
        b.btnTabWallpapers.setOnClickListener { switchTab(CatalogTab.WALLPAPERS) }

        // Initial populate with preset avatars
        switchTab(CatalogTab.AVATARS)

        b.searchCatalog.setOnClickListener {
            val query = b.catalogQuery.text.toString().trim()
            if (query.isBlank()) {
                b.catalogQuery.error = "Digite o nome de um anime"
                return@setOnClickListener
            }
            search?.cancel()
            search = fragment.viewLifecycleOwner.lifecycleScope.launch {
                b.catalogStatus.text = "Buscando imagens…"
                b.catalogImages.removeAllViews()
                try {
                    val choices = resolver.search(query)
                    b.catalogStatus.text = if (choices.isEmpty()) {
                        "Nenhuma imagem encontrada. Tente outro anime."
                    } else {
                        "$query · selecione um retrato ou wallpaper"
                    }
                    populateArtGrid(choices, isWallpaperView = choices.any { it.isWallpaper })
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    b.catalogStatus.text = "Não foi possível buscar. Tente novamente ou use uma URL própria."
                }
            }
        }

        dialog.setOnShowListener {
            TvFocusRing.install(dialog.window!!.decorView)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = b.profileName.text.toString().trim()
                if (name.isBlank()) {
                    b.profileName.error = "Informe um nome"
                    b.profileName.requestFocus()
                    return@setOnClickListener
                }
                val avatar = b.avatarUrl.text.toString().trim().ifBlank { null }
                val background = b.backgroundUrl.text.toString().trim().ifBlank { null }
                for ((field, value) in listOf(b.avatarUrl to avatar, b.backgroundUrl to background)) {
                    if (!validImageUrl(value)) {
                        field.error = "Use uma URL HTTPS válida"
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
                    } catch (_: Exception) {
                        b.profileName.error = "Falha ao salvar. Tente novamente."
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
            if (fontScale >= 1.8f) hint = "Buscar anime"
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
