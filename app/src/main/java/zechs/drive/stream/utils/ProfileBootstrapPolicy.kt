package zechs.drive.stream.utils

import zechs.drive.stream.data.model.UserProfile

/** Fresh installs have no persisted profiles. The pending identity never enters Room. */
object ProfileBootstrapPolicy {
    private const val LEGACY_PRIMARY_ID = "profile-1"
    private const val LEGACY_SECONDARY_ID = "profile-2"
    private const val LEGACY_PRIMARY_NAME = "Perfil principal"
    private const val LEGACY_SECONDARY_NAME = "Perfil secundário"

    fun pendingProfile() = UserProfile(
        id = "profile-pending",
        name = "",
        avatarResName = "profile_placeholder"
    )

    fun firstProfile(profile: UserProfile, pending: UserProfile): UserProfile = profile.copy(
        name = profile.name.trim(),
        isDefault = true,
        isAdmin = true,
        libraryRootId = profile.libraryRootId ?: pending.libraryRootId,
        libraryRootName = profile.libraryRootName ?: pending.libraryRootName
    )

    /** Identifies only the untouched pair seeded by older Makimono releases. */
    fun isLegacyStarterPair(profiles: List<UserProfile>): Boolean {
        if (profiles.size != 2) return false
        val primary = profiles.firstOrNull { it.id == LEGACY_PRIMARY_ID } ?: return false
        val secondary = profiles.firstOrNull { it.id == LEGACY_SECONDARY_ID } ?: return false
        return primary.name == LEGACY_PRIMARY_NAME && primary.avatarResName == "avatar_anime" &&
            primary.isDefault && primary.isAdmin && !primary.isKids &&
            secondary.name == LEGACY_SECONDARY_NAME && secondary.avatarResName == "avatar_anime" &&
            !secondary.isDefault && !secondary.isAdmin && secondary.libraryRootId == null &&
            secondary.avatarUrl == null && secondary.backgroundUrl == null && !secondary.isKids &&
            primary.createdAt == secondary.createdAt
    }
}
