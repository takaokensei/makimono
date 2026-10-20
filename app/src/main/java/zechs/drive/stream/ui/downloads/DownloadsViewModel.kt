package zechs.drive.stream.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.repository.DownloadRepository
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.ProfileManager
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val profileManager: ProfileManager,
    private val appSettings: AppSettings
) : ViewModel() {

    val downloads: StateFlow<List<DownloadItem>> = downloadRepository
        .getDownloadsForCurrentProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _storageUsage = MutableStateFlow(DownloadRepository.StorageUsage(0L, 0L, 0L))
    val storageUsage: StateFlow<DownloadRepository.StorageUsage> = _storageUsage.asStateFlow()

    private val _isSmartDownloadsEnabled = MutableStateFlow(true)
    val isSmartDownloadsEnabled: StateFlow<Boolean> = _isSmartDownloadsEnabled.asStateFlow()

    init {
        refreshStorageUsage()
        loadSmartDownloadsState()
    }

    private fun loadSmartDownloadsState() {
        viewModelScope.launch {
            val profileId = profileManager.getActiveProfile().id
            _isSmartDownloadsEnabled.value = appSettings.fetchSmartDownloads(profileId)
        }
    }

    fun setSmartDownloadsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val profileId = profileManager.getActiveProfile().id
            appSettings.saveSmartDownloads(enabled, profileId)
            _isSmartDownloadsEnabled.value = enabled
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            _storageUsage.value = downloadRepository.getStorageUsage()
        }
    }

    fun deleteDownload(fileId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(fileId)
            refreshStorageUsage()
        }
    }

    fun deleteAll() {
        val profileId = profileManager.getActiveProfile().id
        viewModelScope.launch {
            downloadRepository.deleteAllDownloadsForProfile(profileId)
            refreshStorageUsage()
        }
    }
}
