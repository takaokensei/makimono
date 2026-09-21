package zechs.drive.stream.ui.profile

import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.databinding.DialogProfileEditorBinding
import zechs.drive.stream.utils.ProfileManager
import java.net.URI
import java.util.UUID

/** Uses the existing metadata providers; no bundled/generated anime art is added. */
class ProfileEditorDialog(
    private val fragment: Fragment,
    private val manager: ProfileManager,
    private val resolver: ProfileArtCatalog
) {
    fun show(profile: UserProfile?) {
        val context = fragment.requireContext()
        val b = DialogProfileEditorBinding.inflate(LayoutInflater.from(context))
        b.profileName.setText(profile?.name.orEmpty())
        b.avatarUrl.setText(profile?.avatarUrl.orEmpty())
        b.backgroundUrl.setText(profile?.backgroundUrl.orEmpty())
        b.isAdmin.isChecked = profile?.isAdmin == true
        b.isKids.isChecked = profile?.isKids == true
        var search: Job? = null
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
        b.searchCatalog.setOnClickListener {
            val query = b.catalogQuery.text.toString().trim()
            if (query.isBlank()) { b.catalogQuery.error = "Digite o nome de um anime"; return@setOnClickListener }
            search?.cancel()
            search = fragment.viewLifecycleOwner.lifecycleScope.launch {
                b.catalogStatus.text = "Buscando imagens…"
                b.catalogImages.removeAllViews()
                try {
                    val choices = resolver.search(query)
                    b.catalogStatus.text = if (choices.isEmpty()) "Nenhuma imagem disponível. Tente outro anime ou uma URL própria."
                        else "$query · selecione um retrato ou wallpaper"
                    choices.forEach { art ->
                        val label = art.label
                        val url = art.url
                        val button = ImageButton(context).apply {
                            contentDescription = if (art.isWallpaper) label else "Usar avatar de $label"
                            isFocusable = true
                            setBackgroundResource(R.drawable.bg_player_action_vertical)
                            setPadding(6, 6, 6, 6)
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                                width = 0
                                height = (110 * resources.displayMetrics.density).toInt()
                                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                                setMargins(4, 4, 4, 4)
                            }
                            setOnClickListener {
                                if (art.isWallpaper) b.backgroundUrl.setText(url) else b.avatarUrl.setText(url)
                                b.catalogStatus.text = "$label · selecionado"
                            }
                        }
                        b.catalogImages.addView(button)
                        Glide.with(fragment).load(url).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .error(R.drawable.ic_folder_24).into(button)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { b.catalogStatus.text = "Não foi possível buscar. Tente novamente ou use uma URL própria." }
            }
        }
        dialog.setOnShowListener {
            zechs.drive.stream.utils.FrostedWindow.apply(dialog.window!!)
            zechs.drive.stream.utils.TvFocusRing.install(dialog.window!!.decorView)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = b.profileName.text.toString().trim()
                if (name.isBlank()) { b.profileName.error = "Informe um nome"; b.profileName.requestFocus(); return@setOnClickListener }
                val avatar = b.avatarUrl.text.toString().trim().ifBlank { null }
                val background = b.backgroundUrl.text.toString().trim().ifBlank { null }
                for ((field, value) in listOf(b.avatarUrl to avatar, b.backgroundUrl to background)) {
                    if (!validImageUrl(value)) { field.error = "Use uma URL HTTPS válida"; field.requestFocus(); return@setOnClickListener }
                }
                val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        manager.saveProfile((profile ?: UserProfile(UUID.randomUUID().toString(), name, "avatar_anime"))
                            .copy(name = name, avatarUrl = avatar, backgroundUrl = background,
                                isAdmin = b.isAdmin.isChecked, isKids = b.isKids.isChecked))
                        dialog.dismiss()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { b.profileName.error = "Falha ao salvar. Tente novamente."; button.isEnabled = true }
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
