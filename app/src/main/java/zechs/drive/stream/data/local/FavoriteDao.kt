package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * SEC-03 / DATA-01: DAO for local favorites.
 */
@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: FavoriteFolder)

    @Query("DELETE FROM favorite_folder WHERE folderId = :folderId")
    suspend fun removeFavorite(folderId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_folder WHERE folderId = :folderId AND isFavorite = 1)")
    suspend fun isFavorite(folderId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_folder WHERE folderId = :folderId AND isFavorite = 1)")
    fun observeIsFavorite(folderId: String): Flow<Boolean>

    @Query("SELECT folderId FROM favorite_folder WHERE isFavorite = 1")
    fun observeAllFavoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM favorite_folder WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteFolder>>

    @Query("SELECT * FROM favorite_folder WHERE isFavorite = 1 ORDER BY addedAt DESC")
    suspend fun getFavoritesSync(): List<FavoriteFolder>
}
