package zechs.drive.stream

import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.utils.EncryptedSessionStore
import zechs.drive.stream.utils.SecretStore

class EncryptedSessionStoreTest {

    private val inMemoryPrefs: SharedPreferences = InMemoryTestPreferences()
    private lateinit var store: EncryptedSessionStore

    @Before
    fun setUp() {
        store = EncryptedSessionStore(inMemoryPrefs, true)
    }

    @Test
    fun putAndGet_roundtrip_returnsPersistedSecret() = runBlocking {
        store.put("ACCESS_TOKEN", "test_access_token_123")
        assertEquals("test_access_token_123", store.get("ACCESS_TOKEN"))
    }

    @Test
    fun remove_deletesSecret() = runBlocking {
        store.put("REFRESH_TOKEN", "test_refresh_token_456")
        store.remove("REFRESH_TOKEN")
        assertNull(store.get("REFRESH_TOKEN"))
    }

    @Test
    fun clear_removesAllSecrets() = runBlocking {
        store.put("A", "1")
        store.put("B", "2")
        store.clear()
        assertNull(store.get("A"))
        assertNull(store.get("B"))
    }

    private class InMemoryTestPreferences : SharedPreferences {
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

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pending[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) {
                    map.clear()
                    clearAll = false
                }
                for ((k, v) in pending) {
                    if (v == null) {
                        map.remove(k)
                    } else {
                        map[k] = v
                    }
                }
                pending.clear()
            }
        }
    }
}
