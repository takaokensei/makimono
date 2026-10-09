package zechs.drive.stream.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import com.google.gson.Gson
import zechs.drive.stream.utils.util.Converter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext appContext: Context,
    private val profileManager: ProfileManager
) {

    companion object {
        private val Context.appSettingsDataStore by preferencesDataStore(
            "APP_SETTINGS"
        )
        const val TAG = "AppSettings"
        const val APP_THEME = "APP_THEME"
        const val VIDEO_PLAYER = "VIDEO_PLAYER"
        const val LAST_UPDATED = "LAST_UPDATED"
        const val SUBTITLE_SIZE = "SUBTITLE_SIZE"
        const val SUBTITLE_STYLE = "SUBTITLE_STYLE"

        fun profileThemeKey(profileId: String) = stringPreferencesKey("profile_${profileId}_theme")
        fun profilePlayerKey(profileId: String) = stringPreferencesKey("profile_${profileId}_player")
        fun profileSubtitleKey(profileId: String) = androidx.datastore.preferences.core.floatPreferencesKey("profile_${profileId}_sub_size")
        fun profileSubtitleStyleKey(profileId: String) = stringPreferencesKey("profile_${profileId}_sub_style")
        fun profileMotionPreferenceKey(profileId: String) = stringPreferencesKey("profile_${profileId}_motion_preference")
        fun profileAutoFrameRateKey(profileId: String) = androidx.datastore.preferences.core.booleanPreferencesKey("profile_${profileId}_afr")
        fun profileMatchDynamicRangeKey(profileId: String) = androidx.datastore.preferences.core.booleanPreferencesKey("profile_${profileId}_hdr")
        fun profilePredictiveBufferingKey(profileId: String) = androidx.datastore.preferences.core.booleanPreferencesKey("profile_${profileId}_binge_buffer")
    }

    private val gson = Gson()

    private val sessionStore = appContext.appSettingsDataStore

    private fun currentProfileId(): String = profileManager.getActiveProfile().id

    suspend fun saveTheme(theme: AppTheme, profileId: String = currentProfileId()) {
        val dataStoreKey = profileThemeKey(profileId)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = theme.text
        }
        AppLog.d(TAG, "saveTheme [profile=$profileId]: ${theme.text}")
    }

    suspend fun fetchTheme(profileId: String = currentProfileId()): AppTheme {
        return try {
            val profileKey = profileThemeKey(profileId)
            val preferences = sessionStore.data.first()
            val themeText = preferences[profileKey] ?: preferences[stringPreferencesKey(APP_THEME)]
            val appTheme = themeText?.let(AppTheme::fromText) ?: AppTheme.OBSIDIAN
            AppLog.d(TAG, "fetchTheme [profile=$profileId]: $appTheme")
            appTheme
        } catch (e: Exception) {
            AppLog.e(TAG, "fetchTheme error, fallback to Obsidian", e)
            AppTheme.OBSIDIAN
        }
    }

    suspend fun savePlayer(player: VideoPlayer, profileId: String = currentProfileId()) {
        val dataStoreKey = profilePlayerKey(profileId)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = player.text
        }
        AppLog.d(TAG, "savePlayer [profile=$profileId]: ${player.text}")
    }

    suspend fun fetchPlayer(profileId: String = currentProfileId()): VideoPlayer {
        val profileKey = profilePlayerKey(profileId)
        val preferences = sessionStore.data.first()
        val playerText = preferences[profileKey] ?: preferences[stringPreferencesKey(VIDEO_PLAYER)]
        val videoPlayer = when (playerText) {
            VideoPlayer.MPV.text -> VideoPlayer.MPV
            else -> VideoPlayer.EXO_PLAYER
        }
        AppLog.d(TAG, "fetchPlayer [profile=$profileId]: $videoPlayer")
        return videoPlayer
    }

    suspend fun saveSubtitleSize(sizeSp: Float, profileId: String = currentProfileId()) {
        saveSubtitleStyle(fetchSubtitleStyle(profileId).copy(sizeSp = sizeSp), profileId)
    }

    suspend fun fetchSubtitleSize(profileId: String = currentProfileId()): Float {
        return fetchSubtitleStyle(profileId).sizeSp
    }

    suspend fun saveSubtitleStyle(style: SubtitleStyle, profileId: String = currentProfileId()) {
        val key = profileSubtitleStyleKey(profileId)
        sessionStore.edit { settings ->
            settings[key] = gson.toJson(style)
            settings[profileSubtitleKey(profileId)] = style.sizeSp
        }
        AppLog.d(TAG, "saveSubtitleStyle [profile=$profileId]: ${style.summaryLabel()}")
    }

    suspend fun fetchSubtitleStyle(profileId: String = currentProfileId()): SubtitleStyle {
        return try {
            val profileKey = profileSubtitleStyleKey(profileId)
            val preferences = sessionStore.data.first()
            val json = preferences[profileKey]
            if (!json.isNullOrBlank()) {
                gson.fromJson(json, SubtitleStyle::class.java)
            } else {
                val legacySize = preferences[profileSubtitleKey(profileId)]
                    ?: preferences[androidx.datastore.preferences.core.floatPreferencesKey(SUBTITLE_SIZE)]
                    ?: 20f
                SubtitleStyle(sizeSp = legacySize)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchSubtitleStyle fallback", e)
            SubtitleStyle()
        }
    }

    suspend fun saveMotionPreference(preference: MotionPreference, profileId: String = currentProfileId()) {
        sessionStore.edit { settings -> settings[profileMotionPreferenceKey(profileId)] = preference.name }
        if (profileId == currentProfileId()) {
            MotionPolicy.updateActiveProfilePreference(preference)
        }
        AppLog.d(TAG, "saveMotionPreference [profile=$profileId]: ${preference.name}")
    }

    suspend fun fetchMotionPreference(profileId: String = currentProfileId()): MotionPreference {
        val stored = sessionStore.data.first()[profileMotionPreferenceKey(profileId)]
        val preference = MotionPreference.entries.firstOrNull { it.name == stored } ?: MotionPreference.SYSTEM
        if (profileId == currentProfileId()) {
            MotionPolicy.updateActiveProfilePreference(preference)
        }
        return preference
    }

    suspend fun saveAutoFrameRate(enabled: Boolean, profileId: String = currentProfileId()) {
        val dataStoreKey = profileAutoFrameRateKey(profileId)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = enabled
        }
        AppLog.d(TAG, "saveAutoFrameRate [profile=$profileId]: $enabled")
    }

    suspend fun fetchAutoFrameRate(profileId: String = currentProfileId()): Boolean {
        return try {
            val profileKey = profileAutoFrameRateKey(profileId)
            val preferences = sessionStore.data.first()
            preferences[profileKey] ?: true
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchAutoFrameRate error, fallback to true", e)
            true
        }
    }

    suspend fun saveMatchDynamicRange(enabled: Boolean, profileId: String = currentProfileId()) {
        val dataStoreKey = profileMatchDynamicRangeKey(profileId)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = enabled
        }
        AppLog.d(TAG, "saveMatchDynamicRange [profile=$profileId]: $enabled")
    }

    suspend fun fetchMatchDynamicRange(profileId: String = currentProfileId()): Boolean {
        return try {
            val profileKey = profileMatchDynamicRangeKey(profileId)
            val preferences = sessionStore.data.first()
            preferences[profileKey] ?: true
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchMatchDynamicRange error, fallback to true", e)
            true
        }
    }

    suspend fun savePredictiveBuffering(enabled: Boolean, profileId: String = currentProfileId()) {
        val dataStoreKey = profilePredictiveBufferingKey(profileId)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = enabled
        }
        AppLog.d(TAG, "savePredictiveBuffering [profile=$profileId]: $enabled")
    }

    suspend fun fetchPredictiveBuffering(profileId: String = currentProfileId()): Boolean {
        return try {
            val profileKey = profilePredictiveBufferingKey(profileId)
            val preferences = sessionStore.data.first()
            preferences[profileKey] ?: true
        } catch (e: Exception) {
            AppLog.w(TAG, "fetchPredictiveBuffering error, fallback to true", e)
            true
        }
    }

    suspend fun saveLastUpdated() {
        val dataStoreKey = stringPreferencesKey(LAST_UPDATED)
        val lastUpdated = System.currentTimeMillis()
        sessionStore.edit { settings ->
            settings[dataStoreKey] = lastUpdated.toString()
        }
        AppLog.d(TAG, "saveLastUpdated: ${Converter.fromTimeInMills(lastUpdated)}")
    }

    suspend fun fetchLastUpdated(): String? {
        val dataStoreKey = stringPreferencesKey(LAST_UPDATED)
        val preferences = sessionStore.data.first()
        val lastUpdated = preferences[dataStoreKey] ?: return null
        val parsed = Converter.fromTimeInMills(lastUpdated.toLong())
        AppLog.d(TAG, "fetchLastUpdated: $parsed")
        return parsed
    }

}

