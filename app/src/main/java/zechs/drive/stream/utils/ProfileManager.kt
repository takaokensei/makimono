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
import kotlinx.coroutines.launch
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
    private val profileDataCleaner: Lazy<ProfileDataCleaner>
) {

    companion object {
        private const val PREFS_NAME = "makimono_profiles_prefs"
        private const val KEY_PROFILES = "profiles_list"
        private const val KEY_ACTIVE_ID = "active_profile_id"

        val DEFAULT_PROFILES = listOf(
            UserProfile(id = "profile-1", name = "Perfil principal", avatarResName = "avatar_anime", isDefault = true),
            UserProfile(id = "profile-2", name = "Perfil secundário", avatarResName = "avatar_anime", isDefault = false)
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Escopo próprio para a cascata de exclusão de dados (P0-03): sobrevive
    // à tela que disparou a exclusão e executa em IO.
    private val cleanerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profilesFlow = MutableStateFlow<List<UserProfile>>(loadProfiles())
    val profilesFlow = _profilesFlow.asStateFlow()

    private val _activeProfileFlow = MutableStateFlow<UserProfile>(loadActiveProfile())
    val activeProfileFlow = _activeProfileFlow.asStateFlow()

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
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
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

    fun addProfile(name: String, avatarResName: String): UserProfile {
        val id = UUID.randomUUID().toString().take(8)
        val newProfile = UserProfile(
            id = id,
            name = name.trim(),
            avatarResName = avatarResName,
            isDefault = false
        )
        val updated = _profilesFlow.value + newProfile
        saveProfilesInternal(updated)
        _profilesFlow.value = updated
        return newProfile
    }

    fun updateProfile(id: String, newName: String, newAvatar: String) {
        val updated = _profilesFlow.value.map {
            if (it.id == id) it.copy(name = newName.trim(), avatarResName = newAvatar) else it
        }
        saveProfilesInternal(updated)
        _profilesFlow.value = updated
        if (_activeProfileFlow.value.id == id) {
            _activeProfileFlow.value = updated.first { it.id == id }
        }
    }

    fun getLibraryRoot(): Pair<String?, String?> {
        val active = getActiveProfile()
        return active.libraryRootId to active.libraryRootName
    }

    fun setLibraryRoot(id: String?, name: String?) {
        val activeId = getActiveProfile().id
        val updated = _profilesFlow.value.map {
            if (it.id == activeId) it.copy(
                libraryRootId = id?.trim()?.takeIf { value -> value.isNotBlank() },
                libraryRootName = name?.trim()?.takeIf { value -> value.isNotBlank() }
            ) else it
        }
        saveProfilesInternal(updated)
        _profilesFlow.value = updated
        _activeProfileFlow.value = updated.first { it.id == activeId }
    }

    fun deleteProfile(id: String): Boolean {
        val current = _profilesFlow.value
        if (current.size <= 1) return false
        val updated = current.filterNot { it.id == id }
        saveProfilesInternal(updated)
        _profilesFlow.value = updated
        if (_activeProfileFlow.value.id == id) {
            setActiveProfile(updated.first().id)
        }
        // P0-03: apaga em cascata todos os dados Room do perfil (histórico,
        // favoritos, fila e pastas seguidas) para não vazar dados entre perfis.
        cleanerScope.launch {
            try {
                profileDataCleaner.get().deleteAllDataForProfile(id)
            } catch (e: Exception) {
                android.util.Log.e("ProfileManager", "Falha ao apagar dados do perfil $id", e)
            }
        }
        return true
    }

    fun restoreProfiles(profiles: List<UserProfile>) {
        if (profiles.isEmpty()) return
        saveProfilesInternal(profiles)
        _profilesFlow.value = profiles
        if (profiles.none { it.id == _activeProfileFlow.value.id }) {
            setActiveProfile(profiles.first().id)
        }
    }

    fun getAvatarDrawableRes(avatarName: String?): Int {
        return when (avatarName) {
            "avatar_caua" -> R.drawable.avatar_caua
            "avatar_anime" -> R.drawable.avatar_anime
            else -> R.drawable.avatar_anime
        }
    }
}
