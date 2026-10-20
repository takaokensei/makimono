package zechs.drive.stream.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** SecretStore backed by an app-private AES-GCM payload whose key is held in Android Keystore. */
@Singleton
class EncryptedSessionStore private constructor(
    private val prefs: SharedPreferences,
    private val testOnly: Boolean,
    @Suppress("UNUSED_PARAMETER") marker: Int
) : SecretStore {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        KeystoreEncryptedPreferences(context, PREFS_NAME),
        false,
        0
    )

    /** Test-only constructor. Production callers must use the Keystore-backed constructor. */
    internal constructor(testPreferences: SharedPreferences, testOnly: Boolean = true) : this(
        testPreferences,
        testOnly,
        0
    )

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        check(testOnly || prefs is KeystoreEncryptedPreferences) { "Production session storage must use Android Keystore" }
        check(prefs.edit().putString(key, value).commit()) { "Could not persist encrypted session data" }
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        check(testOnly || prefs is KeystoreEncryptedPreferences) { "Production session storage must use Android Keystore" }
        prefs.getString(key, null)
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        check(testOnly || prefs is KeystoreEncryptedPreferences) { "Production session storage must use Android Keystore" }
        check(prefs.edit().remove(key).commit()) { "Could not update encrypted session data" }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(testOnly || prefs is KeystoreEncryptedPreferences) { "Production session storage must use Android Keystore" }
        check(prefs.edit().clear().commit()) { "Could not clear encrypted session data" }
    }

    companion object {
        private const val PREFS_NAME = "makimono_drive_session_v3"
    }
}
