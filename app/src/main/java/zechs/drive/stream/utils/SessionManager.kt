package zechs.drive.stream.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PendingSessionAction(val action: String, val token: String)

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Suppress("UNUSED_PARAMETER") gson: Gson,
    private val secretStore: EncryptedSessionStore
) {
    constructor(appContext: Context, gson: Gson) : this(appContext, gson, EncryptedSessionStore(appContext))

    constructor(appContext: Context, gson: Gson, customSecretStore: SecretStore) : this(
        appContext,
        gson,
        customSecretStore as? EncryptedSessionStore ?: EncryptedSessionStore(appContext)
    ) {
        fallbackSecretStore = customSecretStore
    }

    private var fallbackSecretStore: SecretStore? = null
    private val activeSecretStore: SecretStore get() = fallbackSecretStore ?: secretStore
    private val sessionStore = appContext.sessionDataStore
    private val legacyEncryptedDrivePreferences = appContext.getSharedPreferences(
        LEGACY_ENCRYPTED_DRIVE_PREFERENCES,
        Context.MODE_PRIVATE
    )

    suspend fun savePairingSessionToken(token: String) {
        require(token.isNotBlank()) { "Pairing session token must not be blank" }
        migrateAwayFromLegacyGoogleCredentials()
        activeSecretStore.put(PAIRING_SESSION_TOKEN, token)
    }

    suspend fun fetchPairingSessionToken(): String? {
        migrateAwayFromLegacyGoogleCredentials()
        return activeSecretStore.get(PAIRING_SESSION_TOKEN)?.takeIf(String::isNotBlank)
    }

    suspend fun hasPairingSession(): Boolean = !fetchPairingSessionToken().isNullOrBlank()

    suspend fun resetDataStore() {
        migrateAwayFromLegacyGoogleCredentials()
        activeSecretStore.remove(PAIRING_SESSION_TOKEN)
        sessionStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(LEGACY_DRIVE_CLIENT))
            preferences.remove(stringPreferencesKey(LEGACY_ACCESS_TOKEN))
            preferences.remove(stringPreferencesKey(LEGACY_REFRESH_TOKEN))
        }
    }

    suspend fun addPendingSessionAction(action: String, token: String) {
        require(action == ACTION_LOGOUT || action == ACTION_DISCONNECT_ALL)
        if (token.isBlank()) return
        val actions = readPendingActions().toMutableList()
        val pending = PendingSessionAction(action, token)
        if (pending !in actions) actions += pending
        activeSecretStore.put(PENDING_SESSION_ACTIONS, actions.joinToString("\n") { "${it.action}:${it.token}" })
    }

    suspend fun getPendingSessionActions(): List<PendingSessionAction> = readPendingActions()

    suspend fun removePendingSessionAction(action: PendingSessionAction) {
        val remaining = readPendingActions().filterNot { it == action }
        if (remaining.isEmpty()) activeSecretStore.remove(PENDING_SESSION_ACTIONS)
        else activeSecretStore.put(PENDING_SESSION_ACTIONS, remaining.joinToString("\n") { "${it.action}:${it.token}" })
    }

    private suspend fun readPendingActions(): List<PendingSessionAction> {
        val serialized = activeSecretStore.get(PENDING_SESSION_ACTIONS).orEmpty()
        return serialized.lineSequence().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
            val action = line.substring(0, separator)
            if (action != ACTION_LOGOUT && action != ACTION_DISCONNECT_ALL) return@mapNotNull null
            PendingSessionAction(action, line.substring(separator + 1))
        }.toList()
    }

    private suspend fun migrateAwayFromLegacyGoogleCredentials() {
        if (activeSecretStore.get(MIGRATION_MARKER) == MIGRATION_COMPLETE) return
        activeSecretStore.remove(LEGACY_DRIVE_CLIENT)
        activeSecretStore.remove(LEGACY_ACCESS_TOKEN)
        activeSecretStore.remove(LEGACY_REFRESH_TOKEN)
        sessionStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(LEGACY_DRIVE_CLIENT))
            preferences.remove(stringPreferencesKey(LEGACY_ACCESS_TOKEN))
            preferences.remove(stringPreferencesKey(LEGACY_REFRESH_TOKEN))
        }
        check(legacyEncryptedDrivePreferences.edit().clear().commit()) {
            "Could not remove the retired encrypted Google Drive credentials"
        }
        activeSecretStore.put(MIGRATION_MARKER, MIGRATION_COMPLETE)
        AppLog.i(TAG, "Removed legacy Google OAuth credentials; TV sessions now use the pairing service")
    }

    companion object {
        private val Context.sessionDataStore by preferencesDataStore("DRIVE_SESSION_V2")
        const val TAG = "SessionManager"
        const val PAIRING_SESSION_TOKEN = "PAIRING_SESSION_TOKEN"
        const val PENDING_SESSION_ACTIONS = "PENDING_SESSION_ACTIONS"
        const val LEGACY_DRIVE_CLIENT = "DRIVE_CLIENT"
        const val LEGACY_ACCESS_TOKEN = "ACCESS_TOKEN"
        const val LEGACY_REFRESH_TOKEN = "REFRESH_TOKEN"
        private const val LEGACY_ENCRYPTED_DRIVE_PREFERENCES = "encrypted_drive_session"
        private const val MIGRATION_MARKER = "PAIRING_SESSION_MIGRATION_V1"
        private const val MIGRATION_COMPLETE = "complete"
        const val ACTION_LOGOUT = "logout"
        const val ACTION_DISCONNECT_ALL = "disconnect_all"
    }
}
