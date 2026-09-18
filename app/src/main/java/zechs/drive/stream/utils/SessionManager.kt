package zechs.drive.stream.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import zechs.drive.stream.data.model.DriveClient
import zechs.drive.stream.data.model.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gson: Gson,
    private val secretStore: EncryptedSessionStore
) {

    // Secondary constructor for non-DI convenience
    constructor(
        appContext: Context,
        gson: Gson
    ) : this(appContext, gson, EncryptedSessionStore(appContext))

    // Test constructor allowing mock/in-memory SecretStore
    constructor(
        appContext: Context,
        gson: Gson,
        customSecretStore: SecretStore
    ) : this(
        appContext,
        gson,
        customSecretStore as? EncryptedSessionStore ?: EncryptedSessionStore(appContext)
    ) {
        this.fallbackSecretStore = customSecretStore
    }

    private var fallbackSecretStore: SecretStore? = null
    private val activeSecretStore: SecretStore get() = fallbackSecretStore ?: secretStore

    private val sessionStore = appContext.sessionDataStore

    suspend fun saveClient(client: DriveClient) {
        val serialized = gson.toJson(client)
        activeSecretStore.put(DRIVE_CLIENT, serialized)
        // Clean legacy plaintext
        val dataStoreKey = stringPreferencesKey(DRIVE_CLIENT)
        sessionStore.edit { it.remove(dataStoreKey) }
        Log.d(TAG, "saveClient: id=${client.clientId.take(6)}..., scopes=${client.scopes.joinToString()}")
    }

    suspend fun fetchClient(): DriveClient? {
        val type = object : TypeToken<DriveClient?>() {}.type
        // 1. Try secure encrypted store first
        val encryptedValue = activeSecretStore.get(DRIVE_CLIENT)
        if (!encryptedValue.isNullOrEmpty()) {
            val client: DriveClient? = gson.fromJson(encryptedValue, type)
            if (client != null) {
                Log.d(TAG, "fetchClient: found in encrypted store")
                return client
            }
        }

        // 2. Fallback to legacy DataStore (one-time migration)
        val dataStoreKey = stringPreferencesKey(DRIVE_CLIENT)
        val preferences = sessionStore.data.first()
        val legacyValue = preferences[dataStoreKey]
        val client: DriveClient? = legacyValue?.let {
            gson.fromJson(legacyValue, type)
        }

        if (client != null) {
            Log.d(TAG, "fetchClient: migrating legacy DataStore client to encrypted store")
            saveClient(client)
            return client
        }

        return zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT
    }

    suspend fun saveAccessToken(data: TokenResponse) {
        val currentTimeInSeconds = System.currentTimeMillis() / 1000
        val newData = data.copy(
            expiresIn = currentTimeInSeconds + data.expiresIn
        )
        val serialized = gson.toJson(newData)
        activeSecretStore.put(ACCESS_TOKEN, serialized)
        // Clean legacy plaintext
        val dataStoreKey = stringPreferencesKey(ACCESS_TOKEN)
        sessionStore.edit { it.remove(dataStoreKey) }
        Log.d(TAG, "saveAccessToken: expiresIn=${newData.expiresIn}")
    }

    suspend fun fetchAccessToken(): TokenResponse? {
        val type = object : TypeToken<TokenResponse?>() {}.type
        // 1. Try secure encrypted store
        val encryptedValue = activeSecretStore.get(ACCESS_TOKEN)
        if (!encryptedValue.isNullOrEmpty()) {
            val token: TokenResponse? = gson.fromJson(encryptedValue, type)
            if (token != null) return token
        }

        // 2. Fallback & migrate legacy DataStore
        val dataStoreKey = stringPreferencesKey(ACCESS_TOKEN)
        val preferences = sessionStore.data.first()
        val legacyValue = preferences[dataStoreKey]
        val token: TokenResponse? = legacyValue?.let {
            gson.fromJson(legacyValue, type)
        }

        if (token != null) {
            Log.d(TAG, "fetchAccessToken: migrating legacy DataStore access token to encrypted store")
            activeSecretStore.put(ACCESS_TOKEN, legacyValue)
            sessionStore.edit { it.remove(dataStoreKey) }
            return token
        }

        return null
    }

    suspend fun saveRefreshToken(refreshToken: String) {
        activeSecretStore.put(REFRESH_TOKEN, refreshToken)
        // Clean legacy plaintext
        val dataStoreKey = stringPreferencesKey(REFRESH_TOKEN)
        sessionStore.edit { it.remove(dataStoreKey) }
        Log.d(TAG, "saveRefreshToken: stored securely")
    }

    suspend fun fetchRefreshToken(): String? {
        // 1. Try secure encrypted store
        val encryptedValue = activeSecretStore.get(REFRESH_TOKEN)
        if (!encryptedValue.isNullOrEmpty()) {
            return encryptedValue
        }

        // 2. Fallback & migrate legacy DataStore
        val dataStoreKey = stringPreferencesKey(REFRESH_TOKEN)
        val preferences = sessionStore.data.first()
        val legacyValue = preferences[dataStoreKey]
        if (!legacyValue.isNullOrEmpty()) {
            Log.d(TAG, "fetchRefreshToken: migrating legacy DataStore refresh token to encrypted store")
            saveRefreshToken(legacyValue)
            return legacyValue
        }

        return zechs.drive.stream.utils.util.Constants.DEFAULT_REFRESH_TOKEN.ifEmpty { null }
    }

    suspend fun resetDataStore() {
        activeSecretStore.clear()
        sessionStore.edit { it.clear() }
    }

    companion object {
        private val Context.sessionDataStore by preferencesDataStore(
            "DRIVE_SESSION_V2"
        )
        const val TAG = "SessionManager"
        const val DRIVE_CLIENT = "DRIVE_CLIENT"
        const val ACCESS_TOKEN = "ACCESS_TOKEN"
        const val REFRESH_TOKEN = "REFRESH_TOKEN"
    }
}
