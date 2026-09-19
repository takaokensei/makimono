package zechs.drive.stream.ui.player

import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
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
import zechs.drive.stream.utils.MakimonoMediaSessionHelper
import zechs.drive.stream.utils.MakimonoPiPHelper
import zechs.drive.stream.utils.PlaybackProgressPolicy
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.ExoPlaybackException.*
import com.google.android.exoplayer2.Format.NO_VALUE
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory
import com.google.android.exoplayer2.extractor.ts.TsExtractor
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.text.CueGroup
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
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
import com.google.android.exoplayer2.util.MimeTypes
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
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.MatroskaChapterParser
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.util.Constants.Companion.DRIVE_API
import zechs.drive.stream.utils.util.Orientation
import zechs.drive.stream.utils.util.getNextOrientation
import zechs.drive.stream.utils.util.setOrientation
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.MotionEvent
import java.util.*
import javax.inject.Inject
import kotlin.math.roundToInt


@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

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
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnPrevEp: ImageButton
    private lateinit var btnNextEp: ImageButton
    private lateinit var btnAudio: View
    private lateinit var btnSubtitle: View
    private lateinit var btnEpisodes: View
    private lateinit var btnChapter: View
    private lateinit var btnResize: View
    private lateinit var btnInfo: View
    private lateinit var btnPip: MaterialButton
    private lateinit var btnSpeed: View
    private lateinit var btnRotate: View
    private lateinit var ivRotate: ImageView
    private lateinit var tvRotate: TextView
    private lateinit var btnLock: MaterialButton
    private lateinit var btnUnlock: MaterialButton
    private lateinit var btnSkipIntro: TextView
    private lateinit var btnSkipIntroBack: MaterialButton
    private lateinit var skipIntroRow: LinearLayout
    private lateinit var aniskipPill: LinearLayout
    private lateinit var tvAniSkipLabel: TextView
    private var prevEpisode: PlaylistItem? = null
    private var aniskipPillJob: Job? = null

    // MediaSession
    private var mediaSessionHelper: MakimonoMediaSessionHelper? = null

    // States
    private var onStopCalled = false
    private var controlsLocked = false
    private var hasAutoSelectedTracks = false
    private var parsedChapters: List<MatroskaChapterParser.ParsedChapter> = emptyList()
    private var activeSkipChapter: MatroskaChapterParser.ParsedChapter? = null
    private lateinit var gestureHelper: PlayerGestureHelper
    private var hasAutoSkippedCurrentInterval = false

    // Kodi features states
    private var isKodiHudVisible = false
    private var kodiHudUpdateJob: Job? = null
    private var forcedSubtitleUri: Uri? = null
    private var activeExternalSubItem: SubtitleItem? = null
    private var activeExternalSubFile: java.io.File? = null
    private var currentSubtitleOffsetMs = 0L

    // Playlist & Next Episode Auto-Play
    private var currentFileId: String = ""
    private var currentTitle: String = ""
    private var currentThumbnailLink: String? = null
    private var playlist = mutableListOf<PlaylistItem>()
    private var nextEpisode: PlaylistItem? = null
    private var nextEpisodeCanceled = false
    private val playbackCoordinator = PlaybackCoordinator()
    private var isNextEpisodeCardShowing = false
    private var countdownJob: Job? = null
    private var progressTrackerJob: Job? = null
    private var folderSubtitles = mutableListOf<SubtitleItem>()
    private val addedSubtitleFileIds = mutableSetOf<String>()

    // MAL Scrobble & Rating State
    private var currentMalAnime: MalAnimeNode? = null
    private var currentEpNumber: Int = 1
    private var hasScrobbledThisEp: Boolean = false
    private var hasPromptedRating: Boolean = false

    // Configs
    private var speed = arrayOf("0.25x", "0.5x", "Normal", "1.25x", "1.5x", "2x")
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

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        playerView = binding.playerView

        mainControlsRoot = playerView.findViewById(R.id.mainControls)
        controlsScrollView = playerView.findViewById(R.id.controlsScrollView)
        progressViewGroup = playerView.findViewById(R.id.linearLayout2)
        toolbar = playerView.findViewById(R.id.playerToolbar)
        titleBlock = playerView.findViewById(R.id.titleBlock)
        btnBack = playerView.findViewById(R.id.btnBack)
        tvPlayerTitle = playerView.findViewById(R.id.tvPlayerTitle)
        tvPlayerMeta = playerView.findViewById(R.id.tvPlayerMeta)
        btnPlayPause = playerView.findViewById(R.id.btnPlayPause)
        btnPrevEp = playerView.findViewById(R.id.btnPrevEp)
        btnNextEp = playerView.findViewById(R.id.btnNextEp)
        btnAudio = playerView.findViewById(R.id.btnAudio)
        btnSubtitle = playerView.findViewById(R.id.btnSubtitle)
        btnEpisodes = playerView.findViewById(R.id.btnEpisodes)
        btnChapter = playerView.findViewById(R.id.btnChapter)
        btnResize = playerView.findViewById(R.id.btnResize)
        btnInfo = playerView.findViewById(R.id.btnInfo)
        btnPip = playerView.findViewById(R.id.btnPip)
        btnSpeed = playerView.findViewById(R.id.btnSpeed)
        btnRotate = playerView.findViewById(R.id.btnRotate)
        ivRotate = playerView.findViewById(R.id.ivRotate)
        tvRotate = playerView.findViewById(R.id.tvRotate)
        btnLock = playerView.findViewById(R.id.btnLock)
        btnUnlock = playerView.findViewById(R.id.btnUnlock)
        btnSkipIntro = playerView.findViewById(R.id.btnSkipIntro)
        btnSkipIntroBack = playerView.findViewById(R.id.btnSkipIntroBack)
        skipIntroRow = playerView.findViewById(R.id.skipIntroRow)
        aniskipPill = playerView.findViewById(R.id.aniskipPill)
        tvAniSkipLabel = playerView.findViewById(R.id.tvAniSkipLabel)

        // Back button
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnPrevEp.setOnClickListener {
            playPrevEpisodeDirectly()
        }
        btnNextEp.setOnClickListener {
            playNextEpisodeDirectly()
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
                    duration = 250
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
                player.playbackParameters = PlaybackParameters(chosenSpeed)
                val idx = speed.indexOfFirst { it.startsWith(chosenSpeed.toString()) }.takeIf { it >= 0 } ?: 2
                speedSnackbar(idx)
            }.show()
        }

        val isTvDevice = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            || (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        btnRotate.visibility = if (isTvDevice) View.GONE else View.VISIBLE

        btnRotate.setOnClickListener {
            orientation = getNextOrientation(orientation)
            Log.d(TAG, "orientation=${orientation}")
            setOrientation(this@PlayerActivity, orientation)
        }

        gestureHelper = PlayerGestureHelper(
            activity = this,
            hudBinding = binding.gestureHud,
            callback = object : PlayerGestureCallback {
                override fun onToggleControls() {
                    if (playerView.isControllerVisible) {
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
                        player.playbackParameters = PlaybackParameters(speed)
                    }
                }

                override fun isControlsLocked(): Boolean = controlsLocked
                override fun isControllerVisible(): Boolean = playerView.isControllerVisible
                override fun getTouchIgnoredViews(): List<View> = listOf(
                    toolbar,
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
        currentTitle = intent.getStringExtra("title") ?: ""
        currentThumbnailLink = intent.getStringExtra("thumbnailLink")

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
        playbackCoordinator.setPlaylist(playlist, currentFileId)
        if (playlist.isEmpty()) {
            nextEpisode = null
            prevEpisode = null
            if (::btnPrevEp.isInitialized) {
                btnPrevEp.isEnabled = false
                btnPrevEp.alpha = 0.35f
                btnNextEp.isEnabled = false
                btnNextEp.alpha = 0.35f
            }
            mediaSessionHelper?.updatePlaybackState(
                isPlaying = if (::player.isInitialized) player.isPlaying else false,
                positionMs = if (::player.isInitialized) player.currentPosition else 0L,
                speed = if (::player.isInitialized) player.playbackParameters.speed else 1.0f,
                canSkipNext = false,
                canSkipPrevious = false
            )
            return
        }
        val coordinatorState = playbackCoordinator.state.value
        nextEpisode = coordinatorState.nextEpisode
        prevEpisode = coordinatorState.prevEpisode
        if (::btnPrevEp.isInitialized) {
            btnPrevEp.isEnabled = prevEpisode != null
            btnPrevEp.alpha = if (prevEpisode != null) 1.0f else 0.35f
            btnNextEp.isEnabled = nextEpisode != null
            btnNextEp.alpha = if (nextEpisode != null) 1.0f else 0.35f
        }
        mediaSessionHelper?.updatePlaybackState(
            isPlaying = if (::player.isInitialized) player.isPlaying else false,
            positionMs = if (::player.isInitialized) player.currentPosition else 0L,
            speed = if (::player.isInitialized) player.playbackParameters.speed else 1.0f,
            canSkipNext = nextEpisode != null,
            canSkipPrevious = prevEpisode != null
        )
        val currentIndex = playlist.indexOfFirst { it.fileId == currentFileId }
        Log.d(TAG, "updateNextEpisode: currentIndex=$currentIndex, prevEpisode=${prevEpisode?.title}, nextEpisode=${nextEpisode?.title}")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
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

                val epPrefix = if (currentEpNumber > 0) "Episódio ${currentEpNumber.toString().padStart(2, '0')}" else ""
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
                    duration = 150L
                }
            )
            btnPlayPause.icon = ContextCompat.getDrawable(
                /* context */ applicationContext,
                /* drawableId */ if (isPlaying) R.drawable.ic_pause_24
                else R.drawable.ic_play_24
            )
            Log.d(TAG, "isPlaying=${isPlaying}")

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
            Log.e(TAG, "ExoPlayer onPlayerError: errorCodeName=${error.errorCodeName}, errorCode=${error.errorCode}", error)
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
        val normalizedCues = cues.map { cue ->
            val builder = cue.buildUpon()

            // 1. HEIGHT NORMALIZATION:
            // Top signs / notes are explicitly in the top third (line < 0.35f with START anchor).
            // All other dialogue is unified to a stable, crisp bottom position: 0.93f (7% clearance from bottom).
            // This eliminates random vertical jumping between ASS styles, prevents text from being
            // buried at the bottom bezel, and stops text from floating awkwardly in the middle.
            val isTopSign = cue.lineType == Cue.LINE_TYPE_FRACTION &&
                    cue.line in 0.0f..0.35f &&
                    cue.lineAnchor == Cue.ANCHOR_TYPE_START

            if (!isTopSign) {
                builder.setLine(0.95f, Cue.LINE_TYPE_FRACTION)
                builder.setLineAnchor(Cue.ANCHOR_TYPE_END)
            }

            // 2. TYPOGRAPHY ENHANCEMENT:
            // Ensure bold styling for tracks that lack bold styling spans
            val text = cue.text
            if (text != null && text.isNotEmpty()) {
                val spannable = SpannableStringBuilder.valueOf(text)
                val hasBold = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
                    .any { it.style == Typeface.BOLD }
                if (!hasBold) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        spannable.length,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    builder.setText(spannable)
                }
            }

            builder.build()
        }
        playerView.subtitleView?.setCues(normalizedCues)
    }

    private fun initPlayer() {
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

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

        playerView.setControllerVisibilityListener { visibility ->
            val density = resources.displayMetrics.density
            val isVisible = visibility == View.VISIBLE

            val targetCardY = if (isVisible) -84f * density else 0f
            if (binding.nextEpisodeCard.root.isVisible) {
                binding.nextEpisodeCard.root.animate().translationY(targetCardY).setDuration(220L).start()
            }

            if (isVisible) {
                handleLockingControls()
                btnPlayPause.post {
                    btnPlayPause.requestFocus()
                }
                if (::player.isInitialized) {
                    updateIntroButtonVisibility(player.currentPosition)
                }
            } else {
                if (::player.isInitialized) {
                    updateIntroButtonVisibility(player.currentPosition)
                }
            }
        }

        // Configure subtitle appearance (PotPlayer style: crisp white text, strong black outline, no background box, bold)
        playerView.subtitleView?.apply {
            val captionStyle = CaptionStyleCompat(
                /* foregroundColor  */ Color.WHITE,
                /* backgroundColor */ Color.TRANSPARENT,
                /* windowColor     */ Color.TRANSPARENT,
                /* edgeType        */ CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                /* edgeColor       */ Color.BLACK,
                /* typeface        */ Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            )
            setStyle(captionStyle)
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setBottomPaddingFraction(0.035f)
        }

        lifecycleScope.launch {
            try {
                val savedSize = appSettings.get().fetchSubtitleSize()
                if (savedSize > 0f) {
                    playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, savedSize)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading subtitle size", e)
            }
        }

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
                    if (!playerView.isControllerVisible) {
                        playerView.showController()
                        btnPlayPause.post { btnPlayPause.requestFocus() }
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!playerView.isControllerVisible) {
                        seekRelative(-10_000L)
                        gestureHelper.showNotification("⏪ -10s")
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!playerView.isControllerVisible) {
                        seekRelative(10_000L)
                        gestureHelper.showNotification("⏩ +10s")
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!playerView.isControllerVisible) {
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

                KeyEvent.KEYCODE_BACK -> {
                    if (isKodiHudVisible) {
                        toggleKodiInfoHud()
                        return true
                    }
                    if (binding.nextEpisodeCard.root.isVisible) {
                        dismissNextEpisodeCard()
                        return true
                    }
                    if (playerView.isControllerVisible) {
                        animateHideController()
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
                    seekRelative(-SKIP_INTRO_MS)
                    gestureHelper.showNotification("⏪ -90s Voltar")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (isKodiHudVisible) {
            toggleKodiInfoHud()
            return
        }
        if (binding.nextEpisodeCard.root.isVisible) {
            dismissNextEpisodeCard()
            return
        }
        if (playerView.isControllerVisible) {
            animateHideController()
            return
        }
        if (binding.netflixSkipRow.isVisible) {
            binding.netflixSkipRow.visibility = View.GONE
            return
        }
        super.onBackPressed()
    }

    private var isHidingControls = false

    private fun animateHideController(onComplete: (() -> Unit)? = null) {
        if (!::playerView.isInitialized || !playerView.isControllerVisible || isHidingControls) return
        isHidingControls = true
        val density = resources.displayMetrics.density
        val animDuration = 220L
        val topScrim = playerView.findViewById<View>(R.id.topScrim)

        titleBlock.animate().translationY(-30f * density).alpha(0f).setDuration(animDuration).start()
        controlsScrollView.animate().translationY(50f * density).alpha(0f).setDuration(animDuration).start()
        mainControlsRoot.animate().alpha(0f).setDuration(animDuration).start()
        skipIntroRow.animate().alpha(0f).setDuration(animDuration).start()

        (topScrim ?: controlsScrollView).animate().alpha(0f).setDuration(animDuration).withEndAction {
            isHidingControls = false
            playerView.hideController()
            titleBlock.translationY = 0f
            titleBlock.alpha = 1f
            controlsScrollView.translationY = 0f
            controlsScrollView.alpha = 1f
            mainControlsRoot.alpha = 1f
            skipIntroRow.alpha = 1f
            topScrim?.alpha = 1f
            onComplete?.invoke()
        }.start()
    }

    private fun animateShowController() {
        if (!::playerView.isInitialized || playerView.isControllerVisible) return
        val density = resources.displayMetrics.density
        val animDuration = 220L
        val topScrim = playerView.findViewById<View>(R.id.topScrim)

        titleBlock.translationY = -25f * density
        titleBlock.alpha = 0f
        controlsScrollView.translationY = 40f * density
        controlsScrollView.alpha = 0f
        mainControlsRoot.alpha = 0f
        topScrim?.alpha = 0f

        playerView.showController()

        titleBlock.animate().translationY(0f).alpha(1f).setDuration(animDuration).start()
        controlsScrollView.animate().translationY(0f).alpha(1f).setDuration(animDuration).start()
        mainControlsRoot.animate().alpha(1f).setDuration(animDuration).start()
        topScrim?.animate()?.alpha(1f)?.setDuration(animDuration)?.start()
    }

    private fun handleLockingControls() {
        TransitionManager.beginDelayedTransition(
            playerView, AutoTransition().apply {
                duration = 150L
            }
        )
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

        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") }
        val title = currentTitle.ifBlank { intent.getStringExtra("title") }

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

        toolbar.title = title
        tvPlayerTitle.text = parsed.showTitle.ifBlank { parsed.cleanTitle.ifBlank { title ?: "" } }
        val epText = if (currentEpNumber > 0) "Episódio ${currentEpNumber.toString().padStart(2, '0')}" else ""
        tvPlayerMeta.text = epText

        mediaSessionHelper?.updateMetadata(
            title = parsed.cleanTitle.ifBlank { title ?: "" },
            seriesName = parsed.showTitle.ifBlank { null },
            durationMs = if (::player.isInitialized) player.duration.coerceAtLeast(0L) else 0L
        )

        playerView.apply {
            player = this@PlayerActivity.player
            // Always hide controls on touch so the UI doesn't get stuck visible
            controllerHideOnTouch = true
            // Auto-show controls when playback starts/resumes so the user sees them immediately
            controllerAutoShow = true
            // Auto-hide controls after 4.5 seconds of inactivity so UI disappears naturally
            controllerShowTimeoutMs = 4500
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        if (fileId != null) {
            viewModel.getWatch(fileId)
            val streamUri = getStreamUrl(fileId)

            val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
            val matching = folderSubtitles.filter { it.matchesVideo(title ?: "") }
            matching.forEach { sub ->
                val safeName = sub.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val cachedFile = java.io.File(cacheDir, "subtitles/${sub.id}_$safeName")
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    val isPortuguese = sub.languageCode == "por" || sub.name.lowercase().contains(".por.") || sub.name.lowercase().contains("pt")
                    val (subUri, mimeType) = zechs.drive.stream.utils.SubtitleConverter.prepareSubtitleForExoPlayer(cacheDir, sub, cachedFile)
                    subConfigs.add(
                        MediaItem.SubtitleConfiguration.Builder(subUri)
                            .setMimeType(mimeType)
                            .setLanguage(sub.languageCode)
                            .setLabel("★ [Drive] ${sub.languageLabel}")
                            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                            .setSelectionFlags(if (isPortuguese) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    )
                    addedSubtitleFileIds.add(sub.id)
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(streamUri)
                .apply {
                    if (subConfigs.isNotEmpty()) {
                        setSubtitleConfigurations(subConfigs)
                    }
                }
                .build()

            lifecycleScope.launch {
                try {
                    val token = tokenProvider.get().validToken()
                    val chapters = MatroskaChapterParser.extractChapters(streamUri.toString(), token)
                    parsedChapters = chapters
                    Log.d(TAG, "Extracted ${chapters.size} chapters from MKV")
                    if (::player.isInitialized) {
                        updateIntroButtonVisibility(player.currentPosition)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Chapter extraction failed", e)
                }
            }

            val passedStartMs = intent.getLongExtra("startPosition", -1L)
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
                    if (startPosition > 5_000L) {
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

        val (rawUri, mimeType) = zechs.drive.stream.utils.SubtitleConverter.prepareSubtitleForExoPlayer(cacheDir, sub, cachedFile)
        val finalUri = if (currentSubtitleOffsetMs != 0L && rawUri.scheme == "file") {
            val sourceFile = java.io.File(rawUri.path ?: "")
            val shiftedFile = java.io.File(cacheDir, "subtitles/${sub.id}_shifted_${currentSubtitleOffsetMs}.srt")
            if (zechs.drive.stream.utils.SubtitleConverter.shiftSrtTimestamps(sourceFile, shiftedFile, currentSubtitleOffsetMs)) {
                Uri.fromFile(shiftedFile)
            } else rawUri
        } else rawUri

        val isPortuguese = sub.languageCode == "por" || sub.name.lowercase().contains(".por.") || sub.name.lowercase().contains("pt")
        val trackLabel = if (isMatching) "★ [Drive] ${sub.languageLabel}" else "📁 [Drive] ${sub.name}"

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
                    checkMalScrobble(pos, duration)
                    checkMalCompletionPrompt(pos, duration)
                }
                delay(1000L)
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
        skipIntroRow.animate().cancel()
        skipIntroRow.visibility = View.GONE

        val card = binding.nextEpisodeCard
        val density = resources.displayMetrics.density
        card.root.translationY = if (playerView.isControllerVisible) -84f * density else 0f

        val parsed = EpisodeParser.parse(next.title)
        card.tvNextEpisodeTitle.text = parsed.cleanTitle

        MediaImageLoader.card(card.ivNextEpisodeThumb, ThumbnailUrl.medium(next.thumbnailLink))

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
                if (::player.isInitialized) {
                    val remainingMs = player.duration - player.currentPosition
                    val sec = kotlin.math.ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
                    card.tvNextEpisodeCountdown.text = "A SEGUIR • ${sec}s"
                    if (sec <= 0 || remainingMs <= 1_000L) {
                        playNextEpisodeDirectly()
                        break
                    }
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
            playbackCoordinator.cancelAutoplay()
        }
        isNextEpisodeCardShowing = false
        val card = binding.nextEpisodeCard.root
        card.animate().alpha(0f).setDuration(200L).withEndAction {
            card.visibility = View.GONE
            card.translationY = 0f
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
        Snackbar.make(playerView, "Iniciando: ${parsed.cleanTitle}", 1500).apply {
            anchorView = progressViewGroup
        }.show()
    }

    private fun showAniSkipPill(label: String = "Abertura pulada (AniSkip)") {
        if (!::aniskipPill.isInitialized) return
        tvAniSkipLabel.text = label
        aniskipPill.animate().cancel()
        aniskipPill.alpha = 1f
        aniskipPill.visibility = View.VISIBLE
        aniskipPillJob?.cancel()
        aniskipPillJob = lifecycleScope.launch {
            delay(3000L)
            aniskipPill.animate().alpha(0f).setDuration(300L).withEndAction {
                aniskipPill.visibility = View.GONE
            }.start()
        }
    }

    private fun mergeAniSkipChapters(aniSkipChapters: List<MatroskaChapterParser.ParsedChapter>) {
        if (aniSkipChapters.isEmpty()) return
        val existing = parsedChapters.toMutableList()
        val hasMkvOp = existing.any { it.type == MatroskaChapterParser.ChapterType.OPENING }
        val hasMkvEd = existing.any { it.type == MatroskaChapterParser.ChapterType.ENDING }

        for (aniCh in aniSkipChapters) {
            if (aniCh.type == MatroskaChapterParser.ChapterType.OPENING && !hasMkvOp) {
                existing.add(aniCh)
            } else if (aniCh.type == MatroskaChapterParser.ChapterType.ENDING && !hasMkvEd) {
                existing.add(aniCh)
            } else if (aniCh.type == MatroskaChapterParser.ChapterType.RECAP && !existing.any { it.type == MatroskaChapterParser.ChapterType.RECAP }) {
                existing.add(aniCh)
            }
        }
        parsedChapters = existing.sortedBy { it.startTimeMs }
        Log.d(TAG, "Updated parsedChapters with AniSkip: ${parsedChapters.size} total")
        if (::player.isInitialized) {
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

        // 1. Check if current position is within an OPENING or ENDING chapter
        val specialChapter = if (parsedChapters.isNotEmpty()) {
            parsedChapters.firstOrNull { chapter ->
                chapter.type != MatroskaChapterParser.ChapterType.OTHER &&
                        positionMs >= chapter.startTimeMs &&
                        positionMs < chapter.endTimeMs
            }
        } else null

        activeSkipChapter = specialChapter

        if (specialChapter != null) {
            val isOpOrRecap = specialChapter.type == MatroskaChapterParser.ChapterType.OPENING ||
                    specialChapter.type == MatroskaChapterParser.ChapterType.RECAP
            val isAutoSkip = malSessionManager.get().isAutoSkipEnabled()
            if (isAutoSkip && isOpOrRecap && !hasAutoSkippedCurrentInterval) {
                if (positionMs in specialChapter.startTimeMs..(specialChapter.startTimeMs + 3_500L)) {
                    hasAutoSkippedCurrentInterval = true
                    player.seekTo(specialChapter.endTimeMs)
                    gestureHelper.showNotification("⏩ Abertura pulada automaticamente (AniSkip)")
                    showAniSkipPill("Abertura pulada (AniSkip)")
                    return
                }
            }

            val label = when (specialChapter.type) {
                MatroskaChapterParser.ChapterType.RECAP -> "Pular Recap"
                MatroskaChapterParser.ChapterType.OPENING -> "Pular Abertura"
                MatroskaChapterParser.ChapterType.ENDING -> "Pular Créditos"
                else -> "Pular"
            }
            btnSkipIntro.text = label
            binding.btnNetflixSkip.text = label

            if (!playerView.isControllerVisible) {
                // Watching video without HUD: show floating Netflix-style pill
                binding.netflixSkipRow.animate().cancel()
                binding.netflixSkipRow.alpha = 1f
                binding.netflixSkipRow.visibility = View.VISIBLE
                skipIntroRow.visibility = View.GONE
            } else {
                // Controls HUD is visible: show button above timeline
                binding.netflixSkipRow.animate().cancel()
                binding.netflixSkipRow.visibility = View.GONE
                skipIntroRow.animate().cancel()
                skipIntroRow.alpha = 1f
                skipIntroRow.visibility = View.VISIBLE
            }
            return
        }

        // 2. Outside special chapters: floating pill MUST ALWAYS BE GONE
        hasAutoSkippedCurrentInterval = false
        binding.netflixSkipRow.animate().cancel()
        binding.netflixSkipRow.visibility = View.GONE

        // 3. Fallback for files WITHOUT chapters: only show skip button in the HUD controls when controller is open
        val hasRecognizedChapters = parsedChapters.any { it.type != MatroskaChapterParser.ChapterType.OTHER }
        if (!hasRecognizedChapters && playerView.isControllerVisible) {
            val isFirst140s = positionMs in 1_000L..140_000L
            if (isFirst140s) {
                btnSkipIntro.text = "Pular Abertura (+90s)"
                skipIntroRow.animate().cancel()
                skipIntroRow.alpha = 1f
                skipIntroRow.visibility = View.VISIBLE
                return
            }
        }

        skipIntroRow.animate().cancel()
        skipIntroRow.visibility = View.GONE
    }

    private fun performSkipIntroOrCredits() {
        val chapter = activeSkipChapter
        if (chapter != null && chapter.endTimeMs > player.currentPosition) {
            seekTo(chapter.endTimeMs)
            val label = when (chapter.type) {
                MatroskaChapterParser.ChapterType.RECAP -> "⏩ Recap pulado"
                MatroskaChapterParser.ChapterType.OPENING -> "⏩ Abertura pulada"
                MatroskaChapterParser.ChapterType.ENDING -> "⏩ Créditos pulados"
                else -> "⏩ Capítulo pulado"
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
                Log.d(TAG, "Seeking immediately to resume position: $startPosition ms")
                Snackbar.make(
                    playerView,
                    "Retomando de onde você parou",
                    Snackbar.LENGTH_SHORT
                ).apply {
                    anchorView = progressViewGroup
                }.show()
            } catch (e: Exception) {
                Log.w(TAG, "Error seeking to resume position", e)
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
        Log.d(TAG, "seekRelative(${deltaMs}ms) -> ${clamped}ms")
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
        Log.d(TAG, "seekTo(${positionMs}ms) -> ${clamped}ms")
        player.seekTo(clamped)
    }

    private fun getStreamUrl(fileId: String): Uri {
        val uri = Uri.parse(
            "$DRIVE_API/files/${fileId}?supportsAllDrives=True&alt=media"
        )
        Log.d(TAG, "STREAM_URL=$uri")
        return uri
    }

    private fun updateOrientation(newConfig: Configuration) {
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                orientation = Orientation.PORTRAIT
                if (::tvRotate.isInitialized) {
                    tvRotate.text = "Paisagem"
                }
                if (::btnRotate.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    btnRotate.tooltipText = getString(R.string.landscape)
                }
                if (::ivRotate.isInitialized) {
                    ivRotate.setImageDrawable(
                        ContextCompat.getDrawable(this@PlayerActivity, R.drawable.ic_landscape_24)
                    )
                }
            }
            else -> {
                orientation = Orientation.LANDSCAPE
                if (::tvRotate.isInitialized) {
                    tvRotate.text = "Girar"
                }
                if (::btnRotate.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    btnRotate.tooltipText = getString(R.string.portrait)
                }
                if (::ivRotate.isInitialized) {
                    ivRotate.setImageDrawable(
                        ContextCompat.getDrawable(this@PlayerActivity, R.drawable.ic_portrait_24)
                    )
                }
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
        }.show()
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

        // 4. ALL Google Drive folder subtitles: ALWAYS list them!
        folderSubtitles.forEach { sub ->
            val alreadyPresent = choices.any {
                it.displayName.contains(sub.name, ignoreCase = true) ||
                (it.displayName.contains("[Drive]", ignoreCase = true) && it.displayName.contains(sub.languageLabel, ignoreCase = true))
            }
            if (!alreadyPresent) {
                val isMatching = sub.matchesVideo(videoTitle)
                val prefix = if (isMatching) "★ [Drive] " else "📁 [Drive] "
                val tag = if (isMatching) " (Episódio atual)" else " (Outro episódio)"
                val label = "$prefix${sub.name}$tag"
                choices.add(ExoSubChoice(null, -1, sub, null, null, label, "Google Drive", false))
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

        glassDialog.setActionButton("🔍 Buscar Legendas Online (PT-BR)") { d ->
            d.showLoading(true)
            lifecycleScope.launch(Dispatchers.IO) {
                val onlineResults = try {
                    onlineSubtitleManager.get().searchSubtitles(videoTitle, currentEpNumber.takeIf { it > 0 })
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching online subtitles", e)
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    d.showLoading(false)
                    if (onlineResults.isEmpty()) {
                        Snackbar.make(playerView, "Nenhuma legenda online encontrada", 2000).apply {
                            anchorView = progressViewGroup
                        }.show()
                        return@withContext
                    }

                    val updatedChoices = choices.toMutableList()
                    onlineResults.forEach { os ->
                        val flag = if (os.isPortuguese) "🇧🇷 " else "🌐 "
                        val name = "$flag${os.langName}"
                        val subText = "Online • ${os.source}"
                        updatedChoices.add(ExoSubChoice(null, -2, null, os, null, name, subText, false))
                    }
                    d.updateItems(buildMenuItems(updatedChoices))
                    Snackbar.make(playerView, "${onlineResults.size} legendas encontradas online!", 1500).apply {
                        anchorView = progressViewGroup
                    }.show()
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
                        Log.w(TAG, "Error saving subtitle size", e)
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

        // 1. Video Codec & Decoder
        val mime = vFormat?.sampleMimeType ?: "Desconhecido"
        val codecPretty = when {
            mime.contains("hevc", true) -> "HEVC (H.265)"
            mime.contains("avc", true) -> "AVC (H.264)"
            mime.contains("vp9", true) -> "VP9"
            mime.contains("av01", true) -> "AV1"
            else -> mime.substringAfterLast("/")
        }
        binding.kodiInfoHud.tvHudVideoCodec.text = "$codecPretty • HW MediaCodec"

        // 2. Video Resolution & Framerate
        val width = vFormat?.width ?: 0
        val height = vFormat?.height ?: 0
        val fps = vFormat?.frameRate ?: 0f
        val fpsText = if (fps > 0) String.format(Locale.US, "%.3f fps", fps) else "Taxa Dinâmica"
        val resText = if (width > 0 && height > 0) "${width}x${height} @ $fpsText" else "1080p @ $fpsText"
        binding.kodiInfoHud.tvHudVideoResolution.text = resText

        // 3. Audio Info
        val audioMime = aFormat?.sampleMimeType ?: "Áudio Padrão"
        val audioCodec = when {
            audioMime.contains("mp4a", true) || audioMime.contains("aac", true) -> "AAC"
            audioMime.contains("opus", true) -> "Opus"
            audioMime.contains("flac", true) -> "FLAC"
            audioMime.contains("ac3", true) -> "Dolby Digital (AC-3)"
            audioMime.contains("eac3", true) -> "Dolby Digital Plus (E-AC-3)"
            else -> audioMime.substringAfterLast("/")
        }
        val channels = when (aFormat?.channelCount) {
            1 -> "Mono (1.0)"
            2 -> "Estéreo (2.0)"
            6 -> "Surround (5.1)"
            8 -> "Surround (7.1)"
            else -> "Estéreo"
        }
        val sampleRateHz = aFormat?.sampleRate ?: 0
        val sampleRate = if (sampleRateHz > 0) "${sampleRateHz / 1000f} kHz" else "48 kHz"
        binding.kodiInfoHud.tvHudAudioInfo.text = "$audioCodec • $channels ($sampleRate)"

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
        binding.kodiInfoHud.tvHudBufferDuration.text = String.format(Locale.US, "%.1fs em buffer (RAM)", bufferSecs)

        if (bufferSecs >= 12.0) {
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

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog).apply {
            setTitle(getString(R.string.chapters))
            setMessage("Nenhum capítulo embutido foi detectado neste arquivo de vídeo.\n\nDeseja alternar para o MPV Player?")
            setPositiveButton("Abrir no MPV") { dialog, _ ->
                dialog.dismiss()
                launchMpvFallback()
            }
            setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            show()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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
                    Log.d(TAG, "Exo auto-selected Japanese audio: lang=$lang, label=$label, index=$i")
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

                val isPortuguese = lang in listOf("pt", "por", "pt-br", "pt_br", "pob", "portuguese") ||
                        label.contains("portugu") || label.contains("pt-br") || label.contains("pt_br") ||
                        label.contains("brazil") || label.contains("ptbr")

                if (isPortuguese) {
                    var score = 10
                    if (label.contains("[drive]")) {
                        score = 25
                    } else if (label.contains("forced") || label.contains("forçada") || label.contains("forçado") ||
                        label.contains("signs") || label.contains("músicas") || label.contains("songs")) {
                        score = 5
                    } else if (label.contains("brasil") || label.contains("brazil") || label.contains("pt-br") || label.contains("pt_br")) {
                        score = 15
                    }

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
            Log.d(TAG, "Exo auto-selected Portuguese subtitle: lang=${format.language}, label=${format.label}, index=$bestSubIndex")
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
        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") ?: return }
        val title = currentTitle.ifBlank { intent.getStringExtra("title") ?: return }
        val thumbnailLink = currentThumbnailLink ?: intent.getStringExtra("thumbnailLink")
        val theme = intent.getIntExtra("theme", 0)
        val currentPos = if (::player.isInitialized) player.currentPosition else 0L

        lifecycleScope.launch {
            try {
                Toast.makeText(this@PlayerActivity, getString(R.string.switching_to_mpv), Toast.LENGTH_SHORT).show()
                val token = tokenProvider.get().validToken()
                if (!token.isNullOrEmpty()) {
                    val mpvIntent = Intent(this@PlayerActivity, MPVActivity::class.java).apply {
                        putExtra("fileId", fileId)
                        putExtra("title", title)
                        putExtra("thumbnailLink", thumbnailLink)
                        putExtra("accessToken", token)
                        putExtra("theme", theme)
                        putExtra("playlist", ArrayList(playlist))
                        putExtra("subtitles", ArrayList(folderSubtitles))
                        if (currentPos > 0) {
                            putExtra("startPosition", currentPos)
                        }
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    finish()
                    startActivity(mpvIntent)
                    return@launch
                }
                Toast.makeText(this@PlayerActivity, "Falha ao obter token para abrir no MPV", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error launching MPV fallback", e)
            }
        }
    }

    private fun showError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer onPlayerError: errorCodeName=${error.errorCodeName}, errorCode=${error.errorCode}", error)

        val errorGeneral = error.localizedMessage ?: getString(R.string.something_went_wrong)
        val exoException = error as? ExoPlaybackException
        val rendererException = exoException?.rendererException
        val rendererFormat = exoException?.rendererFormat
        val mimeType = rendererFormat?.sampleMimeType ?: ""
        val rendererName = exoException?.rendererName ?: ""

        val errorDetailed = when (exoException?.type) {
            TYPE_SOURCE -> exoException.sourceException.localizedMessage
            TYPE_RENDERER -> rendererException?.localizedMessage
            TYPE_UNEXPECTED -> exoException.unexpectedException.localizedMessage
            TYPE_REMOTE -> errorGeneral
            else -> error.message
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
            Log.w(TAG, "Subtitle rendering failed ($mimeType: ${rendererException?.message}). Disabling subtitle and resuming playback.")
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
            Log.w(TAG, "Hardware decoder failed for $reason ($errorDetailed). Switching to MPV...")
            Toast.makeText(
                this,
                "Incompatibilidade no decodificador de $reason do dispositivo. Alternando para o MPV...",
                Toast.LENGTH_SHORT
            ).show()
            launchMpvFallback()
            return
        } else {
            errorSnackbar(errorGeneral, errorDetailed, canOpenMpv = false)
        }
    }

    private fun errorSnackbar(textPrimary: String, textSecondary: String?, canOpenMpv: Boolean = false) {
        Snackbar.make(playerView, textPrimary, Snackbar.LENGTH_LONG).apply {
            anchorView = progressViewGroup

            if (canOpenMpv) {
                setAction("Abrir no MPV") {
                    launchMpvFallback()
                }
            } else if (textSecondary != null) {
                setAction(getString(R.string.details)) {
                    MaterialAlertDialogBuilder(
                        this@PlayerActivity
                    ).apply {
                        setMessage(textSecondary)
                        setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                        create()
                    }.also { it.show() }
                }
            }

        }.also { it.show() }
    }

    private fun saveProgress() {
        val watchedDuration = player.currentPosition
        val totalDuration = player.duration
        if (PlaybackProgressPolicy.shouldSave(watchedDuration, totalDuration)) {
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
                watchedDuration = watchedDuration,
                totalDuration = totalDuration,
                thumbnailLink = thumbnailLink
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.apply {
            controllerAutoShow = !isInPictureInPictureMode
            if (isInPictureInPictureMode) hideController() else showController()
        }
        if (onStopCalled) {
            saveProgress()
            finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation(newConfig)
    }

    override fun onPause() {
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
        countdownJob?.cancel()
        mediaSessionHelper?.release()
        mediaSessionHelper = null
        releasePlayer()
        super.onDestroy()
    }

}
