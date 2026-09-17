package zechs.drive.stream

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.MalTokenResponse

/**
 * Tests for MalSessionManager using a fake in-memory SharedPreferences.
 *
 * MalSessionManager uses EncryptedSharedPreferences in production, but the
 * constructor falls back to plain SharedPreferences if the Android Keystore is
 * unavailable (which is always the case on the JVM). We exploit this by injecting
 * a real in-memory SharedPreferences via MockK so we can exercise the actual
 * read/write logic without any Android framework dependency.
 *
 * Note: MalSessionManager constructor takes a Context and creates SharedPreferences
 * internally. To make it testable without Robolectric we use a MockK Context
 * that returns an in-memory SharedPreferences on getSharedPreferences().
 */
class MalSessionManagerTest {

    // Real in-memory shared prefs backed by a HashMap - no Android dependency.
    private val inMemoryPrefs: SharedPreferences = InMemorySharedPreferences()

    private lateinit var sessionManager: zechs.drive.stream.utils.MalSessionManager

    @Before
    fun setUp() {
        // Mock context: getSharedPreferences always returns our in-memory prefs.
        // MasterKey.Builder will throw on JVM -> constructor catches and falls back to
        // context.getSharedPreferences(), giving us full control over storage.
        val context: Context = mockk {
            every { getSharedPreferences(any(), any()) } returns inMemoryPrefs
            every { packageName } returns "zechs.drive.stream"
        }
        sessionManager = zechs.drive.stream.utils.MalSessionManager(context)
    }

    // saveTokens() roundtrip: access token and refresh token are persisted
    @Test
    fun saveTokens_roundtrip_accessAndRefreshTokenPersisted() {
        val token = MalTokenResponse(
            tokenType = "Bearer",
            accessToken = "test_access_token",
            refreshToken = "test_refresh_token",
            expiresIn = 2_592_000
        )
        sessionManager.saveTokens(token)
        assertEquals("test_access_token", sessionManager.getAccessToken())
        assertEquals("test_refresh_token", sessionManager.getRefreshToken())
    }

    // After saveTokens(), isLoggedIn() must return true
    @Test
    fun saveTokens_setsIsLoggedInTrue() {
        val token = MalTokenResponse(
            tokenType = "Bearer",
            accessToken = "my_token",
            refreshToken = "my_refresh",
            expiresIn = 1_000
        )
        assertFalse(sessionManager.isLoggedIn())
        sessionManager.saveTokens(token)
        assertTrue(sessionManager.isLoggedIn())
    }

    // clearSession() removes all credentials
    @Test
    fun clearSession_removesAllTokens() {
        val token = MalTokenResponse(
            tokenType = "Bearer",
            accessToken = "to_be_cleared",
            refreshToken = "to_be_cleared_refresh",
            expiresIn = 3_600
        )
        sessionManager.saveTokens(token)
        sessionManager.clearSession()
        assertNull(sessionManager.getAccessToken())
        assertNull(sessionManager.getRefreshToken())
        assertFalse(sessionManager.isLoggedIn())
    }

    // isTokenExpired(): token expiry in the past -> expired
    @Test
    fun isTokenExpired_pastExpiry_returnsTrue() {
        // Manually put an already-expired timestamp (epoch 0 is always in the past)
        inMemoryPrefs.edit().putLong("mal_expires_at", 0L).apply()
        assertTrue(sessionManager.isTokenExpired())
    }

    // isTokenExpired(): expiry far in the future -> not expired
    @Test
    fun isTokenExpired_futureExpiry_returnsFalse() {
        val farFuture = System.currentTimeMillis() + 10_000_000L
        inMemoryPrefs.edit().putLong("mal_expires_at", farFuture).apply()
        assertFalse(sessionManager.isTokenExpired())
    }

    // isSyncEnabled() defaults to true; setSyncEnabled toggles it
    @Test
    fun syncEnabled_defaultTrueAndToggleable() {
        assertTrue(sessionManager.isSyncEnabled())
        sessionManager.setSyncEnabled(false)
        assertFalse(sessionManager.isSyncEnabled())
        sessionManager.setSyncEnabled(true)
        assertTrue(sessionManager.isSyncEnabled())
    }
}

// ---------------------------------------------------------------------------
// In-memory SharedPreferences implementation for JVM unit tests.
// Only implements the subset used by MalSessionManager.
// ---------------------------------------------------------------------------
private class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = map.toMap()
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    inner class EditorImpl : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = also { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = also { pending[key] = values }
        override fun putInt(key: String, value: Int) = also { pending[key] = value }
        override fun putLong(key: String, value: Long) = also { pending[key] = value }
        override fun putFloat(key: String, value: Float) = also { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = also { pending[key] = value }
        override fun remove(key: String) = also { pending[key] = null }
        override fun clear() = also { clearAll = true }

        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) map.clear()
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }
}
