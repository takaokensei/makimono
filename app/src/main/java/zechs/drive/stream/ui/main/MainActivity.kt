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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.databinding.ActivityMainBinding
import zechs.drive.stream.databinding.DialogUpdateProgressBinding
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.util.Converter
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_CODE
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_ID
import zechs.drive.stream.utils.util.NotificationKeys.Companion.UPDATE_CHANNEL_NAME
import java.util.*

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val NOTIFICATION_PERMISSION_CODE = 2000
    }

    private val viewModel by viewModels<MainViewModel>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var currentAppliedTheme: AppTheme? = null
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
        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value }

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
                    val release = it.data!!
                    if (release.isLatest()) {
                        Log.d(TAG, "Already on latest version")
                    } else {
                        Log.d(TAG, "Newer version of app is available (latest=${release.tagName})")
                        showUpdateAvailableDialog(release)
                        sendUpdateNotification(release)
                    }
                }

                is Resource.Error -> Log.d(TAG, it.message!!)
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
            progressBinding = DialogUpdateProgressBinding.inflate(layoutInflater)
            progressDialog = MaterialAlertDialogBuilder(this)
                .setView(progressBinding!!.root)
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
                            binding.root.postDelayed({
                                dismissProgressDialog()
                                viewModel.resetUpdateState()
                            }, 2500L)
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

    override fun onDestroy() {
        super.onDestroy()
        updateDialog?.dismiss()
        progressDialog?.dismiss()
    }

}