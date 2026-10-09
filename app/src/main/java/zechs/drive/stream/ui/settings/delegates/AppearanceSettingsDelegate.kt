package zechs.drive.stream.ui.settings.delegates

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.databinding.ItemThemePreviewBinding
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.settings.SettingsEvent
import zechs.drive.stream.ui.settings.SettingsUiState
import zechs.drive.stream.ui.settings.SettingsViewModel
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.MotionPreference
import zechs.drive.stream.utils.TvFocusRing

/**
 * Delegado responsável pela seção de Aparência (Temas e Redução de Movimento).
 * Conforme Seção 4.10 do Plano v2.
 */
class AppearanceSettingsDelegate(
    private val context: Context,
    private val binding: FragmentSettingsBinding,
    private val viewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel,
    private val onReduceMotionChanged: (Boolean) -> Unit
) {

    fun setup() {
        setupThemeMenu()
        setupMotionPreference()
    }

    private fun setupThemeMenu() {
        binding.settingSelectTheme.setOnClickListener {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_theme_selector, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.llThemeContainer)

            val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setView(dialogView)
                .create()

            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                val targetIndex = mainViewModel.currentThemeIndex.coerceAtLeast(0)
                container.getChildAt(targetIndex)?.requestFocus()
            }

            AppTheme.entries.forEach { theme ->
                val itemBinding = ItemThemePreviewBinding.inflate(LayoutInflater.from(context), container, false)
                itemBinding.apply {
                    tvThemeName.text = theme.displayName
                    tvThemeSubtitle.text = theme.subtitle

                    swatchBase.background = ContextCompat.getDrawable(context, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.bgBaseHex))
                    }
                    swatchSurface.background = ContextCompat.getDrawable(context, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.bgSurfaceHex))
                    }
                    swatchAccent.background = ContextCompat.getDrawable(context, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.accentPrimaryHex))
                    }
                    swatchSecondary.background = ContextCompat.getDrawable(context, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.accentSecondaryHex))
                    }

                    ivSelected.isVisible = (mainViewModel.currentThemeIndex == theme.value)

                    cardTheme.setOnClickListener {
                        mainViewModel.setTheme(theme)
                        viewModel.onEvent(SettingsEvent.SetTheme(theme))
                        binding.tvThemeCurrentValue.text = theme.displayName
                        dialog.dismiss()
                    }
                }
                container.addView(itemBinding.root)
            }

            dialog.show()
        }
    }

    private fun setupMotionPreference() {
        binding.settingReduceMotion.setOnClickListener {
            val options = arrayOf(
                context.getString(R.string.motion_follows_device),
                context.getString(R.string.motion_reduced)
            )
            val current = viewModel.uiState.value.motionPreference
            val selected = if (current == MotionPreference.REDUCED) 1 else 0
            val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.reduce_motion)
                .setSingleChoiceItems(options, selected) { dialogInterface, index ->
                    val preference = if (index == 1) MotionPreference.REDUCED else MotionPreference.SYSTEM
                    viewModel.onEvent(SettingsEvent.SaveMotionPreference(preference))
                    dialogInterface.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> TvFocusRing.install(window.decorView) }
            }
            dialog.show()
        }
    }

    fun render(state: SettingsUiState) {
        val theme = AppTheme.fromValue(mainViewModel.currentThemeIndex)
        binding.tvThemeCurrentValue.text = theme.displayName

        binding.tvMotionPreferenceValue.text = when (state.motionPreference) {
            MotionPreference.REDUCED -> context.getString(R.string.motion_reduced)
            MotionPreference.SYSTEM -> context.getString(R.string.motion_follows_device)
        }
        val reduced = MotionPolicy.shouldReduceMotion(
            state.motionPreference,
            MotionPolicy.areSystemAnimationsEnabled()
        )
        onReduceMotionChanged(reduced)
    }
}
