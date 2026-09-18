package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * FEAT-05: DAO for followed-folder management.
 */
@Dao
interface FollowedFolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun follow(folder: FollowedFolder)

    @Query("DELETE FROM followed_folder WHERE folderId = :folderId AND profileId = :profileId")
    suspend fun unfollow(folderId: String, profileId: String)

    @Query("SELECT * FROM followed_folder WHERE profileId = :profileId ORDER BY followedAt DESC")
    fun observeFollowed(profileId: String): Flow<List<FollowedFolder>>

    @Query("SELECT * FROM followed_folder WHERE profileId = :profileId ORDER BY followedAt DESC")
    suspend fun getFollowedSync(profileId: String): List<FollowedFolder>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_folder WHERE folderId = :folderId AND profileId = :profileId)")
    suspend fun isFollowed(folderId: String, profileId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM followed_folder WHERE folderId = :folderId AND profileId = :profileId)")
    fun observeIsFollowed(folderId: String, profileId: String): Flow<Boolean>

    /** Called by [EpisodeCheckWorker] after successfully checking a folder. */
    @Query("UPDATE followed_folder SET lastKnownCount = :count WHERE folderId = :folderId AND profileId = :profileId")
    suspend fun updateLastKnownCount(folderId: String, profileId: String, count: Int)
}
