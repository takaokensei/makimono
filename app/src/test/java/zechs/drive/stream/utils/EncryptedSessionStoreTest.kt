package zechs.drive.stream.utils

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptedSessionStoreTest {

    @Test
    fun `put get remove and clear persist and wipe secrets via SecretStore contract`() = runTest {
        val fakePrefs = InMemorySharedPreferences()
        val store = EncryptedSessionStore(fakePrefs, testOnly = true)

        store.put("ACCESS_TOKEN", "ya29.secret-token")
        store.put("REFRESH_TOKEN", "1//refresh-secret")
        assertEquals("ya29.secret-token", store.get("ACCESS_TOKEN"))
        assertEquals("1//refresh-secret", store.get("REFRESH_TOKEN"))

        store.remove("ACCESS_TOKEN")
        assertNull(store.get("ACCESS_TOKEN"))
        assertEquals("1//refresh-secret", store.get("REFRESH_TOKEN"))

        store.clear()
        assertNull(store.get("REFRESH_TOKEN"))
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val staged = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) staged[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                removals.forEach { target.remove(it) }
                target.putAll(staged)
            }
        }
    }
}
