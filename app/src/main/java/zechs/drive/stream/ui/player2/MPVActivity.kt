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
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.databinding.ActivityMpvBinding
import zechs.drive.stream.databinding.PlayerControlViewBinding
import zechs.drive.stream.ui.player.PlayerViewModel
import zechs.drive.stream.utils.EpisodeParser
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

    // States
    private var activityIsForeground = true
    private var userIsOperatingSeekbar = false
    private var controlsLocked = false
    private var hasAutoSelectedMpvTracks = false
    private var parsedChapters = listOf<ParsedChapter>()
    private var activeSkipChapter: ParsedChapter? = null

    // Playlist & Next Episode Auto-Play
    private var currentFileId: String = ""
    private var currentTitle: String = ""
    private var currentThumbnailLink: String? = null
    private var currentAccessToken: String = ""
    private var playlist = mutableListOf<PlaylistItem>()
    private var nextEpisode: PlaylistItem? = null
    private var nextEpisodeCanceled = false
    private var isNextEpisodeCardShowing = false
    private var countdownJob: Job? = null
    private var folderSubtitles = mutableListOf<SubtitleItem>()
    private val addedSubtitleFileIds = mutableSetOf<String>()
    private var isFileLoaded = false
    private val pendingSubtitles = mutableListOf<Pair<java.io.File, SubtitleItem>>()

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
            .setNavigationOnClickListener { finish() }

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
            exoFfwd.setOnClickListener { skipForward() }
            exoRew.setOnClickListener { rewindBackward() }
            btnSkipIntro.setOnClickListener { performSkipIntroOrCredits() }
            btnSkipIntroBack.setOnClickListener { skipRelative(-90) }
            btnAudio.setOnClickListener { pickAudio() }
            btnSubtitle.setOnClickListener { pickSub() }
            btnChapter.setOnClickListener { pickChapter() }
            btnSpeed.setOnClickListener { pickSpeed() }
            btnResize.setOnClickListener { player.cycleScale() }

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

        binding.btnNetflixSkip.setOnClickListener {
            performSkipIntroOrCredits()
        }

        // hide/show controller
        player.setOnClickListener {
            if (!controller.root.isVisible) {
                showControlsWithFocus()
            } else {
                hideControls()
            }
        }

        updateOrientation(resources.configuration)

    }

    private fun updateNextEpisode() {
        if (playlist.isEmpty()) {
            nextEpisode = null
            return
        }
        val currentIndex = playlist.indexOfFirst { it.fileId == currentFileId }
        nextEpisode = if (currentIndex != -1 && currentIndex + 1 < playlist.size) {
            playlist[currentIndex + 1]
        } else null
        Log.d(TAG, "updateNextEpisode: currentIndex=$currentIndex, nextEpisode=${nextEpisode?.title}")
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

    private fun showControlsWithFocus() {
        val root = controller.root
        TransitionManager.beginDelayedTransition(root, Fade().apply { duration = 200L })
        root.isVisible = true
        val density = resources.displayMetrics.density
        if (binding.nextEpisodeCard.root.isVisible) {
            binding.nextEpisodeCard.root.animate().translationY(-84f * density).setDuration(220L).start()
        }
        updateSubtitlePosition(true)
        handleLockingControls()
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
        controller.btnPlayPause.post {
            controller.btnPlayPause.requestFocus()
        }
    }

    private fun hideControls() {
        val root = controller.root
        TransitionManager.beginDelayedTransition(root, Fade().apply { duration = 200L })
        root.isVisible = false
        if (binding.nextEpisodeCard.root.isVisible) {
            binding.nextEpisodeCard.root.animate().translationY(0f).setDuration(220L).start()
        }
        updateSubtitlePosition(false)
        val currentPos = (player.timePos ?: 0).toDouble()
        checkChapterAutoSkip(currentPos)
    }

    private fun updateSubtitlePosition(controlsVisible: Boolean) {
        val subPos = if (controlsVisible) 78 else 93
        MPVLib.setPropertyInt("sub-pos", subPos)
    }

    private fun checkChapterAutoSkip(currentTimeSeconds: Double) {
        val specialChapter = parsedChapters.firstOrNull { chapter ->
            chapter.type != ChapterType.OTHER &&
                    currentTimeSeconds >= chapter.startTimeSeconds &&
                    currentTimeSeconds < chapter.endTimeSeconds
        }

        activeSkipChapter = specialChapter

        if (specialChapter != null) {
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
        val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId") }
        val title = currentTitle.ifBlank { intent.getStringExtra("title") }
        val accessToken = currentAccessToken.ifBlank { intent.getStringExtra("accessToken") }

        if (fileId == null || accessToken == null) {
            Log.d(TAG, "FileId & AccessToken both are required. exiting...")
            finish()
            return
        }

        Log.d(TAG, "MPVActivity(fileId=$fileId, title=$title, accessToken=$accessToken)")

        viewModel.getWatch(fileId)

        controller.playerToolbar.title = title

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
        if (explicitStart > 0) {
            resumeVideo(explicitStart)
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
        val trackTitle = "★ [Drive] ${sub.languageLabel}"
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
        selectTrack(
            title = getString(R.string.select_audio),
            type = "audio",
            get = { player.aid },
            set = { player.aid = it }
        )
    }

    private fun pickSub() {
        player.loadTracks()
        val tracks = player.tracks.getValue("sub")
        val selectedMpvId = player.sid

        data class SubChoice(
            val mpvTrackId: Int?,
            val folderSub: SubtitleItem?,
            val label: String,
            val isSelected: Boolean
        )

        val choices = mutableListOf<SubChoice>()

        // 1. Off option (mpvId == -1)
        val isOff = selectedMpvId == -1 || tracks.none { it.mpvId == selectedMpvId }
        choices.add(SubChoice(-1, null, getString(R.string.track_off), isOff))

        // 2. Embedded video subtitle tracks
        tracks.filter { it.mpvId > 0 }.forEach { t ->
            choices.add(SubChoice(t.mpvId, null, t.name, t.mpvId == selectedMpvId))
        }

        // 3. ALL Google Drive folder subtitles: ALWAYS list them!
        val videoTitle = currentTitle.ifBlank { intent.getStringExtra("title") ?: "" }
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
                choices.add(SubChoice(null, sub, label, false))
            }
        }

        val displayItems = choices.map { it.label }.toTypedArray()
        val selectedIndex = choices.indexOfFirst { it.isSelected }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog).apply {
            setTitle(getString(R.string.select_subtitle))
            setSingleChoiceItems(
                displayItems,
                selectedIndex
            ) { dialog, item ->
                dialog.dismiss()
                val choice = choices[item]
                if (choice.mpvTrackId != null) {
                    player.sid = choice.mpvTrackId
                    trackSwitchNotification { TrackData(choice.mpvTrackId, "sub") }
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
                }
            }
        }.show()
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

        val chapterArray = chapters.map {
            val timeCode = Utils.prettyTime(it.time.roundToInt())
            val title = if (!it.title.isNullOrEmpty()) it.title else "Capítulo ${it.index + 1}"
            "$title ($timeCode)"
        }.toTypedArray()

        val selectedIndex = (MPVLib.getPropertyInt("chapter") ?: 0).coerceIn(0, chapters.size - 1)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(getString(R.string.chapters))
            .setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
                MPVLib.setPropertyInt("chapter", chapters[item].index)
                dialog.dismiss()
                val chTitle = chapters[item].title?.takeIf { it.isNotBlank() } ?: (chapters[item].index + 1).toString()
                configSnackbar("Capítulo: $chTitle")
            }.show()

    }

    private fun selectTrack(title: String, type: String, get: () -> Int, set: (Int) -> Unit) {
        val tracks = player.tracks.getValue(type)
        val selectedMpvId = get()
        val selectedIndex = tracks.indexOfFirst { it.mpvId == selectedMpvId }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog).apply {
            setTitle(title)
            setSingleChoiceItems(
                tracks.map { it.name }.toTypedArray(),
                selectedIndex
            ) { dialog, item ->
                val trackId = tracks[item].mpvId
                set(trackId)
                dialog.dismiss()
                trackSwitchNotification { TrackData(trackId, type) }
            }
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
        val selectedIndex = speeds.indexOfFirst { abs(it - currentSpeed) < 0.05 }.coerceAtLeast(0)

        val speedLabels = speeds.map { if (it == 1.0) "Normal (1.0x)" else "${it}x" }.toTypedArray()

        Log.d(TAG, "currentSpeed=$currentSpeed, selectedIndex=$selectedIndex")

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog).apply {
            setTitle(getString(R.string.select_speed))
            setSingleChoiceItems(
                speedLabels,
                selectedIndex
            ) { dialog, item ->
                setSpeed(speeds[item])
                dialog.dismiss()
                configSnackbar("Velocidade: ${speedLabels[item]}")
            }
        }.show()
    }

    private fun setSpeed(speed: Double) {
        MPVLib.setPropertyDouble("speed", speed)
    }


    private fun updateOrientation(newConfig: Configuration) {
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                controller.btnRotate.apply {
                    orientation = Orientation.PORTRAIT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tooltipText = getString(R.string.landscape)
                    }
                    icon = ContextCompat.getDrawable(
                        /* context */ this@MPVActivity,
                        /* drawableId */ R.drawable.ic_landscape_24
                    )
                }
            }
            else -> {
                controller.btnRotate.apply {
                    orientation = Orientation.LANDSCAPE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tooltipText = getString(R.string.portrait)
                    }
                    icon = ContextCompat.getDrawable(
                        /* context */ this@MPVActivity,
                        /* drawableId */ R.drawable.ic_portrait_24
                    )
                }
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
        }
        binding.netflixSkipRow.visibility = View.GONE
        binding.nextEpisodeCard.root.visibility = View.GONE
    }

    private fun unlockControls() {
        controller.apply {
            btnUnlock.visibility = View.GONE
            playerToolbar.visibility = View.VISIBLE
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

        val watchProgress = (watchedDuration.toDouble() / totalDuration.toDouble()).toFloat() * 100
        if (watchProgress > 10) {
            val fileId = currentFileId.ifBlank { intent.getStringExtra("fileId")!! }
            val title = currentTitle.ifBlank { intent.getStringExtra("title")!! }
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
        if (controlsLocked || durationSeconds <= 60L) {
            if (isNextEpisodeCardShowing) {
                dismissNextEpisodeCard(isManualCancel = false)
            }
            return
        }
        val next = nextEpisode ?: return
        val remaining = durationSeconds - timePosSeconds

        // Instant advance if episode reaches or exceeds full duration
        if (remaining <= 1L || timePosSeconds >= durationSeconds) {
            if (!nextEpisodeCanceled) {
                playNextEpisodeDirectly()
                return
            }
        }

        val isNearEnd = remaining in 1L..10L

        if (isNextEpisodeCardShowing) {
            if (!isNearEnd) {
                dismissNextEpisodeCard(isManualCancel = false)
            }
            return
        }

        if (nextEpisodeCanceled) return

        if (isNearEnd) {
            showNextEpisodeCard(next)
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

    ////////////////    MPV EVENTS    ////////////////

    override fun eventProperty(property: String, value: Boolean) {
        if (activityIsForeground) {
            if (property == "pause") {
                runOnUiThread { updatePlaybackStatus(value) }
            } else if (property == "eof-reached" && value) {
                runOnUiThread {
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
                    checkAutoPlayNextEpisode(value, (player.duration ?: 0).toLong())
                }
                "duration" -> {
                    updatePlaybackDuration(value.toInt())
                    updateChapters()
                }
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