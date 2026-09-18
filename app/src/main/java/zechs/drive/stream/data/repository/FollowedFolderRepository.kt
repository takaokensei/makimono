package zechs.drive.stream.data.repository

import kotlinx.coroutines.flow.Flow
import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.local.FollowedFolderDao
import zechs.drive.stream.utils.ProfileManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FEAT-05: Repository for followed folders, partitioned by active profile.
 * The profile context is resolved at call time (not injection time) so that
 * switching profiles at runtime is reflected immediately.
 */
@Singleton
class FollowedFolderRepository @Inject constructor(
    private val followedFolderDao: FollowedFolderDao,
    private val profileManager: ProfileManager
) {
    private fun currentProfileId(): String = profileManager.getActiveProfile().id

    suspend fun follow(
        folderId: String,
        folderName: String,
        profileId: String = currentProfileId()
    ) {
        followedFolderDao.follow(
            FollowedFolder(
                profileId = profileId,
                folderId = folderId,
                folderName = folderName
            )
        )
    }

    suspend fun unfollow(
        folderId: String,
        profileId: String = currentProfileId()
    ) = followedFolderDao.unfollow(folderId, profileId)

    suspend fun isFollowed(
        folderId: String,
        profileId: String = currentProfileId()
    ): Boolean = followedFolderDao.isFollowed(folderId, profileId)

    fun observeIsFollowed(
        folderId: String,
        profileId: String = currentProfileId()
    ): Flow<Boolean> = followedFolderDao.observeIsFollowed(folderId, profileId)

    fun observeFollowed(
        profileId: String = currentProfileId()
    ): Flow<List<FollowedFolder>> = followedFolderDao.observeFollowed(profileId)

    suspend fun getFollowedSync(
        profileId: String = currentProfileId()
    ): List<FollowedFolder> = followedFolderDao.getFollowedSync(profileId)

    /** Persists the latest Drive child count after a successful check. */
    suspend fun updateLastKnownCount(
        folderId: String,
        count: Int,
        profileId: String = currentProfileId()
    ) = followedFolderDao.updateLastKnownCount(folderId, profileId, count)
}
