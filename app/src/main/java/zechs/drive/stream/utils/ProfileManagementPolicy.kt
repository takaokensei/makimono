package zechs.drive.stream.utils

import zechs.drive.stream.data.model.UserProfile

/** Protects the local profile set from losing its only administrator. */
object ProfileManagementPolicy {

    enum class DeleteBlockReason {
        PROFILE_NOT_FOUND,
        LAST_PROFILE,
        LAST_ADMINISTRATOR
    }

    fun deleteBlockReason(profileId: String, profiles: List<UserProfile>): DeleteBlockReason? {
        val target = profiles.firstOrNull { it.id == profileId }
            ?: return DeleteBlockReason.PROFILE_NOT_FOUND
        if (profiles.size <= 1) return DeleteBlockReason.LAST_PROFILE
        if (target.isAdmin && profiles.count(UserProfile::isAdmin) <= 1) {
            return DeleteBlockReason.LAST_ADMINISTRATOR
        }
        return null
    }

    fun canDemote(profileId: String, profiles: List<UserProfile>, remainsAdmin: Boolean): Boolean {
        val target = profiles.firstOrNull { it.id == profileId } ?: return false
        return remainsAdmin || !target.isAdmin || profiles.count(UserProfile::isAdmin) > 1
    }

    fun hasAdministrator(profiles: List<UserProfile>): Boolean = profiles.any(UserProfile::isAdmin)
}

class LastAdministratorRequiredException : IllegalStateException("At least one administrator profile must remain")
