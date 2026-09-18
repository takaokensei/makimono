package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import zechs.drive.stream.data.model.WatchQueueItem

/**
 * FEAT-03: DAO for Watch Queue ("Assistir Depois").
 */
@Dao
interface WatchQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: WatchQueueItem): Long

    @Query("DELETE FROM watch_queue WHERE fileId = :fileId AND profileId = :profileId")
    suspend fun dequeue(fileId: String, profileId: String)

    @Query("SELECT * FROM watch_queue WHERE profileId = :profileId ORDER BY orderIndex ASC, addedAt ASC")
    fun observeQueue(profileId: String): Flow<List<WatchQueueItem>>

    @Query("SELECT * FROM watch_queue WHERE profileId = :profileId ORDER BY orderIndex ASC, addedAt ASC")
    suspend fun getQueueSync(profileId: String): List<WatchQueueItem>

    @Query("SELECT EXISTS(SELECT 1 FROM watch_queue WHERE fileId = :fileId AND profileId = :profileId)")
    suspend fun isInQueue(fileId: String, profileId: String): Boolean

    @Query("SELECT COUNT(*) FROM watch_queue WHERE profileId = :profileId")
    suspend fun getQueueCount(profileId: String): Int

    @Query("DELETE FROM watch_queue WHERE profileId = :profileId")
    suspend fun clearQueue(profileId: String)
}
