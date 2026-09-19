package zechs.drive.stream.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SEC-02: Android Keystore-backed encrypted storage for Google Drive OAuth credentials.
 * Protects access tokens, refresh tokens, and client secrets with hardware/AES-256 encryption.
 * Fails closed if Keystore-backed storage is unavailable. JVM tests must inject an
 * explicit in-memory preference implementation through the internal constructor.
 */
@Singleton
class EncryptedSessionStore private constructor(
    private val prefs: SharedPreferences
) : SecretStore {

    @Inject
    constructor(@ApplicationContext context: Context) : this(createSecurePreferences(context))

    @Suppress("UNUSED_PARAMETER")
    internal constructor(testPreferences: SharedPreferences, testOnly: Boolean = true) : this(testPreferences)

    companion object {
        private const val TAG = "EncryptedSessionStore"
        private const val PREFS_NAME = "encrypted_drive_session"

        private fun createSecurePreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create encrypted Drive session store", e)
                throw IllegalStateException(
                    "Secure credential storage is unavailable; refusing to store Drive credentials in plain text",
                    e
                )
            }
        }
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
}
