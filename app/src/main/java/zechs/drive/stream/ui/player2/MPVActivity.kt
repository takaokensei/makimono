package zechs.drive.stream.ui.player2

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.AudioManager.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import zechs.drive.stream.utils.AppLog
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.playback.osd.OsdController
import zechs.drive.stream.playback.osd.OsdViewHost
import zechs.drive.stream.utils.MakimonoMediaSessionHelper
import zechs.drive.stream.utils.MakimonoPiPHelper
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
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ThumbnailUrl
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
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.utils.MediaUriResolver
import zechs.drive.stream.databinding.ActivityMpvBinding
import zechs.drive.stream.databinding.PlayerControlViewBinding
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.ui.player.PlayerViewModel
import zechs.drive.stream.ui.player.PlaybackPlaylistPolicy
import zechs.drive.stream.ui.player.PlaybackLabel
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.ui.player.MalRatingDialog
import zechs.drive.stream.ui.player.PlayerGestureHelper
import zechs.drive.stream.ui.player.ChapterMarker
import zechs.drive.stream.ui.player.PlayerGestureCallback
import zechs.drive.stream.ui.player.PlayerGlassMenuDialog
import zechs.drive.stream.ui.player.SubtitleElevationPolicy
import zechs.drive.stream.ui.player.GlassMenuItem
import zechs.drive.stream.ui.player.PlayerEpisodeDrawerDialog
import zechs.drive.stream.ui.player.PlayerModalHost
import zechs.drive.stream.ui.player.PlayerModalTransition
import zechs.drive.stream.ui.player.PlayerModalVisibilityState
import zechs.drive.stream.utils.OnlineSubtitleManager
import zechs.drive.stream.utils.OnlineSubtitle
import zechs.drive.stream.utils.SavedSubtitle
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.utils.MatroskaChapterParser
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter
import zechs.drive.stream.utils.MatroskaChapterParser.ChapterType
import zechs.drive.stream.utils.util.Constants.Companion.DRIVE_API
import zechs.drive.stream.utils.util.Orientation
import zechs.drive.stream.utils.util.getNextOrientation
import zechs.drive.stream.utils.util.setOrientation
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.VideoPlayer
import zechs.mpv.MPVLib
import zechs.mpv.MPVLib.mpvEventId.MPV_EVENT_END_FILE
import zechs.mpv.MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED
import zechs.mpv.MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART
import zechs.mpv.MPVView
import zechs.mpv.utils.Utils
import kotlin.math.abs
import kotlin.math.roundToInt

