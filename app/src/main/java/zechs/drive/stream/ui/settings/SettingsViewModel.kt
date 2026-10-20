package zechs.drive.stream.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.BackupSerializer
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.MotionPreference
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.SubtitleStyle
import zechs.drive.stream.utils.state.Resource
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileManager: ProfileManager,
    private val appSettings: AppSettings,
    private val malSessionManager: MalSessionManager,
    private val malRepository: MalRepository,
    private val driveRepository: DriveRepository,
    private val backupSerializer: BackupSerializer,
    val profileArtCatalog: ProfileArtCatalog
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SettingsUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeActiveProfile()
        loadInitialSettings()
    }

    private fun observeActiveProfile() {
        viewModelScope.launch {
            profileManager.activeProfileFlow.collect { profile ->
                val (rootId, rootName) = profileManager.getLibraryRoot()
                val motion = runCatching { appSettings.fetchMotionPreference(profile.id) }
                    .getOrDefault(MotionPreference.SYSTEM)

                _uiState.update { current ->
                    current.copy(
                        activeProfile = profile,
                        libraryRootId = rootId,
                        libraryRootName = rootName,
                        motionPreference = motion
                    )
                }
            }
        }
    }

    private fun loadInitialSettings() {
        viewModelScope.launch {
            val subSummary = runCatching { appSettings.fetchSubtitleStyle().summaryLabel() }
                .getOrDefault("")
            val afr = runCatching { appSettings.fetchAutoFrameRate() }.getOrDefault(false)
            val hdr = runCatching { appSettings.fetchMatchDynamicRange() }.getOrDefault(false)
            val predBuffer = runCatching { appSettings.fetchPredictiveBuffering() }.getOrDefault(false)
            val smartDl = runCatching { appSettings.fetchSmartDownloads() }.getOrDefault(true)

            _uiState.update { current ->
                current.copy(
                    subtitleSummary = subSummary,
                    isMalLoggedIn = malSessionManager.isLoggedIn(),
                    malUsername = malSessionManager.getUsername(),
                    isMalSyncEnabled = malSessionManager.isSyncEnabled(),
                    isAutoSkipEnabled = malSessionManager.isAutoSkipEnabled(),
                    isGesturesEnabled = malSessionManager.isGesturesEnabled(),
                    isAutoFrameRateEnabled = afr,
                    isMatchDynamicRangeEnabled = hdr,
                    isPredictiveBufferingEnabled = predBuffer,
                    isSmartDownloadsEnabled = smartDl
                )
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectCategory -> {
                _uiState.update { it.copy(selectedCategory = event.categoryIndex) }
            }
            is SettingsEvent.SaveFocus -> {
                _uiState.update { it.copy(lastFocusedRowId = event.rowId) }
            }
            is SettingsEvent.SetLibraryRoot -> {
                viewModelScope.launch {
                    profileManager.setLibraryRoot(event.rootId, event.rootName)
                    _uiState.update {
                        it.copy(libraryRootId = event.rootId, libraryRootName = event.rootName)
                    }
                    _effects.send(SettingsUiEffect.ShowSnackbar("Pasta raiz da biblioteca selecionada"))
                }
            }
            is SettingsEvent.SaveSubtitleStyle -> {
                viewModelScope.launch {
                    runCatching { appSettings.saveSubtitleStyle(event.style) }
                        .onSuccess {
                            _uiState.update { it.copy(subtitleSummary = event.style.summaryLabel()) }
                            _effects.send(SettingsUiEffect.ShowSnackbar("Preferências de legenda salvas"))
                        }
                }
            }
            is SettingsEvent.SaveMotionPreference -> {
                viewModelScope.launch {
                    val profileId = profileManager.getActiveProfile().id
                    runCatching { appSettings.saveMotionPreference(event.preference, profileId) }
                        .onSuccess {
                            _uiState.update { it.copy(motionPreference = event.preference) }
                            _effects.send(SettingsUiEffect.ShowSnackbar("Preferência de movimento salva"))
                        }
                        .onFailure {
                            _effects.send(SettingsUiEffect.ShowSnackbar("Não foi possível salvar a preferência"))
                        }
                }
            }
            is SettingsEvent.SetAutoSkip -> {
                malSessionManager.setAutoSkipEnabled(event.enabled)
                _uiState.update { it.copy(isAutoSkipEnabled = event.enabled) }
                viewModelScope.launch {
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Pulo de aberturas automático ativado"
                            else "Pulo de aberturas automático desativado"
                        )
                    )
                }
            }
            is SettingsEvent.SetGestures -> {
                malSessionManager.setGesturesEnabled(event.enabled)
                _uiState.update { it.copy(isGesturesEnabled = event.enabled) }
                viewModelScope.launch {
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Gestos touch ativados"
                            else "Gestos touch desativados"
                        )
                    )
                }
            }
            is SettingsEvent.SetAutoFrameRate -> {
                viewModelScope.launch {
                    appSettings.saveAutoFrameRate(event.enabled)
                    _uiState.update { it.copy(isAutoFrameRateEnabled = event.enabled) }
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Taxa de quadros automática (AFR) ativada"
                            else "Taxa de quadros automática (AFR) desativada"
                        )
                    )
                }
            }
            is SettingsEvent.SetMatchDynamicRange -> {
                viewModelScope.launch {
                    appSettings.saveMatchDynamicRange(event.enabled)
                    _uiState.update { it.copy(isMatchDynamicRangeEnabled = event.enabled) }
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Alcance dinâmico HDR automático ativado"
                            else "Alcance dinâmico HDR desativado"
                        )
                    )
                }
            }
            is SettingsEvent.SetPredictiveBuffering -> {
                viewModelScope.launch {
                    appSettings.savePredictiveBuffering(event.enabled)
                    _uiState.update { it.copy(isPredictiveBufferingEnabled = event.enabled) }
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Buffer preditivo de próximo episódio ativado"
                            else "Buffer preditivo desativado"
                        )
                    )
                }
            }
            is SettingsEvent.SetSmartDownloads -> {
                viewModelScope.launch {
                    val profileId = profileManager.getActiveProfile().id
                    appSettings.saveSmartDownloads(event.enabled, profileId)
                    _uiState.update { it.copy(isSmartDownloadsEnabled = event.enabled) }
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Downloads inteligentes ativados"
                            else "Downloads inteligentes desativados"
                        )
                    )
                }
            }
            is SettingsEvent.SetMalSync -> {
                malSessionManager.setSyncEnabled(event.enabled)
                _uiState.update { it.copy(isMalSyncEnabled = event.enabled) }
                viewModelScope.launch {
                    _effects.send(
                        SettingsUiEffect.ShowSnackbar(
                            if (event.enabled) "Sincronização com o MyAnimeList ativada"
                            else "Sincronização com o MyAnimeList pausada"
                        )
                    )
                }
            }
            is SettingsEvent.SetTheme -> {
                _uiState.update { it.copy(currentTheme = event.theme) }
            }
            is SettingsEvent.SetPlayer -> {
                _uiState.update { it.copy(currentPlayer = event.player) }
            }
            is SettingsEvent.DisconnectMal -> {
                malSessionManager.clearSession()
                _uiState.update {
                    it.copy(
                        isMalLoggedIn = false,
                        malUsername = null,
                        isMalSyncEnabled = false
                    )
                }
                viewModelScope.launch {
                    _effects.send(SettingsUiEffect.ShowSnackbar("Conta do MyAnimeList desconectada"))
                }
            }
            is SettingsEvent.ExchangeMalToken -> {
                viewModelScope.launch {
                    _effects.send(SettingsUiEffect.ShowSnackbar("Autenticando com MyAnimeList..."))
                    val res = malRepository.exchangeToken(event.code, event.verifier)
                    if (res is Resource.Success) {
                        _uiState.update {
                            it.copy(
                                isMalLoggedIn = malSessionManager.isLoggedIn(),
                                malUsername = malSessionManager.getUsername(),
                                isMalSyncEnabled = malSessionManager.isSyncEnabled()
                            )
                        }
                        _effects.send(SettingsUiEffect.ShowSnackbar("MyAnimeList conectado com sucesso!"))
                    } else {
                        _effects.send(
                            SettingsUiEffect.ShowSnackbar(
                                res.message ?: "Falha ao conectar MyAnimeList"
                            )
                        )
                    }
                }
            }
            is SettingsEvent.SaveMalCredentials -> {
                malSessionManager.saveClientCredentials(event.clientId, event.clientSecret)
                viewModelScope.launch {
                    _effects.send(SettingsUiEffect.OpenMalAuthDialog(event.clientId))
                }
            }
            is SettingsEvent.UpdateCacheSize -> {
                val sizeMb = event.sizeBytes / (1024.0 * 1024.0)
                val text = if (sizeMb < 0.1) {
                    "Cache limpo (< 100 KB)"
                } else {
                    String.format(Locale.ROOT, "%.1f MB em cache (Posters, capas e legendas)", sizeMb)
                }
                _uiState.update { it.copy(cacheSizeText = text) }
            }
            is SettingsEvent.ResetSession -> {
                viewModelScope.launch {
                    driveRepository.logoutCurrentDevice()
                    _effects.send(SettingsUiEffect.RestartApp)
                }
            }
            is SettingsEvent.DisconnectAllDriveDevices -> {
                viewModelScope.launch {
                    driveRepository.disconnectAllDevices()
                    _effects.send(SettingsUiEffect.RestartApp)
                }
            }
        }
    }

    suspend fun getDriveFolderPage(parentId: String?, pageToken: String?): Resource<FilesResponse> {
        return driveRepository.getFiles(
            query = LibraryRootFolderQuery.forParent(parentId),
            pageToken = pageToken,
            pageSize = 100
        )
    }

    suspend fun exportBackupJson(): String {
        return backupSerializer.exportBackupJson()
    }

    suspend fun importBackupJson(json: String, mode: BackupSerializer.ImportMode): BackupSerializer.ImportResult {
        return backupSerializer.importBackupJson(json, mode)
    }

    fun getMalClientId(): String = malSessionManager.getClientId()
    fun getMalClientSecret(): String = malSessionManager.getClientSecret()
    fun getAvatarDrawableRes(avatarResName: String): Int = profileManager.getAvatarDrawableRes(avatarResName)
    fun getActiveProfileId(): String = profileManager.getActiveProfile().id
    fun getActiveProfile() = profileManager.getActiveProfile()
}
