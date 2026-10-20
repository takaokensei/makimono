package zechs.drive.stream.ui.settings.delegates

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.settings.SettingsEvent
import zechs.drive.stream.ui.settings.SettingsUiState
import zechs.drive.stream.ui.settings.SettingsViewModel
import zechs.drive.stream.utils.SubtitleStyle
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.VideoPlayer

/**
 * Delegado responsável pela seção de Reprodução (Player padrão, legendas, switches de reprodução e vídeo).
 * Conforme Seção 4.10 do Plano v2.
 */
class PlaybackSettingsDelegate(
    private val context: Context,
    private val binding: FragmentSettingsBinding,
    private val viewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel
) {

    fun setup() {
        setupDefaultPlayerMenu()
        setupSubtitleAppearanceSetting()
        setupPlaybackExperience()
    }

    private fun setupDefaultPlayerMenu() {
        val players = listOf(
            context.getString(R.string.exoplayer),
            context.getString(R.string.mpv)
        )
        binding.settingDefaultPlayer.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(context.getString(R.string.default_player))
                .setSingleChoiceItems(
                    players.toTypedArray(),
                    mainViewModel.currentPlayerIndex.value
                ) { d, item ->
                    val player = when (item) {
                        VideoPlayer.EXO_PLAYER.value -> VideoPlayer.EXO_PLAYER
                        VideoPlayer.MPV.value -> VideoPlayer.MPV
                        else -> throw IllegalArgumentException("Unknown default player")
                    }
                    mainViewModel.setPlayer(player)
                    viewModel.onEvent(SettingsEvent.SetPlayer(player))
                    binding.tvPlayerCurrentValue.text = formatSelectedPlayerSummary(player)
                    d.dismiss()
                }
                .setNeutralButton(R.string.details) { _, _ ->
                    showRoutingRulesDialog()
                }
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                dialog.listView?.post {
                    dialog.listView?.requestFocus()
                }
            }
            dialog.show()
        }
    }

    private fun formatSelectedPlayerSummary(player: VideoPlayer): String {
        return when (player) {
            VideoPlayer.MPV -> context.getString(R.string.mpv)
            VideoPlayer.EXO_PLAYER -> "${context.getString(R.string.exoplayer)} (ASS/SSA → MPV)"
        }
    }

    private fun showRoutingRulesDialog() {
        val rulesText = zechs.drive.stream.playback.engine.EngineSelector
            .describeActiveRules()
            .joinToString(separator = "\n• ", prefix = "• ")
        val rulesDialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(R.string.default_player)
            .setMessage(rulesText)
            .setPositiveButton(R.string.ok, null)
            .create()
        rulesDialog.setOnShowListener {
            rulesDialog.window?.let { w -> TvFocusRing.install(w.decorView) }
            rulesDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.requestFocus()
        }
        rulesDialog.show()
    }

    private fun setupSubtitleAppearanceSetting() {
        binding.settingSubtitleSize.setOnClickListener {
            val initial = SubtitleStyle()
            zechs.drive.stream.ui.player.SubtitleStyleDialog.show(context, initial) { updated ->
                viewModel.onEvent(SettingsEvent.SaveSubtitleStyle(updated))
            }
        }
    }

    private fun setupPlaybackExperience() {
        binding.settingAutoSkipToggle.setOnClickListener {
            binding.switchAutoSkip.toggle()
        }
        binding.switchAutoSkip.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetAutoSkip(isChecked))
        }

        binding.settingGesturesToggle.setOnClickListener {
            binding.switchGestures.toggle()
        }
        binding.switchGestures.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetGestures(isChecked))
        }

        binding.settingAutoFrameRateToggle?.setOnClickListener {
            binding.switchAutoFrameRate?.toggle()
        }
        binding.switchAutoFrameRate?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetAutoFrameRate(isChecked))
        }

        binding.settingMatchDynamicRangeToggle?.setOnClickListener {
            binding.switchMatchDynamicRange?.toggle()
        }
        binding.switchMatchDynamicRange?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetMatchDynamicRange(isChecked))
        }

        binding.settingPredictiveBufferingToggle?.setOnClickListener {
            binding.switchPredictiveBuffering?.toggle()
        }
        binding.switchPredictiveBuffering?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetPredictiveBuffering(isChecked))
        }

        binding.settingSmartDownloadsToggle?.setOnClickListener {
            binding.switchSmartDownloads?.toggle()
        }
        binding.switchSmartDownloads?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetSmartDownloads(isChecked))
        }
    }

    fun render(state: SettingsUiState) {
        binding.tvPlayerCurrentValue.text = when (mainViewModel.currentPlayerIndex.value) {
            VideoPlayer.MPV.value -> context.getString(R.string.mpv)
            else -> context.getString(R.string.exoplayer)
        }

        if (state.subtitleSummary.isNotBlank()) {
            binding.tvSubtitleSizeValue.text = state.subtitleSummary
        }

        binding.switchAutoSkip.isChecked = state.isAutoSkipEnabled
        binding.switchGestures.isChecked = state.isGesturesEnabled
        binding.switchAutoFrameRate?.isChecked = state.isAutoFrameRateEnabled
        binding.switchMatchDynamicRange?.isChecked = state.isMatchDynamicRangeEnabled
        binding.switchPredictiveBuffering?.isChecked = state.isPredictiveBufferingEnabled
        binding.switchSmartDownloads?.isChecked = state.isSmartDownloadsEnabled
    }
}
