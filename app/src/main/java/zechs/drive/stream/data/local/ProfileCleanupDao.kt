package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * P0-03: remoção em cascata dos dados locais de um perfil excluído.
 * As tabelas folder_metadata e catalog_entry são globais (sem profileId)
 * e por isso não entram na cascata.
 */
@Dao
interface ProfileCleanupDao {

    @Query("DELETE FROM watch_list WHERE profileId = :profileId")
    suspend fun deleteWatchListForProfile(profileId: String): Int

    @Query("DELETE FROM favorite_folder WHERE profileId = :profileId")
    suspend fun deleteFavoritesForProfile(profileId: String): Int

    @Query("DELETE FROM watch_queue WHERE profileId = :profileId")
    suspend fun deleteWatchQueueForProfile(profileId: String): Int

    @Query("DELETE FROM followed_folder WHERE profileId = :profileId")
    suspend fun deleteFollowedFoldersForProfile(profileId: String): Int

    suspend fun deleteAllDataForProfile(profileId: String): Int =
        deleteWatchListForProfile(profileId) +
                deleteFavoritesForProfile(profileId) +
                deleteWatchQueueForProfile(profileId) +
                deleteFollowedFoldersForProfile(profileId)
}
