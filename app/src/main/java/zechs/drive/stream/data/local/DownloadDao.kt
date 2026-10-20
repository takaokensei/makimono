package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import zechs.drive.stream.data.model.DownloadItem

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem)

    @Query("SELECT * FROM download_items WHERE fileId = :fileId")
    suspend fun getDownload(fileId: String): DownloadItem?

    @Query("SELECT * FROM download_items WHERE fileId = :fileId")
    fun getDownloadFlow(fileId: String): Flow<DownloadItem?>

    @Query("SELECT * FROM download_items WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getDownloadsForProfile(profileId: String): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE profileId = :profileId AND status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedDownloadsForProfile(profileId: String): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE folderId = :folderId AND profileId = :profileId")
    suspend fun getDownloadsForFolder(folderId: String, profileId: String): List<DownloadItem>

    @Query("SELECT * FROM download_items WHERE folderId = :folderId AND profileId = :profileId")
    fun getDownloadsForFolderFlow(folderId: String, profileId: String): Flow<List<DownloadItem>>

    @Query("UPDATE download_items SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status WHERE fileId = :fileId")
    suspend fun updateProgress(fileId: String, downloadedBytes: Long, totalBytes: Long, status: String)

    @Query("UPDATE download_items SET status = :status, completedAt = :completedAt, errorMessage = :errorMessage WHERE fileId = :fileId")
    suspend fun updateStatus(fileId: String, status: String, completedAt: Long? = null, errorMessage: String? = null)

    @Query("DELETE FROM download_items WHERE fileId = :fileId")
    suspend fun deleteDownload(fileId: String)

    @Query("SELECT * FROM download_items WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun getActiveDownloads(): List<DownloadItem>

    @Query("SELECT * FROM download_items WHERE fileId = :fileId AND status = 'COMPLETED'")
    suspend fun getCompletedDownload(fileId: String): DownloadItem?

    @Query("DELETE FROM download_items WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)

    @Query("SELECT SUM(downloadedBytes) FROM download_items WHERE status = 'COMPLETED'")
    suspend fun getTotalCompletedBytes(): Long?
}
