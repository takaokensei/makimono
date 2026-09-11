package zechs.drive.stream.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.model.MalTokenResponse
import zechs.drive.stream.data.model.MalUserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "mal_session_prefs",
        Context.MODE_PRIVATE
    )

    private val _isSyncEnabledFlow = MutableStateFlow(isSyncEnabled())
    val isSyncEnabledFlow = _isSyncEnabledFlow.asStateFlow()

    private val _isLoggedInFlow = MutableStateFlow(isLoggedIn())
    val isLoggedInFlow = _isLoggedInFlow.asStateFlow()

    fun saveTokens(token: MalTokenResponse) {
        val expiryMs = System.currentTimeMillis() + (token.expiresIn * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token.accessToken)
            .putString(KEY_REFRESH_TOKEN, token.refreshToken)
            .putLong(KEY_EXPIRES_AT, expiryMs)
            .apply()
        _isLoggedInFlow.value = true
    }

    fun saveUserProfile(profile: MalUserProfile) {
        prefs.edit()
            .putLong(KEY_USER_ID, profile.id)
            .putString(KEY_USERNAME, profile.name)
            .putString(KEY_USER_PICTURE, profile.picture)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun isTokenExpired(): Boolean {
        val expiresAt = getExpiresAt()
        // Consider expired if within 5 minutes of expiration
        return System.currentTimeMillis() >= (expiresAt - 300_000L)
    }

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun getUserPicture(): String? = prefs.getString(KEY_USER_PICTURE, null)

    fun isSyncEnabled(): Boolean = prefs.getBoolean(KEY_SYNC_ENABLED, true)

    fun setSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
        _isSyncEnabledFlow.value = enabled
    }

    fun isAutoSkipEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SKIP_ENABLED, true)

    fun setAutoSkipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SKIP_ENABLED, enabled).apply()
    }

    fun isGesturesEnabled(): Boolean = prefs.getBoolean(KEY_GESTURES_ENABLED, true)

    fun setGesturesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GESTURES_ENABLED, enabled).apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _isLoggedInFlow.value = false
        _isSyncEnabledFlow.value = false
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "mal_access_token"
        private const val KEY_REFRESH_TOKEN = "mal_refresh_token"
        private const val KEY_EXPIRES_AT = "mal_expires_at"
        private const val KEY_USER_ID = "mal_user_id"
        private const val KEY_USERNAME = "mal_username"
        private const val KEY_USER_PICTURE = "mal_user_picture"
        private const val KEY_SYNC_ENABLED = "mal_sync_enabled"
        private const val KEY_AUTO_SKIP_ENABLED = "auto_skip_enabled"
        private const val KEY_GESTURES_ENABLED = "gestures_enabled"
    }
}
