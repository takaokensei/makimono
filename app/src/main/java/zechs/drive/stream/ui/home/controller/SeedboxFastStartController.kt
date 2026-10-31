package zechs.drive.stream.ui.home.controller

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.DialogSeedboxFastStartBinding
import zechs.drive.stream.databinding.LayoutFastStartHudBinding
import zechs.drive.stream.ui.home.HomeFragmentDirections
import zechs.drive.stream.ui.home.HomeViewModel
import zechs.drive.stream.ui.home.HomeViewModel.FastStartPhase
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.ext.navigateSafe

class SeedboxFastStartController(
    private val fragment: Fragment,
    private val hudView: View?,
    private val viewModel: HomeViewModel,
    private val mainViewModel: MainViewModel,
    private val navController: NavController
) {

    private val context: Context get() = fragment.requireContext()
    private var activeDialog: Dialog? = null
    private var dialogBinding: DialogSeedboxFastStartBinding? = null
    private var hudBinding: LayoutFastStartHudBinding? = null
    private var autoPlayTimer: CountDownTimer? = null

    init {
        val targetHud = hudView
            ?: fragment.view?.findViewById<View>(R.id.layoutFastStartHud)
            ?: fragment.view?.findViewById<View>(R.id.cardFastStartHud)

        targetHud?.let { v ->
            hudBinding = LayoutFastStartHudBinding.bind(v)
            FocusEngine.attach(v, FocusEngine.FocusKind.CARD)
            v.setOnClickListener {
                val current = viewModel.fastStartSession.value ?: return@setOnClickListener
                if (current.phase == FastStartPhase.READY && current.readyVideoFile != null) {
                    playEpisode(current.readyVideoFile)
                } else {
                    viewModel.restoreFastStartSession()
                }
            }
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fastStartSession.collect { session ->
                    handleSessionUpdate(session)
                }
            }
        }
    }

    private fun handleSessionUpdate(session: HomeViewModel.FastStartSession?) {
        if (session == null) {
            cancelAutoPlayTimer()
            dismissActiveDialog()
            hudBinding?.root?.isGone = true
            return
        }

        // Update floating HUD state
        updateHud(session)

        // Manage Dialog visibility
        if (session.isMinimized) {
            dismissActiveDialog()
        } else {
            showOrUpdateDialog(session)
        }
    }

    private fun updateHud(session: HomeViewModel.FastStartSession) {
        val hud = hudBinding ?: return
        hud.root.isVisible = session.isMinimized

        if (!session.isMinimized) return

        hud.tvHudTitle.text = session.animeTitle
        if (!session.posterUrl.isNullOrBlank()) {
            MediaImageLoader.poster(hud.ivHudPoster, session.posterUrl)
        }

        when (session.phase) {
            FastStartPhase.READY -> {
                hud.tvHudPercent.isGone = true
                hud.ivHudReadyIcon.isVisible = true
                hud.tvHudSubtitle.setText(R.string.seedbox_hud_ready)
                hud.progressHudLive.progress = 100
            }
            FastStartPhase.SYNCING_DRIVE -> {
                hud.tvHudPercent.isVisible = true
                hud.ivHudReadyIcon.isGone = true
                hud.tvHudPercent.text = "${session.progressPct}%"
                hud.tvHudSubtitle.setText(R.string.seedbox_hud_uploading)
                hud.progressHudLive.progress = session.progressPct
            }
            FastStartPhase.DOWNLOADING -> {
                hud.tvHudPercent.isVisible = true
                hud.ivHudReadyIcon.isGone = true
                hud.tvHudPercent.text = "${session.progressPct}%"
                val speed = session.speed ?: ""
                hud.tvHudSubtitle.text = context.getString(R.string.seedbox_hud_downloading, speed)
                hud.progressHudLive.progress = session.progressPct
            }
            FastStartPhase.SEARCHING -> {
                hud.tvHudPercent.isVisible = true
                hud.ivHudReadyIcon.isGone = true
                hud.tvHudPercent.text = "${session.progressPct}%"
                hud.tvHudSubtitle.setText(R.string.seedbox_stage_searching)
                hud.progressHudLive.progress = session.progressPct
            }
            FastStartPhase.INITIAL -> {
                hud.root.isGone = true
            }
            FastStartPhase.FAILED -> {
                hud.tvHudPercent.isGone = true
                hud.ivHudReadyIcon.isGone = true
                hud.tvHudSubtitle.text = session.errorMessage ?: context.getString(R.string.files_page_error_generic)
            }
        }
    }

    private fun showOrUpdateDialog(session: HomeViewModel.FastStartSession) {
        var binding = dialogBinding
        var dialog = activeDialog

        if (dialog == null || !dialog.isShowing || binding == null) {
            val inflater = LayoutInflater.from(context)
            binding = DialogSeedboxFastStartBinding.inflate(inflater)
            dialogBinding = binding

            dialog = Dialog(context, R.style.ThemeOverlay_DriveStream_Dialog).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(binding.root)
                window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                    )
                    decorView.let { TvFocusRing.install(it) }
                }
                setOnCancelListener {
                    viewModel.dismissFastStartSession()
                }
            }
            activeDialog = dialog

            setupDialogClicks(binding, session)
            dialog.show()
        }

        renderDialogContent(binding, session)
    }

    private fun setupDialogClicks(binding: DialogSeedboxFastStartBinding, initialSession: HomeViewModel.FastStartSession) {
        binding.btnCloseFastStart.setOnClickListener {
            viewModel.dismissFastStartSession()
        }

        binding.btnFastStartEp1.setOnClickListener {
            val curr = viewModel.fastStartSession.value ?: initialSession
            viewModel.startFastStartDownload(curr.animeTitle, ep1First = true, posterUrl = curr.posterUrl)
        }

        binding.btnFastStartFull.setOnClickListener {
            val curr = viewModel.fastStartSession.value ?: initialSession
            viewModel.startFastStartDownload(curr.animeTitle, ep1First = false, posterUrl = curr.posterUrl)
        }

        binding.btnFastStartMinimize.setOnClickListener {
            viewModel.minimizeFastStartSession()
        }

        binding.btnFastStartPlayNow.setOnClickListener {
            val curr = viewModel.fastStartSession.value ?: return@setOnClickListener
            cancelAutoPlayTimer()
            if (curr.readyVideoFile != null) {
                playEpisode(curr.readyVideoFile)
            } else if (curr.readyFolderId != null) {
                openSeriesDetail(curr.readyFolderId, curr.animeTitle, curr.posterUrl)
            }
        }

        FocusEngine.attach(binding.btnFastStartEp1, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(binding.btnFastStartFull, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(binding.btnFastStartMinimize, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(binding.btnFastStartPlayNow, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(binding.btnCloseFastStart, FocusEngine.FocusKind.BUTTON)
    }

    private fun renderDialogContent(binding: DialogSeedboxFastStartBinding, session: HomeViewModel.FastStartSession) {
        binding.tvFastStartTitle.text = session.animeTitle
        binding.tvFastStartEpCount.apply {
            isVisible = !session.totalEpisodes.isNullOrBlank()
            text = session.totalEpisodes
        }

        if (!session.posterUrl.isNullOrBlank()) {
            MediaImageLoader.poster(binding.ivFastStartPoster, session.posterUrl)
        }

        when (session.phase) {
            FastStartPhase.INITIAL -> {
                binding.layoutFastStartInitialActions.isVisible = true
                binding.layoutFastStartLiveCockpit.isGone = true
                binding.btnFastStartEp1.requestFocus()
            }
            FastStartPhase.SEARCHING,
            FastStartPhase.DOWNLOADING,
            FastStartPhase.SYNCING_DRIVE -> {
                binding.layoutFastStartInitialActions.isGone = true
                binding.layoutFastStartLiveCockpit.isVisible = true
                binding.btnFastStartPlayNow.isGone = true
                binding.btnFastStartMinimize.isVisible = true

                binding.tvFastStartPercent.text = "${session.progressPct}%"
                binding.tvFastStartStatusTitle.text = session.animeTitle
                binding.tvFastStartSpeedEta.text = session.speed ?: session.statusDetail ?: ""

                ObjectAnimator.ofInt(binding.progressFastStartLive, "progress", session.progressPct)
                    .setDuration(250)
                    .start()

                val stageRes = when (session.phase) {
                    FastStartPhase.SEARCHING -> R.string.seedbox_stage_searching
                    FastStartPhase.DOWNLOADING -> R.string.seedbox_stage_downloading
                    FastStartPhase.SYNCING_DRIVE -> R.string.seedbox_stage_syncing
                    else -> R.string.seedbox_stage_downloading
                }
                binding.tvFastStartStageDetail.setText(stageRes)
            }
            FastStartPhase.READY -> {
                binding.layoutFastStartInitialActions.isGone = true
                binding.layoutFastStartLiveCockpit.isVisible = true
                binding.btnFastStartMinimize.isVisible = true
                binding.btnFastStartPlayNow.isVisible = true

                binding.tvFastStartPercent.text = "100%"
                binding.tvFastStartStatusTitle.setText(R.string.seedbox_hud_ready)
                binding.tvFastStartSpeedEta.setText(R.string.seedbox_stage_ready)
                binding.progressFastStartLive.progress = 100
                binding.tvFastStartStageDetail.setText(R.string.seedbox_stage_ready)

                binding.btnFastStartPlayNow.requestFocus()

                // Start 3s countdown for auto-play
                startAutoPlayCountdown(session)
            }
            FastStartPhase.FAILED -> {
                binding.layoutFastStartInitialActions.isGone = true
                binding.layoutFastStartLiveCockpit.isVisible = true
                binding.btnFastStartPlayNow.isGone = true
                binding.btnFastStartMinimize.isVisible = true
                binding.tvFastStartPercent.text = "!"
                binding.tvFastStartStatusTitle.setText(R.string.files_page_error_generic)
                binding.tvFastStartSpeedEta.text = session.errorMessage ?: ""
                binding.tvFastStartStageDetail.text = session.errorMessage ?: ""
            }
        }
    }

    private fun startAutoPlayCountdown(session: HomeViewModel.FastStartSession) {
        if (autoPlayTimer != null) return
        autoPlayTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                dialogBinding?.tvFastStartPlayNowLabel?.text =
                    context.getString(R.string.seedbox_btn_play_now_countdown, seconds)
            }

            override fun onFinish() {
                dialogBinding?.tvFastStartPlayNowLabel?.setText(R.string.seedbox_btn_play_now)
                val curr = viewModel.fastStartSession.value ?: session
                if (curr.readyVideoFile != null) {
                    playEpisode(curr.readyVideoFile)
                } else if (curr.readyFolderId != null) {
                    openSeriesDetail(curr.readyFolderId, curr.animeTitle, curr.posterUrl)
                }
            }
        }.start()
    }

    private fun cancelAutoPlayTimer() {
        autoPlayTimer?.cancel()
        autoPlayTimer = null
    }

    private fun playEpisode(file: DriveFile) {
        dismissActiveDialog()
        viewModel.dismissFastStartSession()

        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                PlayerLauncher.launch(
                    context = context,
                    playerType = VideoPlayer.EXO_PLAYER,
                    fileId = file.id,
                    title = file.name,
                    thumbnailLink = file.posterUrl ?: file.thumbnailLink,
                    themeIndex = mainViewModel.currentThemeIndex,
                    startPosition = 0L
                )
            }
            VideoPlayer.MPV -> {
                viewModel.fetchToken(file.id, file.name, file.posterUrl ?: file.thumbnailLink)
            }
        }
    }

    private fun openSeriesDetail(folderId: String, animeTitle: String, posterUrl: String?) {
        dismissActiveDialog()
        viewModel.dismissFastStartSession()

        val action = HomeFragmentDirections.actionHomeFragmentToSeriesDetailFragment(
            name = animeTitle,
            folderId = folderId,
            posterUrl = posterUrl
        )
        navController.navigateSafe(action)
    }

    private fun dismissActiveDialog() {
        try {
            activeDialog?.dismiss()
        } catch (_: Exception) {}
        activeDialog = null
        dialogBinding = null
    }

    fun onDestroy() {
        cancelAutoPlayTimer()
        dismissActiveDialog()
    }
}
