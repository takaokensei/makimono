package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FolderMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: FolderMetadata): Long

    @Query("SELECT * FROM folder_metadata WHERE folderId = :folderId LIMIT 1")
    suspend fun getMetadata(folderId: String): FolderMetadata?

    @Query("SELECT * FROM folder_metadata")
    suspend fun getAllMetadata(): List<FolderMetadata>

    @Query("UPDATE folder_metadata SET lastOpened = :timestamp WHERE folderId = :folderId")
    suspend fun updateLastOpened(folderId: String, timestamp: Long)

    @Query("UPDATE folder_metadata SET posterUrl = :posterUrl WHERE folderId = :folderId")
    suspend fun updatePosterUrl(folderId: String, posterUrl: String)

}
