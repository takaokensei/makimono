package zechs.drive.stream.ui.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.repository.GithubRepository
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.AppUpdateManager
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.state.Resource
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val githubRepository: GithubRepository,
    private val appSettings: AppSettings,
    val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _hasLoggedIn = MutableStateFlow(false)
    val hasLoggedIn = _hasLoggedIn.asStateFlow()

    private val _latest = MutableLiveData<Resource<LatestRelease>>()
    val latest: LiveData<Resource<LatestRelease>>
        get() = _latest

    private val _theme = MutableSharedFlow<AppTheme>(replay = 1)
    val theme = _theme.asSharedFlow()

    var currentThemeIndex = AppTheme.KODI_ESTUARY.value
        private set

    private val _lastUpdated = MutableStateFlow<String?>(null)
    val lastUpdated = _lastUpdated.asStateFlow()

    var isChecking = false
        private set

    init {
        getTheme()
        getPlayer()
        viewModelScope.launch {
            try {
                withTimeoutOrNull(2500L) {
                    val status = getLoginStatus()
                    if (status) {
                        getLastUpdated()
                    }
                    _hasLoggedIn.value = status
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Login init error", e)
            } finally {
                delay(150L)
                _isLoading.value = false
            }
        }
        getLatestRelease() // check for update
    }

    private suspend fun getLoginStatus(): Boolean {
        sessionManager.fetchClient() ?: return false
        sessionManager.fetchRefreshToken() ?: return false
        return true
    }

    fun getLatestRelease() = viewModelScope.launch {
        _latest.postValue(Resource.Loading())
        isChecking = true
        _latest.postValue(githubRepository.getLatestRelease())
        isChecking = false
        appSettings.saveLastUpdated()
        getLastUpdated()
    }

    private fun getLastUpdated() = viewModelScope.launch {
        _lastUpdated.emit(appSettings.fetchLastUpdated())
    }

    private fun getTheme() = viewModelScope.launch {
        try {
            withTimeoutOrNull(1500L) {
                val fetchTheme = appSettings.fetchTheme()
                currentThemeIndex = fetchTheme.value
                _theme.emit(fetchTheme)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "getTheme error", e)
        }
    }

    fun setTheme(theme: AppTheme) = viewModelScope.launch {
        try {
            appSettings.saveTheme(theme)
            currentThemeIndex = theme.value
            _theme.emit(theme)
        } catch (e: Exception) {
            Log.e("MainViewModel", "setTheme error", e)
        }
    }

    var currentPlayerIndex = VideoPlayer.EXO_PLAYER
        private set

    private fun getPlayer() = viewModelScope.launch {
        try {
            val player = withTimeoutOrNull(1500L) {
                appSettings.fetchPlayer()
            } ?: VideoPlayer.EXO_PLAYER
            currentPlayerIndex = VideoPlayer.EXO_PLAYER
            Log.d("MainViewModel", "Loaded default player: $currentPlayerIndex")
        } catch (e: Exception) {
            Log.e("MainViewModel", "getPlayer error", e)
        }
    }

    fun setPlayer(player: VideoPlayer) {
        currentPlayerIndex = VideoPlayer.EXO_PLAYER
        viewModelScope.launch {
            try {
                appSettings.savePlayer(VideoPlayer.EXO_PLAYER)
            } catch (e: Exception) {
                Log.e("MainViewModel", "savePlayer error", e)
            }
        }
    }

    sealed interface UpdateDownloadState {
        object Idle : UpdateDownloadState
        data class Downloading(val progress: Int, val bytesRead: Long, val totalBytes: Long) : UpdateDownloadState
        data class ReadyToInstall(val apkFile: File) : UpdateDownloadState
        data class Failed(val message: String) : UpdateDownloadState
    }

    private val _updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateDownloadState = _updateDownloadState.asStateFlow()

    fun startUpdateDownload(release: LatestRelease) = viewModelScope.launch {
        val asset = release.getBestApkAsset()
        if (asset == null) {
            _updateDownloadState.value = UpdateDownloadState.Failed("Nenhum APK compatível encontrado nesta release.")
            return@launch
        }

        _updateDownloadState.value = UpdateDownloadState.Downloading(0, 0L, asset.size)
        val result = appUpdateManager.downloadApk(asset) { progress, bytesRead, totalBytes ->
            _updateDownloadState.value = UpdateDownloadState.Downloading(progress, bytesRead, totalBytes)
        }

        result.onSuccess { apkFile ->
            _updateDownloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)
        }.onFailure { error ->
            _updateDownloadState.value = UpdateDownloadState.Failed(error.localizedMessage ?: "Falha ao baixar APK")
        }
    }

    fun resetUpdateState() {
        _updateDownloadState.value = UpdateDownloadState.Idle
    }

}
