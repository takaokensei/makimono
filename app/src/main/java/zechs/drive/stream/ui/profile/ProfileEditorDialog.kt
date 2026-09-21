package zechs.drive.stream.ui.profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
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
import zechs.drive.stream.utils.ProfileManager
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
        b.profileName.setText(profile?.name.orEmpty())
        b.avatarUrl.setText(profile?.avatarUrl.orEmpty())
        b.backgroundUrl.setText(profile?.backgroundUrl.orEmpty())
        b.isAdmin.isChecked = profile?.isAdmin == true
        b.isKids.isChecked = profile?.isKids == true
        var search: Job? = null
        var currentTab = CatalogTab.AVATARS

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Makimono_Glass)
            .setBackground(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_player_bottom_panel))
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
            val itemHeight = if (isWallpaperView) (70 * density).toInt() else (90 * density).toInt()
            val colCount = if (isWallpaperView) 2 else 4
            b.catalogImages.columnCount = colCount

            // Compressed & cached Glide options
            val glideOptions = RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(if (isWallpaperView) 400 else 180, if (isWallpaperView) 225 else 180)
                .centerCrop()

            items.forEach { art ->
                val label = art.label
                val url = art.url
                val button = ImageButton(context).apply {
                    contentDescription = if (art.isWallpaper) label else "Usar avatar de $label"
                    isFocusable = true
                    setBackgroundResource(R.drawable.bg_player_action_vertical)
                    setPadding(4, 4, 4, 4)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = 0
                        height = itemHeight
                        columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                        setMargins(4, 4, 4, 4)
                    }
                    setOnClickListener {
                        if (art.isWallpaper) {
                            b.backgroundUrl.setText(url)
                            b.catalogStatus.text = "Wallpaper selecionado: $label"
                        } else {
                            b.avatarUrl.setText(url)
                            b.catalogStatus.text = "Avatar selecionado: $label"
                        }
                    }
                }
                b.catalogImages.addView(button)
                Glide.with(fragment)
                    .load(url)
                    .apply(glideOptions)
                    .error(R.drawable.ic_folder_24)
                    .into(button)
            }
        }

        fun switchTab(tab: CatalogTab) {
            currentTab = tab
            val cyan = Color.parseColor("#22D3EE")
            val muted = Color.parseColor("#ACC0D6")

            if (tab == CatalogTab.AVATARS) {
                b.btnTabAvatars.setTextColor(cyan)
                b.btnTabWallpapers.setTextColor(muted)
                b.catalogStatus.text = "Avatares populares selecionáveis:"
                populateArtGrid(resolver.getPresetAvatars(), isWallpaperView = false)
            } else {
                b.btnTabAvatars.setTextColor(muted)
                b.btnTabWallpapers.setTextColor(cyan)
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
            zechs.drive.stream.utils.FrostedWindow.apply(dialog.window!!)
            zechs.drive.stream.utils.TvFocusRing.install(dialog.window!!.decorView)
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
}