import zechs.drive.stream.ui.player.BasePlayerActivity

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class MPVActivity : BasePlayerActivity(), MPVLib.EventObserver {

    override val isGesturesEnabled: Boolean
        get() = malSessionManager.get().isGesturesEnabled()
    override val isGestureHelperInitialized: Boolean
        get() = ::gestureHelper.isInitialized
    override fun onGestureTouchEvent(ev: MotionEvent): Boolean = gestureHelper.onTouchEvent(ev)

    companion object {
        const val TAG = "MPVActivity"

        // fraction to which audio volume is ducked on loss of audio focus
        private const val AUDIO_FOCUS_DUCKING = 0.5f
        private const val SKIP_DURATION = 10 // in seconds
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
    lateinit var tokenProvider: dagger.Lazy<zechs.drive.stream.data.repository.TokenProvider>

    @Inject
    lateinit var tenraiService: dagger.Lazy<zechs.drive.stream.data.remote.TenraiAnimeService>

    @Inject
    lateinit var bingeBufferManager: dagger.Lazy<zechs.drive.stream.ui.player.predictive.BingeBufferManager>

    // States
    private var activityIsForeground = true
    private var userIsOperatingSeekbar = false
    private var controlsLocked = false
    private val playerModalVisibilityState = PlayerModalVisibilityState()
    private var modalReturnFocus: View? = null
    private var hasAutoSelectedMpvTracks = false
    private var parsedChapters = listOf<ParsedChapter>()
    private var activeSkipChapter: ParsedChapter? = null
    private lateinit var gestureHelper: PlayerGestureHelper
    private var hasAutoSkippedCurrentInterval = false
    private val displayModeController = zechs.drive.stream.playback.display.DisplayModeController()
    private val currentAfrState get() = displayModeController.currentState
    private var currentHdrStatus: zechs.drive.stream.utils.HdrModeManager.HdrStatus? = null

    // MediaSession
    private var mediaSessionHelper: MakimonoMediaSessionHelper? = null

    // Playlist & Next Episode Auto-Play
    private var currentFileId: String = ""
    private var currentLocalUri: String? = null
    private var currentTitle: String = ""
    private var currentThumbnailLink: String? = null
    private var playlist = mutableListOf<PlaylistItem>()
    private var nextEpisode: PlaylistItem? = null
    private var prevEpisode: PlaylistItem? = null
    private val playbackCoordinator = zechs.drive.stream.playback.session.PlaybackCoordinator()
    private val skipController = zechs.drive.stream.playback.session.SkipController()
    private lateinit var playbackSession: zechs.drive.stream.playback.session.PlaybackSession
    private var nextEpisodeCanceled = false
    private var isNextEpisodeCardShowing = false
    private var countdownJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ignoreNextCenterKeyUp = false
    private var handledFocusDirectionKeyCode: Int? = null
    private var isCenterLongPressSpeeding = false
    private var speedBeforeCenterHold = 1.0
    private val centerLongPressRunnable = Runnable {
        if (!controller.root.isVisible && !controlsLocked && ::player.isInitialized) {
            isCenterLongPressSpeeding = true
            speedBeforeCenterHold = MPVLib.getPropertyDouble("speed") ?: 1.0
            MPVLib.setPropertyDouble("speed", 2.0)
            gestureHelper.showSpeedPill()
            ignoreNextCenterKeyUp = true
        }
    }
    private val seekStepPolicy = zechs.drive.stream.utils.SeekStepPolicy()
    private val resetSeekAccumulatorRunnable = Runnable {
        seekStepPolicy.reset()
    }
    private var folderSubtitles = mutableListOf<SubtitleItem>()
    private val addedSubtitleFileIds = mutableSetOf<String>()
    private var isFileLoaded = false
    private var pendingResumePositionMs = 0L
    private val pendingSubtitles = mutableListOf<Pair<java.io.File, SubtitleItem>>()
    private var currentSubtitleDelayMs = 0L
    private var currentAudioDelayMs = 0L
    private var isNightModeAudioEnabled = false
    private var currentResizeMode = 0

    // MAL Scrobble & Rating State
    private var currentMalAnime: MalAnimeNode? = null
    private var currentEpNumber: Int = 1
    private var hasScrobbledThisEp: Boolean = false
    private var hasPromptedRating: Boolean = false
    private var isMovie: Boolean = false

    // Configs
    private var onLoadCommands = mutableListOf<Array<String>>()
    private val speeds = arrayOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
    private var orientation = Orientation.LANDSCAPE
    private var reduceMotionEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeValue = intent.getIntExtra("theme", zechs.drive.stream.utils.AppTheme.OBSIDIAN.value)
        val themeRes = when (zechs.drive.stream.utils.AppTheme.fromValue(themeValue)) {
            zechs.drive.stream.utils.AppTheme.TOKYO_NIGHT -> R.style.Theme_Fullscreen_TokyoNight
            zechs.drive.stream.utils.AppTheme.DRACULA -> R.style.Theme_Fullscreen_Dracula
            zechs.drive.stream.utils.AppTheme.NORD -> R.style.Theme_Fullscreen_Nord
            zechs.drive.stream.utils.AppTheme.CATPPUCCIN_MOCHA -> R.style.Theme_Fullscreen_CatppuccinMocha
            zechs.drive.stream.utils.AppTheme.KODI_ESTUARY -> R.style.Theme_Fullscreen_KodiEstuary
            zechs.drive.stream.utils.AppTheme.OBSIDIAN -> R.style.Theme_Fullscreen_Obsidian
            zechs.drive.stream.utils.AppTheme.CRUNCHYROLL -> R.style.Theme_Fullscreen_Crunchyroll
        }
        setTheme(themeRes)
        super.onCreate(savedInstanceState)

        Utils.copyAssets(this)

        binding = ActivityMpvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            reduceMotionEnabled = runCatching {
                MotionPolicy.shouldReduceMotion(
                    appSettings.get().fetchMotionPreference(),
                    MotionPolicy.areSystemAnimationsEnabled()
                )
            }.getOrDefault(false)
        }

        // P2-10: substitui onBackPressed() deprecado pelo dispatcher moderno.
        onBackPressedDispatcher.addCallback(object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBackNavigation()) {
                    finish()
                }
            }
        })

        hideSystemUI()

        player = binding.player
        controller = binding.controller

        osdController = OsdController(
            context = this,
            host = object : OsdViewHost {
                override val rootControllerView: View?
                    get() = controller.root
                override val titleBlock: View?
                    get() = controller.titleBlock
                override val controlsScrollView: View?
                    get() = controller.controlsScrollView
                override val mainControlsRoot: View?
                    get() = controller.mainControls
                override val topScrim: View?
                    get() = controller.topScrim
                override val skipIntroRow: View?
                    get() = controller.skipIntroRow
                override val nextEpisodeCard: View?
                    get() = binding.nextEpisodeCard.root

                override fun onOsdVisibilityChanged(visible: Boolean) {
                    if (!visible) {
                        zechs.drive.stream.utils.TvFocusRing.clear(binding.root)
                    } else {
                        handleLockingControls()
                        controller.btnPlayPause.post { controller.btnPlayPause.requestFocus() }
                    }
                    val currentPos = (player.timePos ?: 0).toDouble()
                    checkChapterAutoSkip(currentPos)
                }

                override fun onElevationPolicyChanged(elevated: Boolean) {
                    MPVLib.setPropertyInt("sub-margin-y", SubtitleElevationPolicy.resolve(elevated).mpvMarginY)
                }

                override fun onShowPrepared() {
                    try {
                        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        controller.root.findViewById<TextView>(R.id.tvPlayerClock)?.text = timeFormat.format(java.util.Date())
                    } catch (e: Exception) {
                        AppLog.d(TAG, "Failed updating clock on OSD show: ${e.message}")
                    }
                }
            },
            isReviewMode = intent.getStringExtra("reviewMediaUri") != null,
            getReduceMotion = { reduceMotionEnabled }
        )

        controller
            .playerToolbar
            .setNavigationOnClickListener { finish() }
        controller
            .btnBack
            .setOnClickListener { finish() }
        // Keep the dedicated exit target reliable while PlayerView toggles its
        // controller on the same tap. Invoke the normal click exactly once so
        // touch and accessibility actions share the same exit behavior.
        controller.btnBack.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.isPressed = event.x >= 0f && event.x < view.width &&
                        event.y >= 0f && event.y < view.height
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val activate = view.isPressed && event.x >= 0f && event.x < view.width &&
                        event.y >= 0f && event.y < view.height
                    view.isPressed = false
                    if (activate) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    true
                }
                else -> false
            }
        }

        playbackSession = zechs.drive.stream.playback.session.PlaybackSession(
            progressRecorder = viewModel.progressRecorder,
            coordinator = playbackCoordinator,
            skipController = skipController
        )

        currentFileId = intent.getStringExtra("fileId") ?: ""
        currentLocalUri = intent.getStringExtra(PlayerLauncher.EXTRA_LOCAL_URI)
        currentTitle = intent.getStringExtra("title") ?: ""
        currentThumbnailLink = intent.getStringExtra("thumbnailLink")
        val isMovieExtra = intent.getBooleanExtra(PlayerLauncher.EXTRA_IS_MOVIE, false)
        val initialParsed = EpisodeParser.parse(currentTitle)
        isMovie = isMovieExtra || (initialParsed.episode == null && (initialParsed.episodeBadge == "FILME" || initialParsed.isSpecial))
        currentEpNumber = if (isMovie) 0 else (initialParsed.episode?.toInt() ?: 0)
        val siblingFolderId = intent.getStringExtra(PlayerLauncher.EXTRA_SIBLING_FOLDER_ID)

        @Suppress("DEPRECATION")
        val rawPlaylist = intent.getSerializableExtra("playlist") as? ArrayList<PlaylistItem>
        if (rawPlaylist != null && rawPlaylist.isNotEmpty()) {
            playlist = rawPlaylist.toMutableList()
            updateNextEpisode()
        } else if (currentFileId.isNotEmpty()) {
            viewModel.fetchSiblings(currentFileId, siblingFolderId)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playlistChannel.consumeAsFlow().collect { list ->
                    if (list.message != null) {
                        configSnackbar(list.message, 3000)
                    }
                    if (list.items.isNotEmpty()) {
                        playlist = PlaybackPlaylistPolicy.merge(playlist, list.items).toMutableList()
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
            viewModel.fetchSubtitles(currentFileId, siblingFolderId)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.subtitlesChannel.consumeAsFlow().collect { list ->
                    if (list.message != null) {
                        configSnackbar(list.message, 3000)
                    }
                    if (list.items.isNotEmpty()) {
                        folderSubtitles = PlaybackPlaylistPolicy.mergeSubtitles(folderSubtitles, list.items).toMutableList()
                        loadMatchingExternalSubtitles()
                    }
                }
            }
        }

        player.initialize(filesDir.path)
        MPVLib.setOptionString("audio-pitch-correction", "yes")
        player.addObserver(this)

        lifecycleScope.launch {
            try {
                val style = appSettings.get().fetchSubtitleStyle()
                applyMpvSubtitleStyle(style)
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed loading MPV subtitle style", e)
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
                AppLog.w(TAG, "Audio focus not granted")
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
            skipIntroRow.setOnClickListener { performSkipIntroOrCredits() }
            btnSkipIntroBack.setOnClickListener { skipRelative(-90) }
            btnAudio.setOnClickListener { pickAudio() }
            btnSubtitle.setOnClickListener { pickSub() }
            btnEpisodes.setOnClickListener { showEpisodesDrawer() }
            btnChapter.setOnClickListener { pickChapter() }
            btnSpeed.setOnClickListener { pickSpeed() }
            btnResize.setOnClickListener {
                currentResizeMode = (currentResizeMode + 1) % 3
                val modeLabel = when (currentResizeMode) {
                    0 -> {
                        MPVLib.setPropertyDouble("panscan", 0.0)
                        MPVLib.setPropertyString("video-aspect-override", "-1")
                        "Modo de Vídeo: Ajustar (Original)"
                    }
                    1 -> {
                        MPVLib.setPropertyDouble("panscan", 1.0)
                        MPVLib.setPropertyString("video-aspect-override", "-1")
                        "Modo de Vídeo: Zoom (Cortar Bordas)"
                    }
                    2 -> {
                        MPVLib.setPropertyDouble("panscan", 0.0)
                        MPVLib.setPropertyString("video-aspect-override", "16:9")
                        "Modo de Vídeo: Esticar (16:9)"
                    }
                    else -> "Modo de Vídeo"
                }
                Snackbar.make(binding.root, modeLabel, 1000).apply {
                    anchorView = controller.controlsScrollView
                }.show()
                zechs.drive.stream.utils.TvFocusRing.clear(binding.root)
            }

            val isTvDevice = DeviceUi.isTenFootExperience(this@MPVActivity)
            btnRotate.visibility = if (isTvDevice) View.GONE else View.VISIBLE

            btnRotate.setOnClickListener {
                orientation = getNextOrientation(orientation)
                AppLog.d(TAG, "orientation=${orientation}")
                setOrientation(this@MPVActivity, orientation)
            }

            btnInfo.setOnClickListener {
                player.loadTracks()
                val subtitleId = MPVLib.getPropertyString("sid")
                val subtitleTrack = subtitleId?.toIntOrNull()?.takeIf { it > 0 }?.let { selectedId ->
                    player.tracks["sub"]?.firstOrNull { it.mpvId == selectedId }
                }
                    ?.let { track -> track.title?.takeIf(String::isNotBlank) ?: track.name }
                val properties = MpvTechnicalInfoDisplay.rows(
                    videoCodec = MPVLib.getPropertyString("video-codec"),
                    width = player.videoW,
                    height = player.videoH,
                    containerFps = MPVLib.getPropertyDouble("container-fps"),
                    activeDecoder = MPVLib.getPropertyString("hwdec-current"),
                    audioCodec = MPVLib.getPropertyString("audio-codec"),
                    channels = MPVLib.getPropertyString("audio-params/hr-channels")
                        ?: MPVLib.getPropertyString("audio-params/channels"),
                    sampleRateHz = MPVLib.getPropertyInt("audio-params/samplerate"),
                    subtitleTrack = MpvTechnicalInfoDisplay.subtitleSelection(subtitleId, subtitleTrack),
                    cacheDurationSeconds = MPVLib.getPropertyDouble("demuxer-cache-duration"),
                    engineName = "MPV (libmpv)",
                    hdrSummary = currentHdrStatus?.takeIf { it.isHdrActive }?.colorSpaceDesc,
                    afrSummary = currentAfrState?.technicalSummary
                )
                zechs.drive.stream.ui.player.PlayerGlassMenuDialog(this@MPVActivity, "Informações técnicas",
                    properties.map { row -> zechs.drive.stream.ui.player.GlassMenuItem(
                        id = row.id,
                        title = row.title,
                        subtitle = row.value,
                        isInteractive = false
                    ) }) {}.show()
            }
            zechs.drive.stream.ui.player.PlayerQuickOptions.bind(root)

            btnLock.setOnClickListener {
                controlsLocked = true
                handleLockingControls()
            }

            btnUnlock.setOnClickListener {
                controlsLocked = false
                handleLockingControls()
            }

            btnPip.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    enterPIPMode()
                } else {
                    Toast.makeText(
                        this@MPVActivity,
                        getString(R.string.pip_not_supported),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

        gestureHelper = PlayerGestureHelper(
            activity = this,
            hudBinding = binding.gestureHud,
            callback = object : PlayerGestureCallback {
                override fun onToggleControls() {
                    if (isControlsShowing()) {
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
                override fun isControllerVisible(): Boolean = isControlsShowing()
                override fun getTouchIgnoredViews(): List<View> = listOf(
                    controller.playerToolbar,
                    controller.btnBack,
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
                if (!isControlsShowing()) {
                    showControlsWithFocus()
                } else {
                    hideControls()
                }
            }
        }

        updateOrientation(resources.configuration)
        setupMediaSession()

    }

    private fun setupMediaSession() {
        mediaSessionHelper = MakimonoMediaSessionHelper(
            context = this,
            tag = "MPVSession",
            callback = object : MakimonoMediaSessionHelper.Callback {
                override fun onPlay() {
                    player.paused = false
                }

                override fun onPause() {
                    player.paused = true
                }

                override fun onSkipToNext() {
                    playNextEpisodeDirectly()
                }

                override fun onSkipToPrevious() {
                    playPrevEpisodeDirectly()
                }

                override fun onSeekTo(positionMs: Long) {
                    player.timePos = (positionMs / 1000L).toInt()
                }

                override fun onFastForward() {
                    skipForward()
                }

                override fun onRewind() {
                    rewindBackward()
                }

                override fun onStop() {
                    player.paused = true
                    finish()
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPIPMode() {
        val width = player.videoW ?: 0
        val height = player.videoH ?: 0
        MakimonoPiPHelper.enterPiP(this, width, height)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && !(player.paused ?: true)) {
            val width = player.videoW ?: 0
            val height = player.videoH ?: 0
            MakimonoPiPHelper.enterPiP(this, width, height)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::controller.isInitialized) {
            controller.root.post {
                if (isControlsShowing() && !controller.root.hasFocus()) {
                    controller.btnPlayPause.requestFocus()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hideControls()
        } else {
            showControlsWithFocus()
        }
    }

    private fun updateNextEpisode() {
        playbackCoordinator.setPlaylist(playlist, currentFileId, currentTitle)
        val coordinatorState = playbackCoordinator.state.value
        nextEpisode = coordinatorState.nextEpisode
        prevEpisode = coordinatorState.prevEpisode
        val navState = zechs.drive.stream.playback.session.NextEpisodePolicy.resolveNavigationControls(
            isMovie = isMovie,
            playlistSize = playlist.size,
            prevEpisode = prevEpisode,
            nextEpisode = nextEpisode
        )

        if (::controller.isInitialized) {
            controller.btnEpisodes.visibility = if (navState.showEpisodesDrawerButton) View.VISIBLE else View.GONE
            controller.btnPrevEp.isEnabled = navState.canSkipPrevious
            controller.btnPrevEp.alpha = navState.prevButtonAlpha
            controller.btnNextEp.isEnabled = navState.canSkipNext
            controller.btnNextEp.alpha = navState.nextButtonAlpha
        }
        mediaSessionHelper?.updatePlaybackState(
            isPlaying = if (::player.isInitialized) !(player.paused ?: true) else false,
            positionMs = if (::player.isInitialized) ((player.timePos ?: 0) * 1000L).coerceAtLeast(0L) else 0L,
            speed = (MPVLib.getPropertyDouble("speed") ?: 1.0).toFloat(),
            canSkipNext = navState.canSkipNext,
            canSkipPrevious = navState.canSkipPrevious
        )
        AppLog.d(TAG, "updateNextEpisode: prevEpisode=${prevEpisode?.title}, nextEpisode=${nextEpisode?.title}")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Modal pickers own D-pad and BACK while open; do not let the player
        // shortcuts change tracks or leave playback behind the dialog.
        if (playerModalVisibilityState.isVisible) return super.dispatchKeyEvent(event)

        // Give the focused exit button priority over skip-intro/next-episode shortcuts.
        val activateExit = event.keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A
        ) && controller.btnBack.isFocused

        if (activateExit) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) finish()
            return true
        }

        val isBackKey = event.keyCode in listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B
        )

        if (isBackKey) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                if (!handleBackNavigation()) {
                    finish()
                }
            }
            return true
        }

        val isHorizontalDpad = event.keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT
        )
        if (isControlsShowing() && isHorizontalDpad) {
            if (controller.btnBack.isFocused) {
                return true
            }
            val isTimeBarFocused = controller.progressBar.isFocused
            if (!isTimeBarFocused) {
                val isAtLeftEdge = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    controller.btnSubtitle.isFocused
                val isAtRightEdge = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                    controller.btnInfo.isFocused
                if (isAtLeftEdge || isAtRightEdge) {
                    return true
                }
            }
        }

        if (handledFocusDirectionKeyCode == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) handledFocusDirectionKeyCode = null
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> zechs.drive.stream.utils.PlayerFocusDirection.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> zechs.drive.stream.utils.PlayerFocusDirection.DOWN
                else -> null
            }
            val focusedItem = when {
                controller.btnBack.isFocused -> zechs.drive.stream.utils.PlayerFocusItem.EXIT
                controller.btnPlayPause.isFocused -> zechs.drive.stream.utils.PlayerFocusItem.PLAY_PAUSE
                else -> zechs.drive.stream.utils.PlayerFocusItem.OTHER
            }
            val target = direction?.let {
                zechs.drive.stream.utils.PlayerDpadFocus.next(
                    direction = it,
                    controlsVisible = isControlsShowing(),
                    controlsTransitioning = if (isOsdInitialized) osdController.isHiding else false,
                    blockingOverlayVisible = binding.nextEpisodeCard.root.isVisible ||
                        binding.netflixSkipRow.isVisible,
                    focusedItem = focusedItem
                )
            }
            if (target != null) {
                handledFocusDirectionKeyCode = event.keyCode
                when (target) {
                    zechs.drive.stream.utils.PlayerFocusItem.EXIT -> controller.btnBack.requestFocus()
                    zechs.drive.stream.utils.PlayerFocusItem.PLAY_PAUSE -> controller.btnPlayPause.requestFocus()
                    zechs.drive.stream.utils.PlayerFocusItem.OTHER -> Unit
                }
                return true
            }
        }

        val isDpadNavigationKey = event.keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT
        )
        if (
            isControlsShowing() &&
            !controller.root.hasFocus() &&
            !binding.nextEpisodeCard.root.isVisible &&
            !binding.netflixSkipRow.isVisible &&
            isDpadNavigationKey
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) controller.btnPlayPause.requestFocus()
            return true
        }

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    mainHandler.removeCallbacks(centerLongPressRunnable)
                    if (isCenterLongPressSpeeding) {
                        isCenterLongPressSpeeding = false
                        MPVLib.setPropertyDouble("speed", speedBeforeCenterHold)
                        gestureHelper.hideSpeedPill()
                        ignoreNextCenterKeyUp = true
                        return true
                    }
                    if (ignoreNextCenterKeyUp) {
                        ignoreNextCenterKeyUp = false
                        return true
                    }
                    if (!isControlsShowing()) {
                        showControlsWithFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isControlsShowing()) {
                        return true
                    }
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (binding.nextEpisodeCard.root.isVisible) {
                        if (binding.nextEpisodeCard.btnCancelNextEpisode.isFocused) {
                            dismissNextEpisodeCard(isManualCancel = true)
                        } else {
                            playNextEpisodeDirectly()
                        }
                        return true
                    }
                    if (binding.netflixSkipRow.isVisible) {
                        performSkipIntroOrCredits()
                        return true
                    }
                    if (!isControlsShowing()) {
                        if (event.repeatCount == 0) {
                            mainHandler.removeCallbacks(centerLongPressRunnable)
                            mainHandler.postDelayed(centerLongPressRunnable, 350L)
                        } else if (event.repeatCount > 0 && !isCenterLongPressSpeeding) {
                            mainHandler.removeCallbacks(centerLongPressRunnable)
                            centerLongPressRunnable.run()
                        }
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!isControlsShowing()) {
                        mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
                        val deltaSeconds = seekStepPolicy.onSeekPress(-1)
                        MPVLib.command(arrayOf("seek", deltaSeconds.toString(), "relative"))
                        gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
                        mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isControlsShowing()) {
                        mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
                        val deltaSeconds = seekStepPolicy.onSeekPress(1)
                        MPVLib.command(arrayOf("seek", deltaSeconds.toString(), "relative"))
                        gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
                        mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isControlsShowing()) {
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

                KeyEvent.KEYCODE_CAPTIONS -> {
                    pickSub()
                    return true
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

    private fun handleBackNavigation(): Boolean {
        if (binding.nextEpisodeCard.root.isVisible) {
            dismissNextEpisodeCard(isManualCancel = true)
            return true
        }
        if (binding.netflixSkipRow.isVisible) {
            binding.netflixSkipRow.visibility = View.GONE
            return true
        }
        if (isOsdInitialized && osdController.shouldConsumeBack) {
            if (osdController.isVisible) {
                hideControls()
                gestureHelper.showNotification(getString(R.string.player_back_again_to_exit))
            }
            return true
        }
        return false
    }


    private fun showControlsWithFocus() {
        if (!isOsdInitialized) return
        if (osdController.isVisible) {
            if (!controller.root.hasFocus()) controller.btnPlayPause.requestFocus()
            return
        }
        osdController.show()
    }

    private fun hideControls() {
        if (!isOsdInitialized) return
        osdController.hide()
    }

    override fun onPlayerModalVisibilityChanged(isVisible: Boolean) {
        val transition = if (isVisible) {
            playerModalVisibilityState.onModalOpened(osdWasVisible = isControlsShowing())
        } else {
            playerModalVisibilityState.onModalClosed()
        }

        when (transition) {
            PlayerModalTransition.HIDE_OSD -> {
                modalReturnFocus = controller.root.findFocus()?.takeIf { it.isFocusable }
                hideControls()
            }
            PlayerModalTransition.RESTORE_OSD -> if (!isFinishing && !isDestroyed) {
                showControlsWithFocus()
                val target = modalReturnFocus
                modalReturnFocus = null
                zechs.drive.stream.utils.LifecycleDelays.postDelayed(controller.root, 250L, this@MPVActivity) {
                    if (!isControlsShowing()) return@postDelayed
                    target?.takeIf { it.isShown && it.isFocusable }
                        ?.requestFocus()
                        ?: controller.btnPlayPause.requestFocus()
                }
            }
            PlayerModalTransition.NONE -> Unit
        }
    }

    private fun mergeAniSkipChapters(aniSkipChapters: List<MatroskaChapterParser.ParsedChapter>) {
        if (aniSkipChapters.isEmpty()) return
        skipController.mergeAniSkipChapters(aniSkipChapters)
        parsedChapters = skipController.getChapters()
        AppLog.d(TAG, "[MPV Chapters] Updated parsedChapters with AniSkip: ${parsedChapters.size} total")
        val durationSec = player.duration ?: 0
        if (durationSec > 0) {
            val markers = parsedChapters.map { ch ->
                ChapterMarker(
                    startMs = ch.startTimeMs,
                    endMs = ch.endTimeMs,
                    color = if (ch.type == ChapterType.OPENING || ch.type == ChapterType.ENDING) {
                        0xFF22D3EE.toInt()
                    } else {
                        0xCCFFFFFF.toInt()
                    }
                )
            }
            controller.chapterMarkersView.setMarkers(durationSec * 1000L, markers)
        }
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
    }

    private fun checkChapterAutoSkip(currentTimeSeconds: Double) {
        if (controlsLocked || isNextEpisodeCardShowing) {
            binding.netflixSkipRow.animate().cancel()
            binding.netflixSkipRow.visibility = View.GONE
            controller.skipIntroRow.animate().cancel()
            controller.skipIntroRow.visibility = View.GONE
            return
        }

        val positionMs = (currentTimeSeconds * 1000).toLong()
        val (skipState, skipAction) = skipController.onPlaybackTick(
            positionMs = positionMs,
            autoSkipEnabled = malSessionManager.get().isAutoSkipEnabled(),
            controllerVisible = controller.root.isVisible,
            controlsLocked = controlsLocked
        )

        activeSkipChapter = skipState.activeChapter

        if (skipAction is zechs.drive.stream.playback.session.SkipController.SkipAction.AutoSkip) {
            val seekSeconds = skipAction.targetPositionMs / 1000.0
            MPVLib.command(arrayOf("seek", seekSeconds.toString(), "absolute"))
            gestureHelper.showNotification(skipAction.message)
            return
        }

        when (val ui = skipState.buttonUi) {
            is zechs.drive.stream.playback.session.SkipController.SkipButtonUi.Visible -> {
                binding.btnNetflixSkip.text = ui.label
                controller.btnSkipIntro.text = ui.label
                if (ui.isOverlay) {
                    if (binding.netflixSkipRow.visibility != View.VISIBLE) {
                        binding.netflixSkipRow.animate().cancel()
                        binding.netflixSkipRow.alpha = 0f
                        binding.netflixSkipRow.visibility = View.VISIBLE
                        val motionEnter = resources.getInteger(R.integer.motion_pill_enter).toLong()
                        binding.netflixSkipRow.animate().alpha(1f).setDuration(motionEnter).start()
                    }
                } else {
                    binding.netflixSkipRow.animate().cancel()
                    binding.netflixSkipRow.visibility = View.GONE
                }
                if (controller.root.isVisible && !controller.skipIntroRow.isVisible && !controlsLocked) {
                    controller.skipIntroRow.animate().cancel()
                    controller.skipIntroRow.alpha = 0f
                    controller.skipIntroRow.visibility = View.VISIBLE
                    val motionEnter = resources.getInteger(R.integer.motion_pill_enter).toLong()
                    controller.skipIntroRow.animate().alpha(1f).setDuration(motionEnter).start()
                }
            }
            zechs.drive.stream.playback.session.SkipController.SkipButtonUi.Hidden -> {
                val motionExit = resources.getInteger(R.integer.motion_pill_exit).toLong()
                if (binding.netflixSkipRow.visibility == View.VISIBLE) {
                    binding.netflixSkipRow.animate().cancel()
                    binding.netflixSkipRow.animate().alpha(0f).setDuration(motionExit).withEndAction {
                        binding.netflixSkipRow.visibility = View.GONE
                    }.start()
                }
                if (controller.skipIntroRow.isVisible) {
                    controller.skipIntroRow.animate().cancel()
                    controller.skipIntroRow.animate().alpha(0f).setDuration(motionExit).withEndAction {
                        controller.skipIntroRow.visibility = View.GONE
                    }.start()
                }
            }
        }
    }

    private fun performSkipIntroOrCredits() {
        val currentMs = ((player.timePos ?: 0) * 1000).toLong()
        val targetMs = skipController.performSkip(currentMs)
        if (targetMs != null) {
            val targetSec = targetMs / 1000.0
            AppLog.d(TAG, "[Chapters] Skipping to ${targetSec}s")
            MPVLib.command(arrayOf("seek", targetSec.toString(), "absolute"))
            val chapter = skipController.getActiveChapter()
            val snackText = when (chapter?.type) {
                ChapterType.RECAP -> "⏩ Recap pulado"
                ChapterType.OPENING -> "⏩ Abertura pulada"
                ChapterType.ENDING -> "⏩ Encerramento pulado"
                else -> if (chapter != null) "⏩ Capítulo pulado" else "⏩ +90s Pular Abertura"
            }
            configSnackbar(snackText)
        } else {
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
            skipController.setChapters(emptyList())
            AppLog.d(TAG, "[Chapters] No chapters found for this file.")
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
                    startTimeMs = (startTime * 1000).toLong(),
                    endTimeMs = (endTime * 1000).toLong(),
                    type = type
                )
            )
        }

        skipController.setChapters(list)
        parsedChapters = skipController.getChapters()
        AppLog.d(TAG, "[Chapters] Parsed ${parsedChapters.size} chapters successfully:")
        parsedChapters.forEach {
            AppLog.d(TAG, "[Chapters] #${it.index} '${it.title}' (${it.startTimeMs}ms -> ${it.endTimeMs}ms, type=${it.type})")
        }
        val durationSec = player.duration ?: 0
        if (durationSec > 0) {
            val markers = parsedChapters.map { ch ->
                ChapterMarker(
                    startMs = ch.startTimeMs,
                    endMs = ch.endTimeMs,
                    color = if (ch.type == ChapterType.OPENING || ch.type == ChapterType.ENDING) {
                        0xFF22D3EE.toInt()
                    } else {
                        0xCCFFFFFF.toInt()
                    }
                )
            }
            controller.chapterMarkersView.setMarkers(durationSec * 1000L, markers)
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
        AppLog.v(TAG, "Audio focus changed: $type")
        when (type) {
            AUDIOFOCUS_LOSS -> {
                player.paused = true
                audioFocusRestore = {}
            }
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
        if (i == null) return
        setIntent(i)
        currentFileId = i.getStringExtra("fileId") ?: ""
        currentLocalUri = i.getStringExtra(PlayerLauncher.EXTRA_LOCAL_URI)
        currentTitle = i.getStringExtra("title") ?: ""
        currentThumbnailLink = i.getStringExtra("thumbnailLink")
        playMedia()
    }

    private var mediaLoadJob: kotlinx.coroutines.Job? = null

    private fun playMedia() {
        mediaLoadJob?.cancel()
        mediaLoadJob = lifecycleScope.launch {
            val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId").orEmpty() }
            val title = currentTitle.ifBlank { intent.getStringExtra("title").orEmpty() }
            val reviewMediaUri = intent.getStringExtra("reviewMediaUri")
                ?.takeIf { BuildConfig.DEBUG && it.isNotBlank() }
            if (reviewMediaUri != null) {
                if (!isFinishing) prepareLocalReviewMedia(reviewMediaUri, title)
                return@launch
            }

            val accessToken = withContext(Dispatchers.IO) { tokenProvider.get().validToken() }
            if (isFinishing || fileId != currentFileId) return@launch
            if (fileId.isBlank() || accessToken == null) {
                Toast.makeText(this@MPVActivity, R.string.drive_session_unavailable, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val explicitStart = intent.getLongExtra("startPosition", -1L)
            intent.removeExtra("startPosition")
            val resumeMs = if (explicitStart >= 0L) explicitStart else viewModel.resumePosition(fileId)
            if (isFinishing || fileId != currentFileId) return@launch
            prepareMedia(fileId, title, accessToken, resumeMs)
        }
    }

    private fun prepareLocalReviewMedia(uri: String, title: String) {
        hasScrobbledThisEp = false
        hasPromptedRating = false
        currentMalAnime = null
        currentFileId = ""
        currentTitle = title
        currentThumbnailLink = null
        isFileLoaded = false
        pendingResumePositionMs = 0L
        pendingSubtitles.clear()
        hasAutoSelectedMpvTracks = false

        val parsed = EpisodeParser.parse(title)
        controller.playerToolbar.title = title
        controller.tvPlayerTitle.text = parsed.showTitle.ifBlank { parsed.cleanTitle.ifBlank { title } }
        controller.tvPlayerMeta.text = "Prévia local · sem conta Drive"
        mediaSessionHelper?.updateMetadata(
            title = parsed.cleanTitle.ifBlank { title },
            seriesName = parsed.showTitle.ifBlank { null },
            durationMs = 0L
        )

        MPVLib.setOptionString("http-header-fields", "")
        MPVLib.setOptionString("loop-file", "inf")
        player.play(uri)
    }

    private fun prepareMedia(fileId: String, title: String, accessToken: String, resumeMs: Long) {
        hasScrobbledThisEp = false
        hasPromptedRating = false
        currentMalAnime = null
        val seriesTitleExtra = intent.getStringExtra(PlayerLauncher.EXTRA_SERIES_TITLE)
        val isMovieExtra = intent.getBooleanExtra(PlayerLauncher.EXTRA_IS_MOVIE, false)
        val parsed = EpisodeParser.parse(title ?: "")
        isMovie = isMovieExtra || (parsed.episode == null && (parsed.episodeBadge == "FILME" || parsed.isSpecial))
        currentEpNumber = if (isMovie) 0 else (parsed.episode?.toInt() ?: 0)

        val prewarmed = bingeBufferManager.get().consumePrewarmedData(fileId)
        if (prewarmed != null && prewarmed.aniSkipChapters.isNotEmpty()) {
            mergeAniSkipChapters(prewarmed.aniSkipChapters)
            AppLog.d(TAG, "Applied pre-warmed AniSkip chapters in MPV: ${prewarmed.aniSkipChapters.size} (0ms latency)")
        }

        val searchTitle = parsed.showTitle.ifBlank { title ?: "" }
        if (searchTitle.isNotBlank() && !isMovie && currentEpNumber > 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val matched = malRepository.get().matchAnime(searchTitle)
                    if (matched != null) {
                        currentMalAnime = matched
                        AppLog.d(TAG, "MAL matched anime: '${matched.title}' (ID: ${matched.id}, total eps: ${matched.numEpisodes})")

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
                    AppLog.w(TAG, "Failed to match anime on MAL / AniSkip", e)
                }
            }
        }

        AppLog.d(TAG, "MPVActivity(fileId=$fileId, title=$title, accessToken=${accessToken?.let { "len=${it.length}" } ?: "null"})")

        viewModel.getWatch(fileId)
        lifecycleScope.launch {
            val (savedAudioDelay, savedSubDelay) = viewModel.getDelays(fileId)
            currentAudioDelayMs = savedAudioDelay
            currentSubtitleDelayMs = savedSubDelay
            if (savedAudioDelay != 0L) {
                MPVLib.setPropertyDouble("audio-delay", savedAudioDelay / 1000.0)
                AppLog.d(TAG, "Restored saved audio delay: ${savedAudioDelay}ms")
            }
            if (savedSubDelay != 0L) {
                MPVLib.setPropertyDouble("sub-delay", savedSubDelay / 1000.0)
                AppLog.d(TAG, "Restored saved subtitle delay: ${savedSubDelay}ms")
            }
        }

        val displayTitle = if (isMovie && !seriesTitleExtra.isNullOrBlank()) {
            seriesTitleExtra
        } else {
            parsed.showTitle.ifBlank { parsed.cleanTitle.ifBlank { title ?: "" } }
        }

        controller.playerToolbar.title = displayTitle
        controller.tvPlayerTitle.text = displayTitle
        val epText = PlaybackLabel.renderSubtitle(this, isMovie, currentEpNumber)
        controller.tvPlayerMeta.text = epText
        if (::controller.isInitialized) {
            controller.btnEpisodes.visibility = if (isMovie || playlist.size <= 1) View.GONE else View.VISIBLE
        }

        mediaSessionHelper?.updateMetadata(
            title = parsed.cleanTitle.ifBlank { title ?: "" },
            seriesName = parsed.showTitle.ifBlank { null },
            durationMs = ((player.duration ?: 0) * 1000L).coerceAtLeast(0L)
        )

        val rawUri = getStreamUrl(fileId)
        val isLocal = MediaUriResolver.isLocalUri(rawUri)
        val playUri = if (rawUri.startsWith("content://")) {
            try {
                contentResolver.openFileDescriptor(Uri.parse(rawUri), "r")?.let { pfd ->
                    "fd://${pfd.detachFd()}"
                } ?: rawUri
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to resolve file descriptor for $rawUri", e)
                rawUri
            }
        } else {
            rawUri
        }
        hasAutoSelectedMpvTracks = false
        isFileLoaded = false
        pendingResumePositionMs = resumeMs.coerceAtLeast(0L)
        pendingSubtitles.clear()
        if (isLocal) {
            MPVLib.setOptionString("http-header-fields", "")
        } else {
            MPVLib.setOptionString(
                "http-header-fields",
                "Authorization: Bearer $accessToken"
            )
        }
        player.play(playUri)

        // Load external Drive subtitles (.ass, .srt, etc.)
        if (folderSubtitles.isNotEmpty()) {
            loadMatchingExternalSubtitles()
        } else {
            viewModel.fetchSubtitles(fileId)
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
        val subTag = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "[Local]" else "[Drive]"
        val trackTitle = if (sub.id.startsWith("saved_")) sub.name else "★ $subTag ${sub.languageLabel}"
        AppLog.d(TAG, "Adding external subtitle to MPV: ${cached.absolutePath} (mode=$selectMode)")
        MPVLib.command(arrayOf("sub-add", cached.absolutePath, selectMode, trackTitle, sub.languageCode))
        if (isPortuguese) {
            configSnackbar("Legenda ativada: $trackTitle")
        }
    }

    private fun resumeVideo(startPosition: Long) {
        val startPositionInSeconds = startPosition / 1000
        AppLog.d(TAG, "MPV(resumeVideo=${Utils.prettyTime(startPositionInSeconds.toInt())})")
        MPVLib.setOptionString("start", Utils.prettyTime(startPositionInSeconds.toInt()))
    }

    private fun getStreamUrl(fileId: String): String {
        val streamUrl = MediaUriResolver.resolveStreamUri(fileId, currentLocalUri)
        AppLog.d(TAG, "STREAM_URL=$streamUrl")
        return streamUrl
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
        mediaSessionHelper?.updateMetadata(
            title = controller.tvPlayerTitle.text?.toString() ?: (currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }),
            seriesName = controller.tvPlayerMeta.text?.toString(),
            durationMs = duration * 1000L
        )
        val width = player.videoW ?: 0
        val height = player.videoH ?: 0
        MakimonoPiPHelper.updatePiPParams(this, width, height, isPlaying = !(player.paused ?: true))
    }

    private fun updatePlaybackStatus(paused: Boolean) {
        TransitionManager.beginDelayedTransition(
            controller.mainControls,
            AutoTransition().apply { duration = resources.getInteger(R.integer.motion_spec_normal).toLong() }
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

        mediaSessionHelper?.updatePlaybackState(
            isPlaying = !paused,
            positionMs = ((player.timePos ?: 0) * 1000L).coerceAtLeast(0L),
            speed = (MPVLib.getPropertyDouble("speed") ?: 1.0).toFloat(),
            canSkipNext = nextEpisode != null,
            canSkipPrevious = prevEpisode != null
        )
        val width = player.videoW ?: 0
        val height = player.videoH ?: 0
        MakimonoPiPHelper.updatePiPParams(this, width, height, isPlaying = !paused)
    }

    private fun skipForward() {
        mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
        val deltaSeconds = seekStepPolicy.onSeekPress(1)
        skipRelative(deltaSeconds)
        gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
        mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
    }

    private fun rewindBackward() {
        mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
        val deltaSeconds = seekStepPolicy.onSeekPress(-1)
        skipRelative(deltaSeconds)
        gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
        mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
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

        val nightModeLabel = if (isNightModeAudioEnabled) "Modo Noturno (Ativo)" else "Modo Noturno (Desat.)"
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
        }.setFooterPrimary(nightModeLabel) {
            toggleNightModeAudio()
        }.show()
    }

    private fun toggleNightModeAudio() {
        isNightModeAudioEnabled = !isNightModeAudioEnabled
        if (isNightModeAudioEnabled) {
            MPVLib.setOptionString("af", "lavfi=[dynaudnorm=f=250:g=31:p=0.5]")
            configSnackbar("Modo Noturno ativado: Vozes realçadas e picos balanceados", 3000)
        } else {
            MPVLib.setOptionString("af", "")
            configSnackbar("Modo Noturno desativado: Áudio original", 2500)
        }
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
            val fid = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: "" }
            if (fid.isNotBlank()) {
                viewModel.saveDelays(fid, currentAudioDelayMs, currentSubtitleDelayMs)
            }
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

        // 4. ALL folder subtitles: ALWAYS list them!
        folderSubtitles.forEach { sub ->
            val subTag = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "[Local]" else "[Drive]"
            val sourceDesc = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "Armazenamento Local" else "Google Drive"
            val alreadyPresent = choices.any {
                it.label.contains(sub.name, ignoreCase = true) ||
                (it.label.contains(subTag, ignoreCase = true) && it.label.contains(sub.languageLabel, ignoreCase = true))
            }
            if (!alreadyPresent) {
                val isMatching = sub.matchesVideo(videoTitle)
                val prefix = if (isMatching) "★ $subTag " else "📁 $subTag "
                val tag = if (isMatching) " (Episódio atual)" else " (Outro episódio)"
                val label = "$prefix${sub.name}$tag"
                choices.add(SubChoice(null, sub, null, null, label, sourceDesc, false))
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
                AppLog.d(TAG, "Manual add saved subtitle to MPV: ${saved.file.absolutePath}")
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
                            val subTag = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "[Local]" else "[Drive]"
                            val trackTitle = "★ $subTag ${sub.languageLabel}"
                            AppLog.d(TAG, "Manual add external subtitle to MPV: ${cached.absolutePath}")
                            MPVLib.command(arrayOf("sub-add", cached.absolutePath, "select", trackTitle, sub.languageCode))
                            player.loadTracks()
                            configSnackbar("Legenda ativada: ${sub.languageLabel}")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            val errMsg = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) {
                                "Erro ao carregar legenda local"
                            } else {
                                "Erro ao baixar legenda do Drive"
                            }
                            configSnackbar(errMsg)
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

        glassDialog.setSubtitle("Faixas embutidas, salvas e na nuvem • Verificação de idioma ativa")

        glassDialog.setActionButton("✨ Central de Legendas Oficiais (Download Rápido)") { dialog ->
            dialog.dismiss()
            showOnlineSubtitleHubDialog(videoTitle)
        }

        glassDialog.setFooterTertiary("Aparência") {
            openSubtitleStyleDialog()
        }

        glassDialog.setFooterSecondary("Sincronia") {
            showSubtitleSyncDialog()
        }

        glassDialog.setFooterPrimary("Tamanho") {
            showSubtitleSizeDialog()
        }

        glassDialog.show()
    }

    private fun showOnlineSubtitleHubDialog(
        videoTitle: String,
        initialLanguage: zechs.drive.stream.utils.SubtitleLanguage = zechs.drive.stream.utils.SubtitleLanguage.PT_BR
    ) {
        val seriesTitleExtra = intent.getStringExtra(PlayerLauncher.EXTRA_SERIES_TITLE)
        val parsed = EpisodeParser.parse(videoTitle)
        val seasonNum = parsed.season
        val epDouble = if (currentEpNumber > 0) currentEpNumber.toDouble() else parsed.episode

        var activeLang = initialLanguage
        var currentResults: List<OnlineSubtitle> = emptyList()

        fun buildHubItems(subs: List<OnlineSubtitle>, lang: zechs.drive.stream.utils.SubtitleLanguage): List<GlassMenuItem> {
            if (subs.isEmpty()) {
                return listOf(
                    GlassMenuItem(
                        id = "empty_online",
                        title = "Nenhuma legenda encontrada para ${lang.displayName}",
                        subtitle = "Experimente alternar a pílula de idioma acima (PT-BR, PT-PT, EN, ES, JA)",
                        isInteractive = false
                    )
                )
            }
            return subs.mapIndexed { idx, os ->
                val flag = zechs.drive.stream.utils.SubtitleLanguage.flagForCode(os.lang)
                val titleBadge = if (os.isOfficialQuality) {
                    " • ★ Oficial (${os.confidenceScore}%)"
                } else if (os.isVerifiedLanguage) {
                    " • ✓ ${os.confidenceScore}% Certeza"
                } else {
                    " • ${os.confidenceScore}%"
                }
                val itemTitle = "$flag ${os.langName}$titleBadge"
                val subLines = buildString {
                    append(os.qualityBadge)
                    if (!os.previewSample.isNullOrBlank()) {
                        append("\n💬 \"")
                        append(os.previewSample)
                        append("\"")
                    }
                }
                GlassMenuItem(
                    id = "hub_sub_$idx",
                    title = itemTitle,
                    subtitle = subLines,
                    isSelected = idx == 0 && lang.matchesLanguageCode(os.lang),
                    tag = os
                )
            }
        }

        lateinit var hubDialog: PlayerGlassMenuDialog

        fun triggerSearch(lang: zechs.drive.stream.utils.SubtitleLanguage) {
            activeLang = lang
            hubDialog.showLoading(true)
            hubDialog.updateActionButtonText("⚡ Download Rápido • Melhor Oficial (${lang.shortLabel})")
            hubDialog.updateSubtitle("Verificando certeza léxica (${lang.shortLabel}) e normalizando UTF-8...")

            lifecycleScope.launch(Dispatchers.IO) {
                val results = try {
                    onlineSubtitleManager.get().searchSubtitles(
                        rawTitle = videoTitle,
                        season = seasonNum,
                        episode = epDouble,
                        seriesTitle = seriesTitleExtra,
                        preferredLanguage = lang
                    )
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error in MPV OnlineSubtitleHub search", e)
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    currentResults = results
                    hubDialog.showLoading(false)
                    val verifiedCount = results.count { it.isVerifiedLanguage }
                    val matchedShow = results.firstOrNull()?.matchedSeriesTitle
                    val statusPrefix = if (!matchedShow.isNullOrBlank()) "$matchedShow • " else ""
                    hubDialog.updateSubtitle(
                        "${statusPrefix}${results.size} opções • $verifiedCount verificadas com alta certeza (${lang.shortLabel})"
                    )
                    hubDialog.updateItems(buildHubItems(results, lang))
                }
            }
        }

        fun downloadAndActivate(os: OnlineSubtitle, lang: zechs.drive.stream.utils.SubtitleLanguage) {
            configSnackbar("Baixando e verificando ${os.langName} (${os.confidenceScore}% certeza)...")
            lifecycleScope.launch(Dispatchers.IO) {
                val result = onlineSubtitleManager.get().downloadSubtitle(os, videoTitle, lang)
                withContext(Dispatchers.Main) {
                    result.onSuccess { cachedFile ->
                        val trackTitle = "💾 [Oficial] ${os.langName}"
                        MPVLib.command(arrayOf("sub-add", cachedFile.absolutePath, "select", trackTitle, os.lang))
                        player.loadTracks()
                        configSnackbar("✓ Legenda Oficial ativada: ${os.langName} (${os.confidenceScore}% certeza • UTF-8)", 1800)
                    }.onFailure { err ->
                        configSnackbar("Erro ao baixar legenda: ${err.message}")
                    }
                }
            }
        }

        val initialLoadingItems = listOf(
            GlassMenuItem(
                id = "loading_hub",
                title = "Buscando e verificando legendas oficiais...",
                subtitle = "Analisando vocabulário, ortografia e sincronia para ${initialLanguage.displayName}",
                isInteractive = false
            )
        )

        hubDialog = PlayerGlassMenuDialog(
            context = this,
            title = "Central de Legendas Oficiais",
            items = initialLoadingItems
        ) { selected ->
            val chosen = selected.tag as? OnlineSubtitle ?: return@PlayerGlassMenuDialog
            downloadAndActivate(chosen, activeLang)
        }
            .setSubtitle("Verificação Léxica de Idioma • UTF-8 Automático")
            .setLanguageSelector(initialLanguage) { _, newLang ->
                triggerSearch(newLang)
            }
            .setActionButton("⚡ Download Rápido • Melhor Oficial (${initialLanguage.shortLabel})") { d ->
                val best = currentResults.firstOrNull { activeLang.matchesLanguageCode(it.lang) }
                    ?: currentResults.firstOrNull()
                if (best != null) {
                    d.dismiss()
                    downloadAndActivate(best, activeLang)
                } else {
                    d.showLoading(true)
                    lifecycleScope.launch(Dispatchers.IO) {
                        val fastRes = onlineSubtitleManager.get().downloadBestSubtitle(
                            rawTitle = videoTitle,
                            season = seasonNum,
                            episode = epDouble,
                            seriesTitle = seriesTitleExtra,
                            preferredLanguage = activeLang
                        )
                        withContext(Dispatchers.Main) {
                            d.showLoading(false)
                            fastRes.onSuccess { (cachedFile, sub) ->
                                d.dismiss()
                                val trackTitle = "💾 [Oficial] ${sub.langName}"
                                MPVLib.command(arrayOf("sub-add", cachedFile.absolutePath, "select", trackTitle, sub.lang))
                                player.loadTracks()
                                configSnackbar("⚡ Legenda Oficial ativada: ${sub.langName} (${sub.confidenceScore}% certeza)", 1800)
                            }.onFailure { err ->
                                configSnackbar(err.message ?: "Nenhuma legenda encontrada")
                            }
                        }
                    }
                }
            }
            .setFooterSecondary("← Voltar às Faixas") {
                pickSub()
            }
            .setFooterPrimary("Aparência") {
                openSubtitleStyleDialog()
            }

        hubDialog.show()
        triggerSearch(initialLanguage)
    }

    private fun openSubtitleStyleDialog() {
        lifecycleScope.launch {
            val style = try {
                appSettings.get().fetchSubtitleStyle()
            } catch (_: Exception) {
                zechs.drive.stream.utils.SubtitleStyle()
            }
            zechs.drive.stream.ui.player.SubtitleStyleDialog.show(this@MPVActivity, style) { updated ->
                applyMpvSubtitleStyle(updated)
                lifecycleScope.launch {
                    try {
                        appSettings.get().saveSubtitleStyle(updated)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Error saving subtitle style", e)
                    }
                }
                configSnackbar("Estilo de legenda salvo", 1200)
            }
        }
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
            val fid = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: "" }
            if (fid.isNotBlank()) {
                viewModel.saveDelays(fid, currentAudioDelayMs, currentSubtitleDelayMs)
            }
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
                lifecycleScope.launch {
                    try {
                        val style = appSettings.get().fetchSubtitleStyle().copy(sizeSp = chosenSp)
                        appSettings.get().saveSubtitleStyle(style)
                        applyMpvSubtitleStyle(style)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Error saving subtitle size", e)
                    }
                }
                configSnackbar("Tamanho definido: ${selected.title}", 1000)
            }.show()
        }
    }

    private fun pickChapter() {
        val chapters = player.loadChapters()

        if (chapters.isEmpty()) {
            val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(getString(R.string.chapters))
                .setMessage(R.string.mpv_no_chapters_found)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .create()
            dialog.setOnDismissListener { onPlayerModalVisibilityChanged(false) }
            onPlayerModalVisibilityChanged(true)
            dialog.show()
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


    override fun updateOrientation(newConfig: Configuration) {
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
        if (!reduceMotionEnabled) {
            TransitionManager.beginDelayedTransition(
                controller.root,
                AutoTransition().apply { duration = resources.getInteger(R.integer.motion_osd_hide).toLong() }
            )
        }
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

    override fun getWatchedPositionMs(): Long = ((player.timePos ?: 0) * 1000L)
    override fun getTotalDurationMs(): Long = ((player.duration ?: 0) * 1000L)
    override fun getCurrentFileId(): String = currentFileId
    override fun getCurrentTitle(): String = currentTitle
    override fun getCurrentThumbnail(): String? = currentThumbnailLink
    override fun getAudioDelayMs(): Long? = currentAudioDelayMs
    override fun getSubtitleDelayMs(): Long? = currentSubtitleDelayMs
    override fun saveWatchProgress(
        title: String,
        fileId: String,
        watchedDuration: Long,
        totalDuration: Long,
        thumbnailLink: String?,
        audioDelayMs: Long?,
        subtitleDelayMs: Long?
    ) {
        viewModel.saveWatch(
            name = title,
            videoId = fileId,
            watchedDuration = watchedDuration,
            totalDuration = totalDuration,
            thumbnailLink = thumbnailLink,
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs
        )
    }

    private fun checkAutoPlayNextEpisode(timePosSeconds: Long, durationSeconds: Long) {
        if (durationSeconds > 60 && timePosSeconds >= (durationSeconds * 0.75).toLong()) {
            val next = nextEpisode
            if (next != null && !nextEpisodeCanceled) {
                lifecycleScope.launch {
                    if (appSettings.get().fetchPredictiveBuffering()) {
                        bingeBufferManager.get().prewarmNextEpisode(
                            scope = lifecycleScope,
                            nextItem = next,
                            currentMalAnime = currentMalAnime,
                            folderSubtitles = folderSubtitles
                        )
                    }
                }
            }
        }

        val isEndingActive = activeSkipChapter?.type == ChapterType.ENDING
        val decision = PlaybackProgressPolicy.evaluateAutoplay(
            positionMs = timePosSeconds * 1000L,
            durationMs = durationSeconds * 1000L,
            hasNextEpisode = nextEpisode != null,
            isCanceled = nextEpisodeCanceled,
            controlsLocked = controlsLocked,
            isEndingChapterActive = isEndingActive
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

        // Pre-warm next episode predictive buffer immediately when countdown appears
        lifecycleScope.launch {
            if (appSettings.get().fetchPredictiveBuffering()) {
                bingeBufferManager.get().prewarmNextEpisode(
                    scope = lifecycleScope,
                    nextItem = next,
                    currentMalAnime = currentMalAnime,
                    folderSubtitles = folderSubtitles
                )
            }
        }

        binding.netflixSkipRow.animate().cancel()
        binding.netflixSkipRow.visibility = View.GONE
        controller.skipIntroRow.animate().cancel()
        controller.skipIntroRow.visibility = View.GONE

        val card = binding.nextEpisodeCard
        card.root.translationY = if (controller.root.isVisible) -resources.getDimensionPixelOffset(R.dimen.next_episode_card_offset).toFloat() else 0f

        val parsed = EpisodeParser.parse(next.title)
        card.tvNextEpisodeTitle.text = parsed.cleanTitle

        MediaImageLoader.card(card.ivNextEpisodeThumb, ThumbnailUrl.medium(next.thumbnailLink))

        card.root.animate().cancel()
        card.root.alpha = 0f
        card.root.visibility = View.VISIBLE
        val motionCardDuration = resources.getInteger(R.integer.motion_card).toLong()
        card.root.animate().alpha(1f).setDuration(motionCardDuration).start()

        card.btnPlayNextEpisodeNow.setOnClickListener {
            playNextEpisodeDirectly()
        }

        card.btnCancelNextEpisode.setOnClickListener {
            dismissNextEpisodeCard(isManualCancel = true)
        }

        card.btnPlayNextEpisodeNow.post {
            card.btnPlayNextEpisodeNow.requestFocus()
        }

        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            val totalCountdownSeconds = 10
            while (isActive && isNextEpisodeCardShowing) {
                val timePosDouble = MPVLib.getPropertyDouble("time-pos") ?: (player.timePos ?: 0).toDouble()
                val durationDouble = (player.duration ?: 0).toDouble()
                val remainingDouble = (durationDouble - timePosDouble).coerceAtLeast(0.0)
                val sec = kotlin.math.ceil(remainingDouble).toInt().coerceAtLeast(0)
                card.tvNextEpisodeCountdown.text = "A SEGUIR • ${sec}s"
                val progressPercent = ((sec.toFloat() / totalCountdownSeconds.toFloat()) * 100).toInt().coerceIn(0, 100)
                card.pbNextEpisodeCountdown.progress = progressPercent
                if (sec <= 0 || remainingDouble <= 1.0) {
                    playNextEpisodeDirectly()
                    break
                }
                delay(150L)
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
        card.animate().cancel()
        val motionCardDuration = resources.getInteger(R.integer.motion_card).toLong()
        card.animate().alpha(0f).setDuration(motionCardDuration).withEndAction {
            card.visibility = View.GONE
            card.translationY = 0f
        }.start()
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
    }

    private fun playNextEpisodeDirectly() {
        val next = nextEpisode ?: return
        countdownJob?.cancel()
        isNextEpisodeCardShowing = false
        binding.nextEpisodeCard.root.visibility = View.GONE

        saveProgress()

        currentFileId = next.fileId
        currentLocalUri = next.localUri
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
        currentLocalUri = prev.localUri
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
        showEpisodesDrawer(
            currentTitle = currentTitle,
            currentFileId = currentFileId,
            playlist = playlist,
            tenraiService = tenraiService.get()
        ) { selectedEpisode ->
            playPlaylistItemDirectly(selectedEpisode)
        }
    }

    private fun playPlaylistItemDirectly(item: PlaylistItem) {
        if (item.fileId == currentFileId) return
        countdownJob?.cancel()
        isNextEpisodeCardShowing = false
        binding.nextEpisodeCard.root.visibility = View.GONE

        saveProgress()

        currentFileId = item.fileId
        currentLocalUri = item.localUri
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
                        AppLog.w(TAG, "MAL scrobble failed: ${res.message}")
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
                                    val msg = getString(R.string.mal_anime_completed, score)
                                    android.widget.Toast.makeText(this@MPVActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    val msg = getString(R.string.mal_save_score_error, res.message ?: "")
                                    android.widget.Toast.makeText(this@MPVActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
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
        val motionEnter = resources.getInteger(R.integer.motion_pill_enter).toLong()
        val motionExit = resources.getInteger(R.integer.motion_pill_exit).toLong()
        if (shouldShow && !row.isVisible && !controlsLocked && controller.root.isVisible) {
            row.animate().cancel()
            row.alpha = 0f
            row.isVisible = true
            row.animate().alpha(1f).setDuration(motionEnter).start()
        } else if (!shouldShow && row.isVisible) {
            row.animate().cancel()
            row.animate().alpha(0f).setDuration(motionExit).withEndAction {
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
            zechs.drive.stream.utils.SubtitleSelectionPolicy.isJapanese(track.lang, track.title, track.name)
        }
        if (targetAudio != null && targetAudio.mpvId != currentAid) {
            AppLog.d(TAG, "MPV auto-selected Japanese audio: ${targetAudio.name} (id=${targetAudio.mpvId})")
            player.aid = targetAudio.mpvId
        }

        // 2. Subtitle: Auto-select Portuguese (prefer Brazilian Portuguese / full dialogue)
        val currentSid = player.sid
        val ptSubs = subTracks.filter { track ->
            if (track.mpvId == -1) return@filter false
            zechs.drive.stream.utils.SubtitleSelectionPolicy.isPortuguese(track.lang, track.title, track.name)
        }

        val targetSub = ptSubs.maxByOrNull { track ->
            // P2-08: mesma pontuação do player Exo, em regra única testada.
            zechs.drive.stream.utils.SubtitleSelectionPolicy
                .scorePortuguese("${track.title} ${track.name}")
        }

        if (targetSub != null && targetSub.mpvId != currentSid) {
            AppLog.d(TAG, "MPV auto-selected Portuguese subtitle: ${targetSub.name} (id=${targetSub.mpvId})")
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
                        // `start` is a per-file option and may be ignored after MPV has
                        // already opened a previous playlist item. Apply the resume seek
                        // only after the requested file is loaded.
                        val resumeMs = pendingResumePositionMs
                        pendingResumePositionMs = 0L
                        if (resumeMs > 0L) {
                            MPVLib.setPropertyDouble("time-pos", resumeMs / 1000.0)
                        }
                        updateChapters()
                        if (isControlsShowing() && !controller.root.hasFocus()) {
                            controller.btnPlayPause.requestFocus()
                        }
                        pendingSubtitles.forEach { (cached, sub) ->
                            addSubtitleToMpv(cached, sub)
                        }
                        pendingSubtitles.clear()

                        // Apple TV / Netflix Grade AFR (Auto Frame Rate) & HDR (Match Dynamic Range)
                        val containerFps = MPVLib.getPropertyDouble("container-fps")
                        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            display
                        } else {
                            @Suppress("DEPRECATION")
                            windowManager.defaultDisplay
                        }

                        lifecycleScope.launch {
                            val afrEnabled = appSettings.get().fetchAutoFrameRate()
                            val hdrEnabled = appSettings.get().fetchMatchDynamicRange()

                            if (containerFps != null && containerFps > 0.0) {
                                val afrState = displayModeController.apply(
                                    window = window,
                                    surface = player.holder.surface,
                                    display = display,
                                    targetFps = containerFps,
                                    enabled = afrEnabled
                                )
                                controller.tvPlayerAfrBadge.apply {
                                    if (afrState != null && afrEnabled) {
                                        text = afrState.label
                                        visibility = View.VISIBLE
                                    } else {
                                        visibility = View.GONE
                                    }
                                }
                            }

                            // Match Dynamic Range / HDR
                            val gamma = MPVLib.getPropertyString("video-params/gamma")
                            val matrix = MPVLib.getPropertyString("video-params/colormatrix")
                            val codec = MPVLib.getPropertyString("video-codec")
                            val dynRange = zechs.drive.stream.utils.HdrModeManager.detectMpvHdr(gamma, matrix, codec)
                            val hdrStatus = zechs.drive.stream.utils.HdrModeManager.applyHdrMode(
                                activity = this@MPVActivity,
                                dynamicRange = dynRange,
                                enabled = hdrEnabled,
                                display = display
                            )
                            currentHdrStatus = hdrStatus
                            controller.tvPlayerHdrBadge.apply {
                                if (hdrStatus.isHdrActive && dynRange.badgeText.isNotEmpty()) {
                                    text = dynRange.badgeText
                                    visibility = View.VISIBLE
                                } else {
                                    visibility = View.GONE
                                }
                            }
                        }
                    }
                }
                MPV_EVENT_END_FILE -> {
                    runOnUiThread {
                        if (!isFileLoaded && activityIsForeground && !isFinishing) {
                            showPlaybackFailureDialog("Não foi possível carregar a mídia no MPV.")
                        }
                    }
                }
            }
        }
    }

    private fun showPlaybackFailureDialog(errorMessage: String? = null) {
        if (isFinishing || isDestroyed) return
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.state_error_playback_title)
            .setMessage(errorMessage ?: getString(R.string.state_error_playback_message))
            .setPositiveButton(R.string.state_error_network_action) { d, _ ->
                d.dismiss()
                playMedia()
            }
            .setNeutralButton(R.string.state_error_playback_action) { d, _ ->
                d.dismiss()
                launchExoFallback()
            }
            .setNegativeButton(R.string.state_error_go_back_action) { d, _ ->
                d.dismiss()
                finish()
            }
            .create()
        dialog.setOnDismissListener { onPlayerModalVisibilityChanged(false) }
        onPlayerModalVisibilityChanged(true)
        dialog.show()
    }

    private fun launchExoFallback() {
        try {
            val fileId = intent.getStringExtra("fileId") ?: ""
            val title = intent.getStringExtra("title") ?: ""
            if (fileId.isEmpty()) {
                finish()
                return
            }
            val currentPos = ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000.0).toLong()
            val themeIndex = intent.getIntExtra("theme", AppTheme.OBSIDIAN.value)
            val exoIntent = PlayerLauncher.createIntent(
                context = this,
                playerType = VideoPlayer.EXO_PLAYER,
                fileId = fileId,
                title = title,
                seriesTitle = intent.getStringExtra("seriesTitle"),
                accessToken = intent.getStringExtra("accessToken"),
                thumbnailLink = intent.getStringExtra("thumbnailLink"),
                themeIndex = themeIndex,
                playlist = ArrayList(playlist),
                subtitles = ArrayList(folderSubtitles),
                siblingFolderId = intent.getStringExtra("siblingFolderId"),
                startPosition = currentPos,
                isMovie = intent.getBooleanExtra("isMovie", false),
                autoRouting = false,
                localUri = intent.getStringExtra("localUri")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(exoIntent)
            Toast.makeText(this, R.string.mpv_switching_to_exoplayer, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error launching Exo fallback", e)
            finish()
        }
    }

    ////////////////    END OF MPV EVENTS    ////////////////


    override fun onPause() {
        if (isOsdInitialized) {
            osdController.cancelInactivityTimer()
        }
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

    override fun onDestroy() {
        countdownJob?.cancel()
        AppLog.v(TAG, "Exiting.")

        mediaSessionHelper?.release()
        mediaSessionHelper = null

        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(audioFocusChangeListener)

        player.removeObserver(this)
        val surface = player.holder.surface
        player.destroy()

        displayModeController.reset(window, surface)
        zechs.drive.stream.utils.HdrModeManager.resetHdrMode(this)
        bingeBufferManager.get().clear()

        super.onDestroy()
    }

    private fun applyMpvSubtitleStyle(style: zechs.drive.stream.utils.SubtitleStyle) {
        MPVLib.setPropertyInt("sub-pos", 96)
        MPVLib.setPropertyString("sub-ass-override", "force")
        MPVLib.setPropertyString("sub-font", style.fontFamily.mpvFont)
        MPVLib.setPropertyString("sub-bold", if (style.bold) "yes" else "no")
        MPVLib.setPropertyString("sub-italic", if (style.italic) "yes" else "no")
        MPVLib.setPropertyString("sub-color", argbToMpvColor(style.foregroundColor))
        MPVLib.setPropertyString("sub-border-color", argbToMpvColor(style.edgeColor))
        val edge = style.edgeType
        MPVLib.setPropertyDouble(
            "sub-border-size",
            when (edge) {
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE -> 0.0
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> 2.0
                else -> 3.2
            }
        )
        MPVLib.setPropertyDouble(
            "sub-shadow-offset",
            if (edge == androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) 1.5 else 0.0
        )
        val back = if (style.backgroundColor == android.graphics.Color.TRANSPARENT) {
            "#00000000"
        } else {
            argbToMpvColor(style.backgroundColor)
        }
        MPVLib.setPropertyString("sub-back-color", back)
        MPVLib.setPropertyInt("sub-font-size", zechs.drive.stream.utils.SubtitleAppearance.mpvFontSize(style.sizeSp))
    }

    private fun argbToMpvColor(color: Int): String {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return String.format("#%02X%02X%02X%02X", a, r, g, b)
    }

}