enum class AppTheme(
    val text: String,
    val value: Int,
    val displayName: String,
    val subtitle: String,
    val bgBaseHex: String,
    val bgSurfaceHex: String,
    val accentPrimaryHex: String,
    val accentSecondaryHex: String
) {
    TOKYO_NIGHT(
        text = "Tokyo Night",
        value = 0,
        displayName = "Tokyo Night",
        subtitle = "Equilibrado & Glassmorphism",
        bgBaseHex = "#1A1B26",
        bgSurfaceHex = "#1F2335",
        accentPrimaryHex = "#7AA2F7",
        accentSecondaryHex = "#BB9AF7"
    ),
    DRACULA(
        text = "Dracula",
        value = 1,
        displayName = "Dracula",
        subtitle = "Alto contraste escuro vampírico",
        bgBaseHex = "#282A36",
        bgSurfaceHex = "#343746",
        accentPrimaryHex = "#BD93F9",
        accentSecondaryHex = "#FF79C6"
    ),
    NORD(
        text = "Nord",
        value = 2,
        displayName = "Nord",
        subtitle = "Minimalismo Nórdico Escandinavo",
        bgBaseHex = "#2E3440",
        bgSurfaceHex = "#3B4252",
        accentPrimaryHex = "#88C0D0",
        accentSecondaryHex = "#81A1C1"
    ),
    CATPPUCCIN_MOCHA(
        text = "Catppuccin Mocha",
        value = 3,
        displayName = "Catppuccin Mocha",
        subtitle = "Paleta suave, elegante e pastel",
        bgBaseHex = "#1E1E2E",
        bgSurfaceHex = "#313244",
        accentPrimaryHex = "#89B4FA",
        accentSecondaryHex = "#CBA6F7"
    ),
    KODI_ESTUARY(
        text = "Kodi Estuary",
        value = 4,
        displayName = "Kodi Estuary",
        subtitle = "Petróleo profundo & ciano cinematográfico",
        bgBaseHex = "#030B14",
        bgSurfaceHex = "#0B1928",
        accentPrimaryHex = "#22D3EE",
        accentSecondaryHex = "#0E597E"
    ),
    OBSIDIAN(
        text = "Obsidian",
        value = 5,
        displayName = "Obsidian",
        subtitle = "Cinema escuro & foco ciano",
        bgBaseHex = "#070B11",
        bgSurfaceHex = "#111925",
        accentPrimaryHex = "#22D3EE",
        accentSecondaryHex = "#4F8CFF"
    );

    companion object {
        fun fromValue(value: Int): AppTheme = entries.find { it.value == value } ?: OBSIDIAN
        fun fromText(text: String?): AppTheme = entries.find { it.text.equals(text, ignoreCase = true) } ?: OBSIDIAN
    }
}

enum class VideoPlayer(
    val text: String,
    val value: Int
) {
    EXO_PLAYER("ExoPlayer", 0),
    MPV("MPV", 1);

    companion object {
        fun fromText(text: String?): VideoPlayer =
            entries.find { it.text.equals(text, ignoreCase = true) } ?: EXO_PLAYER
    }
}

enum class MotionPreference { SYSTEM, REDUCED }
