package zechs.drive.stream.ui.settings

import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.BackupSerializer
import zechs.drive.stream.utils.MotionPreference
import zechs.drive.stream.utils.SubtitleStyle
import zechs.drive.stream.utils.VideoPlayer

data class SettingsUiState(
    val selectedCategory: Int = 0,
    val activeProfile: UserProfile? = null,
    val libraryRootId: String? = null,
    val libraryRootName: String? = null,
    val currentTheme: AppTheme = AppTheme.TOKYO_NIGHT,
    val currentPlayer: VideoPlayer = VideoPlayer.EXO_PLAYER,
    val themeDisplayName: String = "",
    val playerDisplayName: String = "",
    val subtitleSummary: String = "",
    val motionPreference: MotionPreference = MotionPreference.SYSTEM,
    val cacheSizeText: String = "",
    val isMalLoggedIn: Boolean = false,
    val malUsername: String? = null,
    val isMalSyncEnabled: Boolean = false,
    val isAutoSkipEnabled: Boolean = false,
    val isGesturesEnabled: Boolean = false,
    val isAutoFrameRateEnabled: Boolean = false,
    val isMatchDynamicRangeEnabled: Boolean = false,
    val isPredictiveBufferingEnabled: Boolean = false,
    val isSmartDownloadsEnabled: Boolean = true,
    val lastFocusedRowId: Int? = null
)

sealed interface SettingsEvent {
    data class SelectCategory(val categoryIndex: Int) : SettingsEvent
    data class SaveFocus(val rowId: Int) : SettingsEvent
    data class SetLibraryRoot(val rootId: String?, val rootName: String?) : SettingsEvent
    data class SaveSubtitleStyle(val style: SubtitleStyle) : SettingsEvent
    data class SaveMotionPreference(val preference: MotionPreference) : SettingsEvent
    data class SetAutoSkip(val enabled: Boolean) : SettingsEvent
    data class SetGestures(val enabled: Boolean) : SettingsEvent
    data class SetAutoFrameRate(val enabled: Boolean) : SettingsEvent
    data class SetMatchDynamicRange(val enabled: Boolean) : SettingsEvent
    data class SetPredictiveBuffering(val enabled: Boolean) : SettingsEvent
    data class SetSmartDownloads(val enabled: Boolean) : SettingsEvent
    data class SetMalSync(val enabled: Boolean) : SettingsEvent
    data class SetTheme(val theme: AppTheme) : SettingsEvent
    data class SetPlayer(val player: VideoPlayer) : SettingsEvent
    object DisconnectMal : SettingsEvent
    data class ExchangeMalToken(val code: String, val verifier: String) : SettingsEvent
    data class SaveMalCredentials(val clientId: String, val clientSecret: String) : SettingsEvent
    data class UpdateCacheSize(val sizeBytes: Long) : SettingsEvent
    object ResetSession : SettingsEvent
    object DisconnectAllDriveDevices : SettingsEvent
}

sealed interface SettingsUiEffect {
    data class ShowSnackbar(val message: String) : SettingsUiEffect
    object RestartApp : SettingsUiEffect
    data class OpenMalAuthDialog(val clientId: String) : SettingsUiEffect
}
