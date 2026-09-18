package zechs.drive.stream.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zechs.drive.stream.data.local.FavoriteDao
import zechs.drive.stream.data.local.FavoriteFolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SEC-03: Repository for local favorites.
 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao
) {
    suspend fun set(folderId: String, folderName: String = "", isFavorite: Boolean) {
        if (isFavorite) {
            favoriteDao.setFavorite(
                FavoriteFolder(
                    folderId = folderId,
                    folderName = folderName,
                    isFavorite = true,
                    addedAt = System.currentTimeMillis()
                )
            )
        } else {
            favoriteDao.removeFavorite(folderId)
        }
    }

    suspend fun isFavorite(folderId: String): Boolean = favoriteDao.isFavorite(folderId)

    fun observeIsFavorite(folderId: String): Flow<Boolean> = favoriteDao.observeIsFavorite(folderId)

    fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeAllFavoriteIds().map { it.toSet() }

    suspend fun getFavoritesSync(): List<FavoriteFolder> = favoriteDao.getFavoritesSync()

    fun getAllFavorites(): Flow<List<FavoriteFolder>> = favoriteDao.getAllFavorites()
}
