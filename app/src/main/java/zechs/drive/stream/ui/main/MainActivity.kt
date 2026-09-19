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
import android.util.Log
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
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.databinding.ActivityMainBinding
import zechs.drive.stream.databinding.DialogUpdateProgressBinding
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.AppUpdateManager
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.util.Converter
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_CODE
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_ID
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_NAME
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val NOTIFICATION_PERMISSION_CODE = 2000
        private var hasPlayedVinheta = false
    }

    private var vinhetaPlayer: ExoPlayer? = null
    private var isVinhetaDismissed = false

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    private val viewModel by viewModels<MainViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    // The splash theme is Kodi Estuary, so the first persisted theme can be
    // compared against it without flashing Tokyo Night during startup.
    private var currentAppliedTheme: AppTheme? = AppTheme.KODI_ESTUARY
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
                .setDuration(250L)
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
    }

    private fun themeObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.theme.collect { theme ->
                    Log.d(TAG, "theme=${theme}")
                    changeTheme(theme)
                }
            }
        }
    }

    private fun redirectOnLogin() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasLoggedIn.collect { hasLoggedIn ->
                    Log.d(TAG, "hasLoggedIn=${hasLoggedIn}")
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
                        Log.d(TAG, "Already on latest version")
                    } else {
                        Log.d(TAG, "Newer version of app is available (latest=${release.tagName})")
                        if (viewModel.updateDownloadState.value is MainViewModel.UpdateDownloadState.Idle) {
                            showUpdateAvailableDialog(release)
                        }
                    }
                }

                is Resource.Error -> Log.d(TAG, it.message)
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
                recreate()
            }
        } else if (currentAppliedTheme != appTheme) {
            currentAppliedTheme = appTheme
            recreate()
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
            .setTitle("Atualização — Makimono ${release.tagName}")
            .setMessage("Uma nova versão do Makimono (${release.tagName}) está disponível com melhorias e correções.\n\nArquivo: ${asset?.name ?: "makimono.apk"}\n\nDeseja atualizar agora?")
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

                            binding.root.postDelayed({
                                dismissProgressDialog()
                                viewModel.resetUpdateState()
                            }, 1500L)
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

    private fun setupVinheta(savedInstanceState: Bundle?) {
        if (savedInstanceState != null || hasPlayedVinheta) {
            binding.vinhetaContainer.visibility = View.GONE
            return
        }
        hasPlayedVinheta = true

        binding.vinhetaContainer.visibility = View.VISIBLE
        binding.vinhetaContainer.alpha = 1f
        isVinhetaDismissed = false

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
                            dismissVinheta()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Vinheta playback error: ${error.message}", error)
                        dismissVinheta()
                    }
                })
                prepare()
                play()
            }
            vinhetaPlayer = player
            binding.pvVinheta.player = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize vinheta player", e)
            dismissVinheta()
        }

        binding.vinhetaContainer.setOnClickListener {
            dismissVinheta()
        }
    }

    private fun dismissVinheta() {
        if (isVinhetaDismissed) return
        isVinhetaDismissed = true

        try {
            vinhetaPlayer?.pause()
        } catch (_: Exception) {}

        binding.vinhetaContainer.animate()
            .alpha(0f)
            .setDuration(280L)
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
            if (event.action == KeyEvent.ACTION_DOWN) {
                dismissVinheta()
            }
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