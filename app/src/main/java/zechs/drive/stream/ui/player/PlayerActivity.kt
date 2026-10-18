package zechs.drive.stream.ui.player

import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import zechs.drive.stream.utils.AppLog
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.ExoPlaybackException.*
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.AudioAttributes
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector.MappedTrackInfo
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ThumbnailUrl
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.databinding.ActivityPlayerBinding
import zechs.drive.stream.ui.player.utils.AuthenticatingDataSource
import zechs.drive.stream.ui.player.engine.PlaybackCoordinator
import zechs.drive.stream.ui.player.utils.BufferConfig
import zechs.drive.stream.ui.player.utils.CustomTrackNameProvider
import zechs.drive.stream.ui.player2.MPVActivity
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.utils.MatroskaChapterParser
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.MediaUriResolver
import zechs.drive.stream.utils.util.Constants.Companion.DRIVE_API
import zechs.drive.stream.utils.util.Orientation
import zechs.drive.stream.utils.util.getNextOrientation
import zechs.drive.stream.utils.util.setOrientation
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.MotionPolicy
import android.view.MotionEvent
import zechs.drive.stream.utils.ext.resolveThemeColor
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    override val isGesturesEnabled: Boolean
        get() = malSessionManager.get().isGesturesEnabled()
    override val isGestureHelperInitialized: Boolean
        get() = ::gestureHelper.isInitialized
    override fun onGestureTouchEvent(ev: MotionEvent): Boolean = gestureHelper.onTouchEvent(ev)

    companion object {
        const val TAG = "PlayerActivity"

        // Anime intro/outro skip amount. Change this if you want a
        // different default (e.g. 85_000L for a slightly shorter OP).
        const val SKIP_INTRO_MS = 90_000L
    }

    @Inject
    lateinit var driveRepository: Lazy<DriveRepository>

    @Inject
    lateinit var tokenProvider: Lazy<zechs.drive.stream.data.repository.TokenProvider>

    @Inject
    lateinit var sessionManager: Lazy<SessionManager>

    @Inject
    lateinit var appSettings: Lazy<zechs.drive.stream.utils.AppSettings>

    @Inject
    lateinit var malRepository: Lazy<MalRepository>

    @Inject
    lateinit var malSessionManager: Lazy<MalSessionManager>

    @Inject
    lateinit var aniSkipRepository: Lazy<AniSkipRepository>

    @Inject
    lateinit var onlineSubtitleManager: Lazy<zechs.drive.stream.utils.OnlineSubtitleManager>

    @Inject
    lateinit var tenraiService: Lazy<zechs.drive.stream.data.remote.TenraiAnimeService>

    @Inject
    lateinit var bingeBufferManager: Lazy<zechs.drive.stream.ui.player.predictive.BingeBufferManager>

    // View binding
    private lateinit var binding: ActivityPlayerBinding

    // ViewModel
    private val viewModel by viewModels<PlayerViewModel>()

    // Exoplayer
    private lateinit var player: ExoPlayer
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var trackSelector: DefaultTrackSelector

    @Suppress("DEPRECATION")
    private lateinit var playerView: PlayerView

    // Player views
    private lateinit var mainControlsRoot: LinearLayout
    private lateinit var controlsScrollView: View
    private lateinit var progressViewGroup: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var titleBlock: View
    private lateinit var btnBack: ImageButton
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerMeta: TextView
    private var tvPlayerHdrBadge: TextView? = null
    private var tvPlayerAfrBadge: TextView? = null
    private val displayModeController = zechs.drive.stream.playback.display.DisplayModeController()
    private val currentAfrState get() = displayModeController.currentState
    private var currentHdrStatus: zechs.drive.stream.utils.HdrModeManager.HdrStatus? = null
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnPrevEp: ImageButton
    private lateinit var btnNextEp: ImageButton
    private lateinit var btnRew: ImageButton
    private lateinit var btnFfwd: ImageButton
    private lateinit var btnAudio: View
    private lateinit var btnSubtitle: View
    private lateinit var btnEpisodes: View
    private lateinit var btnChapter: View
    private lateinit var btnResize: View
    private lateinit var btnInfo: View
    private lateinit var btnPip: MaterialButton
    private lateinit var btnSpeed: View
    private var btnRotate: View? = null
    private var ivRotate: ImageView? = null
    private var tvRotate: TextView? = null
    private lateinit var btnLock: MaterialButton
    private lateinit var btnUnlock: MaterialButton
    private lateinit var btnSkipIntro: TextView
    private lateinit var btnSkipIntroBack: MaterialButton
    private lateinit var skipIntroRow: LinearLayout
    private lateinit var aniskipPill: LinearLayout
    private lateinit var tvAniSkipLabel: TextView
    private var prevEpisode: PlaylistItem? = null
    private var aniskipPillJob: Job? = null
    private var tvPlayerClock: TextView? = null
    private var chapterMarkersView: ChapterMarkersView? = null

    // MediaSession
    private var mediaSessionHelper: MakimonoMediaSessionHelper? = null

    // States
    private var onStopCalled = false
    private var controlsLocked = false
    private val playerModalVisibilityState = PlayerModalVisibilityState()
    private var modalReturnFocus: View? = null
    private var hasAutoSelectedTracks = false
    private var parsedChapters: List<MatroskaChapterParser.ParsedChapter> = emptyList()
    private var activeSkipChapter: MatroskaChapterParser.ParsedChapter? = null
    private lateinit var gestureHelper: PlayerGestureHelper
    private var hasAutoSkippedCurrentInterval = false

    // Kodi features states
    private var isKodiHudVisible = false
    private var kodiHudUpdateJob: Job? = null
    private var activeVideoDecoderName: String? = null
    private var forcedSubtitleUri: Uri? = null
    private var activeExternalSubItem: SubtitleItem? = null
    private var activeExternalSubFile: java.io.File? = null
    private var currentAudioDelayMs = 0L
    private var currentSubtitleOffsetMs = 0L
    private var currentSubtitleStyle = zechs.drive.stream.utils.SubtitleStyle()
    private var isNightModeAudioEnabled = false
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    // Playlist & Next Episode Auto-Play
    private var currentFileId: String = ""
    private var currentLocalUri: String? = null
    private var currentTitle: String = ""
    private var currentThumbnailLink: String? = null
    private var playlist = mutableListOf<PlaylistItem>()
    private var nextEpisode: PlaylistItem? = null
    private var nextEpisodeCanceled = false
    private var isNextEpisodeCardShowing = false
    private val playbackCoordinator = PlaybackCoordinator()
    private val skipController = zechs.drive.stream.playback.session.SkipController()
    private lateinit var playbackSession: zechs.drive.stream.playback.session.PlaybackSession
    private var countdownJob: Job? = null
    private var progressTrackerJob: Job? = null
    private var folderSubtitles = mutableListOf<SubtitleItem>()
    private val addedSubtitleFileIds = mutableSetOf<String>()

    // MAL Scrobble & Rating State
    private var currentMalAnime: MalAnimeNode? = null
    private var currentEpNumber: Int = 1
    private var hasScrobbledThisEp: Boolean = false
    private var hasPromptedRating: Boolean = false
    private var isMovie: Boolean = false

    private var speed = arrayOf("0.25x", "0.5x", "Normal", "1.25x", "1.5x", "2x")
    private var orientation = Orientation.LANDSCAPE
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ignoreNextCenterKeyUp = false
    private var handledFocusDirectionKeyCode: Int? = null
    private var isCenterLongPressSpeeding = false
    private var speedBeforeCenterHold = 1.0f
    private val centerLongPressRunnable = Runnable {
        if (!isControlsShowing() && !controlsLocked && ::player.isInitialized && player.isPlaying) {
            isCenterLongPressSpeeding = true
            speedBeforeCenterHold = player.playbackParameters.speed
            player.playbackParameters = androidx.media3.common.PlaybackParameters(2.0f)
            gestureHelper.showSpeedPill()
            ignoreNextCenterKeyUp = true
        }
    }
    private val seekStepPolicy = zechs.drive.stream.utils.SeekStepPolicy()
    private var reduceMotionEnabled = false
    private val resetSeekAccumulatorRunnable = Runnable {
        seekStepPolicy.reset()
    }

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

        binding = ActivityPlayerBinding.inflate(layoutInflater)
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

        playerView = binding.playerView
        (playerView.videoSurfaceView as? SurfaceView)?.holder?.setFormat(PixelFormat.RGBA_8888)

        mainControlsRoot = playerView.findViewById(R.id.mainControls)
        controlsScrollView = playerView.findViewById(R.id.controlsScrollView)
        progressViewGroup = playerView.findViewById(R.id.linearLayout2)
        toolbar = playerView.findViewById(R.id.playerToolbar)
        titleBlock = playerView.findViewById(R.id.titleBlock)
        btnBack = playerView.findViewById(R.id.btnBack)
        tvPlayerTitle = playerView.findViewById(R.id.tvPlayerTitle)
        tvPlayerMeta = playerView.findViewById(R.id.tvPlayerMeta)
        tvPlayerHdrBadge = playerView.findViewById(R.id.tvPlayerHdrBadge)
        tvPlayerAfrBadge = playerView.findViewById(R.id.tvPlayerAfrBadge)
        btnPlayPause = playerView.findViewById(R.id.btnPlayPause)
        btnPrevEp = playerView.findViewById(R.id.btnPrevEp)
        btnNextEp = playerView.findViewById(R.id.btnNextEp)
        btnRew = playerView.findViewById(R.id.exo_rew)
        btnFfwd = playerView.findViewById(R.id.exo_ffwd)
        btnAudio = playerView.findViewById(R.id.btnAudio)
        btnSubtitle = playerView.findViewById(R.id.btnSubtitle)
        btnEpisodes = playerView.findViewById(R.id.btnEpisodes)
        btnChapter = playerView.findViewById(R.id.btnChapter)
        btnResize = playerView.findViewById(R.id.btnResize)
        btnInfo = playerView.findViewById(R.id.btnInfo)
        btnPip = playerView.findViewById(R.id.btnPip)
        btnSpeed = playerView.findViewById(R.id.btnSpeed)
        btnRotate = runCatching { playerView.findViewById<View>(R.id.btnRotate) }.getOrNull()
        ivRotate = runCatching { playerView.findViewById<ImageView>(R.id.ivRotate) }.getOrNull()
        tvRotate = runCatching { playerView.findViewById<TextView>(R.id.tvRotate) }.getOrNull()
        btnLock = playerView.findViewById(R.id.btnLock)
        btnUnlock = playerView.findViewById(R.id.btnUnlock)
        btnSkipIntro = playerView.findViewById(R.id.btnSkipIntro)
        btnSkipIntroBack = playerView.findViewById(R.id.btnSkipIntroBack)
        skipIntroRow = playerView.findViewById(R.id.skipIntroRow)
        aniskipPill = playerView.findViewById(R.id.aniskipPill)
        tvAniSkipLabel = playerView.findViewById(R.id.tvAniSkipLabel)
        tvPlayerClock = playerView.findViewById(R.id.tvPlayerClock)
        chapterMarkersView = playerView.findViewById(R.id.chapterMarkersView)
        playbackSession = zechs.drive.stream.playback.session.PlaybackSession(
            progressRecorder = viewModel.progressRecorder,
            coordinator = playbackCoordinator,
            skipController = skipController
        )

        osdController = OsdController(
            context = this,
            host = object : OsdViewHost {
                override val rootControllerView: View?
                    get() = playerView.findViewById(R.id.controls)
                override val titleBlock: View?
                    get() = this@PlayerActivity.titleBlock
                override val controlsScrollView: View?
                    get() = this@PlayerActivity.controlsScrollView
                override val mainControlsRoot: View?
                    get() = this@PlayerActivity.mainControlsRoot
                override val topScrim: View?
                    get() = playerView.findViewById(R.id.topScrim)
                override val skipIntroRow: View?
                    get() = this@PlayerActivity.skipIntroRow
                override val nextEpisodeCard: View?
                    get() = binding.nextEpisodeCard.root

                override fun onOsdVisibilityChanged(visible: Boolean) {
                    val becameVisible = visible && !controllerVisible
                    controllerVisible = visible
                    if (!visible) {
                        zechs.drive.stream.utils.TvFocusRing.clear(playerView)
                        playerView.hideController()
                    } else {
                        playerView.showController()
                    }
                    if (becameVisible) {
                        handleLockingControls()
                        btnPlayPause.post { btnPlayPause.requestFocus() }
                    }
                    if (::player.isInitialized) {
                        updateIntroButtonVisibility(player.currentPosition)
                    }
                }

                override fun onElevationPolicyChanged(elevated: Boolean) {
                    playerView.subtitleView?.setBottomPaddingFraction(
                        SubtitleElevationPolicy.resolve(elevated).exoPaddingFraction
                    )
                }

                override fun onShowPrepared() {
                    try {
                        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        tvPlayerClock?.text = timeFormat.format(java.util.Date())
                    } catch (e: Exception) {
                        AppLog.logSwallowed(TAG, "updating player clock", e)
                    }
                }
            },
            isReviewMode = intent.getStringExtra("reviewMediaUri") != null,
            getReduceMotion = { reduceMotionEnabled }
        )

        // Back button
        toolbar.setNavigationOnClickListener {
            finish()
        }
        btnBack.setOnClickListener {
            finish()
        }
        // PlayerView may consume the first touch while toggling its controller. Keep the
        // dedicated exit target deterministic for touch: one completed tap invokes the same
        // accessible click action exactly once.
        btnBack.setOnTouchListener { view, event ->
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

        btnPrevEp.setOnClickListener {
            playPrevEpisodeDirectly()
        }
        btnNextEp.setOnClickListener {
            playNextEpisodeDirectly()
        }

        btnRew.setOnClickListener {
            mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
            val deltaSeconds = seekStepPolicy.onSeekPress(-1)
            seekRelative(deltaSeconds * 1000L)
            gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
            mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
        }

        btnFfwd.setOnClickListener {
            mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
            val deltaSeconds = seekStepPolicy.onSeekPress(1)
            seekRelative(deltaSeconds * 1000L)
            gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
            mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
        }

        skipIntroRow.setOnClickListener {
            performSkipIntroOrCredits()
        }

        btnAudio.setOnClickListener {
            showAudioTrackDialog()
        }

        btnSubtitle.setOnClickListener {
            showSubtitleTrackDialog()
        }

        btnEpisodes.setOnClickListener {
            showEpisodesDrawer()
        }

        btnChapter.setOnClickListener {
            showChapterDialog()
        }

        btnInfo.setOnClickListener {
            toggleKodiInfoHud()
        }

        btnResize.setOnClickListener {
            TransitionManager.beginDelayedTransition(
                playerView, AutoTransition().apply {
                    interpolator = AccelerateInterpolator()
                    duration = resources.getInteger(R.integer.motion_spec_normal).toLong()
                }
            )
            playerView.apply {
                val nextMode = when (resizeMode) {
                    RESIZE_MODE_FIT -> RESIZE_MODE_ZOOM
                    RESIZE_MODE_ZOOM -> RESIZE_MODE_FILL
                    else -> RESIZE_MODE_FIT
                }
                resizeMode = nextMode
                val modeLabel = when (nextMode) {
                    RESIZE_MODE_FIT -> "Modo de Vídeo: Ajustar (Original)"
                    RESIZE_MODE_ZOOM -> "Modo de Vídeo: Zoom (Cortar Bordas)"
                    RESIZE_MODE_FILL -> "Modo de Vídeo: Esticar (Preencher Tela)"
                    else -> "Modo de Vídeo"
                }
                Snackbar.make(playerView, modeLabel, 1000).apply {
                    anchorView = progressViewGroup
                }.show()
                zechs.drive.stream.utils.TvFocusRing.clear(playerView)
            }
        }

        btnPip.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPIPMode()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.pip_not_supported),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnSpeed.setOnClickListener {
            val currentSpeed = if (::player.isInitialized) player.playbackParameters.speed else 1.0f
            val speedOptions = listOf(
                0.25f to "0.25x",
                0.50f to "0.5x",
                0.75f to "0.75x",
                1.00f to "Normal (1.0x)",
                1.25f to "1.25x",
                1.50f to "1.5x",
                1.75f to "1.75x",
                2.00f to "2.0x"
            )
            val items = speedOptions.map { (sp, label) ->
                val isSelected = kotlin.math.abs(currentSpeed - sp) < 0.05f
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
                val chosenSpeed = selected.tag as? Float ?: 1.0f
                player.playbackParameters = PlaybackParameters(chosenSpeed, 1.0f)
                val idx = speed.indexOfFirst { it.startsWith(chosenSpeed.toString()) }.takeIf { it >= 0 } ?: 2
                speedSnackbar(idx)
            }.show()
        }

        val isTvDevice = DeviceUi.isTenFootExperience(this)
        btnRotate?.visibility = if (isTvDevice) View.GONE else View.VISIBLE

        btnRotate?.setOnClickListener {
            orientation = getNextOrientation(orientation)
            AppLog.d(TAG, "orientation=${orientation}")
            setOrientation(this@PlayerActivity, orientation)
        }

        PlayerQuickOptions.bind(playerView)

        val timeBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
        val timeBarContainer = playerView.findViewById<View>(R.id.timeBarContainer)
        val tvExoPosition = playerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)
        val tvExoDuration = playerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)
        val accentColor = resolveThemeColor(R.attr.colorAccentPrimary)
        val defaultPositionColor = androidx.core.content.ContextCompat.getColor(this, R.color.obsidian_text_primary)
        val defaultDurationColor = androidx.core.content.ContextCompat.getColor(this, R.color.obsidian_text_secondary)

        timeBar?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                timeBarContainer?.animate()?.scaleY(1.35f)?.scaleX(1.008f)?.setDuration(150L)?.start()
                tvExoPosition?.setTextColor(accentColor)
                tvExoDuration?.setTextColor(accentColor)
                osdController.resetInactivityTimer()
            } else {
                timeBarContainer?.animate()?.scaleY(1.0f)?.scaleX(1.0f)?.setDuration(150L)?.start()
                tvExoPosition?.setTextColor(defaultPositionColor)
                tvExoDuration?.setTextColor(defaultDurationColor)
            }
        }

        gestureHelper = PlayerGestureHelper(
            activity = this,
            hudBinding = binding.gestureHud,
            callback = object : PlayerGestureCallback {
                override fun onToggleControls() {
                    if (isControlsShowing()) {
                        animateHideController()
                    } else {
                        animateShowController()
                    }
                }

                override fun onSeekRelative(deltaMs: Long) {
                    seekRelative(deltaMs)
                }

                override fun onSeekTo(positionMs: Long) {
                    if (::player.isInitialized) {
                        player.seekTo(positionMs)
                    }
                }

                override fun getCurrentPosition(): Long = if (::player.isInitialized) player.currentPosition else 0L
                override fun getDuration(): Long = if (::player.isInitialized) player.duration.coerceAtLeast(0L) else 0L

                override fun onTogglePlayPause() {
                    if (!::player.isInitialized) return
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                        player.play()
                    } else if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }

                override fun onSetSpeed(speed: Float) {
                    if (::player.isInitialized) {
                        player.playbackParameters = PlaybackParameters(speed, 1.0f)
                    }
                }

                override fun isControlsLocked(): Boolean = controlsLocked
                override fun isControllerVisible(): Boolean = isControlsShowing()
                override fun getTouchIgnoredViews(): List<View> = listOf(
                    toolbar,
                    btnBack,
                    titleBlock,
                    controlsScrollView,
                    progressViewGroup,
                    mainControlsRoot,
                    binding.netflixSkipRow,
                    binding.nextEpisodeCard.root
                )
            }
        )

        btnLock.setOnClickListener {
            controlsLocked = true
            handleLockingControls()
        }

        btnUnlock.setOnClickListener {
            controlsLocked = false
            handleLockingControls()
        }

        btnSkipIntro.setOnClickListener {
            performSkipIntroOrCredits()
        }

        binding.btnNetflixSkip.setOnClickListener {
            performSkipIntroOrCredits()
        }

        btnSkipIntroBack.setOnClickListener {
            seekRelative(-SKIP_INTRO_MS)
        }

        currentFileId = intent.getStringExtra("fileId") ?: ""
        currentLocalUri = intent.getStringExtra(PlayerLauncher.EXTRA_LOCAL_URI)
        currentTitle = intent.getStringExtra("title") ?: ""
        currentThumbnailLink = intent.getStringExtra("thumbnailLink")
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
                        Snackbar.make(playerView, list.message, Snackbar.LENGTH_LONG).show()
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
                        Snackbar.make(playerView, list.message, Snackbar.LENGTH_LONG).show()
                    }
                    if (list.items.isNotEmpty()) {
                        folderSubtitles = PlaybackPlaylistPolicy.mergeSubtitles(folderSubtitles, list.items).toMutableList()
                        loadMatchingExternalSubtitles()
                    }
                }
            }
        }

        updateOrientation(resources.configuration)
        setupMediaSession()
        initPlayer()
        playMedia()
    }

    private fun setupMediaSession() {
        mediaSessionHelper = MakimonoMediaSessionHelper(
            context = this,
            tag = "ExoPlayerSession",
            callback = object : MakimonoMediaSessionHelper.Callback {
                override fun onPlay() {
                    if (::player.isInitialized) {
                        player.play()
                    }
                }

                override fun onPause() {
                    if (::player.isInitialized) {
                        player.pause()
                    }
                }

                override fun onSkipToNext() {
                    playNextEpisodeDirectly()
                }

                override fun onSkipToPrevious() {
                    playPrevEpisodeDirectly()
                }

                override fun onSeekTo(positionMs: Long) {
                    if (::player.isInitialized) {
                        player.seekTo(positionMs)
                    }
                }

                override fun onFastForward() {
                    if (::player.isInitialized) {
                        player.seekTo(player.currentPosition + 10_000L)
                    }
                }

                override fun onRewind() {
                    if (::player.isInitialized) {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                }

                override fun onStop() {
                    if (::player.isInitialized) {
                        player.stop()
                    }
                    finish()
                }
            }
        )
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
        if (::btnEpisodes.isInitialized) {
            btnEpisodes.visibility = if (navState.showEpisodesDrawerButton) View.VISIBLE else View.GONE
        }
        if (::btnPrevEp.isInitialized) {
            btnPrevEp.isEnabled = navState.canSkipPrevious
            btnPrevEp.isFocusable = navState.canSkipPrevious
            btnPrevEp.alpha = navState.prevButtonAlpha
            btnNextEp.isEnabled = navState.canSkipNext
            btnNextEp.isFocusable = navState.canSkipNext
            btnNextEp.alpha = navState.nextButtonAlpha
            if (::btnRew.isInitialized && ::btnAudio.isInitialized) {
                btnRew.nextFocusLeftId = if (navState.canSkipPrevious) R.id.btnPrevEp else R.id.btnAudio
                btnAudio.nextFocusRightId = if (navState.canSkipPrevious) R.id.btnPrevEp else R.id.exo_rew
            }
            if (::btnFfwd.isInitialized) {
                btnFfwd.nextFocusRightId = if (navState.canSkipNext) R.id.btnNextEp else R.id.btnMoreOptions
            }
        }
        mediaSessionHelper?.updatePlaybackState(
            isPlaying = if (::player.isInitialized) player.isPlaying else false,
            positionMs = if (::player.isInitialized) player.currentPosition else 0L,
            speed = if (::player.isInitialized) player.playbackParameters.speed else 1.0f,
            canSkipNext = navState.canSkipNext,
            canSkipPrevious = navState.canSkipPrevious
        )
        val currentIndex = playlist.indexOfFirst { it.fileId == currentFileId }
        AppLog.d(TAG, "updateNextEpisode: currentIndex=$currentIndex, prevEpisode=${prevEpisode?.title}, nextEpisode=${nextEpisode?.title}")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        currentFileId = intent.getStringExtra("fileId") ?: ""
        currentLocalUri = intent.getStringExtra(PlayerLauncher.EXTRA_LOCAL_URI)
        currentTitle = intent.getStringExtra("title") ?: ""
        currentThumbnailLink = intent.getStringExtra("thumbnailLink")
        playMedia()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPIPMode() {
        val width = if (::player.isInitialized) player.videoFormat?.width ?: 0 else 0
        val height = if (::player.isInitialized) player.videoFormat?.height ?: 0 else 0
        MakimonoPiPHelper.enterPiP(this, width, height)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && player.isPlaying) {
            val width = player.videoFormat?.width ?: 0
            val height = player.videoFormat?.height ?: 0
            MakimonoPiPHelper.enterPiP(this, width, height)
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Prevent screen from timing-out when video is playing
            playerView.keepScreenOn = when (playbackState) {
                Player.STATE_BUFFERING, Player.STATE_READY -> true
                else -> false
            }

            if (playbackState == Player.STATE_ENDED) {
                checkMalScrobble(player.duration, player.duration)
                checkMalCompletionPrompt(player.duration, player.duration)
                if (nextEpisode != null && !nextEpisodeCanceled) {
                    playNextEpisodeDirectly()
                    return
                }
            }

            if (playbackState == Player.STATE_READY) {
                var subtitleText = ""

                player.videoFormat?.let {
                    if (it.width != NO_VALUE && it.height != NO_VALUE) {
                        subtitleText += "${it.width}x${it.height} - "
                    }
                    // frameRate can return NO_VALUE, which is a int
                    // can't compare it against float.
                    if (it.frameRate > 0) {
                        val rounded = (it.frameRate * 100.0).roundToInt() / 100.0
                        subtitleText += "${rounded}fps - "
                    }
                    it.codecs?.let { codec ->
                        subtitleText += codec
                    }

                    // Apple TV / Netflix Grade AFR (Auto Frame Rate) & HDR (Match Dynamic Range)
                    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        display
                    } else {
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay
                    }

                    lifecycleScope.launch {
                        val afrEnabled = appSettings.get().fetchAutoFrameRate()
                        val hdrEnabled = appSettings.get().fetchMatchDynamicRange()

                        // 1. Auto Frame Rate (AFR) matching
                        if (it.frameRate > 0) {
                            val surface = (playerView.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface
                            val afrState = displayModeController.apply(
                                window = window,
                                surface = surface,
                                display = display,
                                targetFps = it.frameRate.toDouble(),
                                enabled = afrEnabled
                            )
                            tvPlayerAfrBadge?.apply {
                                if (afrState != null && afrEnabled) {
                                    text = afrState.label
                                    visibility = View.VISIBLE
                                } else {
                                    visibility = View.GONE
                                }
                            }
                        }

                        // 2. HDR mode matching
                        val dynRange = zechs.drive.stream.utils.HdrModeManager.detectExoPlayerHdr(it)
                        val hdrStatus = zechs.drive.stream.utils.HdrModeManager.applyHdrMode(
                            activity = this@PlayerActivity,
                            dynamicRange = dynRange,
                            enabled = hdrEnabled,
                            display = display
                        )
                        currentHdrStatus = hdrStatus
                        tvPlayerHdrBadge?.apply {
                            if (hdrStatus.isHdrActive && dynRange.badgeText.isNotEmpty()) {
                                text = dynRange.badgeText
                                visibility = View.VISIBLE
                            } else {
                                visibility = View.GONE
                            }
                        }
                    }
                }

                btnPlayPause.setOnClickListener {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                        player.play()
                    } else {
                        player.playWhenReady = !player.playWhenReady
                    }
                }

                val mediaTitle = player.mediaMetadata.title?.toString()
                if (tvPlayerTitle.text.isNullOrEmpty() && !mediaTitle.isNullOrEmpty()) {
                    tvPlayerTitle.text = mediaTitle
                }
                if (toolbar.title.isNullOrEmpty()) {
                    toolbar.title = player.mediaMetadata.title
                }

                if (toolbar.title.isNullOrEmpty()) {
                    // if `player.mediaMetadata.title` was empty
                    toolbar.title = getString(R.string.unknown)
                }

                val epPrefix = if (isMovie) {
                    "Filme Completo"
                } else if (currentEpNumber > 0) {
                    "Episódio ${currentEpNumber.toString().padStart(2, '0')}"
                } else {
                    ""
                }
                val fullMeta = if (epPrefix.isNotEmpty() && subtitleText.isNotEmpty()) {
                    "$epPrefix • $subtitleText"
                } else if (epPrefix.isNotEmpty()) {
                    epPrefix
                } else {
                    subtitleText
                }
                tvPlayerMeta.text = fullMeta
                toolbar.subtitle = subtitleText

                mediaSessionHelper?.updateMetadata(
                    title = currentTitle.ifBlank { toolbar.title?.toString() ?: "" },
                    seriesName = fullMeta,
                    durationMs = player.duration.coerceAtLeast(0L)
                )
                val width = player.videoFormat?.width ?: 0
                val height = player.videoFormat?.height ?: 0
                MakimonoPiPHelper.updatePiPParams(this@PlayerActivity, width, height, isPlaying = player.isPlaying)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            TransitionManager.beginDelayedTransition(
                mainControlsRoot,
                AutoTransition().apply {
                    interpolator = AccelerateInterpolator()
                    duration = resources.getInteger(R.integer.motion_osd_hide).toLong()
                }
            )
            btnPlayPause.icon = ContextCompat.getDrawable(
                /* context */ applicationContext,
                /* drawableId */ if (isPlaying) R.drawable.ic_pause_24
                else R.drawable.ic_play_24
            )
            AppLog.d(TAG, "isPlaying=${isPlaying}")

            mediaSessionHelper?.updatePlaybackState(
                isPlaying = isPlaying,
                positionMs = player.currentPosition,
                speed = player.playbackParameters.speed,
                canSkipNext = nextEpisode != null,
                canSkipPrevious = prevEpisode != null
            )
            val width = player.videoFormat?.width ?: 0
            val height = player.videoFormat?.height ?: 0
            MakimonoPiPHelper.updatePiPParams(this@PlayerActivity, width, height, isPlaying = isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            AppLog.e(TAG, "ExoPlayer onPlayerError: errorCodeName=${error.errorCodeName}, errorCode=${error.errorCode}", error)
            showError(error)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            if (::player.isInitialized) {
                val pos = player.currentPosition
                val duration = player.duration
                updateIntroButtonVisibility(pos)
                checkAutoPlayNextEpisode(pos, duration)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            autoSelectPreferredTracks(tracks)
        }

        override fun onCues(cueGroup: CueGroup) {
            super.onCues(cueGroup)
            handleNormalizedCues(cueGroup.cues)
        }

        @Deprecated("Deprecated in Java")
        override fun onCues(cues: List<Cue>) {
            @Suppress("DEPRECATION")
            super.onCues(cues)
            handleNormalizedCues(cues)
        }
    }

    private fun handleNormalizedCues(cues: List<Cue>) {
        if (cues.isEmpty()) {
            playerView.subtitleView?.setCues(emptyList())
            return
        }
        playerView.subtitleView?.setCues(
            zechs.drive.stream.utils.SubtitleCueComposer.compose(cues, currentSubtitleStyle)
        )
    }

    private fun applySubtitleStyleToPlayer(style: zechs.drive.stream.utils.SubtitleStyle) {
        currentSubtitleStyle = style
        zechs.drive.stream.utils.SubtitleAppearance.applyToExo(playerView.subtitleView, style)
    }

    private fun isTransportStreamMedia(titleOrName: String, uriString: String = ""): Boolean {
        val lowerTitle = titleOrName.lowercase()
        val lowerUri = uriString.lowercase().substringBefore('?')
        return lowerTitle.endsWith(".ts") ||
            lowerTitle.endsWith(".m2ts") ||
            lowerTitle.endsWith(".mts") ||
            lowerTitle.endsWith(".tp") ||
            lowerUri.endsWith(".ts") ||
            lowerUri.endsWith(".m2ts") ||
            lowerUri.endsWith(".mts") ||
            lowerUri.endsWith(".tp")
    }

    private fun initPlayer() {
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setTsExtractorTimestampSearchBytes(2500 * TsExtractor.TS_PACKET_SIZE)
            .setConstantBitrateSeekingEnabled(true)

        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            // Always prioritize hardware-accelerated decoders (e.g. c2.mtk.hevc.decoder on Android TV).
            // Do not force c2.android software decoders for HEVC as they drop to 1 FPS on mobile/TV ARM chips.
            decoders.sortedWith(compareByDescending { it.hardwareAccelerated })
        }

        val rendererFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
            setMediaCodecSelector(mediaCodecSelector)
            setEnableDecoderFallback(true)
        }

        // handles the duration of media to retain in the buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                BufferConfig.MIN_BUFFER_DURATION,
                BufferConfig.MAX_BUFFER_DURATION,
                BufferConfig.MIN_PLAYBACK_START_BUFFER,
                BufferConfig.MIN_PLAYBACK_RESUME_BUFFER
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(BufferConfig.BACK_BUFFER_DURATION, /* retainBackBufferFromKeyframe = */ true)
            .build()

        trackSelector = DefaultTrackSelector(this).apply {
            parameters = this.buildUponParameters()
                .setPreferredAudioLanguages("ja", "jpn", "jp", "japanese")
                .setPreferredTextLanguages("pt", "por", "pt-BR", "pt_BR", "pob", "portuguese")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }

        val baseHttpDataSourceFactory = DataSource.Factory {
            val dataSource = DefaultHttpDataSource.Factory()

            AuthenticatingDataSource
                .Factory(dataSource, tokenProvider.get())
                .createDataSource()
        }

        dataSourceFactory = DefaultDataSource.Factory(this, baseHttpDataSourceFactory)

        player = ExoPlayer.Builder(this, rendererFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    dataSourceFactory,
                    extractorsFactory
                )
            )
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setSeekForwardIncrementMs(10_000)
            .setSeekBackIncrementMs(10_000)
            .build()

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                activeVideoDecoderName = decoderName
                if (isKodiHudVisible) updateKodiInfoHud()
            }
        })

        // Plan v4 Fase I: Controller visibility and satellite animations are managed
        // exclusively by OsdController. No competing listeners or timers on PlayerView.

        lifecycleScope.launch {
            try {
                applySubtitleStyleToPlayer(appSettings.get().fetchSubtitleStyle())
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed loading subtitle style", e)
            }
        }

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
        ) && btnBack.isFocused

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
            if (btnBack.isFocused) {
                return true
            }
            val timeBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
            val isTimeBarFocused = timeBar?.isFocused == true
            if (!isTimeBarFocused) {
                val isAtLeftEdge = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    playerView.findViewById<View>(R.id.btnSubtitle)?.isFocused == true
                val isAtRightEdge = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                    playerView.findViewById<View>(R.id.btnInfo)?.isFocused == true
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
                btnBack.isFocused -> zechs.drive.stream.utils.PlayerFocusItem.EXIT
                btnPlayPause.isFocused -> zechs.drive.stream.utils.PlayerFocusItem.PLAY_PAUSE
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
                    zechs.drive.stream.utils.PlayerFocusItem.EXIT -> btnBack.requestFocus()
                    zechs.drive.stream.utils.PlayerFocusItem.PLAY_PAUSE -> btnPlayPause.requestFocus()
                    zechs.drive.stream.utils.PlayerFocusItem.OTHER -> Unit
                }
                return true
            }
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
                        if (::player.isInitialized) {
                            player.playbackParameters = androidx.media3.common.PlaybackParameters(speedBeforeCenterHold)
                        }
                        gestureHelper.hideSpeedPill()
                        ignoreNextCenterKeyUp = true
                        return true
                    }
                    if (ignoreNextCenterKeyUp) {
                        ignoreNextCenterKeyUp = false
                        return true
                    }
                    if (!isControlsShowing()) {
                        animateShowController()
                        btnPlayPause.post { btnPlayPause.requestFocus() }
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
                        seekRelative(deltaSeconds * 1000L)
                        gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
                        mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
                        return true
                    } else {
                        val timeBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
                        if (timeBar?.isFocused == true) {
                            seekRelative(-10000L)
                            gestureHelper.showSeekRipple(-10)
                            gestureHelper.showNotification("⏪ -10s")
                            osdController.resetInactivityTimer()
                            return true
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isControlsShowing()) {
                        mainHandler.removeCallbacks(resetSeekAccumulatorRunnable)
                        val deltaSeconds = seekStepPolicy.onSeekPress(1)
                        seekRelative(deltaSeconds * 1000L)
                        gestureHelper.showSeekRipple(seekStepPolicy.cumulativeSeconds)
                        mainHandler.postDelayed(resetSeekAccumulatorRunnable, zechs.drive.stream.utils.SeekStepPolicy.TIMEOUT_MS)
                        return true
                    } else {
                        val timeBar = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
                        if (timeBar?.isFocused == true) {
                            seekRelative(10000L)
                            gestureHelper.showSeekRipple(10)
                            gestureHelper.showNotification("⏩ +10s")
                            osdController.resetInactivityTimer()
                            return true
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isControlsShowing()) {
                        playerView.showController()
                        btnPlayPause.post { btnPlayPause.requestFocus() }
                        return true
                    }
                }

                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                        player.play()
                    } else if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                    }
                    player.play()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    player.pause()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_PAGE_DOWN -> {
                    seekRelative(SKIP_INTRO_MS)
                    gestureHelper.showNotification("⏩ +90s Pular Abertura")
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

                KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_GUIDE,
                KeyEvent.KEYCODE_WINDOW -> {
                    toggleKodiInfoHud()
                    return true
                }

                KeyEvent.KEYCODE_CAPTIONS -> {
                    showSubtitleTrackDialog()
                    return true
                }

                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_PAGE_UP -> {
                    seekRelative(-SKIP_INTRO_MS)
                    gestureHelper.showNotification("⏪ -90s Voltar")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPlayerModalVisibilityChanged(isVisible: Boolean) {
        val transition = if (isVisible) {
            playerModalVisibilityState.onModalOpened(osdWasVisible = isControlsShowing())
        } else {
            playerModalVisibilityState.onModalClosed()
        }

        when (transition) {
            PlayerModalTransition.HIDE_OSD -> {
                modalReturnFocus = playerView.findFocus()?.takeIf { it.isFocusable }
                animateHideController()
            }
            PlayerModalTransition.RESTORE_OSD -> if (!isFinishing && !isDestroyed) {
                animateShowController()
                val target = modalReturnFocus
                modalReturnFocus = null
                zechs.drive.stream.utils.LifecycleDelays.postDelayed(playerView, 250L, this@PlayerActivity) {
                    if (!isControlsShowing()) return@postDelayed
                    target?.takeIf { it.isShown && it.isFocusable }
                        ?.requestFocus()
                        ?: playerView.findViewById<View>(R.id.btnPlayPause)?.requestFocus()
                }
            }
            PlayerModalTransition.NONE -> Unit
        }
    }

    private fun handleBackNavigation(): Boolean {
        if (isKodiHudVisible) {
            toggleKodiInfoHud()
            return true
        }
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
                osdController.hide()
                gestureHelper.showNotification(getString(R.string.player_back_again_to_exit))
            }
            return true
        }
        return false
    }

    private var controllerVisible = false

    private fun animateHideController(onComplete: (() -> Unit)? = null) {
        if (!isOsdInitialized) return
        osdController.hide(onComplete)
    }

    private fun animateShowController() {
        if (!isOsdInitialized) return
        osdController.show()
    }

    private fun handleLockingControls() {
        if (!reduceMotionEnabled) {
            TransitionManager.beginDelayedTransition(
                playerView, AutoTransition().apply { duration = resources.getInteger(R.integer.motion_osd_hide).toLong() }
            )
        }
        if (controlsLocked) {
            lockControls()
        } else {
            unlockControls()
        }
    }

    private fun lockControls() {
        controlsScrollView.visibility = View.GONE
        mainControlsRoot.visibility = View.GONE
        skipIntroRow.visibility = View.GONE
        binding.netflixSkipRow.visibility = View.GONE
        binding.nextEpisodeCard.root.visibility = View.GONE
        btnUnlock.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        titleBlock.visibility = View.GONE
    }

    private fun unlockControls() {
        btnUnlock.visibility = View.GONE
        toolbar.visibility = View.GONE
        titleBlock.visibility = View.VISIBLE
        controlsScrollView.visibility = View.VISIBLE
        mainControlsRoot.visibility = View.VISIBLE
        skipIntroRow.visibility = View.VISIBLE
    }

    private fun releasePlayer() {
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            AppLog.logSwallowed(TAG, "releasing loudnessEnhancer", e)
        }
        loudnessEnhancer = null
        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.clearMediaItems()
            player.release()
        }
        playerView.player = null
    }

    private fun playMedia(startAtPositionMs: Long? = null) {
        hasAutoSelectedTracks = false
        hasScrobbledThisEp = false
        hasPromptedRating = false
        currentMalAnime = null
        activeVideoDecoderName = null

        val reviewMediaUri = intent.getStringExtra("reviewMediaUri")
            ?.takeIf { BuildConfig.DEBUG && it.isNotBlank() }

        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") }
        val title = currentTitle.ifBlank { intent.getStringExtra("title") }
        val seriesTitleExtra = intent.getStringExtra(PlayerLauncher.EXTRA_SERIES_TITLE)
        val isMovieExtra = intent.getBooleanExtra(PlayerLauncher.EXTRA_IS_MOVIE, false)

        val parsed = EpisodeParser.parse(title ?: "")
        isMovie = isMovieExtra || (parsed.episode == null && (parsed.episodeBadge == "FILME" || parsed.isSpecial))
        currentEpNumber = if (isMovie) 0 else (parsed.episode?.toInt() ?: 0)

        val searchTitle = parsed.showTitle.ifBlank { title ?: "" }
        if (reviewMediaUri == null && searchTitle.isNotBlank() && !isMovie && currentEpNumber > 0) {
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

        val displayTitle = if (isMovie && !seriesTitleExtra.isNullOrBlank()) {
            seriesTitleExtra
        } else {
            parsed.showTitle.ifBlank { parsed.cleanTitle.ifBlank { title ?: "" } }
        }

        toolbar.title = displayTitle
        tvPlayerTitle.text = displayTitle
        val epText = PlaybackLabel.renderSubtitle(this, isMovie, currentEpNumber)
        tvPlayerMeta.text = epText
        if (::btnEpisodes.isInitialized) {
            btnEpisodes.visibility = if (isMovie || playlist.size <= 1) View.GONE else View.VISIBLE
        }

        mediaSessionHelper?.updateMetadata(
            title = parsed.cleanTitle.ifBlank { title ?: "" },
            seriesName = parsed.showTitle.ifBlank { null },
            durationMs = if (::player.isInitialized) player.duration.coerceAtLeast(0L) else 0L
        )

        playerView.apply {
            player = this@PlayerActivity.player
            // Plan v4 Fase I: Disable native timeout and touch hide; OsdController is the single owner.
            controllerHideOnTouch = false
            controllerAutoShow = false
            controllerShowTimeoutMs = 0
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        if (reviewMediaUri != null) {
            playerView.useController = true
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setAudioAttributes(audioAttributes, true)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && !isFinishing && !isDestroyed) {
                        player.pause()
                        animateShowController()
                        btnPlayPause.post { btnPlayPause.requestFocus() }
                    }
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.parse(reviewMediaUri)))
            player.prepare()
            player.playWhenReady = true
            player.play()
            playerView.showController()
            btnPlayPause.post { btnPlayPause.requestFocus() }
            return
        }

        if (fileId != null) {
            viewModel.getWatch(fileId)
            lifecycleScope.launch {
                val (savedAudioDelay, savedSubDelay) = viewModel.getDelays(fileId)
                if (savedAudioDelay != 0L) {
                    currentAudioDelayMs = savedAudioDelay
                    AppLog.d(TAG, "Restored saved audio offset: ${savedAudioDelay}ms")
                }
                if (savedSubDelay != 0L) {
                    currentSubtitleOffsetMs = savedSubDelay
                    AppLog.d(TAG, "Restored saved subtitle offset: ${savedSubDelay}ms")
                }
            }
            val streamUri = getStreamUrl(fileId)

            val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
            val matching = folderSubtitles.filter { it.matchesVideo(title ?: "") }
            matching.forEach { sub ->
                val safeName = sub.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val cachedFile = java.io.File(cacheDir, "subtitles/${sub.id}_$safeName")
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    val isPortuguese = sub.languageCode == "por" || sub.name.lowercase().contains(".por.") || sub.name.lowercase().contains("pt")
                    val (subUri, mimeType) = zechs.drive.stream.utils.SubtitleConverter.prepareSubtitleForExoPlayer(
                        cacheDir, sub, cachedFile, currentSubtitleStyle.bridgeGapMs, currentSubtitleStyle.subtitleDelayMs
                    )
                    val subTag = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "[Local]" else "[Drive]"
                    subConfigs.add(
                        MediaItem.SubtitleConfiguration.Builder(subUri)
                            .setMimeType(mimeType)
                            .setLanguage(sub.languageCode)
                            .setLabel("★ $subTag ${sub.languageLabel}")
                            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                            .setSelectionFlags(if (isPortuguese) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    )
                    addedSubtitleFileIds.add(sub.id)
                }
            }

            val isTsStream = isTransportStreamMedia(title ?: "", streamUri.toString())

            val mediaItem = MediaItem.Builder()
                .setUri(streamUri)
                .apply {
                    if (isTsStream) {
                        setMimeType(MimeTypes.VIDEO_MP2T)
                    }
                    if (subConfigs.isNotEmpty()) {
                        setSubtitleConfigurations(subConfigs)
                    }
                }
                .build()

            val prewarmed = bingeBufferManager.get().consumePrewarmedData(fileId)
            if (prewarmed != null && prewarmed.chapters.isNotEmpty()) {
                skipController.setChapters(prewarmed.chapters)
                parsedChapters = skipController.getChapters()
                AppLog.d(TAG, "Applied pre-warmed MKV chapters for '$fileId': ${parsedChapters.size} (0ms latency)")
                if (::player.isInitialized) {
                    updateIntroButtonVisibility(player.currentPosition)
                }
            } else if (!isTsStream) {
                lifecycleScope.launch {
                    try {
                        val token = withContext(Dispatchers.IO) { tokenProvider.get().validToken() }
                        // P2-04: extração de capítulos MKV não pode travar o
                        // startup do episódio — IO explícito + timeout de 5s.
                        val chapters = kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                            withContext(Dispatchers.IO) {
                                MatroskaChapterParser.extractChapters(streamUri.toString(), token)
                            }
                        } ?: emptyList()
                        skipController.setChapters(chapters)
                        parsedChapters = skipController.getChapters()
                        AppLog.d(TAG, "Extracted ${chapters.size} chapters from MKV")
                        if (::player.isInitialized) {
                            updateIntroButtonVisibility(player.currentPosition)
                        }
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Chapter extraction failed", e)
                    }
                }
            }

            if (prewarmed != null && prewarmed.aniSkipChapters.isNotEmpty()) {
                mergeAniSkipChapters(prewarmed.aniSkipChapters)
                AppLog.d(TAG, "Applied pre-warmed AniSkip chapters for '$fileId': ${prewarmed.aniSkipChapters.size} (0ms latency)")
            }

            val passedStartMs = intent.getLongExtra("startPosition", -1L)
            intent.removeExtra("startPosition")
            val effectiveStartMs = startAtPositionMs ?: if (passedStartMs >= 0L) passedStartMs else null

            player.apply {
                removeListener(playerListener)
                addListener(playerListener)
                setAudioAttributes(audioAttributes, true)
                setMediaItem(mediaItem, /* resetPosition = */ true)
                prepare()
                if (effectiveStartMs != null && effectiveStartMs > 0L) {
                    seekTo(effectiveStartMs)
                } else {
                    seekToDefaultPosition()
                }
                playWhenReady = true
            }
            player.play()
            startProgressTracker()

            if (folderSubtitles.isNotEmpty()) {
                loadMatchingExternalSubtitles()
            } else {
                viewModel.fetchSubtitles(fileId)
            }

            if (effectiveStartMs == null) {
                viewModel.getWatchPosition(fileId) { startPosition ->
                    if (currentFileId == fileId && !isFinishing && startPosition > 5_000L) {
                        resumeVideo(startPosition)
                    }
                }
            }

        }
    }

    private fun loadMatchingExternalSubtitles() {
        val videoTitle = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }
        val matching = folderSubtitles.filter { it.matchesVideo(videoTitle) }

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Load permanently saved online subtitles from internal storage
            val savedSubs = onlineSubtitleManager.get().getSavedSubtitles(videoTitle)
            savedSubs.forEach { saved ->
                val virtualId = "saved_${saved.file.nameWithoutExtension}"
                if (!addedSubtitleFileIds.contains(virtualId)) {
                    addedSubtitleFileIds.add(virtualId)
                    val virtualSub = SubtitleItem(
                        id = virtualId,
                        name = "💾 [Salva] ${saved.langName}",
                        languageLabel = saved.langName,
                        languageCode = saved.lang
                    )
                    withContext(Dispatchers.Main) {
                        applyExternalSubtitle(saved.file, virtualSub, isMatching = true, forceSelect = saved.isPortuguese)
                    }
                }
            }

            // 2. Load matching Drive subtitles
            matching.forEach { sub ->
                if (!addedSubtitleFileIds.contains(sub.id)) {
                    val cached = viewModel.downloadSubtitle(sub, cacheDir)
                    if (cached != null && cached.exists()) {
                        addedSubtitleFileIds.add(sub.id)
                        withContext(Dispatchers.Main) {
                            applyExternalSubtitle(cached, sub, isMatching = true)
                        }
                    }
                }
            }

            // 3. Automatically download and index PT-BR subtitle if none is present
            val hasPortugueseSub = savedSubs.any { it.isPortuguese } ||
                matching.any {
                    zechs.drive.stream.utils.SubtitleSelectionPolicy.isPortuguese(it.languageCode, it.name)
                }

            if (!hasPortugueseSub) {
                try {
                    val parsed = EpisodeParser.parse(videoTitle)
                    val seasonNum = parsed.season?.toInt()
                    val epDouble = parsed.episode?.toDouble()
                    val seriesExtra = intent.getStringExtra(PlayerLauncher.EXTRA_SERIES_TITLE)
                    val autoRes = onlineSubtitleManager.get().downloadBestSubtitle(
                        rawTitle = videoTitle,
                        season = seasonNum,
                        episode = epDouble,
                        seriesTitle = seriesExtra,
                        preferredLanguage = zechs.drive.stream.utils.SubtitleLanguage.PT_BR
                    )
                    autoRes.onSuccess { (cachedFile, sub) ->
                        val virtualId = "saved_${cachedFile.nameWithoutExtension}"
                        if (!addedSubtitleFileIds.contains(virtualId)) {
                            addedSubtitleFileIds.add(virtualId)
                            val virtualSub = SubtitleItem(
                                id = virtualId,
                                name = "💾 [PT-BR] ${sub.langName}",
                                languageLabel = sub.langName,
                                languageCode = sub.lang
                            )
                            withContext(Dispatchers.Main) {
                                applyExternalSubtitle(
                                    cachedFile,
                                    virtualSub,
                                    isMatching = true,
                                    forceSelect = true
                                )
                                Snackbar.make(
                                    playerView,
                                    "⚡ Legenda PT-BR baixada e indexada (${sub.confidenceScore}% certeza)",
                                    1800
                                ).apply {
                                    anchorView = progressViewGroup
                                }.show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Auto PT-BR subtitle download error: ${e.message}")
                }
            }
        }
    }

    private fun applyExternalSubtitle(
        cachedFile: java.io.File,
        sub: SubtitleItem,
        isMatching: Boolean = false,
        forceSelect: Boolean = false
    ) {
        if (!::player.isInitialized) return
        val currentMediaItem = player.currentMediaItem ?: return

        activeExternalSubItem = sub
        activeExternalSubFile = cachedFile

        val (rawUri, mimeType) = zechs.drive.stream.utils.SubtitleConverter.prepareSubtitleForExoPlayer(
            cacheDir, sub, cachedFile, currentSubtitleStyle.bridgeGapMs, currentSubtitleStyle.subtitleDelayMs
        )
        val finalUri = rawUri

        val isPortuguese = sub.languageCode == "por" || sub.name.lowercase().contains(".por.") || sub.name.lowercase().contains("pt")
        val subTag = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "[Local]" else "[Drive]"
        val trackLabel = if (isMatching) "★ $subTag ${sub.languageLabel}" else "📁 $subTag ${sub.name}"

        val subConfig = MediaItem.SubtitleConfiguration.Builder(finalUri)
            .setMimeType(mimeType)
            .setLanguage(sub.languageCode)
            .setLabel(trackLabel)
            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .setSelectionFlags(if (forceSelect || (isMatching && isPortuguese)) C.SELECTION_FLAG_DEFAULT else 0)
            .build()

        val existingConfigs = currentMediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
        val filteredConfigs = existingConfigs.filter { !it.uri.toString().contains(sub.id) }
        val newConfigs = filteredConfigs + subConfig

        forcedSubtitleUri = subConfig.uri

        val newMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(newConfigs)
            .build()

        val currentPos = player.currentPosition
        val playWhenReady = player.playWhenReady
        hasAutoSelectedTracks = false
        player.setMediaItem(newMediaItem, /* resetPosition = */ false)
        player.prepare()
        player.seekTo(currentPos)
        player.playWhenReady = playWhenReady

        val delaySuffix = if (currentSubtitleOffsetMs != 0L) " (${if (currentSubtitleOffsetMs > 0) "+" else ""}${currentSubtitleOffsetMs}ms)" else ""
        Snackbar.make(playerView, "Legenda: $trackLabel$delaySuffix", 1500).apply {
            anchorView = progressViewGroup
        }.show()
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = lifecycleScope.launch {
            while (isActive) {
                if (::player.isInitialized) {
                    val pos = player.currentPosition
                    val duration = player.duration
                    updateIntroButtonVisibility(pos)
                    checkAutoPlayNextEpisode(pos, duration)
                    checkPredictiveBingeBuffer(pos, duration)
                    checkMalScrobble(pos, duration)
                    checkMalCompletionPrompt(pos, duration)
                }
                delay(1000L)
            }
        }
    }

    private fun checkPredictiveBingeBuffer(posMs: Long, durationMs: Long) {
        if (durationMs <= 60_000L) return
        val progress = posMs.toDouble() / durationMs.toDouble()
        if (progress >= 0.75) {
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
                    if (res is Resource.Success) {
                        Snackbar.make(playerView, "MAL: Episódio $currentEpNumber sincronizado! (${anime.title})", 2500).apply {
                            anchorView = progressViewGroup
                        }.show()
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
                    activity = this@PlayerActivity,
                    animeTitle = anime.title,
                    totalEpisodes = anime.numEpisodes,
                    onSubmit = { score ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val res = malRepository.get().completeAnimeWithScore(anime.id, anime.numEpisodes, score)
                            withContext(Dispatchers.Main) {
                                if (res is Resource.Success) {
                                    Toast.makeText(this@PlayerActivity, getString(R.string.mal_anime_completed, score), Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@PlayerActivity, getString(R.string.mal_save_score_error, res.message ?: ""), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun checkAutoPlayNextEpisode(positionMs: Long, durationMs: Long) {
        val decision = playbackCoordinator.onPlaybackTick(
            positionMs = positionMs,
            durationMs = durationMs,
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

        // Suppress skip intro/credits buttons to prevent overlap with next episode card
        binding.netflixSkipRow.animate().cancel()
        binding.netflixSkipRow.visibility = View.GONE
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

        val card = binding.nextEpisodeCard
        card.root.translationY = if (controllerVisible) -resources.getDimensionPixelOffset(R.dimen.next_episode_card_offset).toFloat() else 0f

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
                if (::player.isInitialized) {
                    val remainingMs = player.duration - player.currentPosition
                    val sec = kotlin.math.ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
                    card.tvNextEpisodeCountdown.text = "A SEGUIR • ${sec}s"
                    val progressPercent = ((sec.toFloat() / totalCountdownSeconds.toFloat()) * 100).toInt().coerceIn(0, 100)
                    card.pbNextEpisodeCountdown.progress = progressPercent
                    if (sec <= 0 || remainingMs <= 1_000L) {
                        playNextEpisodeDirectly()
                        break
                    }
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
            playbackCoordinator.cancelAutoplay()
        }
        isNextEpisodeCardShowing = false
        val card = binding.nextEpisodeCard.root
        card.animate().cancel()
        val motionCardDuration = resources.getInteger(R.integer.motion_card).toLong()
        card.animate().alpha(0f).setDuration(motionCardDuration).withEndAction {
            if (!isNextEpisodeCardShowing) {
                card.visibility = View.GONE
                card.translationY = 0f
            }
        }.start()
        if (::player.isInitialized) {
            updateIntroButtonVisibility(player.currentPosition)
        }
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
        playbackCoordinator.resetAutoplayCancellation()
        addedSubtitleFileIds.clear()
        updateNextEpisode()

        playMedia()
        val parsed = EpisodeParser.parse(next.title)
        Snackbar.make(playerView, "Iniciando: ${parsed.cleanTitle}", 1500).apply {
            anchorView = progressViewGroup
        }.show()
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
        playbackCoordinator.resetAutoplayCancellation()
        addedSubtitleFileIds.clear()
        updateNextEpisode()

        playMedia()
        val parsed = EpisodeParser.parse(prev.title)
        Snackbar.make(playerView, "Iniciando: ${parsed.cleanTitle}", 1500).apply {
            anchorView = progressViewGroup
        }.show()
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
        Snackbar.make(playerView, "Iniciando: ${parsed.cleanTitle}", 1500).apply {
            anchorView = progressViewGroup
        }.show()
    }

    private fun showAniSkipPill(label: String = "Abertura pulada") {
        if (!::aniskipPill.isInitialized) return
        val cleanLabel = label
            .replace(Regex("\\s*\\(AniSkip\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^⏩\\s*"), "")
            .replace("automaticamente", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        tvAniSkipLabel.text = cleanLabel.ifBlank { "Abertura pulada" }

        val density = resources.displayMetrics.density
        val motionEnter = resources.getInteger(R.integer.motion_pill_enter).toLong()
        val motionExit = resources.getInteger(R.integer.motion_pill_exit).toLong()

        aniskipPill.animate().cancel()
        aniskipPill.translationX = 32f * density
        aniskipPill.alpha = 0f
        aniskipPill.visibility = View.VISIBLE
        aniskipPill.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(motionEnter)
            .start()

        aniskipPillJob?.cancel()
        aniskipPillJob = lifecycleScope.launch {
            delay(3200L)
            aniskipPill.animate()
                .translationX(32f * density)
                .alpha(0f)
                .setDuration(motionExit)
                .withEndAction {
                    aniskipPill.visibility = View.GONE
                    aniskipPill.translationX = 0f
                }
                .start()
        }
    }

    private fun mergeAniSkipChapters(aniSkipChapters: List<MatroskaChapterParser.ParsedChapter>) {
        if (aniSkipChapters.isEmpty()) return
        skipController.mergeAniSkipChapters(aniSkipChapters)
        parsedChapters = skipController.getChapters()
        AppLog.d(TAG, "Updated parsedChapters with AniSkip: ${parsedChapters.size} total")
        if (::player.isInitialized) {
            val markers = parsedChapters.map { ch ->
                ChapterMarker(
                    startMs = ch.startTimeMs,
                    endMs = ch.endTimeMs,
                    color = if (ch.type == MatroskaChapterParser.ChapterType.OPENING || ch.type == MatroskaChapterParser.ChapterType.ENDING) {
                        0xFF22D3EE.toInt()
                    } else {
                        0xCCFFFFFF.toInt()
                    }
                )
            }
            chapterMarkersView?.setMarkers(player.duration, markers)
            updateIntroButtonVisibility(player.currentPosition)
        }
    }

    private fun updateIntroButtonVisibility(positionMs: Long) {
        if (controlsLocked || isNextEpisodeCardShowing) {
            binding.netflixSkipRow.animate().cancel()
            binding.netflixSkipRow.visibility = View.GONE
            skipIntroRow.animate().cancel()
            skipIntroRow.visibility = View.GONE
            return
        }

        val (skipState, skipAction) = skipController.onPlaybackTick(
            positionMs = positionMs,
            autoSkipEnabled = malSessionManager.get().isAutoSkipEnabled(),
            controllerVisible = controllerVisible,
            controlsLocked = controlsLocked
        )

        activeSkipChapter = skipState.activeChapter

        if (skipAction is zechs.drive.stream.playback.session.SkipController.SkipAction.AutoSkip) {
            player.seekTo(skipAction.targetPositionMs)
            gestureHelper.showNotification(skipAction.message)
            showAniSkipPill(skipAction.message)
            return
        }

        when (val ui = skipState.buttonUi) {
            is zechs.drive.stream.playback.session.SkipController.SkipButtonUi.Visible -> {
                binding.btnNetflixSkip.text = ui.label
                binding.netflixSkipRow.animate().cancel()
                binding.netflixSkipRow.alpha = 1f
                binding.netflixSkipRow.visibility = View.VISIBLE
                skipIntroRow.visibility = View.GONE
            }
            zechs.drive.stream.playback.session.SkipController.SkipButtonUi.Hidden -> {
                binding.netflixSkipRow.animate().cancel()
                binding.netflixSkipRow.visibility = View.GONE
                skipIntroRow.visibility = View.GONE
            }
        }
    }

    private fun performSkipIntroOrCredits() {
        val target = skipController.performSkip(player.currentPosition)
        if (target != null && target > player.currentPosition) {
            seekTo(target)
            val chapter = skipController.getActiveChapter()
            val label = when (chapter?.type) {
                MatroskaChapterParser.ChapterType.RECAP -> "⏩ Recap pulado"
                MatroskaChapterParser.ChapterType.OPENING -> "⏩ Abertura pulada"
                MatroskaChapterParser.ChapterType.ENDING -> "⏩ Encerramento pulado"
                else -> if (chapter != null) "⏩ Capítulo pulado" else "⏩ +90s Pular Abertura"
            }
            Snackbar.make(playerView, label, 750).apply {
                anchorView = progressViewGroup
            }.show()
        } else {
            seekRelative(SKIP_INTRO_MS)
        }
        binding.netflixSkipRow.visibility = View.GONE
        skipIntroRow.visibility = View.GONE
    }

    private fun resumeVideo(startPosition: Long) {
        if (!::player.isInitialized || startPosition <= 0L) return
        runOnUiThread {
            try {
                player.seekTo(startPosition)
                AppLog.d(TAG, "Seeking immediately to resume position: $startPosition ms")
                Snackbar.make(
                    playerView,
                    "Retomando de onde você parou",
                    Snackbar.LENGTH_SHORT
                ).apply {
                    anchorView = progressViewGroup
                }.show()
            } catch (e: Exception) {
                AppLog.w(TAG, "Error seeking to resume position", e)
            }
        }
    }


    /**
     * Seeks forward/backward by [deltaMs], clamped to the media's
     * actual bounds so it never seeks past the end or before zero.
     * Pass a negative value to seek backward (used by btnSkipIntroBack).
     *
     * Guard: does nothing (with a brief Toast) if the stream's index has not
     * been fetched yet — this is the reason the buttons appeared broken when
     * tapped immediately after playback started.
     */
    private fun seekRelative(deltaMs: Long) {
        if (!player.isCurrentMediaItemSeekable) {
            Toast.makeText(this, getString(R.string.seeking_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        val duration = player.duration
        val target = player.currentPosition + deltaMs
        val clamped = when {
            target < 0L -> 0L
            duration != C.TIME_UNSET && target > duration -> duration
            else -> target
        }
        AppLog.d(TAG, "seekRelative(${deltaMs}ms) -> ${clamped}ms")
        player.seekTo(clamped)
    }

    private fun seekTo(positionMs: Long) {
        if (!player.isCurrentMediaItemSeekable) {
            Toast.makeText(this, getString(R.string.seeking_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        val duration = player.duration
        val clamped = when {
            positionMs < 0L -> 0L
            duration != C.TIME_UNSET && positionMs > duration -> duration
            else -> positionMs
        }
        AppLog.d(TAG, "seekTo(${positionMs}ms) -> ${clamped}ms")
        player.seekTo(clamped)
    }

    private fun getStreamUrl(fileId: String): Uri {
        val streamUrl = MediaUriResolver.resolveStreamUri(fileId, currentLocalUri)
        val uri = Uri.parse(streamUrl)
        AppLog.d(TAG, "STREAM_URL=$uri")
        return uri
    }

    override fun updateOrientation(newConfig: Configuration) {
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                orientation = Orientation.PORTRAIT
                tvRotate?.text = "Paisagem"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    btnRotate?.tooltipText = getString(R.string.landscape)
                }
                ivRotate?.setImageDrawable(
                    ContextCompat.getDrawable(this@PlayerActivity, R.drawable.ic_landscape_24)
                )
            }
            else -> {
                orientation = Orientation.LANDSCAPE
                tvRotate?.text = "Girar"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    btnRotate?.tooltipText = getString(R.string.portrait)
                }
                ivRotate?.setImageDrawable(
                    ContextCompat.getDrawable(this@PlayerActivity, R.drawable.ic_portrait_24)
                )
            }
        }
    }

    private fun showAudioTrackDialog() {
        data class AudioTrackItem(val group: Tracks.Group, val trackIndex: Int, val name: String, val isSelected: Boolean)
        val audioItems = mutableListOf<AudioTrackItem>()

        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.takeIf { it.isNotBlank() }
                val label = format.label?.takeIf { it.isNotBlank() }
                val name = if (label != null && lang != null) {
                    "$label ($lang)"
                } else label ?: lang ?: "Faixa ${audioItems.size + 1}"
                val selected = group.isTrackSelected(i)
                audioItems.add(AudioTrackItem(group, i, name, selected))
            }
        }

        if (audioItems.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_audio_tracks), Toast.LENGTH_SHORT).show()
            return
        }

        val menuItems = audioItems.mapIndexed { idx, item ->
            GlassMenuItem(
                id = "audio_$idx",
                title = item.name,
                isSelected = item.isSelected,
                tag = item
            )
        }

        val nightModeLabel = if (isNightModeAudioEnabled) "Modo Noturno (Ativo)" else "Modo Noturno (Desat.)"
        PlayerGlassMenuDialog(
            context = this,
            title = getString(R.string.select_audio),
            items = menuItems
        ) { selected ->
            val chosen = selected.tag as? AudioTrackItem ?: return@PlayerGlassMenuDialog
            val builder = player.trackSelectionParameters.buildUpon()
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            builder.addOverride(TrackSelectionOverride(chosen.group.mediaTrackGroup, listOf(chosen.trackIndex)))
            player.trackSelectionParameters = builder.build()
            Snackbar.make(playerView, "Áudio: ${chosen.name}", 750).apply {
                anchorView = progressViewGroup
            }.show()
        }.setFooterSecondary("Sincronia") {
            showAudioSyncDialog()
        }.setFooterPrimary(nightModeLabel) {
            toggleNightModeAudio()
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
            val fid = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: "" }
            if (fid.isNotBlank()) {
                viewModel.saveDelays(fid, currentAudioDelayMs, currentSubtitleOffsetMs)
            }
            val sign = if (chosenOffset > 0) "+" else ""
            Snackbar.make(playerView, "Sincronia de áudio: ${sign}${chosenOffset}ms", 1500).apply {
                anchorView = progressViewGroup
            }.show()
        }.show()
    }

    private fun toggleNightModeAudio() {
        isNightModeAudioEnabled = !isNightModeAudioEnabled
        if (isNightModeAudioEnabled) {
            try {
                val sessionId = player.audioSessionId
                if (sessionId != android.media.AudioManager.ERROR) {
                    if (loudnessEnhancer == null) {
                        loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId)
                    }
                    // Boost speech clarity (+6 dB target gain in mB: 600mB)
                    loudnessEnhancer?.setTargetGain(600)
                    loudnessEnhancer?.enabled = true
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "LoudnessEnhancer could not be attached: ${e.message}")
            }
            player.volume = 1.0f
            Snackbar.make(playerView, "Modo Noturno ativado: Vozes realçadas e picos balanceados", 2500).apply {
                anchorView = progressViewGroup
            }.show()
        } else {
            try {
                loudnessEnhancer?.enabled = false
            } catch (e: Exception) {
                AppLog.w(TAG, "Error disabling LoudnessEnhancer: ${e.message}")
            }
            Snackbar.make(playerView, "Modo Noturno desativado: Áudio original", 2000).apply {
                anchorView = progressViewGroup
            }.show()
        }
    }

    private fun showSubtitleTrackDialog() {
        data class ExoSubChoice(
            val group: Tracks.Group?,
            val trackIndex: Int,
            val folderSub: SubtitleItem?,
            val onlineSub: zechs.drive.stream.utils.OnlineSubtitle? = null,
            val savedSub: zechs.drive.stream.utils.SavedSubtitle? = null,
            val displayName: String,
            val subtitle: String? = null,
            val isSelected: Boolean
        )

        val choices = mutableListOf<ExoSubChoice>()

        val anySubtitleSelected = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .any { group -> (0 until group.length).any { group.isTrackSelected(it) } }
        val isTextDisabled = !anySubtitleSelected

        // 1. Off option
        choices.add(ExoSubChoice(null, -1, null, null, null, getString(R.string.track_off), null, isTextDisabled))

        // 2. Active/embedded tracks in ExoPlayer
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.takeIf { it.isNotBlank() }
                val label = format.label?.takeIf { it.isNotBlank() }
                val name = if (label != null && lang != null) {
                    "$label ($lang)"
                } else label ?: lang ?: "Legenda ${choices.size}"
                val selected = group.isTrackSelected(i)
                choices.add(ExoSubChoice(group, i, null, null, null, name, "Embutida", selected))
            }
        }

        val videoTitle = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }

        // 3. Permanently saved online subtitles from internal storage
        val savedSubs = onlineSubtitleManager.get().getSavedSubtitles(videoTitle)
        savedSubs.forEach { saved ->
            val alreadyPresent = choices.any {
                it.displayName.contains(saved.langName, ignoreCase = true)
            }
            if (!alreadyPresent) {
                choices.add(
                    ExoSubChoice(
                        group = null,
                        trackIndex = -1,
                        folderSub = null,
                        onlineSub = null,
                        savedSub = saved,
                        displayName = "💾 [Salva] ${saved.langName}",
                        subtitle = "Armazenada no dispositivo",
                        isSelected = false
                    )
                )
            }
        }

        // 4. ALL folder subtitles: ALWAYS list them!
        folderSubtitles.forEach { sub ->
            val subTag = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "[Local]" else "[Drive]"
            val sourceDesc = if (sub.source == zechs.drive.stream.data.model.MediaSource.LOCAL) "Armazenamento Local" else "Google Drive"
            val alreadyPresent = choices.any {
                it.displayName.contains(sub.name, ignoreCase = true) ||
                (it.displayName.contains(subTag, ignoreCase = true) && it.displayName.contains(sub.languageLabel, ignoreCase = true))
            }
            if (!alreadyPresent) {
                val isMatching = sub.matchesVideo(videoTitle)
                val prefix = if (isMatching) "★ $subTag " else "📁 $subTag "
                val tag = if (isMatching) " (Episódio atual)" else " (Outro episódio)"
                val label = "$prefix${sub.name}$tag"
                choices.add(ExoSubChoice(null, -1, sub, null, null, label, sourceDesc, false))
            }
        }

        fun buildMenuItems(sourceChoices: List<ExoSubChoice>): List<GlassMenuItem> {
            return sourceChoices.mapIndexed { idx, c ->
                GlassMenuItem(
                    id = "sub_$idx",
                    title = c.displayName,
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
            val choice = selected.tag as? ExoSubChoice ?: return@PlayerGlassMenuDialog
            if (choice.group != null) {
                val builder = player.trackSelectionParameters.buildUpon()
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                builder.addOverride(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.trackIndex)))
                player.trackSelectionParameters = builder.build()
                Snackbar.make(playerView, "Legenda: ${choice.displayName}", 750).apply {
                    anchorView = progressViewGroup
                }.show()
            } else if (choice.trackIndex == -1 && choice.folderSub == null && choice.onlineSub == null && choice.savedSub == null) {
                val builder = player.trackSelectionParameters.buildUpon()
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                player.trackSelectionParameters = builder.build()
                Snackbar.make(playerView, getString(R.string.track_off), 750).apply {
                    anchorView = progressViewGroup
                }.show()
            } else if (choice.savedSub != null) {
                val saved = choice.savedSub
                val virtualSub = SubtitleItem(
                    id = "saved_${saved.file.nameWithoutExtension}",
                    name = "💾 [Salva] ${saved.langName}",
                    languageLabel = saved.langName,
                    languageCode = saved.lang
                )
                applyExternalSubtitle(saved.file, virtualSub, isMatching = true, forceSelect = true)
            } else if (choice.folderSub != null) {
                val sub = choice.folderSub
                val isMatching = sub.matchesVideo(videoTitle)
                Snackbar.make(playerView, "Carregando legenda: ${sub.name}...", 1000).apply {
                    anchorView = progressViewGroup
                }.show()
                lifecycleScope.launch(Dispatchers.IO) {
                    val cached = viewModel.downloadSubtitle(sub, cacheDir)
                    if (cached != null && cached.exists()) {
                        withContext(Dispatchers.Main) {
                            applyExternalSubtitle(cached, sub, isMatching = isMatching, forceSelect = true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Snackbar.make(playerView, "Erro ao baixar legenda do Drive", 1500).apply {
                                anchorView = progressViewGroup
                            }.show()
                        }
                    }
                }
            } else if (choice.onlineSub != null) {
                val onlineSub = choice.onlineSub
                Snackbar.make(playerView, "Baixando legenda online: ${onlineSub.langName}...", 1200).apply {
                    anchorView = progressViewGroup
                }.show()
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = onlineSubtitleManager.get().downloadSubtitle(onlineSub, videoTitle)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { cachedFile ->
                            val virtualSub = SubtitleItem(
                                id = "saved_${cachedFile.nameWithoutExtension}",
                                name = "💾 [Salva] ${onlineSub.langName}",
                                languageLabel = onlineSub.langName,
                                languageCode = onlineSub.lang
                            )
                            applyExternalSubtitle(cachedFile, virtualSub, isMatching = true, forceSelect = true)
                            Snackbar.make(playerView, "Legenda salva e ativada: ${onlineSub.langName}!", 1500).apply {
                                anchorView = progressViewGroup
                            }.show()
                        }.onFailure { err ->
                            Snackbar.make(playerView, "Erro ao baixar legenda online: ${err.message}", 2000).apply {
                                anchorView = progressViewGroup
                            }.show()
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
        var currentResults: List<zechs.drive.stream.utils.OnlineSubtitle> = emptyList()

        data class HubActionTarget(
            val onlineSub: zechs.drive.stream.utils.OnlineSubtitle? = null,
            val embeddedGroup: Tracks.Group? = null,
            val embeddedIndex: Int = -1,
            val embeddedName: String? = null
        )

        fun findMatchingEmbeddedOfficialTracks(lang: zechs.drive.stream.utils.SubtitleLanguage): List<GlassMenuItem> {
            val matchedItems = mutableListOf<GlassMenuItem>()
            player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEachIndexed { gIdx, group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val trackLang = format.language?.takeIf { it.isNotBlank() }.orEmpty()
                    val label = format.label?.takeIf { it.isNotBlank() }.orEmpty()
                    val combined = "$trackLang $label".lowercase()
                    val isMatch = when (lang) {
                        zechs.drive.stream.utils.SubtitleLanguage.PT_BR ->
                            lang.matchesLanguageCode(trackLang) || combined.contains("brazil") || combined.contains("pt-br") || combined.contains("portug")
                        zechs.drive.stream.utils.SubtitleLanguage.PT_PT ->
                            lang.matchesLanguageCode(trackLang) || combined.contains("portug")
                        zechs.drive.stream.utils.SubtitleLanguage.EN ->
                            lang.matchesLanguageCode(trackLang) || combined.contains("english")
                        zechs.drive.stream.utils.SubtitleLanguage.ES ->
                            lang.matchesLanguageCode(trackLang) || combined.contains("spanish") || combined.contains("español")
                        zechs.drive.stream.utils.SubtitleLanguage.JA ->
                            lang.matchesLanguageCode(trackLang) || combined.contains("japanese")
                    }
                    if (isMatch) {
                        val cleanLabel = label.ifBlank { lang.displayName }
                        val isCrOfficial = cleanLabel.startsWith("CR_", ignoreCase = true) || cleanLabel.contains("Crunchyroll", ignoreCase = true)
                        val badge = if (isCrOfficial) "★ 100% Certeza • Oficial Crunchyroll (Stream)" else "★ 100% Certeza • Faixa Oficial do Stream"
                        matchedItems.add(
                            GlassMenuItem(
                                id = "hub_emb_${gIdx}_$i",
                                title = "${lang.flag} $cleanLabel • ★ Oficial (100%)",
                                subtitle = "$badge • Sincronia Perfeita (0ms)",
                                isSelected = group.isTrackSelected(i),
                                tag = HubActionTarget(embeddedGroup = group, embeddedIndex = i, embeddedName = cleanLabel)
                            )
                        )
                    }
                }
            }
            return matchedItems
        }

        fun buildHubItems(subs: List<zechs.drive.stream.utils.OnlineSubtitle>, lang: zechs.drive.stream.utils.SubtitleLanguage): List<GlassMenuItem> {
            val embeddedMatches = findMatchingEmbeddedOfficialTracks(lang)
            val onlineItems = subs.mapIndexed { idx, os ->
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
                    isSelected = embeddedMatches.isEmpty() && idx == 0 && lang.matchesLanguageCode(os.lang),
                    tag = HubActionTarget(onlineSub = os)
                )
            }
            val allItems = embeddedMatches + onlineItems
            if (allItems.isEmpty()) {
                return listOf(
                    GlassMenuItem(
                        id = "empty_online",
                        title = "Nenhuma legenda encontrada para ${lang.displayName}",
                        subtitle = "Experimente alternar a pílula de idioma acima (PT-BR, PT-PT, EN, ES, JA)",
                        isInteractive = false
                    )
                )
            }
            return allItems
        }

        lateinit var hubDialog: PlayerGlassMenuDialog

        fun triggerSearch(lang: zechs.drive.stream.utils.SubtitleLanguage) {
            activeLang = lang
            hubDialog.showLoading(true)
            hubDialog.updateActionButtonText("⚡ Ativar / Baixar Melhor Oficial (${lang.shortLabel})")
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
                    AppLog.e(TAG, "Error in OnlineSubtitleHub search", e)
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    currentResults = results
                    hubDialog.showLoading(false)
                    val embeddedCount = findMatchingEmbeddedOfficialTracks(lang).size
                    val verifiedCount = results.count { it.isVerifiedLanguage } + embeddedCount
                    val totalOptions = results.size + embeddedCount
                    val matchedShow = results.firstOrNull()?.matchedSeriesTitle
                    val statusPrefix = if (!matchedShow.isNullOrBlank()) "$matchedShow • " else ""
                    hubDialog.updateSubtitle(
                        "${statusPrefix}$totalOptions opções • $verifiedCount verificadas com alta certeza (${lang.shortLabel})"
                    )
                    hubDialog.updateItems(buildHubItems(results, lang))
                }
            }
        }

        fun downloadAndActivate(os: zechs.drive.stream.utils.OnlineSubtitle, lang: zechs.drive.stream.utils.SubtitleLanguage) {
            Snackbar.make(playerView, "Baixando e verificando ${os.langName} (${os.confidenceScore}% certeza)...", 1200).apply {
                anchorView = progressViewGroup
            }.show()
            lifecycleScope.launch(Dispatchers.IO) {
                val result = onlineSubtitleManager.get().downloadSubtitle(os, videoTitle, lang)
                withContext(Dispatchers.Main) {
                    result.onSuccess { cachedFile ->
                        val virtualSub = SubtitleItem(
                            id = "saved_${cachedFile.nameWithoutExtension}",
                            name = "💾 [Oficial] ${os.langName}",
                            languageLabel = os.langName,
                            languageCode = os.lang
                        )
                        applyExternalSubtitle(cachedFile, virtualSub, isMatching = true, forceSelect = true)
                        Snackbar.make(
                            playerView,
                            "✓ Legenda Oficial ativada: ${os.langName} (${os.confidenceScore}% certeza • UTF-8)",
                            1800
                        ).apply {
                            anchorView = progressViewGroup
                        }.show()
                    }.onFailure { err ->
                        Snackbar.make(playerView, "Erro ao baixar legenda: ${err.message}", 2000).apply {
                            anchorView = progressViewGroup
                        }.show()
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
            val target = selected.tag as? HubActionTarget ?: return@PlayerGlassMenuDialog
            if (target.embeddedGroup != null && target.embeddedIndex >= 0) {
                val builder = player.trackSelectionParameters.buildUpon()
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                builder.addOverride(TrackSelectionOverride(target.embeddedGroup.mediaTrackGroup, listOf(target.embeddedIndex)))
                player.trackSelectionParameters = builder.build()
                Snackbar.make(
                    playerView,
                    "★ Legenda Oficial ativada: ${target.embeddedName} (100% Certeza • Sincronia 0ms)",
                    1800
                ).apply {
                    anchorView = progressViewGroup
                }.show()
            } else if (target.onlineSub != null) {
                downloadAndActivate(target.onlineSub, activeLang)
            }
        }
            .setSubtitle("Verificação Léxica de Idioma • UTF-8 Automático")
            .setLanguageSelector(initialLanguage) { _, newLang ->
                triggerSearch(newLang)
            }
            .setActionButton("⚡ Ativar / Baixar Melhor Oficial (${initialLanguage.shortLabel})") { d ->
                val embeddedBest = findMatchingEmbeddedOfficialTracks(activeLang).firstOrNull()?.tag as? HubActionTarget
                if (embeddedBest != null && embeddedBest.embeddedGroup != null && embeddedBest.embeddedIndex >= 0) {
                    d.dismiss()
                    val builder = player.trackSelectionParameters.buildUpon()
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    builder.addOverride(TrackSelectionOverride(embeddedBest.embeddedGroup.mediaTrackGroup, listOf(embeddedBest.embeddedIndex)))
                    player.trackSelectionParameters = builder.build()
                    Snackbar.make(
                        playerView,
                        "⚡ Legenda Oficial ativada: ${embeddedBest.embeddedName} (100% Certeza)",
                        1800
                    ).apply {
                        anchorView = progressViewGroup
                    }.show()
                    return@setActionButton
                }
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
                                val virtualSub = SubtitleItem(
                                    id = "saved_${cachedFile.nameWithoutExtension}",
                                    name = "💾 [Oficial] ${sub.langName}",
                                    languageLabel = sub.langName,
                                    languageCode = sub.lang
                                )
                                applyExternalSubtitle(cachedFile, virtualSub, isMatching = true, forceSelect = true)
                                Snackbar.make(
                                    playerView,
                                    "⚡ Legenda Oficial ativada: ${sub.langName} (${sub.confidenceScore}% certeza)",
                                    1800
                                ).apply {
                                    anchorView = progressViewGroup
                                }.show()
                            }.onFailure { err ->
                                Snackbar.make(playerView, err.message ?: "Nenhuma legenda encontrada", 2000).apply {
                                    anchorView = progressViewGroup
                                }.show()
                            }
                        }
                    }
                }
            }
            .setFooterSecondary("← Voltar às Faixas") {
                showSubtitleTrackDialog()
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
                currentSubtitleStyle
            }
            SubtitleStyleDialog.show(this@PlayerActivity, style) { updated ->
                applySubtitleStyleToPlayer(updated)
                lifecycleScope.launch {
                    try {
                        appSettings.get().saveSubtitleStyle(updated)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Error saving subtitle style", e)
                    }
                }
                Snackbar.make(playerView, "Estilo de legenda salvo", 1200).apply {
                    anchorView = progressViewGroup
                }.show()
            }
        }
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
            val selectedIdx = sizeValues.indexOfFirst { kotlin.math.abs(it - savedSp) < 0.5f }.coerceAtLeast(1)

            val items = sizes.mapIndexed { idx, label ->
                GlassMenuItem(
                    id = "size_$idx",
                    title = label,
                    isSelected = idx == selectedIdx,
                    tag = sizeValues[idx]
                )
            }

            PlayerGlassMenuDialog(
                context = this@PlayerActivity,
                title = "Tamanho da Legenda",
                items = items
            ) { selected ->
                val chosenSp = selected.tag as? Float ?: 20f
                playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, chosenSp)
                lifecycleScope.launch {
                    try {
                        appSettings.get().saveSubtitleSize(chosenSp)
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Error saving subtitle size", e)
                    }
                }
                Snackbar.make(playerView, "Tamanho definido: ${selected.title}", 1000).apply {
                    anchorView = progressViewGroup
                }.show()
            }.show()
        }
    }

    private fun showSubtitleSyncDialog() {
        val options = arrayOf(
            "+500 ms (Adiantar muito)",
            "+250 ms (Adiantar)",
            "+100 ms (Adiantar um pouco)",
            "0 ms (Sincronizado)",
            "-100 ms (Atrasar um pouco)",
            "-250 ms (Atrasar)",
            "-500 ms (Atrasar muito)"
        )
        val offsets = longArrayOf(500L, 250L, 100L, 0L, -100L, -250L, -500L)
        val selectedIdx = offsets.indexOfFirst { it == currentSubtitleOffsetMs }.takeIf { it >= 0 } ?: 3

        val title = if (currentSubtitleOffsetMs != 0L) {
            "Sincronia de Legenda (${if (currentSubtitleOffsetMs > 0) "+" else ""}${currentSubtitleOffsetMs}ms)"
        } else {
            "Sincronia de Legenda (0ms)"
        }

        val items = options.mapIndexed { idx, label ->
            GlassMenuItem(
                id = "sync_$idx",
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
            currentSubtitleOffsetMs = chosenOffset
            val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: "" }
            if (fileId.isNotBlank()) {
                viewModel.saveDelays(fileId, currentAudioDelayMs, currentSubtitleOffsetMs)
            }

            val activeSub = activeExternalSubItem
            val activeFile = activeExternalSubFile

            if (activeSub != null && activeFile != null && activeFile.exists()) {
                applyExternalSubtitle(activeFile, activeSub, isMatching = true, forceSelect = true)
            } else {
                val sign = if (chosenOffset > 0) "+" else ""
                Snackbar.make(playerView, "Offset definido para ${sign}${chosenOffset}ms (aplicável a legendas do Drive/Online)", 2000).apply {
                    anchorView = progressViewGroup
                }.show()
            }
        }.show()
    }

    private fun toggleKodiInfoHud() {
        isKodiHudVisible = !isKodiHudVisible
        binding.kodiInfoHud.root.isVisible = isKodiHudVisible
        if (isKodiHudVisible) {
            startKodiHudUpdater()
        } else {
            kodiHudUpdateJob?.cancel()
        }
    }

    private fun startKodiHudUpdater() {
        kodiHudUpdateJob?.cancel()
        kodiHudUpdateJob = lifecycleScope.launch {
            while (isActive && isKodiHudVisible) {
                updateKodiInfoHud()
                delay(800L)
            }
        }
    }

    private fun updateKodiInfoHud() {
        if (!::player.isInitialized) return
        val vFormat = player.videoFormat
        val aFormat = player.audioFormat

        val codecLabel = buildString {
            append(MediaInfoDisplay.videoCodec(vFormat?.sampleMimeType))
            currentHdrStatus?.let { hdr ->
                if (hdr.isHdrActive) {
                    append(" • ${hdr.colorSpaceDesc}")
                }
            }
        }
        binding.kodiInfoHud.tvHudVideoCodec.text = codecLabel
        binding.kodiInfoHud.tvHudVideoDecoder.text = activeVideoDecoderName
            ?.let { "Decoder: $it" }
            ?: "Decoder não reportado"

        val resolutionParts = mutableListOf<String>()
        resolutionParts.add(MediaInfoDisplay.videoDimensions(vFormat?.width, vFormat?.height))
        currentAfrState?.let { afr ->
            resolutionParts.add("${afr.label} (AFR)")
        } ?: run {
            resolutionParts.add(MediaInfoDisplay.frameRate(vFormat?.frameRate))
        }
        binding.kodiInfoHud.tvHudVideoResolution.text = resolutionParts.joinToString(" • ")

        binding.kodiInfoHud.tvHudAudioInfo.text = MediaInfoDisplay.audioSummary(
            mimeType = aFormat?.sampleMimeType,
            channelCount = aFormat?.channelCount,
            sampleRateHz = aFormat?.sampleRate
        )

        // 4. Subtitle Info
        val activeSub = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSelected(it) }.map { group.getTrackFormat(it) } }
            .firstOrNull()

        binding.kodiInfoHud.tvHudSubtitleInfo.text = if (activeSub != null) {
            activeSub.label ?: activeSub.language ?: "Ativa"
        } else {
            "Nenhuma legenda ativa"
        }

        // 5. Buffer health
        val bufferedPos = player.bufferedPosition
        val currentPos = player.currentPosition
        val bufferSecs = maxOf(0.0, (bufferedPos - currentPos) / 1000.0)
        binding.kodiInfoHud.tvHudBufferDuration.text = String.format(Locale.US, "%.1fs disponíveis à frente", bufferSecs)

        val hasBufferedToEnd = player.duration > 0L && bufferedPos >= player.duration
        if (hasBufferedToEnd) {
            binding.kodiInfoHud.tvHudBufferBadge.text = "Tudo carregado"
            binding.kodiInfoHud.tvHudBufferBadge.setTextColor(Color.parseColor("#4CAF50"))
        } else if (bufferSecs >= 12.0) {
            binding.kodiInfoHud.tvHudBufferBadge.text = "🟢 Saudável (>12s)"
            binding.kodiInfoHud.tvHudBufferBadge.setTextColor(Color.parseColor("#4CAF50"))
        } else if (bufferSecs >= 4.0) {
            binding.kodiInfoHud.tvHudBufferBadge.text = "🟡 Estável (4-12s)"
            binding.kodiInfoHud.tvHudBufferBadge.setTextColor(Color.parseColor("#FFC107"))
        } else {
            binding.kodiInfoHud.tvHudBufferBadge.text = "🔴 Baixo (<4s)"
            binding.kodiInfoHud.tvHudBufferBadge.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun showChapterDialog() {
        if (parsedChapters.isNotEmpty()) {
            val currentPos = if (::player.isInitialized) player.currentPosition else 0L
            val currentIndex = parsedChapters.indexOfLast { currentPos >= it.startTimeMs }.coerceAtLeast(0)

            val items = parsedChapters.mapIndexed { idx, ch ->
                val min = (ch.startTimeMs / 1000) / 60
                val sec = (ch.startTimeMs / 1000) % 60
                val timeStr = String.format(Locale.getDefault(), "%02d:%02d", min, sec)
                GlassMenuItem(
                    id = "chapter_$idx",
                    title = ch.title,
                    subtitle = timeStr,
                    isSelected = idx == currentIndex,
                    tag = ch
                )
            }

            PlayerGlassMenuDialog(
                context = this,
                title = getString(R.string.chapters),
                items = items
            ) { selected ->
                val targetChapter = selected.tag as? MatroskaChapterParser.ParsedChapter ?: return@PlayerGlassMenuDialog
                seekTo(targetChapter.startTimeMs)
                Snackbar.make(playerView, "Capítulo: ${targetChapter.title}", 750).apply {
                    anchorView = progressViewGroup
                }.show()
            }.show()
            return
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog).apply {
            setTitle(getString(R.string.chapters))
            setMessage(R.string.player_no_chapters_switch_mpv)
            setPositiveButton("Abrir no MPV") { dialog, _ ->
                dialog.dismiss()
                launchMpvFallback()
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        }.create()
        dialog.setOnDismissListener { onPlayerModalVisibilityChanged(false) }
        onPlayerModalVisibilityChanged(true)
        dialog.show()
    }

    private fun speedSnackbar(speedIndex: Int) {
        Snackbar.make(
            binding.playerView,
            "Playback speed set to ${speed[speedIndex]}",
            /* duration */ 750
        ).apply {
            anchorView = progressViewGroup
        }.also { it.show() }
    }

    private fun autoSelectPreferredTracks(tracks: Tracks) {
        if (hasAutoSelectedTracks) return

        var preferredAudioOverride: TrackSelectionOverride? = null
        var preferredTextOverride: TrackSelectionOverride? = null

        // 1. Audio: Auto-select Japanese
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        audioLoop@ for (group in audioGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.lowercase() ?: ""
                val label = (format.label ?: "").lowercase()

                val isJapanese = lang in listOf("ja", "jpn", "jp", "japanese") ||
                        label.contains("jap") || label.contains("jpn")

                if (isJapanese) {
                    AppLog.d(TAG, "Exo auto-selected Japanese audio: lang=$lang, label=$label, index=$i")
                    preferredAudioOverride = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    break@audioLoop
                }
            }
        }

        // 2. Subtitle: Auto-select Portuguese (prioritize Brazilian Portuguese / full dialogue)
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        var bestSubScore = -1
        var bestSubGroup: Tracks.Group? = null
        var bestSubIndex = -1

        if (forcedSubtitleUri != null) {
            val targetStr = forcedSubtitleUri.toString()
            findForced@ for (group in textGroups) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id == targetStr || format.label?.contains("[Drive]") == true) {
                        bestSubGroup = group
                        bestSubIndex = i
                        bestSubScore = 999
                        break@findForced
                    }
                }
            }
        }

        if (bestSubScore < 0) {
            for (group in textGroups) {
                for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.lowercase() ?: ""
                val label = (format.label ?: "").lowercase()

                val isPortuguese = zechs.drive.stream.utils.SubtitleSelectionPolicy.isPortuguese(lang, label)

                if (isPortuguese) {
                    // P2-08: regra de pontuação única, compartilhada com o MPV.
                    val score = zechs.drive.stream.utils.SubtitleSelectionPolicy
                        .scorePortuguese("$lang $label")

                    if (score > bestSubScore) {
                        bestSubScore = score
                        bestSubGroup = group
                        bestSubIndex = i
                    }
                }
            }
        }
    }

        if (bestSubGroup != null && bestSubIndex >= 0) {
            val format = bestSubGroup.getTrackFormat(bestSubIndex)
            AppLog.d(TAG, "Exo auto-selected Portuguese subtitle: lang=${format.language}, label=${format.label}, index=$bestSubIndex")
            preferredTextOverride = TrackSelectionOverride(bestSubGroup.mediaTrackGroup, listOf(bestSubIndex))
        }

        if (preferredAudioOverride != null || preferredTextOverride != null) {
            val builder = player.trackSelectionParameters.buildUpon()
            preferredAudioOverride?.let {
                builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                builder.addOverride(it)
            }
            preferredTextOverride?.let {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                builder.addOverride(it)
            }
            player.trackSelectionParameters = builder.build()
            hasAutoSelectedTracks = true
        } else if (audioGroups.isNotEmpty() || textGroups.isNotEmpty()) {
            hasAutoSelectedTracks = true
        }
    }

    private fun launchMpvFallback() {
        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId").orEmpty() }
        val title = currentTitle.ifBlank { intent.getStringExtra("title").orEmpty() }
        if (fileId.isBlank() || title.isBlank()) {
            Snackbar.make(
                playerView,
                R.string.player_engine_fallback_missing_media,
                Snackbar.LENGTH_LONG
            ).apply {
                anchorView = progressViewGroup
            }.show()
            return
        }
        val thumbnailLink = currentThumbnailLink ?: intent.getStringExtra("thumbnailLink")
        val theme = intent.getIntExtra("theme", 0)
        val currentPos = if (::player.isInitialized) player.currentPosition else 0L

        lifecycleScope.launch {
            try {
                // P0-04: o token de acesso não transita mais em extras de
                // Intent; o MPVActivity o obtém via TokenProvider.
                run {
                    val mpvIntent = Intent(this@PlayerActivity, MPVActivity::class.java).apply {
                        putExtra("fileId", fileId)
                        putExtra("title", title)
                        putExtra(PlayerLauncher.EXTRA_SERIES_TITLE, intent.getStringExtra(PlayerLauncher.EXTRA_SERIES_TITLE))
                        putExtra(PlayerLauncher.EXTRA_IS_MOVIE, isMovie || currentEpNumber == 0)
                        putExtra("thumbnailLink", thumbnailLink)
                        putExtra("theme", theme)
                        putExtra(PlayerLauncher.EXTRA_PROFILE_ID, viewModel.playbackProfileIdForHandoff)
                        PlaybackProfileSessionPolicy.siblingFolderIdForHandoff(
                            intent.getStringExtra(PlayerLauncher.EXTRA_SIBLING_FOLDER_ID)
                        )?.let { putExtra(PlayerLauncher.EXTRA_SIBLING_FOLDER_ID, it) }
                        putExtra(
                            PlayerLauncher.EXTRA_START_POSITION,
                            PlaybackProfileSessionPolicy.explicitPositionForHandoff(currentPos)
                        )
                        putExtra("playlist", ArrayList(playlist))
                        putExtra("subtitles", ArrayList(folderSubtitles))
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(mpvIntent)
                    Toast.makeText(this@PlayerActivity, getString(R.string.switching_to_mpv), Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error launching MPV fallback", e)
                if (!isFinishing) {
                    Snackbar.make(
                        playerView,
                        R.string.player_engine_fallback_failed,
                        Snackbar.LENGTH_LONG
                    ).apply {
                        anchorView = progressViewGroup
                    }.show()
                }
            }
        }
    }

    private fun showError(error: PlaybackException) {
        AppLog.e(TAG, "ExoPlayer onPlayerError: errorCodeName=${error.errorCodeName}, errorCode=${error.errorCode}", error)

        val errorGeneral = error.localizedMessage ?: getString(R.string.something_went_wrong)
        val exoException = error as? ExoPlaybackException
        val isRendererType = exoException?.type == TYPE_RENDERER
        val rendererException = if (isRendererType) exoException?.rendererException else null
        val rendererFormat = if (isRendererType) exoException?.rendererFormat else null
        val mimeType = rendererFormat?.sampleMimeType ?: ""
        val rendererName = if (isRendererType) (exoException?.rendererName ?: "") else ""

        val errorDetailed = when (exoException?.type) {
            TYPE_SOURCE -> exoException.sourceException.localizedMessage
            TYPE_RENDERER -> rendererException?.localizedMessage
            TYPE_UNEXPECTED -> exoException.unexpectedException.localizedMessage
            TYPE_REMOTE -> errorGeneral
            else -> error.message
        }

        // 0. Mid-stream HTTP 401 recovery: automatically refresh token and resume playback
        val isHttp401 = (exoException?.type == TYPE_SOURCE) &&
                ((exoException.sourceException as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode == 401 ||
                        error.message?.contains("401") == true ||
                        errorDetailed?.contains("401") == true)

        if (isHttp401 && ::player.isInitialized) {
            AppLog.w(TAG, "HTTP 401 mid-stream error encountered. Invalidating token and attempting transparent resume...")
            val resumePos = player.currentPosition
            lifecycleScope.launch {
                try {
                    tokenProvider.get().invalidateToken()
                    val newToken = tokenProvider.get().validToken()
                    if (!newToken.isNullOrEmpty()) {
                        AppLog.i(TAG, "Token successfully refreshed after 401 mid-stream. Resuming playback at ${resumePos}ms")
                        playMedia(startAtPositionMs = resumePos)
                        return@launch
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to recover from 401 mid-stream", e)
                }
                showPlaybackFailureDialog(errorDetailed ?: errorGeneral)
            }
            return
        }

        // 1. Subtitle error resilience: recover automatically if ASS/SSA subtitles fail
        val isSubtitleError = (exoException?.type == TYPE_RENDERER) && (
                mimeType.startsWith("text/") ||
                mimeType.contains("sub") ||
                mimeType.contains("ssa") ||
                mimeType.contains("ass") ||
                rendererName.contains("Text")
        )

        if (isSubtitleError && ::player.isInitialized) {
            AppLog.w(TAG, "Subtitle rendering failed ($mimeType: ${rendererException?.message}). Disabling subtitle and resuming playback.")
            val resumePosition = player.currentPosition
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            player.seekTo(resumePosition)
            player.prepare()
            player.play()

            Snackbar.make(
                playerView,
                "Legenda incompatível com decodificador padrão. Reproduzindo sem legenda.",
                Snackbar.LENGTH_LONG
            ).apply {
                anchorView = progressViewGroup
                setAction("Abrir no MPV") { launchMpvFallback() }
            }.show()
            return
        }

        // 2. Audio error classification
        val isAudioError = (exoException?.type == TYPE_RENDERER) && (
                mimeType.startsWith("audio/") ||
                rendererName.contains("Audio") ||
                rendererException?.javaClass?.name?.contains("Audio") == true
        )

        // 3. Video error classification
        val isVideoError = (exoException?.type == TYPE_RENDERER) && (
                mimeType.startsWith("video/") ||
                rendererName.contains("Video") ||
                rendererException?.javaClass?.name?.contains("Video") == true ||
                rendererException?.message?.contains("video", ignoreCase = true) == true
        )

        val isCodecError = (exoException?.type == TYPE_RENDERER) ||
                (error.message?.contains("decoder", ignoreCase = true) == true) ||
                (errorDetailed?.contains("decoder", ignoreCase = true) == true) ||
                (errorDetailed?.contains("OMX.", ignoreCase = true) == true) ||
                (errorDetailed?.contains("c2.", ignoreCase = true) == true)

        if (isVideoError || isCodecError) {
            val reason = if (isVideoError) "vídeo" else if (isAudioError) "áudio" else "decodificação"
            AppLog.w(TAG, "Hardware decoder failed for $reason ($errorDetailed). Switching to MPV...")
            Toast.makeText(
                this,
                "Incompatibilidade no decodificador de $reason do dispositivo. Alternando para o MPV...",
                Toast.LENGTH_SHORT
            ).show()
            launchMpvFallback()
            return
        } else {
            showPlaybackFailureDialog(errorDetailed ?: errorGeneral)
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
                launchMpvFallback()
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

    override fun getWatchedPositionMs(): Long = if (::player.isInitialized) player.currentPosition else 0L
    override fun getTotalDurationMs(): Long = if (::player.isInitialized) player.duration else 0L
    override fun getCurrentFileId(): String = currentFileId
    override fun getCurrentTitle(): String = currentTitle
    override fun getCurrentThumbnail(): String? = currentThumbnailLink
    override fun getAudioDelayMs(): Long? = null
    override fun getSubtitleDelayMs(): Long? = currentSubtitleOffsetMs
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.apply {
            controllerAutoShow = false
            if (isInPictureInPictureMode) hideController() else showController()
        }
        if (onStopCalled) {
            saveProgress()
            finish()
        }
    }

    override fun onPause() {
        if (isOsdInitialized) {
            osdController.cancelInactivityTimer()
        }
        saveProgress()
        super.onPause()
    }

    override fun onStop() {
        player.pause()
        super.onStop()
        onStopCalled = true
    }

    override fun onResume() {
        super.onResume()
        onStopCalled = false
    }

    override fun onDestroy() {
        if (::gestureHelper.isInitialized) {
            gestureHelper.cancelAll()
        }
        countdownJob?.cancel()
        mediaSessionHelper?.release()
        mediaSessionHelper = null
        val surface = (playerView.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface
        releasePlayer()
        displayModeController.reset(window, surface)
        zechs.drive.stream.utils.HdrModeManager.resetHdrMode(this)
        bingeBufferManager.get().clear()
        super.onDestroy()
    }

}
