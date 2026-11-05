package zechs.drive.stream.ui.profile

import zechs.drive.stream.domain.profile.ProfileSetupPolicy

import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.ui.common.ModalFocusGuard
import zechs.drive.stream.ui.settings.DriveFolderPickerDialog
import zechs.drive.stream.ui.settings.LibraryRootFolderQuery
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.MediaImageLoader

/** Resumable profile setup; every folder shown comes from the authorized Drive API. */
internal class ProfileOnboardingDialog(
    private val fragment: Fragment, private val manager: ProfileManager,
    private val settings: AppSettings, private val drive: DriveRepository,
    private val onFinished: () -> Unit, private val onDismiss: () -> Unit
) {
    fun show(profile: UserProfile): Dialog {
        val context = fragment.requireContext()
        val owner = fragment.viewLifecycleOwner
        val scope = owner.lifecycleScope
        val tv = DeviceUi.isTenFootExperience(context)
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        fun color(id: Int) = ContextCompat.getColor(context, id)
        val anchor = fragment.requireView().findFocus() ?: fragment.requireView()
        val guard = ModalFocusGuard(anchor)
        val dialog = Dialog(context, R.style.ThemeOverlay_DriveStream_Dialog)
        val root = LinearLayout(context).apply {
            orientation = if (tv) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(color(R.color.obsidian_bg_base))
            setPadding(dp(if (tv) 48 else 20), dp(27), dp(if (tv) 48 else 20), dp(27))
        }
        if (tv) {
            val card = android.widget.FrameLayout(context).apply {
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                    }
                }
            }

            val backdropIv = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
                val artwork = profile.backgroundUrl?.takeIf(String::isNotBlank)
                if (artwork != null) MediaImageLoader.backdrop(this, artwork) else setImageResource(R.drawable.bg_profile_fantasy)
            }
            card.addView(backdropIv, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val scrim = View(context).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(color(R.color.obsidian_bg_base), android.graphics.Color.TRANSPARENT)
                )
            }
            card.addView(scrim, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply {
                gravity = Gravity.BOTTOM
            })

            val profileInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val avatarIv = ImageView(context).apply {
                contentDescription = profile.name
                val avatarSource: Any = profile.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: manager.getAvatarDrawableRes(profile.avatarResName)
                com.bumptech.glide.Glide.with(context)
                    .load(avatarSource)
                    .placeholder(R.drawable.avatar_caua)
                    .error(R.drawable.avatar_caua)
                    .circleCrop()
                    .into(this)
                val avatarRing = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color(R.color.obsidian_bg_surface))
                    setStroke(dp(3), color(R.color.obsidian_accent_primary))
                }
                background = avatarRing
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            profileInfo.addView(avatarIv, LinearLayout.LayoutParams(dp(96), dp(96)))

            val nameTv = TextView(context).apply {
                text = profile.name
                androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Makimono_TV_Title2)
                setTextColor(color(R.color.obsidian_text_primary))
                gravity = Gravity.CENTER_HORIZONTAL
            }
            profileInfo.addView(nameTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            })

            card.addView(profileInfo, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            })

            root.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.40f).apply { marginEnd = dp(36) })
        }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val viewport = ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(viewport, if (tv) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.60f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.setContentView(root)
        var busy = false
        var step = ProfileSetupPolicy.LANGUAGE
        lateinit var render: () -> Unit

        fun heading(text: String, style: Int) {
            content.addView(TextView(context).apply {
                this.text = text
                androidx.core.widget.TextViewCompat.setTextAppearance(this, style)
                setTextColor(color(R.color.obsidian_text_primary))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(16) })
        }
        fun submit(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            scope.launch {
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { Toast.makeText(context, R.string.profile_setup_save_error, Toast.LENGTH_SHORT).show() }
                finally { busy = false }
            }
        }
        fun button(label: String, action: () -> Unit) {
            val button = MaterialButton(context).apply {
                text = label
                isAllCaps = false
                maxLines = 2
                cornerRadius = dp(8)
                setTextColor(color(R.color.obsidian_text_primary))
                backgroundTintList = ColorStateList.valueOf(color(R.color.obsidian_bg_surface))
                strokeColor = ColorStateList.valueOf(color(R.color.obsidian_border_glass))
                strokeWidth = dp(1)
                setOnClickListener { action() }
            }
            FocusEngine.attach(button, FocusEngine.FocusKind.BUTTON) { _, focused ->
                button.strokeColor = ColorStateList.valueOf(color(if (focused) R.color.obsidian_accent_primary else R.color.obsidian_border_glass))
                button.strokeWidth = dp(if (focused) 3 else 1)
            }
            content.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(8) })
        }
        fun finish() = submit {
            settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.COMPLETE)
            dialog.dismiss()
            onFinished()
        }
        render = {
            scope.launch {
                step = settings.fetchProfileSetupStep(profile.id)
                if (step == ProfileSetupPolicy.COMPLETE) { dialog.dismiss(); onFinished(); return@launch }
                if (!dialog.isShowing) return@launch
                content.removeAllViews()
                viewport.scrollTo(0, 0)
                heading(context.getString(R.string.profile_setup_step, step, ProfileSetupPolicy.TOTAL_STEPS), R.style.TextAppearance_Makimono_TV_Body)
                val title = when (step) {
                    ProfileSetupPolicy.LANGUAGE -> R.string.profile_setup_language
                    ProfileSetupPolicy.AUDIO -> R.string.profile_setup_audio
                    ProfileSetupPolicy.SUBTITLES -> R.string.profile_setup_subtitles
                    else -> R.string.profile_setup_library
                }
                heading(context.getString(title), R.style.TextAppearance_Makimono_TV_Title1)
                if (step == ProfileSetupPolicy.LANGUAGE) {
                    heading(context.getString(R.string.profile_setup_language_hint), R.style.TextAppearance_Makimono_TV_Body)
                    button("Português (Brasil)") {
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.forLanguageTags("pt-BR")
                        )
                        submit {
                            settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.AUDIO)
                            render()
                        }
                    }
                    button("English") {
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.forLanguageTags("en")
                        )
                        submit {
                            settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.AUDIO)
                            render()
                        }
                    }
                } else if (step == ProfileSetupPolicy.LIBRARY) {
                    heading(context.getString(R.string.profile_setup_library_hint), R.style.TextAppearance_Makimono_TV_Body)
                    val current = manager.getActiveProfile()
                    current.libraryRootId?.let {
                        val name = current.libraryRootName ?: context.getString(R.string.my_drive)
                        button(context.getString(R.string.profile_setup_use_folder, name)) { finish() }
                    }
                    button(context.getString(R.string.profile_setup_choose_folder)) {
                        DriveFolderPickerDialog(context, scope, current.libraryRootName,
                            loadPage = { parent, token -> drive.getFiles(LibraryRootFolderQuery.forParent(parent), token, 100) },
                            focusAnchor = content.findFocus(),
                            onSelected = { id, name -> submit {
                                manager.setLibraryRoot(id ?: "root", name ?: context.getString(R.string.my_drive))
                                settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.COMPLETE)
                                dialog.dismiss(); onFinished()
                            } }, onDismiss = { if (dialog.isShowing) render() }).show()
                    }
                } else {
                    heading(context.getString(R.string.profile_setup_preferences_hint), R.style.TextAppearance_Makimono_TV_Body)
                    val choices = listOf(R.string.profile_setup_default to null,
                        R.string.profile_setup_japanese to "ja", R.string.profile_setup_portuguese to "pt-BR", R.string.profile_setup_english to "en")
                        .filter { step == ProfileSetupPolicy.AUDIO || it.second != "ja" }
                    for ((label, language) in choices) button(context.getString(label)) { submit {
                        if (step == ProfileSetupPolicy.AUDIO) settings.saveAudioLanguage(profile.id, language)
                        else settings.saveSubtitleLanguage(profile.id, language, false)
                        settings.saveProfileSetupStep(profile.id, step + 1); render()
                    } }
                    if (step == ProfileSetupPolicy.SUBTITLES) button(context.getString(R.string.profile_setup_subtitles_off)) { submit {
                        settings.saveSubtitleLanguage(profile.id, null, true)
                        settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.LIBRARY); render()
                    } }
                }
                if (step > ProfileSetupPolicy.LANGUAGE) button(context.getString(R.string.back)) { submit {
                    settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.previous(step)); render()
                } }
                content.post {
                    (0 until content.childCount).map(content::getChildAt).firstOrNull { it.isFocusable }?.requestFocus()
                }
            }
        }
        dialog.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && step > ProfileSetupPolicy.LANGUAGE) {
                submit { settings.saveProfileSetupStep(profile.id, ProfileSetupPolicy.previous(step)); render() }; true
            } else false
        }
        val observer = object : DefaultLifecycleObserver { override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() } }
        owner.lifecycle.addObserver(observer)
        dialog.setOnDismissListener {
            guard.restore(); owner.lifecycle.removeObserver(observer); onDismiss()
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        render()
        return dialog
    }
}
