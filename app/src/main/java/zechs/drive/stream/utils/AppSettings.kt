package zechs.drive.stream.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import zechs.drive.stream.utils.util.Converter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext appContext: Context
) {

    companion object {
        private val Context.appSettingsDataStore by preferencesDataStore(
            "APP_SETTINGS"
        )
        const val TAG = "AppSettings"
        const val APP_THEME = "APP_THEME"
        const val VIDEO_PLAYER = "VIDEO_PLAYER"
        const val LAST_UPDATED = "LAST_UPDATED"
    }

    private val sessionStore = appContext.appSettingsDataStore

    suspend fun saveTheme(theme: AppTheme) {
        val dataStoreKey = stringPreferencesKey(APP_THEME)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = theme.text
        }
        Log.d(TAG, "saveTheme: ${theme.text}")
    }

    suspend fun fetchTheme(): AppTheme {
        return try {
            val dataStoreKey = stringPreferencesKey(APP_THEME)
            val preferences = sessionStore.data.first()
            val themeText = preferences[dataStoreKey]
            val appTheme = AppTheme.fromText(themeText)
            Log.d(TAG, "fetchTheme: $appTheme")
            appTheme
        } catch (e: Exception) {
            Log.e(TAG, "fetchTheme error, fallback to Kodi Estuary", e)
            AppTheme.KODI_ESTUARY
        }
    }

    suspend fun savePlayer(player: VideoPlayer) {
        val dataStoreKey = stringPreferencesKey(VIDEO_PLAYER)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = player.text
        }
        Log.d(TAG, "savePlayer: ${player.text}")
    }

    suspend fun fetchPlayer(): VideoPlayer {
        val dataStoreKey = stringPreferencesKey(VIDEO_PLAYER)
        val preferences = sessionStore.data.first()
        val videoPlayer = when (preferences[dataStoreKey]) {
            VideoPlayer.MPV.text -> VideoPlayer.MPV
            else -> VideoPlayer.EXO_PLAYER
        }
        Log.d(TAG, "fetchPlayer: $videoPlayer")
        return videoPlayer
    }

    suspend fun saveSubtitleSize(sizeSp: Float) {
        val dataStoreKey = androidx.datastore.preferences.core.floatPreferencesKey("SUBTITLE_SIZE")
        sessionStore.edit { settings ->
            settings[dataStoreKey] = sizeSp
        }
        Log.d(TAG, "saveSubtitleSize: $sizeSp")
    }

    suspend fun fetchSubtitleSize(): Float {
        return try {
            val dataStoreKey = androidx.datastore.preferences.core.floatPreferencesKey("SUBTITLE_SIZE")
            val preferences = sessionStore.data.first()
            preferences[dataStoreKey] ?: 20f
        } catch (e: Exception) {
            20f
        }
    }

    suspend fun saveLastUpdated() {
        val dataStoreKey = stringPreferencesKey(LAST_UPDATED)
        val lastUpdated = System.currentTimeMillis()
        sessionStore.edit { settings ->
            settings[dataStoreKey] = lastUpdated.toString()
        }
        Log.d(TAG, "saveLastUpdated: ${Converter.fromTimeInMills(lastUpdated)}")
    }

    suspend fun fetchLastUpdated(): String? {
        val dataStoreKey = stringPreferencesKey(LAST_UPDATED)
        val preferences = sessionStore.data.first()
        val lastUpdated = preferences[dataStoreKey] ?: return null
        val parsed = Converter.fromTimeInMills(lastUpdated.toLong())
        Log.d(TAG, "fetchLastUpdated: $parsed")
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
        subtitle = "Equilibrado & Glassmorphism (Padrão)",
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
        bgBaseHex = "#0A1C2A",
        bgSurfaceHex = "#1A2123",
        accentPrimaryHex = "#12A0C7",
        accentSecondaryHex = "#147995"
    );

    companion object {
        fun fromValue(value: Int): AppTheme = entries.find { it.value == value } ?: KODI_ESTUARY
        fun fromText(text: String?): AppTheme = entries.find { it.text.equals(text, ignoreCase = true) } ?: KODI_ESTUARY
    }
}

enum class VideoPlayer(
    val text: String,
    val value: Int
) {
    EXO_PLAYER("ExoPlayer", 0),
    MPV("MPV", 1),

}