package zechs.drive.stream.ui.main

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import android.view.KeyEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.content.res.Configuration
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.data.local.CatalogEntry
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.repository.CatalogRepository
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.databinding.ActivityMainBinding
import zechs.drive.stream.databinding.DialogUpdateProgressBinding
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.AppUpdateManager
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.util.Converter
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_CODE
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_ID
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_NAME
import java.util.*
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val NOTIFICATION_PERMISSION_CODE = 2000
        private var hasPlayedVinheta = false
    }

    private var vinhetaPlayer: ExoPlayer? = null
    private var isVinhetaDismissed = false
    private var isVideoFinished = false
    private var isPreloadFinished = false

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    @Inject
    lateinit var driveRepository: Lazy<DriveRepository>

    @Inject
    lateinit var folderMetadataRepository: FolderMetadataRepository

    @Inject
    lateinit var animePosterResolver: AnimePosterResolver

    @Inject
    lateinit var profileManager: ProfileManager

    @Inject
    lateinit var catalogRepository: CatalogRepository

    @Inject
    lateinit var sessionManager: Lazy<SessionManager>

    private val viewModel by viewModels<MainViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    // The splash theme is Kodi Estuary, so the first persisted theme can be
    // compared against it without flashing Tokyo Night during startup.
    private var currentAppliedTheme: AppTheme? = AppTheme.OBSIDIAN
    private var updateDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null
    private var progressBinding: DialogUpdateProgressBinding? = null

    private fun getThemeResId(theme: AppTheme): Int {
        return when (theme) {
            AppTheme.TOKYO_NIGHT -> R.style.Theme_DriveStream_TokyoNight
            AppTheme.DRACULA -> R.style.Theme_DriveStream_Dracula
            AppTheme.NORD -> R.style.Theme_DriveStream_Nord
            AppTheme.CATPPUCCIN_MOCHA -> R.style.Theme_DriveStream_CatppuccinMocha
            AppTheme.KODI_ESTUARY -> R.style.Theme_DriveStream_KodiEstuary
            AppTheme.OBSIDIAN -> R.style.Theme_DriveStream_Obsidian
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Always dismiss immediately — vinheta overlay or nav graph handles the "loading" UX.
        // Keeping the splash alive causes the old round Torii icon to flash for ~1s before
        // the vinheta video starts, which breaks the cinematic intro we want.
        splashScreen.setKeepOnScreenCondition { false }
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.view.animate()
                .alpha(0f)
                .setDuration(resources.getInteger(R.integer.motion_crossfade).toLong())
                .withEndAction { splashScreenView.remove() }
                .start()
        }

        super.onCreate(savedInstanceState)

        if (savedInstanceState != null && savedInstanceState.containsKey("CURRENT_THEME")) {
            val theme = AppTheme.fromValue(savedInstanceState.getInt("CURRENT_THEME"))
            currentAppliedTheme = theme
            setTheme(getThemeResId(theme))
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createUpdateNotificationChannel()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) handlePermission()
        }

        val navHostFragment = supportFragmentManager.findFragmentById(
            R.id.mainNavHostFragment
        ) as NavHostFragment
        navController = navHostFragment.navController

        themeObserver()
        updateObserver()
        updateDownloadObserver()
        redirectOnLogin()

        setupVinheta(savedInstanceState)
        handlePlaybackDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlaybackDeepLink(intent)
    }

    private fun handlePlaybackDeepLink(intent: Intent?) {
        if (intent == null) return
        val fileId = intent.getStringExtra(PlayerLauncher.EXTRA_FILE_ID)
            ?: intent.data?.takeIf { it.scheme == "makimono" && it.host == "watch" }?.lastPathSegment
            ?: return

        val title = intent.getStringExtra(PlayerLauncher.EXTRA_TITLE) ?: ""
        val isMovie = intent.getBooleanExtra(PlayerLauncher.EXTRA_IS_MOVIE, false)

        AppLog.d(TAG, "Handling deep link playback from Android TV Watch Next: fileId=$fileId, title=$title, isMovie=$isMovie")
        lifecycleScope.launch {
            PlayerLauncher.launch(
                context = this@MainActivity,
                fileId = fileId,
                title = title,
                isMovie = isMovie
            )
        }
    }

    private fun themeObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.theme.collect { theme ->
                    AppLog.d(TAG, "theme=${theme}")
                    changeTheme(theme)
                }
            }
        }
    }

    private fun redirectOnLogin() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasLoggedIn.collect { hasLoggedIn ->
                    AppLog.d(TAG, "hasLoggedIn=${hasLoggedIn}")
                    if (hasLoggedIn) handleLogin()
                }
            }
        }
    }

    private fun handleLogin() {
        val currentFragment = navController.currentDestination?.id
        if (currentFragment != null && currentFragment == R.id.signInFragment) {
            navController.navigateSafe(R.id.action_signInFragment_to_homeFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun updateObserver() {
        viewModel.latest.observe(this) {
            when (it) {
                is Resource.Success -> {
                    val release = it.data
                    if (release.isLatest()) {
                        AppLog.d(TAG, "Already on latest version")
                    } else {
                        AppLog.d(TAG, "Newer version of app is available (latest=${release.tagName})")
                        if (viewModel.updateDownloadState.value is MainViewModel.UpdateDownloadState.Idle) {
                            showUpdateAvailableDialog(release)
                        }
                    }
                }

                is Resource.Error -> AppLog.d(TAG, it.message)
                else -> {}
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createUpdateNotificationChannel() {
        val channel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            UPDATE_CHANNEL_NAME,
            IMPORTANCE_DEFAULT
        )

        val notificationManager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    private fun sendUpdateNotification(release: LatestRelease) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(release.htmlUrl)
        }

        val pendingIntent = PendingIntent.getActivity(
            /* context */ this,
            /* requestCode */ Random().nextInt(),
            /* intent */ intent,
            /* flags */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )


        val defaultSoundUri = RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_NOTIFICATION
        )

        val notificationBuilder = NotificationCompat.Builder(
            /* context */this,
            /* channelId */ UPDATE_CHANNEL_ID
        ).apply {
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setSmallIcon(R.drawable.ic_update_24)
            setContentTitle(release.name)
            setContentText(getString(R.string.new_version_available))
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            setSound(defaultSoundUri)
        }

        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(
                        /* requestCode */ UPDATE_CHANNEL_CODE,
                        /* notification */ notificationBuilder.build()
                    )
                }
            } else {
                notify(
                    /* requestCode */ UPDATE_CHANNEL_CODE,
                    /* notification */ notificationBuilder.build()
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handlePermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            Snackbar.make(
                binding.root,
                getString(R.string.notification_permission_rationale),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.grant)) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }.show()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_CODE
            )
        }
    }

    private fun changeTheme(appTheme: AppTheme) {
        if (currentAppliedTheme == null) {
            currentAppliedTheme = appTheme
            if (appTheme != AppTheme.TOKYO_NIGHT) {
                smoothRecreate()
            }
        } else if (currentAppliedTheme != appTheme) {
            currentAppliedTheme = appTheme
            smoothRecreate()
        }
    }

    private fun smoothRecreate() {
        recreate()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentAppliedTheme?.let {
            outState.putInt("CURRENT_THEME", it.value)
        }
    }

    private fun showUpdateAvailableDialog(release: LatestRelease) {
        if (isFinishing || isDestroyed || updateDialog?.isShowing == true) return

        val asset = release.getBestApkAsset()

        updateDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_dialog_title, release.tagName))
            .setMessage(getString(R.string.update_dialog_message, release.tagName, asset?.name ?: "makimono.apk"))
            .setPositiveButton("Atualizar Agora") { dialog, _ ->
                dialog.dismiss()
                viewModel.startUpdateDownload(release)
            }
            .setNegativeButton("Mais Tarde") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Ver no GitHub") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                startActivity(intent)
            }
            .create()

        updateDialog?.show()
    }

    private fun showProgressDialog() {
        if (isFinishing || isDestroyed) return
        if (progressDialog == null) {
            val binding = DialogUpdateProgressBinding.inflate(layoutInflater)
            progressBinding = binding
            progressDialog = MaterialAlertDialogBuilder(this)
                .setView(binding.root)
                .setCancelable(false)
                .create()
        }
        if (progressDialog?.isShowing == false) {
            progressDialog?.show()
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBinding = null
    }

    private fun updateDownloadObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateDownloadState.collect { state ->
                    when (state) {
                        is MainViewModel.UpdateDownloadState.Idle -> {
                            dismissProgressDialog()
                        }
                        is MainViewModel.UpdateDownloadState.Downloading -> {
                            showProgressDialog()
                            progressBinding?.apply {
                                tvUpdateTitle.text = "Baixando atualização..."
                                tvUpdateSubtitle.text = "Baixando pacote do GitHub Releases..."
                                if (state.totalBytes > 0) {
                                    progressBarUpdate.isIndeterminate = false
                                    progressBarUpdate.progress = state.progress
                                    tvProgressPercent.text = "${state.progress}%"
                                    val currentSize = Converter.toHumanSize(state.bytesRead)
                                    val totalSize = Converter.toHumanSize(state.totalBytes)
                                    tvProgressSize.text = "$currentSize / $totalSize"
                                } else {
                                    progressBarUpdate.isIndeterminate = true
                                    tvProgressPercent.text = ""
                                    tvProgressSize.text = Converter.toHumanSize(state.bytesRead)
                                }
                            }
                        }
                        is MainViewModel.UpdateDownloadState.ReadyToInstall -> {
                            progressBinding?.apply {
                                tvUpdateTitle.text = "Download Concluído!"
                                tvUpdateSubtitle.text = "Iniciando instalador do sistema..."
                                progressBarUpdate.isIndeterminate = false
                                progressBarUpdate.progress = 100
                                tvProgressPercent.text = "100%"
                            }

                            val success = appUpdateManager.installApk(state.apkFile, this@MainActivity)
                            if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "Permita a instalação de fontes desconhecidas para atualizar",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }

                            zechs.drive.stream.utils.LifecycleDelays.postDelayed(binding.root, 1500L, this@MainActivity) {
                                dismissProgressDialog()
                                viewModel.resetUpdateState()
                            }
                        }
                        is MainViewModel.UpdateDownloadState.Failed -> {
                            dismissProgressDialog()
                            Snackbar.make(
                                binding.root,
                                "Erro ao atualizar: ${state.message}",
                                Snackbar.LENGTH_LONG
                            ).setAction("Tentar Novamente") {
                                viewModel.latest.value?.data?.let { release ->
                                    viewModel.startUpdateDownload(release)
                                }
                            }.show()
                            viewModel.resetUpdateState()
                        }
                    }
                }
            }
        }
    }

    private fun checkDismissVinheta() {
        if (isVideoFinished && isPreloadFinished) {
            dismissVinheta()
        }
    }

    private fun setupVinheta(savedInstanceState: Bundle?) {
        if (savedInstanceState != null || hasPlayedVinheta) {
            binding.vinhetaContainer.visibility = View.GONE
            return
        }
        hasPlayedVinheta = true

        binding.vinhetaContainer.visibility = View.VISIBLE
        binding.vinhetaContainer.alpha = 1f
        isVinhetaDismissed = false
        isVideoFinished = false
        isPreloadFinished = false

        try {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            binding.pvVinheta.resizeMode = if (isLandscape) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }

            val vinhetaUri = Uri.parse("android.resource://$packageName/${R.raw.vinheta}")
            val player = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri(vinhetaUri))
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            isVideoFinished = true
                            checkDismissVinheta()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        AppLog.e(TAG, "Vinheta playback error: ${error.message}", error)
                        isVideoFinished = true
                        checkDismissVinheta()
                    }
                })
                prepare()
                play()
            }
            vinhetaPlayer = player
            binding.pvVinheta.player = player

            startStartupPreloader()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to initialize vinheta player", e)
            isVideoFinished = true
            isPreloadFinished = true
            dismissVinheta()
        }

        // Unskippable intro: consume touch/click events without dismissing
        binding.vinhetaContainer.setOnClickListener { /* unskippable intro */ }
    }

    private fun startStartupPreloader() {
        lifecycleScope.launch(Dispatchers.IO) {
            val timeoutJob = launch {
                // Safety maximum timeout of 10s so user is never stuck indefinitely
                delay(10000L)
                withContext(Dispatchers.Main) {
                    if (!isPreloadFinished) {
                        AppLog.d(TAG, "Preloader reached safety timeout, proceeding to app")
                        isPreloadFinished = true
                        checkDismissVinheta()
                    }
                }
            }

            try {
                // Warm up profiles first so profile selection screen has zero delay
                try {
                    profileManager.awaitReady()
                    val profiles = profileManager.getProfiles()
                    profiles.forEach { profile ->
                        if (!profile.backgroundUrl.isNullOrBlank()) {
                            MediaImageLoader.preloadBackdrop(applicationContext, profile.backgroundUrl)
                        }
                    }
                } catch (e: Exception) {
                    AppLog.logSwallowed(TAG, "warming up profiles during startup", e)
                }

                // Check if user is logged into Google Drive
                val client = try {
                    sessionManager.get().fetchClient()
                } catch (e: Exception) {
                    AppLog.logSwallowed(TAG, "fetching client credentials during startup", e)
                    null
                }

                if (client == null) {
                    AppLog.d(TAG, "User not signed in, skipping library catalog preload")
                    withContext(Dispatchers.Main) {
                        isPreloadFinished = true
                        timeoutJob.cancel()
                        checkDismissVinheta()
                    }
                    return@launch
                }

                // Fast Reload: if metadata is already cached in Room, release intro immediately
                // Background sync continues concurrently without blocking the user
                val hasCachedMetadata = try {
                    folderMetadataRepository.getAllMetadata().isNotEmpty()
                } catch (e: Exception) {
                    false
                }

                if (hasCachedMetadata) {
                    AppLog.d(TAG, "Fast Reload: local metadata cached, releasing vinheta immediately")
                    withContext(Dispatchers.Main) {
                        isPreloadFinished = true
                        timeoutJob.cancel()
                        checkDismissVinheta()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvVinhetaPreloadStatus.text = "Sincronizando biblioteca…"
                    }
                }

                val (configuredId, configuredName) = profileManager.getLibraryRoot()
                var resolvedFolderId = configuredId

                if (resolvedFolderId.isNullOrBlank()) {
                    val searchName = configuredName?.trimEnd('/')?.trim() ?: "oneblacki"
                    val escapedName = searchName.replace("'", "\\'")
                    val resp = driveRepository.get().getAllFilesDetailed(
                        query = "name = '$escapedName' and (mimeType = 'application/vnd.google-apps.folder' or mimeType = 'application/vnd.google-apps.shortcut') and trashed=false",
                        pageSize = 25
                    )
                    val folder = when (resp) {
                        is Resource.Success -> resp.data?.files?.firstOrNull()
                        is Resource.Error -> resp.data?.files?.firstOrNull()
                        is Resource.Loading -> null
                    }
                    if (folder != null) {
                        resolvedFolderId = folder.shortcutDetails.targetId ?: folder.id
                    } else if (configuredName.isNullOrBlank()) {
                        resolvedFolderId = "root"
                    }
                }

                if (resolvedFolderId.isNullOrBlank()) {
                    resolvedFolderId = "root"
                }

                // Query files in library root folder
                val filesResponse = driveRepository.get().getAllFilesDetailed(
                    query = "'$resolvedFolderId' in parents and trashed=false",
                    pageSize = 100
                )
                val fileListing = when (filesResponse) {
                    is Resource.Success -> filesResponse.data
                    is Resource.Error -> filesResponse.data
                    is Resource.Loading -> null
                }

                val driveFiles = fileListing?.files?.map { it.toDriveFile() }.orEmpty()
                val validMedia = driveFiles.filter { file ->
                    val isMedia = file.isFolder || file.isShortcutFolder || file.isVideoFile || file.isShortcutVideo
                    val lower = file.name.lowercase()
                    isMedia &&
                        !lower.endsWith(".ico") &&
                        !lower.endsWith(".apk") &&
                        !lower.endsWith(".exe") &&
                        !lower.endsWith(".ini") &&
                        !lower.endsWith(".txt") &&
                        !lower.startsWith(".")
                }

                if (validMedia.isNotEmpty()) {
                    // Cache in catalogRepository for instant offline availability
                    catalogRepository.cacheFolderContents(
                        parentId = resolvedFolderId,
                        entries = validMedia.map {
                            CatalogEntry(
                                id = it.id,
                                name = it.name,
                                mimeType = it.mimeType,
                                parentId = resolvedFolderId,
                                posterUrl = it.posterUrl
                            )
                        }
                    )

                    val existingMeta = folderMetadataRepository.getAllMetadata().associateBy { it.folderId }
                    val total = validMedia.size
                    var processed = 0

                    withContext(Dispatchers.Main) {
                        binding.tvVinhetaPreloadStatus.text = "Sincronizando capas e banners (0/$total)…"
                    }

                    // Process concurrently in chunks of 6
                    validMedia.chunked(6).forEach { chunk ->
                        coroutineScope {
                            chunk.map { file ->
                                async {
                                    val targetId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                                        file.shortcutDetails.targetId
                                    } else file.id

                                    val cached = existingMeta[targetId] ?: existingMeta[file.id]
                                    val isComplete = cached != null &&
                                        !cached.posterUrl.isNullOrBlank() &&
                                        !cached.bannerUrl.isNullOrBlank() &&
                                        !cached.synopsis.isNullOrBlank()

                                    if (isComplete) {
                                        // Already cached in Room: ensure Glide memory and disk cache are warm
                                        MediaImageLoader.preloadPoster(applicationContext, cached!!.posterUrl)
                                        MediaImageLoader.preloadBackdrop(applicationContext, cached.bannerUrl)
                                    } else {
                                        // Missing poster, banner, or synopsis: resolve from AniList/TMDB
                                        val meta = animePosterResolver.resolveMetadata(file.name)
                                        if (meta != null) {
                                            folderMetadataRepository.saveAnimeMetadata(targetId, file.name, meta)
                                            MediaImageLoader.preloadPoster(applicationContext, meta.posterUrl)
                                            MediaImageLoader.preloadBackdrop(applicationContext, meta.bannerUrl)
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                        processed += chunk.size
                        withContext(Dispatchers.Main) {
                            binding.tvVinhetaPreloadStatus.text = "Sincronizando capas e banners ($processed/$total)…"
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.tvVinhetaPreloadStatus.text = "Biblioteca pronta!"
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Catalog preload error: ${e.message}")
            } finally {
                timeoutJob.cancel()
                withContext(Dispatchers.Main) {
                    isPreloadFinished = true
                    checkDismissVinheta()
                }
            }
        }
    }

    private fun dismissVinheta() {
        if (isVinhetaDismissed) return
        isVinhetaDismissed = true

        try {
            vinhetaPlayer?.pause()
        } catch (e: Exception) {
            AppLog.logSwallowed(TAG, "pausing vinheta player on dismiss", e)
        }

        binding.vinhetaContainer.animate()
            .alpha(0f)
            .setDuration(resources.getInteger(R.integer.motion_spec_slow).toLong())
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.vinhetaContainer.visibility = View.GONE
                binding.pvVinheta.player = null
                vinhetaPlayer?.release()
                vinhetaPlayer = null
            }
            .start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.vinhetaContainer.isVisible) {
            // Unskippable intro: absorb all key events until intro completes naturally
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (binding.vinhetaContainer.isVisible) {
            dismissVinheta()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vinhetaPlayer?.release()
        vinhetaPlayer = null
        updateDialog?.dismiss()
        progressDialog?.dismiss()
    }

}
