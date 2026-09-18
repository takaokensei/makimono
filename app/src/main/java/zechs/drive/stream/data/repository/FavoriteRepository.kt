package zechs.drive.stream.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zechs.drive.stream.data.local.FavoriteDao
import zechs.drive.stream.data.local.FavoriteFolder
import zechs.drive.stream.utils.ProfileManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SEC-03 / FEAT-01.2: Repository for local favorites, partitioned by profileId.
 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val profileManager: ProfileManager
) {
    private fun currentProfileId(): String = profileManager.getActiveProfile().id

    suspend fun set(
        folderId: String,
        folderName: String = "",
        isFavorite: Boolean,
        profileId: String = currentProfileId()
    ) {
        if (isFavorite) {
            favoriteDao.setFavorite(
                FavoriteFolder(
                    profileId = profileId,
                    folderId = folderId,
                    folderName = folderName,
                    isFavorite = true,
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            favoriteDao.removeFavorite(folderId, profileId)
        }
    }

    suspend fun isFavorite(
        folderId: String,
        profileId: String = currentProfileId()
    ): Boolean = favoriteDao.isFavorite(folderId, profileId)

    fun observeIsFavorite(
        folderId: String,
        profileId: String = currentProfileId()
    ): Flow<Boolean> = favoriteDao.observeIsFavorite(folderId, profileId)

    fun observeFavoriteIds(
        profileId: String = currentProfileId()
    ): Flow<Set<String>> =
        favoriteDao.observeAllFavoriteIds(profileId).map { it.toSet() }

    suspend fun getFavoritesSync(
        profileId: String = currentProfileId()
    ): List<FavoriteFolder> = favoriteDao.getFavoritesSync(profileId)

    fun getAllFavorites(
        profileId: String = currentProfileId()
    ): Flow<List<FavoriteFolder>> = favoriteDao.getAllFavorites(profileId)
}
