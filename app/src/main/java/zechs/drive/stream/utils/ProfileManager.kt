package zechs.drive.stream.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import zechs.drive.stream.data.local.WatchListDatabase
import zechs.drive.stream.data.local.ProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import zechs.drive.stream.R
import zechs.drive.stream.data.local.ProfileDataCleaner
import zechs.drive.stream.data.model.UserProfile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDataCleaner: Lazy<ProfileDataCleaner>,
    private val database: WatchListDatabase
) {

    companion object {
        private const val PREFS_NAME = "makimono_profiles_prefs"
        private const val KEY_PROFILES = "profiles_list"
        private const val KEY_ACTIVE_ID = "active_profile_id"

        val DEFAULT_PROFILES = listOf(
            UserProfile(id = "profile-1", name = "Perfil principal", avatarResName = "avatar_anime", isDefault = true, isAdmin = true),
            UserProfile(id = "profile-2", name = "Perfil secundário", avatarResName = "avatar_anime", isDefault = false)
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Bootstrap Room off the UI thread. Writes publish only after a successful transaction;
    // legacy preferences are read only to import an empty profiles table.
    private val cleanerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profilesFlow = MutableStateFlow<List<UserProfile>>(loadProfiles())
    val profilesFlow = _profilesFlow.asStateFlow()

    private val _activeProfileFlow = MutableStateFlow<UserProfile>(loadActiveProfile())
    val activeProfileFlow = _activeProfileFlow.asStateFlow()

    private val mutationMutex = Mutex()
    private val initialized = cleanerScope.async {
        val stored = database.getProfileDao().getAll()
        val profiles = if (stored.isEmpty()) {
            val legacy = _profilesFlow.value
            database.getProfileDao().replaceAll(legacy.map(ProfileEntity::from))
            legacy
        } else stored.map { it.toProfile() }
        _activeProfileFlow.value = profiles.firstOrNull { it.id == prefs.getString(KEY_ACTIVE_ID, null) }
            ?: profiles.first()
        _profilesFlow.value = profiles
    }

    suspend fun awaitReady() { initialized.await() }

    private fun loadProfiles(): List<UserProfile> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw.isNullOrBlank()) {
            saveProfilesInternal(DEFAULT_PROFILES)
            return DEFAULT_PROFILES
        }
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        avatarResName = obj.optString("avatarResName", "avatar_anime"),
                        isDefault = obj.optBoolean("isDefault", false),
                        libraryRootId = obj.optString("libraryRootId").takeIf { it.isNotBlank() },
                        libraryRootName = obj.optString("libraryRootName").takeIf { it.isNotBlank() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        avatarUrl = obj.optString("avatarUrl").takeIf { it.startsWith("https://") },
                        backgroundUrl = obj.optString("backgroundUrl").takeIf { it.startsWith("https://") },
                        isAdmin = obj.optBoolean("isAdmin", obj.optBoolean("isDefault", false)),
                        isKids = obj.optBoolean("isKids", false)
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_PROFILES else list
        } catch (_: Exception) {
            DEFAULT_PROFILES
        }
    }

    private fun loadActiveProfile(): UserProfile {
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        val profiles = loadProfiles()
        return profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull() ?: DEFAULT_PROFILES.first()
    }

    private fun saveProfilesInternal(profiles: List<UserProfile>) {
        val jsonArray = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("avatarResName", p.avatarResName)
                put("isDefault", p.isDefault)
                put("libraryRootId", p.libraryRootId ?: "")
                put("libraryRootName", p.libraryRootName ?: "")
                put("createdAt", p.createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES, jsonArray.toString()).apply()
    }

    fun getProfiles(): List<UserProfile> = _profilesFlow.value

    fun getActiveProfile(): UserProfile = _activeProfileFlow.value

    fun setActiveProfile(id: String) {
        val profile = getProfiles().firstOrNull { it.id == id } ?: return
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
        _activeProfileFlow.value = profile
    }

    private suspend fun mutate(transform: (List<UserProfile>) -> List<UserProfile>) {
        awaitReady()
        mutationMutex.withLock {
            val updated = transform(_profilesFlow.value)
            require(updated.isNotEmpty())
            database.getProfileDao().replaceAll(updated.map(ProfileEntity::from))
            _activeProfileFlow.value = updated.firstOrNull { it.id == _activeProfileFlow.value.id }
                ?: updated.first()
            _profilesFlow.value = updated
            prefs.edit().putString(KEY_ACTIVE_ID, _activeProfileFlow.value.id).apply()
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        require(profile.name.isNotBlank())
        mutate { current ->
            if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it }
            else current + profile
        }
    }

    suspend fun addProfile(name: String, avatarResName: String): UserProfile {
        val profile = UserProfile(UUID.randomUUID().toString(), name.trim(), avatarResName)
        saveProfile(profile)
        return profile
    }

    suspend fun updateProfile(id: String, newName: String, newAvatar: String) {
        require(newName.isNotBlank())
        mutate { list -> list.map { if (it.id == id) it.copy(name = newName.trim(), avatarResName = newAvatar) else it } }
    }

    fun getLibraryRoot(): Pair<String?, String?> = getActiveProfile().let { it.libraryRootId to it.libraryRootName }

    suspend fun setLibraryRoot(id: String?, name: String?) {
        awaitReady()
        val activeId = getActiveProfile().id
        mutate { list -> list.map {
            if (it.id == activeId) it.copy(libraryRootId = id?.takeIf(String::isNotBlank),
                libraryRootName = name?.takeIf(String::isNotBlank)) else it
        } }
    }

    suspend fun deleteProfile(id: String): Boolean {
        awaitReady()
        return mutationMutex.withLock {
            val current = _profilesFlow.value
            if (current.size <= 1 || current.none { it.id == id }) return@withLock false
            val updated = current.filterNot { it.id == id }
            database.withTransaction {
                profileDataCleaner.get().deleteAllDataForProfile(id)
                database.getProfileDao().replaceAll(updated.map(ProfileEntity::from))
            }
            if (_activeProfileFlow.value.id == id) setActiveProfile(updated.first().id)
            _profilesFlow.value = updated
            true
        }
    }

    suspend fun restoreProfiles(profiles: List<UserProfile>) {
        if (profiles.isNotEmpty()) mutate { profiles }
    }

    fun getAvatarDrawableRes(avatarName: String?): Int {
        return when (avatarName) {
            "avatar_caua" -> R.drawable.avatar_caua
            "avatar_anime" -> R.drawable.avatar_anime
            else -> R.drawable.avatar_anime
        }
    }
}
