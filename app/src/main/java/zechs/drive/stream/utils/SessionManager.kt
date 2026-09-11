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
    @ApplicationContext appContext: Context,
    private val gson: Gson
) {

    private val sessionStore = appContext.sessionDataStore

    suspend fun saveClient(client: DriveClient) {
        val dataStoreKey = stringPreferencesKey(DRIVE_CLIENT)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = gson.toJson(client)
        }
        Log.d(TAG, "saveClient: $client")
    }

    suspend fun fetchClient(): DriveClient? {
        val dataStoreKey = stringPreferencesKey(DRIVE_CLIENT)
        val preferences = sessionStore.data.first()
        val value = preferences[dataStoreKey]
        val client: DriveClient? = value?.let {
            val type = object : TypeToken<DriveClient?>() {}.type
            gson.fromJson(value, type)
        }
        Log.d(TAG, "fetchClient: found=${client != null}")
        // Bugfix: this used to discard whatever was actually saved and always
        // return the bundled DEFAULT_CLIENT, which silently ignored anything the
        // user configured on the sign-in screen. Fall back to the (now empty)
        // default only when nothing has been saved yet.
        return client ?: zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT
    }

    suspend fun saveAccessToken(data: TokenResponse) {
        val dataStoreKey = stringPreferencesKey(ACCESS_TOKEN)
        val currentTimeInSeconds = System.currentTimeMillis() / 1000
        val newData = data.copy(
            expiresIn = currentTimeInSeconds + data.expiresIn
        )
        sessionStore.edit { settings ->
            settings[dataStoreKey] = gson.toJson(newData)
        }
        Log.d(TAG, "saveAccessToken: expiresIn=${newData.expiresIn}")
    }

    suspend fun fetchAccessToken(): TokenResponse? {
        val dataStoreKey = stringPreferencesKey(ACCESS_TOKEN)
        val preferences = sessionStore.data.first()
        val value = preferences[dataStoreKey]
        val login: TokenResponse? = value?.let {
            val type = object : TypeToken<TokenResponse?>() {}.type
            gson.fromJson(value, type)
        }
        Log.d(TAG, "fetchAccessToken: found=${login != null}")
        return login
    }

    suspend fun saveRefreshToken(refreshToken: String) {
        val dataStoreKey = stringPreferencesKey(REFRESH_TOKEN)
        sessionStore.edit { settings ->
            settings[dataStoreKey] = refreshToken
        }
        Log.d(TAG, "saveRefreshToken: stored")
    }

    suspend fun fetchRefreshToken(): String? {
        val dataStoreKey = stringPreferencesKey(REFRESH_TOKEN)
        val preferences = sessionStore.data.first()
        val value = preferences[dataStoreKey]
        Log.d(TAG, "fetchRefreshToken: found=${!value.isNullOrEmpty()}")
        // Bugfix: same issue as fetchClient() - previously always returned the
        // bundled DEFAULT_REFRESH_TOKEN and ignored the token actually saved
        // after sign-in, which meant every install shared one Drive account.
        return value?.ifEmpty { null }
            ?: zechs.drive.stream.utils.util.Constants.DEFAULT_REFRESH_TOKEN.ifEmpty { null }
    }

    suspend fun resetDataStore() {
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