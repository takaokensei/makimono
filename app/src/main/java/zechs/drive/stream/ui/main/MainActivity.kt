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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull
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
    }

    private var vinhetaPlayer: ExoPlayer? = null
    private var authenticatedPreparationInProgress = false
    private var startupRoutingInProgress = false
    private var vinhetaSafetyJob: kotlinx.coroutines.Job? = null
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

    @Inject
    lateinit var mediaPreloadManager: zechs.drive.stream.data.repository.MediaPreloadManager

    @Inject
    lateinit var profileArtCatalog: zechs.drive.stream.data.remote.ProfileArtCatalog

    private val viewModel by viewModels<MainViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    // The splash theme is Kodi Estuary, so the first persisted theme can be
    // compared against it without flashing Tokyo Night during startup.
    private var currentAppliedTheme: AppTheme? = AppTheme.CRUNCHYROLL
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
            AppTheme.CRUNCHYROLL -> R.style.Theme_DriveStream_Crunchyroll
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

        val navHostFragment = supportFragmentManager.findFragmentById(
            R.id.mainNavHostFragment
        ) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.homeFragment && viewModel.hasLoggedIn.value) {
                requestNotificationPermissionAfterOnboarding()
            }
            routeForSessionState(destination.id, viewModel.hasLoggedIn.value)
        }


        themeObserver()
        updateObserver()
        updateDownloadObserver()
        redirectOnLogin()

        handlePlaybackDeepLink(intent)
    }

    private fun requestNotificationPermissionAfterOnboarding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) handlePermission()
        }

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
            if (!sessionManager.get().hasPairingSession()) return@launch
            profileManager.awaitReady()
            if (profileManager.getProfiles().isEmpty()) return@launch
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
                combine(viewModel.hasLoggedIn, viewModel.isLoading) { paired, loading -> paired to loading }
                    .collect { (paired, loading) ->
                        if (!loading) navController.currentDestination?.id?.let {
                            routeForSessionState(it, paired)
                        }
                    }
            }
        }
    }

    private fun routeForSessionState(destinationId: Int, paired: Boolean) {
        if (viewModel.isLoading.value || authenticatedPreparationInProgress || startupRoutingInProgress) return
        AppLog.d(TAG, "Startup route: paired=$paired destination=$destinationId")
        when (StartupNavigationPolicy.destination(paired)) {
            StartupNavigationPolicy.Destination.PAIRING -> {
                if (destinationId != R.id.signInFragment) {
                    navController.navigateSafe(R.id.action_global_signInFragment)
                }
            }
            StartupNavigationPolicy.Destination.PROFILES -> {
                if (destinationId != R.id.startupFragment &&
                    destinationId != R.id.signInFragment &&
                    destinationId != R.id.profileSelectionFragment) return
                startupRoutingInProgress = true
                lifecycleScope.launch {
                    try {
                        profileManager.prepareFirstProfileAfterPairing()
                        val currentDestination = navController.currentDestination?.id
                        if (currentDestination == R.id.startupFragment || currentDestination == R.id.signInFragment) {
                            navController.navigateSafe(R.id.action_global_profileSelectionFragment)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.logSwallowed(TAG, "preparing first profile after Drive pairing", e)
                    } finally {
                        startupRoutingInProgress = false
                    }
                }
            }
            StartupNavigationPolicy.Destination.PREPARATION -> Unit
        }
    }

    fun onPairingCompleted() {
        if (authenticatedPreparationInProgress) return
        if (StartupNavigationPolicy.destination(true, justPaired = true) !=
            StartupNavigationPolicy.Destination.PREPARATION) return
        authenticatedPreparationInProgress = true
        viewModel.onPairingCompleted()
        navController.navigateSafe(R.id.action_global_startupFragment)
        setupVinheta()
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

    private fun setupVinheta() {
        binding.vinhetaContainer.visibility = View.VISIBLE
        binding.vinhetaContainer.alpha = 1f
        binding.mainNavHostFragment.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.vinhetaContainer.requestFocus()
        isVinhetaDismissed = false
        isVideoFinished = false
        isPreloadFinished = false
        vinhetaSafetyJob?.cancel()
        vinhetaSafetyJob = lifecycleScope.launch {
            delay(15_000L)
            isVideoFinished = true
            isPreloadFinished = true
            checkDismissVinheta()
        }

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
                    if (authenticatedPreparationInProgress) {
                        profileManager.prepareFirstProfileAfterPairing()
                    }
                    (profileArtCatalog.getPresetAvatars().take(8) +
                        profileArtCatalog.getPresetWallpapers().take(4)).forEach { art ->
                        MediaImageLoader.preloadPoster(applicationContext, art.url)
                    }
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
                val hasPairingSession = try {
                    sessionManager.get().hasPairingSession()
                } catch (e: Exception) {
                    AppLog.logSwallowed(TAG, "checking the paired TV session during startup", e)
                    false
                }

                if (!hasPairingSession) {
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
                        binding.tvVinhetaPreloadStatus.setText(R.string.startup_preparing_library)
                    }
                }

                val (configuredId, configuredName) = profileManager.getLibraryRoot()
                var resolvedFolderId = configuredId

                if (resolvedFolderId.isNullOrBlank()) {
                    val searchCandidates = if (!configuredName.isNullOrBlank()) {
                        listOf(configuredName.trimEnd('/').trim())
                    } else {
                        listOf("oneblacki", "Animes", "Anime", "Séries", "Series", "Filmes", "Movies")
                    }
                    var matchedFolder: zechs.drive.stream.data.model.File? = null
                    for (candidate in searchCandidates) {
                        val escapedName = candidate.replace("'", "\\'")
                        val folderMimes = "(mimeType = 'application/vnd.google-apps.folder' or mimeType = 'application/vnd.google-apps.shortcut')"
                        val resp = driveRepository.get().getAllFilesDetailed(
                            query = "name = '$escapedName' and 'root' in parents and $folderMimes and trashed=false",
                            pageSize = 10
                        )
                        val folder = when (resp) {
                            is Resource.Success -> resp.data?.files?.firstOrNull()
                            is Resource.Error -> resp.data?.files?.firstOrNull()
                            is Resource.Loading -> null
                        }
                        if (folder != null) {
                            matchedFolder = folder
                            break
                        }
                    }
                    if (matchedFolder != null) {
                        resolvedFolderId = matchedFolder.shortcutDetails.targetId ?: matchedFolder.id
                        if (configuredId.isNullOrBlank() && configuredName.isNullOrBlank()) {
                            profileManager.setLibraryRoot(resolvedFolderId, matchedFolder.name)
                        }
                    } else if (configuredName.isNullOrBlank()) {
                        resolvedFolderId = "root"
                    }
                }

                if (resolvedFolderId.isNullOrBlank() || resolvedFolderId == "root") {
                    AppLog.d(TAG, "Library root is unconfigured or 'root': skipping automatic anime metadata scraping to prevent Drive pollution.")
                    return@launch
                }

                // Query files strictly in designated library root folder
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

                    // Purge non-library folders from metadata
                    val animeFolderIds = validMedia.map { if (it.isShortcut) it.shortcutDetails.targetId ?: it.id else it.id }.toSet()
                    if (animeFolderIds.isNotEmpty()) {
                        folderMetadataRepository.retainOnlyLibraryFolders(animeFolderIds)
                    }

                    withContext(Dispatchers.Main) {
                        binding.tvVinhetaPreloadStatus.setText(R.string.startup_preparing_artwork)
                    }
                    // Warm the first visible items; larger libraries continue outside the intro.
                    withTimeoutOrNull(8_000L) {
                        mediaPreloadManager.preloadMetadataParallel(validMedia.take(8))
                    }
                    mediaPreloadManager.startPreload(validMedia)
                }

                withContext(Dispatchers.Main) {
                    binding.tvVinhetaPreloadStatus.setText(R.string.startup_library_ready)
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
        vinhetaSafetyJob?.cancel()

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
                binding.mainNavHostFragment.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                if (authenticatedPreparationInProgress) {
                    authenticatedPreparationInProgress = false
                    navController.navigateSafe(R.id.action_global_profileSelectionFragment)
                }
            }
            .start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.vinhetaContainer.isVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissVinheta()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (binding.vinhetaContainer.isVisible) vinhetaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && binding.vinhetaContainer.isVisible) vinhetaPlayer?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        vinhetaPlayer?.release()
        vinhetaPlayer = null
        updateDialog?.dismiss()
        progressDialog?.dismiss()
    }

}
