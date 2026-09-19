package zechs.drive.stream.ui.player2

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.AudioManager.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import zechs.drive.stream.utils.PlaybackProgressPolicy
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.AutoTransition
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.view.MotionEvent
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.databinding.ActivityMpvBinding
import zechs.drive.stream.databinding.PlayerControlViewBinding
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.ui.player.PlayerViewModel
import zechs.drive.stream.ui.player.MalRatingDialog
import zechs.drive.stream.ui.player.PlayerGestureHelper
import zechs.drive.stream.ui.player.PlayerGestureCallback
import zechs.drive.stream.ui.player.PlayerGlassMenuDialog
import zechs.drive.stream.ui.player.GlassMenuItem
import zechs.drive.stream.ui.player.PlayerEpisodeDrawerDialog
import zechs.drive.stream.utils.OnlineSubtitleManager
import zechs.drive.stream.utils.OnlineSubtitle
import zechs.drive.stream.utils.SavedSubtitle
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.MatroskaChapterParser
import zechs.drive.stream.utils.util.Constants.Companion.DRIVE_API
import zechs.drive.stream.utils.util.Orientation
import zechs.drive.stream.utils.util.getNextOrientation
import zechs.drive.stream.utils.util.setOrientation
import zechs.mpv.MPVLib
import zechs.mpv.MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED
import zechs.mpv.MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART
import zechs.mpv.MPVView
import zechs.mpv.utils.Utils
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class MPVActivity : AppCompatActivity(), MPVLib.EventObserver {

    companion object {
        const val TAG = "MPVActivity"

        // fraction to which audio volume is ducked on loss of audio focus
        private const val AUDIO_FOCUS_DUCKING = 0.5f
        private const val SKIP_DURATION = 10 // in seconds
    }

    data class ParsedChapter(
        val index: Int,
        val title: String,
        val startTimeSeconds: Double,
        val endTimeSeconds: Double,
        val type: ChapterType
    )

    enum class ChapterType {
        RECAP,
        OPENING,
        ENDING,
        OTHER
    }

    private lateinit var audioManager: AudioManager
    private var audioFocusRestore: () -> Unit = {}

    // View-binding
    private lateinit var binding: ActivityMpvBinding
    private lateinit var player: MPVView
    private lateinit var controller: PlayerControlViewBinding

    // ViewModel
    private val viewModel by viewModels<PlayerViewModel>()

    @Inject
    lateinit var malRepository: dagger.Lazy<MalRepository>

    @Inject
    lateinit var malSessionManager: dagger.Lazy<MalSessionManager>

    @Inject
    lateinit var aniSkipRepository: dagger.Lazy<AniSkipRepository>

    @Inject
    lateinit var onlineSubtitleManager: dagger.Lazy<OnlineSubtitleManager>

    @Inject
    lateinit var appSettings: dagger.Lazy<AppSettings>

    @Inject
    lateinit var tenraiService: dagger.Lazy<zechs.drive.stream.data.remote.TenraiAnimeService>

    // States
    private var activityIsForeground = true
    private var userIsOperatingSeekbar = false
    private var controlsLocked = false
    private var hasAutoSelectedMpvTracks = false
    private var parsedChapters = listOf<ParsedChapter>()
    private var activeSkipChapter: ParsedChapter? = null
    private lateinit var gestureHelper: PlayerGestureHelper
    private var hasAutoSkippedCurrentInterval = false

    // Playlist & Next Episode Auto-Play
    private var currentFileId: String = ""
    private var currentTitle: String = ""
    private var currentThumbnailLink: String? = null
    private var currentAccessToken: String = ""
    private var playlist = mutableListOf<PlaylistItem>()
    private var nextEpisode: PlaylistItem? = null
    private var prevEpisode: PlaylistItem? = null
    private var nextEpisodeCanceled = false
    private var isNextEpisodeCardShowing = false
    private var countdownJob: Job? = null
    private var folderSubtitles = mutableListOf<SubtitleItem>()
    private val addedSubtitleFileIds = mutableSetOf<String>()
    private var isFileLoaded = false
    private val pendingSubtitles = mutableListOf<Pair<java.io.File, SubtitleItem>>()
    private var currentSubtitleDelayMs = 0L
    private var currentAudioDelayMs = 0L

    // MAL Scrobble & Rating State
    private var currentMalAnime: MalAnimeNode? = null
    private var currentEpNumber: Int = 1
    private var hasScrobbledThisEp: Boolean = false
    private var hasPromptedRating: Boolean = false

    // Configs
    private var onLoadCommands = mutableListOf<Array<String>>()
    private val speeds = arrayOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
    private var orientation = Orientation.LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeValue = intent.getIntExtra("theme", 0)
        val themeRes = when (zechs.drive.stream.utils.AppTheme.fromValue(themeValue)) {
            zechs.drive.stream.utils.AppTheme.TOKYO_NIGHT -> R.style.Theme_Fullscreen_TokyoNight
            zechs.drive.stream.utils.AppTheme.DRACULA -> R.style.Theme_Fullscreen_Dracula
            zechs.drive.stream.utils.AppTheme.NORD -> R.style.Theme_Fullscreen_Nord
            zechs.drive.stream.utils.AppTheme.CATPPUCCIN_MOCHA -> R.style.Theme_Fullscreen_CatppuccinMocha
            zechs.drive.stream.utils.AppTheme.KODI_ESTUARY -> R.style.Theme_Fullscreen_KodiEstuary
        }
        setTheme(themeRes)
        super.onCreate(savedInstanceState)

        Utils.copyAssets(this)

        binding = ActivityMpvBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        player = binding.player
        controller = binding.controller

        controller
            .playerToolbar
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        controller
            .btnBack
            .setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        currentFileId = intent.getStringExtra("fileId") ?: ""
        currentTitle = intent.getStringExtra("title") ?: ""
        currentThumbnailLink = intent.getStringExtra("thumbnailLink")
        currentAccessToken = intent.getStringExtra("accessToken") ?: ""

        @Suppress("DEPRECATION")
        val rawPlaylist = intent.getSerializableExtra("playlist") as? ArrayList<PlaylistItem>
        if (rawPlaylist != null && rawPlaylist.isNotEmpty()) {
            playlist = rawPlaylist.toMutableList()
            updateNextEpisode()
        } else if (currentFileId.isNotEmpty()) {
            viewModel.fetchSiblings(currentFileId)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playlistChannel.consumeAsFlow().collect { list ->
                    if (playlist.isEmpty() && list.isNotEmpty()) {
                        playlist = list.toMutableList()
                        updateNextEpisode()
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        val rawSubtitles = intent.getSerializableExtra("subtitles") as? ArrayList<SubtitleItem>
        if (rawSubtitles != null && rawSubtitles.isNotEmpty()) {
            folderSubtitles = rawSubtitles.toMutableList()
        } else if (currentFileId.isNotEmpty()) {
            viewModel.fetchSubtitles(currentFileId)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.subtitlesChannel.consumeAsFlow().collect { list ->
                    if (list.isNotEmpty()) {
                        folderSubtitles = list.toMutableList()
                        loadMatchingExternalSubtitles()
                    }
                }
            }
        }

        player.initialize(filesDir.path)
        player.addObserver(this)

        // Enforce PotPlayer subtitle typography & fixed 96% bottom positioning
        MPVLib.setPropertyInt("sub-pos", 96)
        MPVLib.setPropertyString("sub-ass-override", "force")
        MPVLib.setPropertyString("sub-font", "sans-serif")
        MPVLib.setPropertyString("sub-bold", "yes")
        MPVLib.setPropertyString("sub-color", "#FFFFFFFF")
        MPVLib.setPropertyString("sub-border-color", "#FF000000")
        MPVLib.setPropertyDouble("sub-border-size", 3.2)
        MPVLib.setPropertyDouble("sub-shadow-offset", 0.0)
        MPVLib.setPropertyString("sub-back-color", "#00000000")

        lifecycleScope.launch {
            try {
                val savedSp = appSettings.get().fetchSubtitleSize()
                if (savedSp > 0f) {
                    val mpvSize = when {
                        savedSp <= 16f -> 38
                        savedSp <= 20f -> 48
                        savedSp <= 24f -> 58
                        else -> 68
                    }
                    MPVLib.setPropertyInt("sub-font-size", mpvSize)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading MPV subtitle size", e)
            }
        }

        playMedia()

        audioManager = getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager

        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            audioFocusChangeListener,
            STREAM_MUSIC,
            AUDIOFOCUS_GAIN
        ).also {
            if (it != AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Audio focus not granted")
                onLoadCommands.add(arrayOf("set", "pause", "yes"))
            }
        }

        // setVolumeControlStream
        volumeControlStream = STREAM_MUSIC


        controller.apply {
            // setup controller view for mpv
            exoProgress.isVisible = false
            progressBar.isVisible = true

            // progress bar
            progressBar.setOnSeekBarChangeListener(seekBarChangeListener)

            // init onClick listeners
            btnPlayPause.setOnClickListener { player.cyclePause() }
            btnPrevEp.setOnClickListener { playPrevEpisodeDirectly() }
            btnNextEp.setOnClickListener { playNextEpisodeDirectly() }
            exoFfwd.setOnClickListener { skipForward() }
            exoRew.setOnClickListener { rewindBackward() }
            btnSkipIntro.setOnClickListener { performSkipIntroOrCredits() }
            btnSkipIntroBack.setOnClickListener { skipRelative(-90) }
            btnAudio.setOnClickListener { pickAudio() }
            btnSubtitle.setOnClickListener { pickSub() }
            btnEpisodes.setOnClickListener { showEpisodesDrawer() }
            btnChapter.setOnClickListener { pickChapter() }
            btnSpeed.setOnClickListener { pickSpeed() }
            btnResize.setOnClickListener { player.cycleScale() }

            val isTvDevice = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            btnRotate.visibility = if (isTvDevice) View.GONE else View.VISIBLE

            btnRotate.setOnClickListener {
                orientation = getNextOrientation(orientation)
                Log.d(TAG, "orientation=${orientation}")
                setOrientation(this@MPVActivity, orientation)
            }

            btnLock.setOnClickListener {
                controlsLocked = true
                handleLockingControls()
            }

            btnUnlock.setOnClickListener {
                controlsLocked = false
                handleLockingControls()
            }

        }

        gestureHelper = PlayerGestureHelper(
            activity = this,
            hudBinding = binding.gestureHud,
            callback = object : PlayerGestureCallback {
                override fun onToggleControls() {
                    if (controller.root.isVisible) {
                        hideControls()
                    } else {
                        showControlsWithFocus()
                    }
                }

                override fun onSeekRelative(deltaMs: Long) {
                    skipRelative((deltaMs / 1000).toInt())
                }

                override fun onSeekTo(positionMs: Long) {
                    val posSec = (positionMs / 1000).toInt()
                    player.timePos = posSec
                }

                override fun getCurrentPosition(): Long = ((player.timePos ?: 0) * 1000L).coerceAtLeast(0L)
                override fun getDuration(): Long = ((player.duration ?: 0) * 1000L).coerceAtLeast(0L)

                override fun onTogglePlayPause() {
                    player.cyclePause()
                }

                override fun onSetSpeed(speed: Float) {
                    MPVLib.setPropertyDouble("speed", speed.toDouble())
                }

                override fun isControlsLocked(): Boolean = controlsLocked
                override fun isControllerVisible(): Boolean = controller.root.isVisible
                override fun getTouchIgnoredViews(): List<View> = listOf(
                    controller.playerToolbar,
                    controller.titleBlock,
                    controller.controlsScrollView,
                    controller.linearLayout2,
                    controller.mainControls,
                    binding.netflixSkipRow,
                    binding.nextEpisodeCard.root
                )
            }
        )

        binding.btnNetflixSkip.setOnClickListener {
            performSkipIntroOrCredits()
        }

        // hide/show controller
        player.setOnClickListener {
            if (!malSessionManager.get().isGesturesEnabled()) {
                if (!controller.root.isVisible) {
                    showControlsWithFocus()
                } else {
                    hideControls()
                }
            }
        }

        updateOrientation(resources.configuration)

    }

    private fun updateNextEpisode() {
        if (playlist.isEmpty()) {
            nextEpisode = null
            prevEpisode = null
            if (::controller.isInitialized) {
                controller.btnPrevEp.isEnabled = false
                controller.btnPrevEp.alpha = 0.35f
                controller.btnNextEp.isEnabled = false
                controller.btnNextEp.alpha = 0.35f
            }
            return
        }
        val currentIndex = playlist.indexOfFirst { it.fileId == currentFileId }
        nextEpisode = if (currentIndex != -1 && currentIndex + 1 < playlist.size) {
            playlist[currentIndex + 1]
        } else null
        prevEpisode = if (currentIndex > 0) {
            playlist[currentIndex - 1]
        } else null
        if (::controller.isInitialized) {
            controller.btnPrevEp.isEnabled = prevEpisode != null
            controller.btnPrevEp.alpha = if (prevEpisode != null) 1.0f else 0.35f
            controller.btnNextEp.isEnabled = nextEpisode != null
            controller.btnNextEp.alpha = if (nextEpisode != null) 1.0f else 0.35f
        }
        Log.d(TAG, "updateNextEpisode: currentIndex=$currentIndex, prevEpisode=${prevEpisode?.title}, nextEpisode=${nextEpisode?.title}")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (malSessionManager.get().isGesturesEnabled() && ::gestureHelper.isInitialized && gestureHelper.onTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (binding.nextEpisodeCard.root.isVisible) {
                        playNextEpisodeDirectly()
                        return true
                    }
                    if (binding.netflixSkipRow.isVisible) {
                        performSkipIntroOrCredits()
                        return true
                    }
                    if (!controller.root.isVisible) {
                        showControlsWithFocus()
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!controller.root.isVisible) {
                        rewindBackward()
                        configSnackbar("<< -10s", 500)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!controller.root.isVisible) {
                        skipForward()
                        configSnackbar("+10s >>", 500)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!controller.root.isVisible) {
                        showControlsWithFocus()
                        return true
                    }
                }

                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    player.cyclePause()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    player.paused = false
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    player.paused = true
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_PAGE_DOWN -> {
                    skipRelative(90)
                    configSnackbar("⏩ +90s Pular Abertura", 750)
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    playNextEpisodeDirectly()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    playPrevEpisodeDirectly()
                    return true
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (binding.nextEpisodeCard.root.isVisible) {
                        dismissNextEpisodeCard()
                        return true
                    }
                    if (controller.root.isVisible) {
                        hideControls()
                        return true
                    }
                    if (binding.netflixSkipRow.isVisible) {
                        binding.netflixSkipRow.visibility = View.GONE
                        return true
                    }
                }

                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_PAGE_UP -> {
                    skipRelative(-90)
                    configSnackbar("⏪ -90s Voltar", 750)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (binding.nextEpisodeCard.root.isVisible) {
            dismissNextEpisodeCard()
            return
        }
        if (controller.root.isVisible) {
            hideControls()
            return
        }
        if (binding.netflixSkipRow.isVisible) {
            binding.netflixSkipRow.visibility = View.GONE
            return
        }
        super.onBackPressed()
    }

    private var isHidingControls = false

    private fun showControlsWithFocus() {
        if (controller.root.isVisible) return
        val root = controller.root
        val density = resources.displayMetrics.density
        val animDuration = 220L

        controller.titleBlock.translationY = -25f * density
        controller.titleBlock.alpha = 0f
        controller.controlsScrollView.translationY = 40f * density
        controller.controlsScrollView.alpha = 0f
        controller.mainControls.alpha = 0f
        controller.topScrim.alpha = 0f

        root.isVisible = true

        controller.titleBlock.animate().translationY(0f).alpha(1f).setDuration(animDuration).start()
        controller.controlsScrollView.animate().translationY(0f).alpha(1f).setDuration(animDuration).start()
        controller.mainControls.animate().alpha(1f).setDuration(animDuration).start()
        controller.topScrim.animate().alpha(1f).setDuration(animDuration).start()

        if (binding.nextEpisodeCard.root.isVisible) {
            binding.nextEpisodeCard.root.animate().translationY(-84f * density).setDuration(animDuration).start()
        }
        handleLockingControls()
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
        controller.btnPlayPause.post {
            controller.btnPlayPause.requestFocus()
        }
    }

    private fun hideControls() {
        if (!controller.root.isVisible || isHidingControls) return
        isHidingControls = true
        val density = resources.displayMetrics.density
        val animDuration = 220L

        controller.titleBlock.animate().translationY(-30f * density).alpha(0f).setDuration(animDuration).start()
        controller.controlsScrollView.animate().translationY(50f * density).alpha(0f).setDuration(animDuration).start()
        controller.mainControls.animate().alpha(0f).setDuration(animDuration).start()
        controller.skipIntroRow.animate().alpha(0f).setDuration(animDuration).start()
        controller.topScrim.animate().alpha(0f).setDuration(animDuration).withEndAction {
            isHidingControls = false
            controller.root.isVisible = false
            controller.titleBlock.translationY = 0f
            controller.titleBlock.alpha = 1f
            controller.controlsScrollView.translationY = 0f
            controller.controlsScrollView.alpha = 1f
            controller.mainControls.alpha = 1f
            controller.skipIntroRow.alpha = 1f
            controller.topScrim.alpha = 1f
        }.start()

        if (binding.nextEpisodeCard.root.isVisible) {
            binding.nextEpisodeCard.root.animate().translationY(0f).setDuration(animDuration).start()
        }
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
    }

    private fun mergeAniSkipChapters(aniSkipChapters: List<MatroskaChapterParser.ParsedChapter>) {
        if (aniSkipChapters.isEmpty()) return
        val existing = parsedChapters.toMutableList()
        val hasMkvOp = existing.any { it.type == ChapterType.OPENING }
        val hasMkvEd = existing.any { it.type == ChapterType.ENDING }

        for (aniCh in aniSkipChapters) {
            val mpvType = when (aniCh.type) {
                MatroskaChapterParser.ChapterType.OPENING -> ChapterType.OPENING
                MatroskaChapterParser.ChapterType.ENDING -> ChapterType.ENDING
                MatroskaChapterParser.ChapterType.RECAP -> ChapterType.RECAP
                else -> ChapterType.OTHER
            }
            if (mpvType == ChapterType.OPENING && !hasMkvOp) {
                existing.add(ParsedChapter(
                    index = existing.size,
                    title = aniCh.title,
                    startTimeSeconds = aniCh.startTimeMs / 1000.0,
                    endTimeSeconds = aniCh.endTimeMs / 1000.0,
                    type = mpvType
                ))
            } else if (mpvType == ChapterType.ENDING && !hasMkvEd) {
                existing.add(ParsedChapter(
                    index = existing.size,
                    title = aniCh.title,
                    startTimeSeconds = aniCh.startTimeMs / 1000.0,
                    endTimeSeconds = aniCh.endTimeMs / 1000.0,
                    type = mpvType
                ))
            } else if (mpvType == ChapterType.RECAP && !existing.any { it.type == ChapterType.RECAP }) {
                existing.add(ParsedChapter(
                    index = existing.size,
                    title = aniCh.title,
                    startTimeSeconds = aniCh.startTimeMs / 1000.0,
                    endTimeSeconds = aniCh.endTimeMs / 1000.0,
                    type = mpvType
                ))
            }
        }
        parsedChapters = existing.sortedBy { it.startTimeSeconds }
        Log.d(TAG, "[MPV Chapters] Updated parsedChapters with AniSkip: ${parsedChapters.size} total")
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
    }

    private fun checkChapterAutoSkip(currentTimeSeconds: Double) {
        val specialChapter = parsedChapters.firstOrNull { chapter ->
            chapter.type != ChapterType.OTHER &&
                    currentTimeSeconds >= chapter.startTimeSeconds &&
                    currentTimeSeconds < chapter.endTimeSeconds
        }

        activeSkipChapter = specialChapter

        if (specialChapter != null) {
            val isOpOrRecap = specialChapter.type == ChapterType.OPENING || specialChapter.type == ChapterType.RECAP
            val isAutoSkip = malSessionManager.get().isAutoSkipEnabled()
            if (isAutoSkip && isOpOrRecap && !hasAutoSkippedCurrentInterval) {
                if (currentTimeSeconds in specialChapter.startTimeSeconds..(specialChapter.startTimeSeconds + 3.5)) {
                    hasAutoSkippedCurrentInterval = true
                    MPVLib.command(arrayOf("seek", specialChapter.endTimeSeconds.toString(), "absolute"))
                    gestureHelper.showNotification("⏩ Abertura pulada automaticamente (AniSkip)")
                    return
                }
            }

            val skipLabel = when (specialChapter.type) {
                ChapterType.RECAP -> "Pular Recap"
                ChapterType.OPENING -> "Pular Abertura"
                ChapterType.ENDING -> "Pular Créditos"
                else -> "Pular"
            }

            binding.btnNetflixSkip.text = skipLabel
            controller.btnSkipIntro.text = skipLabel

            // If controls are hidden, show floating Netflix pill
            if (!controller.root.isVisible) {
                if (binding.netflixSkipRow.visibility != View.VISIBLE) {
                    binding.netflixSkipRow.alpha = 0f
                    binding.netflixSkipRow.visibility = View.VISIBLE
                    binding.netflixSkipRow.animate().alpha(1f).setDuration(250L).start()
                }
            } else {
                binding.netflixSkipRow.visibility = View.GONE
            }

            // Always ensure controller's own skipIntroRow is visible when controls are open
            if (controller.root.isVisible && !controller.skipIntroRow.isVisible && !controlsLocked) {
                controller.skipIntroRow.alpha = 0f
                controller.skipIntroRow.visibility = View.VISIBLE
                controller.skipIntroRow.animate().alpha(1f).setDuration(200L).start()
            }
        } else {
            hasAutoSkippedCurrentInterval = false
            // Natural playback crossed the chapter boundary: hide Netflix floating pill
            if (binding.netflixSkipRow.visibility == View.VISIBLE) {
                binding.netflixSkipRow.animate().alpha(0f).setDuration(200L).withEndAction {
                    binding.netflixSkipRow.visibility = View.GONE
                }.start()
            }

            // Fallback: If no recognized chapters, use default time-based skip button (0 to 140s) inside controller
            val hasRecognizedChapters = parsedChapters.any { it.type != ChapterType.OTHER }
            if (!hasRecognizedChapters) {
                updateIntroButtonVisibility(currentTimeSeconds.toLong())
            } else if (controller.skipIntroRow.isVisible) {
                controller.skipIntroRow.animate().alpha(0f).setDuration(200L).withEndAction {
                    controller.skipIntroRow.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun performSkipIntroOrCredits() {
        val chapter = activeSkipChapter
        if (chapter != null) {
            Log.d(TAG, "[Chapters] Skipping '${chapter.title}' to ${chapter.endTimeSeconds}s")
            MPVLib.command(arrayOf("seek", chapter.endTimeSeconds.toString(), "absolute"))
            val snackText = when (chapter.type) {
                ChapterType.RECAP -> "⏩ Recap pulado"
                ChapterType.OPENING -> "⏩ Abertura pulada"
                ChapterType.ENDING -> "⏩ Créditos pulados"
                else -> "⏩ Capítulo pulado"
            }
            configSnackbar(snackText)
        } else {
            // Fallback: standard +90s
            skipRelative(90)
            configSnackbar("⏩ +90s Pular Abertura")
        }
        binding.netflixSkipRow.visibility = View.GONE
        controller.skipIntroRow.visibility = View.GONE
    }

    private fun updateChapters() {
        val rawChapters = player.loadChapters()
        if (rawChapters.isEmpty()) {
            parsedChapters = emptyList()
            Log.d(TAG, "[Chapters] No chapters found for this file.")
            return
        }

        val sortedRaw = rawChapters.sortedBy { it.time }
        val duration = (player.duration ?: 0).toDouble()
        val list = mutableListOf<ParsedChapter>()

        for (i in sortedRaw.indices) {
            val raw = sortedRaw[i]
            val startTime = raw.time
            val nextDistinctStart = sortedRaw.drop(i + 1).firstOrNull { it.time > startTime }?.time
            val endTime = if (nextDistinctStart != null) {
                nextDistinctStart
            } else if (duration > startTime) {
                duration
            } else {
                startTime + 90.0
            }

            val rawTitle = raw.title ?: ""
            val cleanTitle = java.text.Normalizer.normalize(rawTitle, java.text.Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .trim()
                .lowercase()

            val isEndMarker = cleanTitle.endsWith("end") || cleanTitle.endsWith("fim") ||
                    cleanTitle.endsWith("stop") || cleanTitle.contains("recap end") ||
                    cleanTitle.contains("credits end") || cleanTitle.contains("op end") ||
                    cleanTitle.contains("ed end") || cleanTitle.contains("preview end") ||
                    cleanTitle.endsWith("(end)") || cleanTitle.endsWith("[end]")

            val type = if (isEndMarker) {
                ChapterType.OTHER
            } else when {
                cleanTitle.contains("recap") || cleanTitle.contains("resumo") ||
                cleanTitle.contains("previously") || cleanTitle.contains("anteriormente") ->
                    ChapterType.RECAP
                cleanTitle == "op" || cleanTitle.contains("opening") || cleanTitle.contains("intro") ||
                cleanTitle.contains("abertura") || cleanTitle.matches(Regex(".*\\b(op|ncop)\\d*\\b.*")) ->
                    ChapterType.OPENING
                cleanTitle == "ed" || cleanTitle.contains("ending") || cleanTitle.contains("credits") ||
                cleanTitle.contains("outro") || cleanTitle.contains("encerramento") ||
                cleanTitle.contains("credito") || cleanTitle.matches(Regex(".*\\b(ed|nced)\\d*\\b.*")) ->
                    ChapterType.ENDING
                else -> ChapterType.OTHER
            }

            list.add(
                ParsedChapter(
                    index = raw.index,
                    title = rawTitle.ifBlank { "Capítulo ${raw.index + 1}" },
                    startTimeSeconds = startTime,
                    endTimeSeconds = endTime,
                    type = type
                )
            )
        }

        parsedChapters = list
        Log.d(TAG, "[Chapters] Parsed ${parsedChapters.size} chapters successfully:")
        parsedChapters.forEach {
            Log.d(TAG, "[Chapters] #${it.index} '${it.title}' (${it.startTimeSeconds}s -> ${it.endTimeSeconds}s, type=${it.type})")
        }
    }

    private val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser || controlsLocked)
                return
            player.timePos = progress
            updatePlaybackPos(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = false
        }
    }
    private val audioFocusChangeListener = OnAudioFocusChangeListener { type ->
        Log.v(TAG, "Audio focus changed: $type")
        when (type) {
            AUDIOFOCUS_LOSS,
            AUDIOFOCUS_LOSS_TRANSIENT -> {
                // loss can occur in addition to ducking, so remember the old callback
                val oldRestore = audioFocusRestore
                val wasPlayerPaused = player.paused ?: false
                player.paused = true
                audioFocusRestore = {
                    oldRestore()
                    if (!wasPlayerPaused) player.paused = false
                }
            }
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
                audioFocusRestore = {
                    val inv = 1f / AUDIO_FOCUS_DUCKING
                    MPVLib.command(arrayOf("multiply", "volume", inv.toString()))
                }
            }
            AUDIOFOCUS_GAIN -> {
                audioFocusRestore()
                audioFocusRestore = {}
            }
        }
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        playMedia()
    }

    private fun playMedia() {
        hasScrobbledThisEp = false
        hasPromptedRating = false
        currentMalAnime = null

        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") }
        val title = currentTitle.ifBlank { intent.getStringExtra("title") }
        val accessToken = currentAccessToken.ifBlank { intent.getStringExtra("accessToken") }

        if (fileId == null || accessToken == null) {
            Log.d(TAG, "FileId & AccessToken both are required. exiting...")
            finish()
            return
        }

        val parsed = EpisodeParser.parse(title ?: "")
        currentEpNumber = parsed.episode?.toInt() ?: 1

        val searchTitle = parsed.showTitle.ifBlank { title ?: "" }
        if (searchTitle.isNotBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val matched = malRepository.get().matchAnime(searchTitle)
                    if (matched != null) {
                        currentMalAnime = matched
                        Log.d(TAG, "MAL matched anime: '${matched.title}' (ID: ${matched.id}, total eps: ${matched.numEpisodes})")

                        // Query AniSkip
                        val aniSkipChapters = aniSkipRepository.get().getSkipChapters(
                            malId = matched.id,
                            episodeNumber = currentEpNumber,
                            episodeLength = 0.0
                        )
                        if (aniSkipChapters.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                mergeAniSkipChapters(aniSkipChapters)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to match anime on MAL / AniSkip", e)
                }
            }
        }

        Log.d(TAG, "MPVActivity(fileId=$fileId, title=$title, accessToken=${accessToken?.let { "len=${it.length}" } ?: "null"})")

        viewModel.getWatch(fileId)

        controller.playerToolbar.title = title
        controller.tvPlayerTitle.text = parsed.showTitle.ifBlank { parsed.cleanTitle.ifBlank { title ?: "" } }
        val epText = if (currentEpNumber > 0) "Episódio ${currentEpNumber.toString().padStart(2, '0')}" else ""
        controller.tvPlayerMeta.text = epText

        val playUri = getStreamUrl(fileId)
        hasAutoSelectedMpvTracks = false
        isFileLoaded = false
        pendingSubtitles.clear()
        MPVLib.setOptionString(
            "http-header-fields",
            "Authorization: Bearer $accessToken"
        )
        MPVLib.command(arrayOf("loadfile", playUri))
        player.play(playUri)

        // Load external Drive subtitles (.ass, .srt, etc.)
        if (folderSubtitles.isNotEmpty()) {
            loadMatchingExternalSubtitles()
        } else {
            viewModel.fetchSubtitles(fileId)
        }

        val explicitStart = intent.getLongExtra("startPosition", -1L)
        if (explicitStart >= 0L) {
            if (explicitStart > 0L) {
                resumeVideo(explicitStart)
            }
        } else {
            viewModel.getWatchPosition(fileId) { startPos ->
                if (startPos > 5_000L) {
                    resumeVideo(startPos)
                }
            }
        }

    }

    private fun loadMatchingExternalSubtitles() {
        val videoTitle = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }
        val matching = folderSubtitles.filter { it.matchesVideo(videoTitle) }
        val toLoad = matching

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Load permanently saved online subtitles from internal storage
            val savedSubs = onlineSubtitleManager.get().getSavedSubtitles(videoTitle)
            savedSubs.forEach { saved ->
                val virtualId = "saved_${saved.file.nameWithoutExtension}"
                if (!addedSubtitleFileIds.contains(virtualId)) {
                    val virtualSub = SubtitleItem(
                        id = virtualId,
                        name = "💾 [Salva] ${saved.langName}",
                        languageLabel = saved.langName,
                        languageCode = saved.lang
                    )
                    withContext(Dispatchers.Main) {
                        addSubtitleToMpv(saved.file, virtualSub)
                    }
                }
            }

            // 2. Load matching Drive subtitles
            toLoad.forEach { sub ->
                if (!addedSubtitleFileIds.contains(sub.id)) {
                    val cached = viewModel.downloadSubtitle(sub, cacheDir)
                    if (cached != null && cached.exists()) {
                        withContext(Dispatchers.Main) {
                            addSubtitleToMpv(cached, sub)
                        }
                    }
                }
            }
        }
    }

    private fun addSubtitleToMpv(cached: java.io.File, sub: SubtitleItem) {
        if (!isFileLoaded) {
            pendingSubtitles.add(Pair(cached, sub))
            return
        }
        if (addedSubtitleFileIds.contains(sub.id)) return
        addedSubtitleFileIds.add(sub.id)

        val isPortuguese = sub.languageCode == "por" || sub.name.lowercase().contains(".por.") || sub.name.lowercase().contains("pt")
        val selectMode = if (isPortuguese) "select" else "auto"
        val trackTitle = if (sub.id.startsWith("saved_")) sub.name else "★ [Drive] ${sub.languageLabel}"
        Log.d(TAG, "Adding external subtitle to MPV: ${cached.absolutePath} (mode=$selectMode)")
        MPVLib.command(arrayOf("sub-add", cached.absolutePath, selectMode, trackTitle, sub.languageCode))
        if (isPortuguese) {
            configSnackbar("Legenda ativada: $trackTitle")
        }
    }

    private fun resumeVideo(startPosition: Long) {
        val startPositionInSeconds = startPosition / 1000
        Log.d(TAG, "MPV(resumeVideo=${Utils.prettyTime(startPositionInSeconds.toInt())})")
        MPVLib.setOptionString("start", Utils.prettyTime(startPositionInSeconds.toInt()))
    }

    private fun getStreamUrl(fileId: String): String {
        val uri = Uri.parse(
            "${DRIVE_API}/files/${fileId}?supportsAllDrives=True&alt=media"
        )
        Log.d(TAG, "STREAM_URL=$uri")
        return uri.toString()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updatePlaybackPos(position: Int) {
        controller.exoPosition.text = Utils.prettyTime(position)
        if (!userIsOperatingSeekbar) {
            controller.progressBar.progress = position
        }
    }

    private fun updatePlaybackDuration(duration: Int) {
        controller.exoDuration.text = Utils.prettyTime(duration)
        if (!userIsOperatingSeekbar) {
            controller.progressBar.max = duration
        }
    }

    private fun updatePlaybackStatus(paused: Boolean) {
        TransitionManager.beginDelayedTransition(
            controller.mainControls,
            AutoTransition().apply { duration = 250L }
        )

        controller.btnPlayPause.icon = ContextCompat.getDrawable(
            /* context */ applicationContext,
            /* drawableId */ if (paused) R.drawable.ic_play_24
            else R.drawable.ic_pause_24
        )

        if (paused) {
            window.clearFlags(FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun skipForward() {
        val currentPos = player.timePos ?: return
        val newPos = currentPos + SKIP_DURATION
        player.timePos = newPos
    }

    private fun rewindBackward() {
        val currentPos = player.timePos ?: return
        val newPos = currentPos - SKIP_DURATION
        player.timePos = newPos
    }

    private fun skipRelative(deltaSeconds: Int) {
        val currentPos = player.timePos ?: return
        val duration = player.duration
        val target = currentPos + deltaSeconds
        val clamped = when {
            target < 0 -> 0
            duration != null && duration > 0 && target > duration -> duration
            else -> target
        }
        player.timePos = clamped
    }


    data class TrackData(
        val track_id: Int,
        val track_type: String
    )

    private fun trackSwitchNotification(f: () -> TrackData) {
        val (track_id, track_type) = f()
        val trackPrefix = when (track_type) {
            "audio" -> getString(R.string.audio)
            "sub" -> getString(R.string.subtitles)
            "video" -> getString(R.string.video)
            else -> "???"
        }

        if (track_id == -1) {
            configSnackbar("$trackPrefix ${getString(R.string.track_off)}")
            return
        }

        val trackName = player.tracks[track_type]
            ?.firstOrNull { it.mpvId == track_id }
            ?.name
            ?: "???"

        configSnackbar("$trackPrefix $trackName")
    }

    private fun pickAudio() {
        val tracks = player.tracks.getValue("audio")
        val selectedMpvId = player.aid

        val items = tracks.map { track ->
            GlassMenuItem(
                id = "track_${track.mpvId}",
                title = track.name,
                isSelected = track.mpvId == selectedMpvId,
                tag = track.mpvId
            )
        }

        PlayerGlassMenuDialog(
            context = this,
            title = getString(R.string.select_audio),
            items = items
        ) { selected ->
            val trackId = selected.tag as? Int ?: return@PlayerGlassMenuDialog
            player.aid = trackId
            trackSwitchNotification { TrackData(trackId, "audio") }
        }.setFooterSecondary("Sincronia") {
            showAudioSyncDialog()
        }.show()
    }

    private fun showAudioSyncDialog() {
        val options = arrayOf(
            "+1000 ms (Adiantar 1s)",
            "+500 ms (Adiantar)",
            "+250 ms (Adiantar)",
            "+100 ms (Adiantar um pouco)",
            "0 ms (Sincronizado)",
            "-100 ms (Atrasar um pouco)",
            "-250 ms (Atrasar)",
            "-500 ms (Atrasar)",
            "-1000 ms (Atrasar 1s)"
        )
        val offsets = longArrayOf(1000L, 500L, 250L, 100L, 0L, -100L, -250L, -500L, -1000L)
        val selectedIdx = offsets.indexOfFirst { it == currentAudioDelayMs }.takeIf { it >= 0 } ?: 4

        val title = if (currentAudioDelayMs != 0L) {
            "Sincronia de Áudio (${if (currentAudioDelayMs > 0) "+" else ""}${currentAudioDelayMs}ms)"
        } else {
            "Sincronia de Áudio (0ms)"
        }

        val items = options.mapIndexed { idx, label ->
            GlassMenuItem(
                id = "audio_sync_$idx",
                title = label,
                isSelected = idx == selectedIdx,
                tag = offsets[idx]
            )
        }

        PlayerGlassMenuDialog(
            context = this,
            title = title,
            items = items
        ) { selected ->
            val chosenOffset = selected.tag as? Long ?: 0L
            currentAudioDelayMs = chosenOffset
            MPVLib.setPropertyDouble("audio-delay", chosenOffset / 1000.0)
            val sign = if (chosenOffset > 0) "+" else ""
            configSnackbar("Sincronia de áudio: ${sign}${chosenOffset}ms", 1500)
        }.show()
    }

    private fun pickSub() {
        player.loadTracks()
        val tracks = player.tracks.getValue("sub")
        val selectedMpvId = player.sid

        data class SubChoice(
            val mpvTrackId: Int?,
            val folderSub: SubtitleItem?,
            val onlineSub: OnlineSubtitle? = null,
            val savedSub: SavedSubtitle? = null,
            val label: String,
            val subtitle: String? = null,
            val isSelected: Boolean
        )

        val choices = mutableListOf<SubChoice>()

        // 1. Off option (mpvId == -1)
        val isOff = selectedMpvId == -1 || tracks.none { it.mpvId == selectedMpvId }
        choices.add(SubChoice(-1, null, null, null, getString(R.string.track_off), null, isOff))

        // 2. Embedded video subtitle tracks
        tracks.filter { it.mpvId > 0 }.forEach { t ->
            choices.add(SubChoice(t.mpvId, null, null, null, t.name, "Embutida", t.mpvId == selectedMpvId))
        }

        val videoTitle = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }

        // 3. Permanently saved online subtitles from internal storage
        val savedSubs = onlineSubtitleManager.get().getSavedSubtitles(videoTitle)
        savedSubs.forEach { saved ->
            val matchingTrack = tracks.firstOrNull {
                it.name.contains(saved.langName, ignoreCase = true) ||
                (it.title?.contains(saved.langName, ignoreCase = true) == true)
            }
            val isSelected = matchingTrack != null && matchingTrack.mpvId == selectedMpvId
            choices.add(
                SubChoice(
                    mpvTrackId = matchingTrack?.mpvId,
                    folderSub = null,
                    onlineSub = null,
                    savedSub = saved,
                    label = "💾 [Salva] ${saved.langName}",
                    subtitle = "Armazenada no dispositivo",
                    isSelected = isSelected
                )
            )
        }

        // 4. ALL Google Drive folder subtitles: ALWAYS list them!
        folderSubtitles.forEach { sub ->
            val alreadyPresent = choices.any {
                it.label.contains(sub.name, ignoreCase = true) ||
                (it.label.contains("[Drive]", ignoreCase = true) && it.label.contains(sub.languageLabel, ignoreCase = true))
            }
            if (!alreadyPresent) {
                val isMatching = sub.matchesVideo(videoTitle)
                val prefix = if (isMatching) "★ [Drive] " else "📁 [Drive] "
                val tag = if (isMatching) " (Episódio atual)" else " (Outro episódio)"
                val label = "$prefix${sub.name}$tag"
                choices.add(SubChoice(null, sub, null, null, label, "Google Drive", false))
            }
        }

        fun buildMenuItems(sourceChoices: List<SubChoice>): List<GlassMenuItem> {
            return sourceChoices.mapIndexed { idx, c ->
                GlassMenuItem(
                    id = "sub_$idx",
                    title = c.label,
                    subtitle = c.subtitle,
                    isSelected = c.isSelected,
                    tag = c
                )
            }
        }

        val menuItems = buildMenuItems(choices).toMutableList()

        lateinit var glassDialog: PlayerGlassMenuDialog
        glassDialog = PlayerGlassMenuDialog(
            context = this,
            title = getString(R.string.select_subtitle),
            items = menuItems
        ) { selected ->
            val choice = selected.tag as? SubChoice ?: return@PlayerGlassMenuDialog
            if (choice.mpvTrackId != null) {
                player.sid = choice.mpvTrackId
                trackSwitchNotification { TrackData(choice.mpvTrackId, "sub") }
            } else if (choice.savedSub != null) {
                val saved = choice.savedSub
                val trackTitle = "💾 [Salva] ${saved.langName}"
                Log.d(TAG, "Manual add saved subtitle to MPV: ${saved.file.absolutePath}")
                MPVLib.command(arrayOf("sub-add", saved.file.absolutePath, "select", trackTitle, saved.lang))
                player.loadTracks()
                configSnackbar("Legenda salva ativada: ${saved.langName}")
            } else if (choice.folderSub != null) {
                val sub = choice.folderSub
                configSnackbar("Carregando legenda: ${sub.name}...")
                lifecycleScope.launch(Dispatchers.IO) {
                    val cached = viewModel.downloadSubtitle(sub, cacheDir)
                    if (cached != null && cached.exists()) {
                        withContext(Dispatchers.Main) {
                            val trackTitle = "★ [Drive] ${sub.languageLabel}"
                            Log.d(TAG, "Manual add external subtitle to MPV: ${cached.absolutePath}")
                            MPVLib.command(arrayOf("sub-add", cached.absolutePath, "select", trackTitle, sub.languageCode))
                            player.loadTracks()
                            configSnackbar("Legenda ativada: ${sub.languageLabel}")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            configSnackbar("Erro ao baixar legenda do Drive")
                        }
                    }
                }
            } else if (choice.onlineSub != null) {
                val os = choice.onlineSub
                configSnackbar("Baixando legenda online: ${os.langName}...")
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = onlineSubtitleManager.get().downloadSubtitle(os, videoTitle)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { cachedFile ->
                            val trackTitle = "💾 [Salva] ${os.langName}"
                            MPVLib.command(arrayOf("sub-add", cachedFile.absolutePath, "select", trackTitle, os.lang))
                            player.loadTracks()
                            configSnackbar("Legenda salva e ativada: ${os.langName}!")
                        }.onFailure { err ->
                            configSnackbar("Erro ao baixar legenda online: ${err.message}")
                        }
                    }
                }
            }
        }

        glassDialog.setActionButton("🔍 Buscar Legendas Online (PT-BR)") { d ->
            d.showLoading(true)
            lifecycleScope.launch(Dispatchers.IO) {
                val onlineResults = try {
                    onlineSubtitleManager.get().searchSubtitles(videoTitle)
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching online subtitles", e)
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    d.showLoading(false)
                    if (onlineResults.isEmpty()) {
                        configSnackbar("Nenhuma legenda online encontrada")
                        return@withContext
                    }

                    val updatedChoices = choices.toMutableList()
                    onlineResults.forEach { os ->
                        val flag = if (os.isPortuguese) "🇧🇷 " else "🌐 "
                        val name = "$flag${os.langName}"
                        val subText = "Online • ${os.source}"
                        updatedChoices.add(SubChoice(null, null, os, null, name, subText, false))
                    }
                    d.updateItems(buildMenuItems(updatedChoices))
                    configSnackbar("${onlineResults.size} legendas encontradas online!", 1500)
                }
            }
        }

        glassDialog.setFooterSecondary("Sincronia") {
            showSubtitleSyncDialog()
        }

        glassDialog.setFooterPrimary("Tamanho") {
            showSubtitleSizeDialog()
        }

        glassDialog.show()
    }

    private fun showSubtitleSyncDialog() {
        val options = arrayOf(
            "+1000 ms (Adiantar 1s)",
            "+500 ms (Adiantar)",
            "+250 ms (Adiantar)",
            "+100 ms (Adiantar um pouco)",
            "0 ms (Sincronizado)",
            "-100 ms (Atrasar um pouco)",
            "-250 ms (Atrasar)",
            "-500 ms (Atrasar)",
            "-1000 ms (Atrasar 1s)"
        )
        val offsets = longArrayOf(1000L, 500L, 250L, 100L, 0L, -100L, -250L, -500L, -1000L)
        val selectedIdx = offsets.indexOfFirst { it == currentSubtitleDelayMs }.takeIf { it >= 0 } ?: 4

        val title = if (currentSubtitleDelayMs != 0L) {
            "Sincronia de Legenda (${if (currentSubtitleDelayMs > 0) "+" else ""}${currentSubtitleDelayMs}ms)"
        } else {
            "Sincronia de Legenda (0ms)"
        }

        val items = options.mapIndexed { idx, label ->
            GlassMenuItem(
                id = "sub_sync_$idx",
                title = label,
                isSelected = idx == selectedIdx,
                tag = offsets[idx]
            )
        }

        PlayerGlassMenuDialog(
            context = this,
            title = title,
            items = items
        ) { selected ->
            val chosenOffset = selected.tag as? Long ?: 0L
            currentSubtitleDelayMs = chosenOffset
            MPVLib.setPropertyDouble("sub-delay", chosenOffset / 1000.0)
            val sign = if (chosenOffset > 0) "+" else ""
            configSnackbar("Sincronia de legenda: ${sign}${chosenOffset}ms", 1500)
        }.show()
    }

    private fun showSubtitleSizeDialog() {
        val sizes = arrayOf(
            "Pequeno (16sp)",
            "Médio (20sp - Padrão)",
            "Grande (24sp)",
            "Extra Grande (28sp)"
        )
        val sizeValues = floatArrayOf(16f, 20f, 24f, 28f)

        lifecycleScope.launch {
            val savedSp = try {
                appSettings.get().fetchSubtitleSize()
            } catch (e: Exception) {
                20f
            }
            val selectedIdx = sizeValues.indexOfFirst { abs(it - savedSp) < 0.5f }.coerceAtLeast(1)

            val items = sizes.mapIndexed { idx, label ->
                GlassMenuItem(
                    id = "size_$idx",
                    title = label,
                    isSelected = idx == selectedIdx,
                    tag = sizeValues[idx]
                )
            }

            PlayerGlassMenuDialog(
                context = this@MPVActivity,
                title = "Tamanho da Legenda",
                items = items
            ) { selected ->
                val chosenSp = selected.tag as? Float ?: 20f
                val mpvSize = when {
                    chosenSp <= 16f -> 38
                    chosenSp <= 20f -> 48
                    chosenSp <= 24f -> 58
                    else -> 68
                }
                MPVLib.setPropertyInt("sub-font-size", mpvSize)
                lifecycleScope.launch {
                    try {
                        appSettings.get().saveSubtitleSize(chosenSp)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error saving subtitle size", e)
                    }
                }
                configSnackbar("Tamanho definido: ${selected.title}", 1000)
            }.show()
        }
    }

    private fun pickChapter() {
        val chapters = player.loadChapters()

        if (chapters.isEmpty()) {
            MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(getString(R.string.chapters))
                .setMessage("Nenhum capítulo encontrado neste arquivo.")
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
            return
        }

        val selectedIndex = (MPVLib.getPropertyInt("chapter") ?: 0).coerceIn(0, chapters.size - 1)

        val items = chapters.mapIndexed { idx, ch ->
            val timeCode = Utils.prettyTime(ch.time.roundToInt())
            val title = ch.title?.takeIf { it.isNotBlank() } ?: "Capítulo ${ch.index + 1}"
            GlassMenuItem(
                id = "chapter_${ch.index}",
                title = title,
                subtitle = timeCode,
                isSelected = idx == selectedIndex,
                tag = ch.index
            )
        }

        PlayerGlassMenuDialog(
            context = this,
            title = getString(R.string.chapters),
            items = items
        ) { selected ->
            val chIndex = selected.tag as? Int ?: return@PlayerGlassMenuDialog
            MPVLib.setPropertyInt("chapter", chIndex)
            val chTitle = selected.title
            configSnackbar("Capítulo: $chTitle")
        }.show()
    }

    private fun selectTrack(title: String, type: String, get: () -> Int, set: (Int) -> Unit) {
        val tracks = player.tracks.getValue(type)
        val selectedMpvId = get()

        val items = tracks.map { track ->
            GlassMenuItem(
                id = "track_${track.mpvId}",
                title = track.name,
                isSelected = track.mpvId == selectedMpvId,
                tag = track.mpvId
            )
        }

        PlayerGlassMenuDialog(
            context = this,
            title = title,
            items = items
        ) { selected ->
            val trackId = selected.tag as? Int ?: return@PlayerGlassMenuDialog
            set(trackId)
            trackSwitchNotification { TrackData(trackId, type) }
        }.show()
    }

    private fun configSnackbar(msg: String, duration: Int = 750) {
        Snackbar.make(
            controller.root, msg, duration
        ).apply {
            anchorView = controller.linearLayout2
        }.also { it.show() }
    }

    private fun pickSpeed() {
        val currentSpeed = MPVLib.getPropertyDouble("speed") ?: 1.0
        val speedOptions = listOf(
            0.25 to "0.25x",
            0.50 to "0.5x",
            0.75 to "0.75x",
            1.00 to "Normal (1.0x)",
            1.25 to "1.25x",
            1.50 to "1.5x",
            1.75 to "1.75x",
            2.00 to "2.0x"
        )
        val items = speedOptions.map { (sp, label) ->
            val isSelected = abs(currentSpeed - sp) < 0.05
            GlassMenuItem(
                id = sp.toString(),
                title = label,
                isSelected = isSelected,
                tag = sp
            )
        }

        PlayerGlassMenuDialog(
            context = this,
            title = getString(R.string.playback_speed),
            items = items
        ) { selected ->
            val chosenSpeed = selected.tag as? Double ?: 1.0
            setSpeed(chosenSpeed)
            configSnackbar("Velocidade: ${selected.title}")
        }.show()
    }

    private fun setSpeed(speed: Double) {
        MPVLib.setPropertyDouble("speed", speed)
    }


    private fun updateOrientation(newConfig: Configuration) {
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                orientation = Orientation.PORTRAIT
                controller.tvRotate.text = "Paisagem"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    controller.btnRotate.tooltipText = getString(R.string.landscape)
                }
                controller.ivRotate.setImageDrawable(
                    ContextCompat.getDrawable(this@MPVActivity, R.drawable.ic_landscape_24)
                )
            }
            else -> {
                orientation = Orientation.LANDSCAPE
                controller.tvRotate.text = "Girar"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    controller.btnRotate.tooltipText = getString(R.string.portrait)
                }
                controller.ivRotate.setImageDrawable(
                    ContextCompat.getDrawable(this@MPVActivity, R.drawable.ic_portrait_24)
                )
            }
        }
    }


    private fun handleLockingControls() {
        TransitionManager.beginDelayedTransition(
            controller.root,
            AutoTransition().apply { duration = 150L }
        )
        if (controlsLocked) {
            lockControls()
        } else {
            unlockControls()
        }
    }

    private fun lockControls() {
        controller.apply {
            controlsScrollView.visibility = View.GONE
            mainControls.visibility = View.GONE
            skipIntroRow.visibility = View.GONE
            btnUnlock.visibility = View.VISIBLE
            playerToolbar.visibility = View.GONE
            titleBlock.visibility = View.GONE
        }
        binding.netflixSkipRow.visibility = View.GONE
        binding.nextEpisodeCard.root.visibility = View.GONE
    }

    private fun unlockControls() {
        controller.apply {
            btnUnlock.visibility = View.GONE
            playerToolbar.visibility = View.GONE
            titleBlock.visibility = View.VISIBLE
            controlsScrollView.visibility = View.VISIBLE
            mainControls.visibility = View.VISIBLE
            skipIntroRow.visibility = View.VISIBLE
        }
    }

    private fun saveProgress() {
        val watchedDuration = player.timePos ?: return
        val totalDuration = player.duration ?: return
        Log.d(TAG, "MPV(current=$watchedDuration, total=$totalDuration)")
        // convert these two values from seconds to milliseconds
        val watchedDurationInMills = (watchedDuration * 1000).toLong()
        val totalDurationInMills = (totalDuration * 1000).toLong()
        Log.d(TAG, "MPV(current=$watchedDurationInMills, total=$totalDurationInMills)")
        Log.d(TAG, "MPV(saveProgress=${Utils.prettyTime(watchedDuration)})")

        if (PlaybackProgressPolicy.shouldSave(watchedDurationInMills, totalDurationInMills)) {
            val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: "" }
            val title = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }
            if (fileId.isBlank()) {
                Log.w(TAG, "saveProgress: no fileId available (neither state nor intent), skipping save")
                return
            }
            val thumbnailLink = currentThumbnailLink ?: intent.getStringExtra("thumbnailLink")
            viewModel.saveWatch(
                name = title,
                videoId = fileId,
                watchedDuration = watchedDurationInMills,
                totalDuration = totalDurationInMills,
                thumbnailLink = thumbnailLink
            )
        }
    }

    private fun checkAutoPlayNextEpisode(timePosSeconds: Long, durationSeconds: Long) {
        val decision = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = timePosSeconds * 1000L,
            durationMs = durationSeconds * 1000L,
            hasNextEpisode = nextEpisode != null,
            isCanceled = nextEpisodeCanceled,
            controlsLocked = controlsLocked
        )

        when (decision) {
            is PlaybackProgressPolicy.AutoplayDecision.PlayNext -> {
                playNextEpisodeDirectly()
            }
            is PlaybackProgressPolicy.AutoplayDecision.ShowCountdown -> {
                val next = nextEpisode ?: return
                if (!isNextEpisodeCardShowing) {
                    showNextEpisodeCard(next)
                }
                binding.nextEpisodeCard.tvNextEpisodeCountdown.text = "A SEGUIR • ${decision.countdownSeconds}s"
            }
            is PlaybackProgressPolicy.AutoplayDecision.DismissCard -> {
                if (isNextEpisodeCardShowing) {
                    dismissNextEpisodeCard(isManualCancel = false)
                }
            }
            is PlaybackProgressPolicy.AutoplayDecision.None -> Unit
        }
    }

    private fun showNextEpisodeCard(next: PlaylistItem) {
        if (isNextEpisodeCardShowing) return
        isNextEpisodeCardShowing = true

        binding.netflixSkipRow.animate().cancel()
        binding.netflixSkipRow.visibility = View.GONE
        controller.skipIntroRow.animate().cancel()
        controller.skipIntroRow.visibility = View.GONE

        val card = binding.nextEpisodeCard
        val density = resources.displayMetrics.density
        card.root.translationY = if (controller.root.isVisible) -84f * density else 0f

        val parsed = EpisodeParser.parse(next.title)
        card.tvNextEpisodeTitle.text = parsed.cleanTitle

        val thumbUrl = next.thumbnailLink?.let {
            if (it.contains(Regex("=s\\d+"))) it.replace(Regex("=s\\d+"), "=s600") else "$it=s600"
        }
        Glide.with(this)
            .load(thumbUrl)
            .placeholder(R.drawable.kodi_primary_bg)
            .error(R.drawable.kodi_primary_bg)
            .centerCrop()
            .into(card.ivNextEpisodeThumb)

        card.root.alpha = 0f
        card.root.visibility = View.VISIBLE
        card.root.animate().alpha(1f).setDuration(250L).start()

        card.btnPlayNextEpisodeNow.setOnClickListener {
            playNextEpisodeDirectly()
        }

        card.btnCancelNextEpisode.setOnClickListener {
            dismissNextEpisodeCard(isManualCancel = true)
        }

        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            while (isActive && isNextEpisodeCardShowing) {
                val timePosDouble = MPVLib.getPropertyDouble("time-pos") ?: (player.timePos ?: 0).toDouble()
                val durationDouble = (player.duration ?: 0).toDouble()
                val remainingDouble = (durationDouble - timePosDouble).coerceAtLeast(0.0)
                val sec = kotlin.math.ceil(remainingDouble).toInt().coerceAtLeast(0)
                card.tvNextEpisodeCountdown.text = "A SEGUIR • ${sec}s"
                if (sec <= 0 || remainingDouble <= 1.0) {
                    playNextEpisodeDirectly()
                    break
                }
                delay(300L)
            }
        }
    }

    private fun dismissNextEpisodeCard(isManualCancel: Boolean = true) {
        countdownJob?.cancel()
        countdownJob = null
        if (isManualCancel) {
            nextEpisodeCanceled = true
        }
        isNextEpisodeCardShowing = false
        val card = binding.nextEpisodeCard.root
        card.animate().alpha(0f).setDuration(200L).withEndAction {
            card.visibility = View.GONE
            card.translationY = 0f
        }.start()
    }

    private fun playNextEpisodeDirectly() {
        val next = nextEpisode ?: return
        countdownJob?.cancel()
        isNextEpisodeCardShowing = false
        binding.nextEpisodeCard.root.visibility = View.GONE

        saveProgress()

        currentFileId = next.fileId
        currentTitle = next.title
        currentThumbnailLink = next.thumbnailLink
        nextEpisodeCanceled = false
        addedSubtitleFileIds.clear()
        updateNextEpisode()

        playMedia()
        val parsed = EpisodeParser.parse(next.title)
        configSnackbar("Iniciando: ${parsed.cleanTitle}")
    }

    private fun playPrevEpisodeDirectly() {
        val prev = prevEpisode ?: return
        countdownJob?.cancel()
        isNextEpisodeCardShowing = false
        binding.nextEpisodeCard.root.visibility = View.GONE

        saveProgress()

        currentFileId = prev.fileId
        currentTitle = prev.title
        currentThumbnailLink = prev.thumbnailLink
        nextEpisodeCanceled = false
        addedSubtitleFileIds.clear()
        updateNextEpisode()

        playMedia()
        val parsed = EpisodeParser.parse(prev.title)
        configSnackbar("Iniciando: ${parsed.cleanTitle}")
    }

    private fun showEpisodesDrawer() {
        val seriesFromIntent = intent.getStringExtra("seriesTitle")?.takeIf { it.isNotBlank() }
        val showName = seriesFromIntent ?: EpisodeParser.parse(currentTitle).showTitle.ifBlank { currentTitle }
        PlayerEpisodeDrawerDialog(
            activity = this,
            showTitle = showName,
            currentPlayingFileId = currentFileId,
            playlist = playlist,
            tenraiService = tenraiService.get()
        ) { selectedEpisode ->
            playPlaylistItemDirectly(selectedEpisode)
        }.show()
    }

    private fun playPlaylistItemDirectly(item: PlaylistItem) {
        if (item.fileId == currentFileId) return
        countdownJob?.cancel()
        isNextEpisodeCardShowing = false
        binding.nextEpisodeCard.root.visibility = View.GONE

        saveProgress()

        currentFileId = item.fileId
        currentTitle = item.title
        currentThumbnailLink = item.thumbnailLink
        nextEpisodeCanceled = false
        addedSubtitleFileIds.clear()
        updateNextEpisode()

        playMedia()
        val parsed = EpisodeParser.parse(item.title)
        configSnackbar("Iniciando: ${parsed.cleanTitle}")
    }

    ////////////////    MPV EVENTS    ////////////////

    override fun eventProperty(property: String, value: Boolean) {
        if (activityIsForeground) {
            if (property == "pause") {
                runOnUiThread { updatePlaybackStatus(value) }
            } else if (property == "eof-reached" && value) {
                runOnUiThread {
                    val dur = (player.duration ?: 0).toLong()
                    checkMalScrobble(dur * 1000L, dur * 1000L)
                    checkMalCompletionPrompt(dur * 1000L, dur * 1000L)
                    if (nextEpisode != null && !nextEpisodeCanceled) {
                        playNextEpisodeDirectly()
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        if (!activityIsForeground) return
        runOnUiThread {
            when (property) {
                "time-pos" -> {
                    updatePlaybackPos(value.toInt())
                    checkChapterAutoSkip(value.toDouble())
                    val dur = (player.duration ?: 0).toLong()
                    checkAutoPlayNextEpisode(value, dur)
                    checkMalScrobble(value * 1000L, dur * 1000L)
                    checkMalCompletionPrompt(value * 1000L, dur * 1000L)
                }
                "duration" -> {
                    updatePlaybackDuration(value.toInt())
                    updateChapters()
                }
            }
        }
    }

    private fun checkMalScrobble(posMs: Long, durationMs: Long) {
        if (durationMs <= 60_000L || hasScrobbledThisEp) return
        val progress = posMs.toDouble() / durationMs.toDouble()
        if (progress < 0.85) return

        val session = malSessionManager.get()
        if (!session.isSyncEnabled() || !session.isLoggedIn()) return

        hasScrobbledThisEp = true
        lifecycleScope.launch(Dispatchers.IO) {
            val anime = currentMalAnime ?: run {
                val title = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }
                val parsed = EpisodeParser.parse(title)
                val searchTitle = parsed.showTitle.ifBlank { title }
                malRepository.get().matchAnime(searchTitle).also { currentMalAnime = it }
            }
            if (anime != null) {
                val res = malRepository.get().updateEpisodeProgress(anime.id, currentEpNumber)
                withContext(Dispatchers.Main) {
                    if (res is zechs.drive.stream.utils.state.Resource.Success) {
                        Snackbar.make(binding.root, "MAL: Episódio $currentEpNumber sincronizado! (${anime.title})", 2500).show()
                    } else {
                        Log.w(TAG, "MAL scrobble failed: ${res.message}")
                    }
                }
            }
        }
    }

    private fun checkMalCompletionPrompt(posMs: Long, durationMs: Long) {
        if (hasPromptedRating || durationMs <= 60_000L) return
        val remainingMs = durationMs - posMs
        if (remainingMs > 2_000L) return

        val session = malSessionManager.get()
        if (!session.isSyncEnabled() || !session.isLoggedIn()) return

        val anime = currentMalAnime ?: return
        val isFinal = (anime.numEpisodes > 0 && currentEpNumber >= anime.numEpisodes) ||
                (nextEpisode == null && currentEpNumber >= 1)

        if (isFinal) {
            hasPromptedRating = true
            runOnUiThread {
                MalRatingDialog.show(
                    activity = this@MPVActivity,
                    animeTitle = anime.title,
                    totalEpisodes = anime.numEpisodes,
                    onSubmit = { score ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val res = malRepository.get().completeAnimeWithScore(anime.id, anime.numEpisodes, score)
                            withContext(Dispatchers.Main) {
                                if (res is zechs.drive.stream.utils.state.Resource.Success) {
                                    android.widget.Toast.makeText(this@MPVActivity, getString(R.string.mal_anime_completed, score), android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(this@MPVActivity, getString(R.string.mal_save_score_error, res.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun updateIntroButtonVisibility(timeSeconds: Long) {
        val shouldShow = timeSeconds in 1L..140L
        val row = controller.skipIntroRow
        if (shouldShow && !row.isVisible && !controlsLocked && controller.root.isVisible) {
            row.alpha = 0f
            row.isVisible = true
            row.animate().alpha(1f).setDuration(200L).start()
        } else if (!shouldShow && row.isVisible) {
            row.animate().alpha(0f).setDuration(200L).withEndAction {
                row.isVisible = false
            }.start()
        }
    }

    override fun eventProperty(property: String) {
        if (!activityIsForeground) return
        runOnUiThread { eventPropertyUi(property) }
    }

    override fun eventProperty(property: String, value: String) {
        if (!activityIsForeground) return
        runOnUiThread { eventPropertyUi(property) }
    }

    private fun eventPropertyUi(property: String) {
        if (activityIsForeground) {
            when (property) {
                "track-list" -> {
                    player.loadTracks()
                    autoSelectMpvTracks()
                }
                "chapter-list" -> {
                    updateChapters()
                }
            }
        }
    }

    private fun autoSelectMpvTracks() {
        if (hasAutoSelectedMpvTracks) return

        val audioTracks = player.tracks["audio"] ?: emptyList()
        val subTracks = player.tracks["sub"] ?: emptyList()

        if (audioTracks.isEmpty() && subTracks.isEmpty()) return

        // 1. Audio: Auto-select Japanese
        val currentAid = player.aid
        val targetAudio = audioTracks.firstOrNull { track ->
            if (track.mpvId == -1) return@firstOrNull false
            val lang = (track.lang ?: "").lowercase()
            val title = (track.title ?: "").lowercase()
            val name = track.name.lowercase()
            lang in listOf("ja", "jpn", "jp", "japanese") ||
                    title.contains("jap") || title.contains("jpn") ||
                    name.contains("jap") || name.contains("jpn")
        }
        if (targetAudio != null && targetAudio.mpvId != currentAid) {
            Log.d(TAG, "MPV auto-selected Japanese audio: ${targetAudio.name} (id=${targetAudio.mpvId})")
            player.aid = targetAudio.mpvId
        }

        // 2. Subtitle: Auto-select Portuguese (prefer Brazilian Portuguese / full dialogue)
        val currentSid = player.sid
        val ptSubs = subTracks.filter { track ->
            if (track.mpvId == -1) return@filter false
            val lang = (track.lang ?: "").lowercase()
            val title = (track.title ?: "").lowercase()
            val name = track.name.lowercase()
            lang in listOf("pt", "por", "pt-br", "pt_br", "pob", "portuguese") ||
                    title.contains("portugu") || title.contains("pt-br") || title.contains("pt_br") || title.contains("brazil") ||
                    name.contains("portugu") || name.contains("pt-br") || name.contains("pt_br") || name.contains("brazil")
        }

        val targetSub = ptSubs.maxByOrNull { track ->
            val label = ((track.title ?: "") + " " + track.name).lowercase()
            when {
                label.contains("[drive]") -> 25
                label.contains("brasil") || label.contains("brazil") || label.contains("pt-br") || label.contains("pt_br") -> 15
                label.contains("forced") || label.contains("forçada") || label.contains("signs") -> 5
                else -> 10
            }
        }

        if (targetSub != null && targetSub.mpvId != currentSid) {
            Log.d(TAG, "MPV auto-selected Portuguese subtitle: ${targetSub.name} (id=${targetSub.mpvId})")
            player.sid = targetSub.mpvId
        }

        if (audioTracks.size > 1 || subTracks.size > 1) {
            hasAutoSelectedMpvTracks = true
        }
    }

    override fun event(eventId: Int) {
        if (activityIsForeground) {
            when (eventId) {
                MPV_EVENT_PLAYBACK_RESTART -> {
                    runOnUiThread {
                        updatePlaybackStatus(player.paused ?: false)
                        updateChapters()
                    }
                }
                MPV_EVENT_FILE_LOADED -> {
                    isFileLoaded = true
                    runOnUiThread {
                        updateChapters()
                        pendingSubtitles.forEach { (cached, sub) ->
                            addSubtitleToMpv(cached, sub)
                        }
                        pendingSubtitles.clear()
                    }
                }
            }
        }
    }

    ////////////////    END OF MPV EVENTS    ////////////////


    override fun onPause() {
        saveProgress()

        activityIsForeground = false

        if (isFinishing) {
            MPVLib.command(arrayOf("stop"))
        }
        super.onPause()
    }

    override fun onResume() {
        // If we weren't actually in the background
        // (e.g. multi window mode), don't reinitialize stuff
        if (activityIsForeground) {
            super.onResume()
            return
        }

        activityIsForeground = true
        super.onResume()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation(newConfig)
    }

    private fun launchExoFallback() {
        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: return }
        val title = currentTitle.ifBlank { intent.getStringExtra("title") ?: return }
        val thumbnailLink = currentThumbnailLink ?: intent.getStringExtra("thumbnailLink")
        val theme = intent.getIntExtra("theme", 0)
        val currentPos = (player.timePos ?: 0) * 1000L

        val exoIntent = Intent(this, zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
            putExtra("fileId", fileId)
            putExtra("title", title)
            putExtra("thumbnailLink", thumbnailLink)
            putExtra("theme", theme)
            putExtra("playlist", ArrayList(playlist))
            putExtra("subtitles", ArrayList(folderSubtitles))
            if (currentPos > 0) {
                putExtra("startPosition", currentPos)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        finish()
        startActivity(exoIntent)
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        Log.v(TAG, "Exiting.")

        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(audioFocusChangeListener)

        player.removeObserver(this)
        player.destroy()

        super.onDestroy()
    }

}