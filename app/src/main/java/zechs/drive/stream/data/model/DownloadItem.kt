package zechs.drive.stream.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Keep
@Entity(
    tableName = "download_items",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["folderId"])
    ]
)
data class DownloadItem(
    @PrimaryKey
    val fileId: String,
    val folderId: String? = null,
    val seriesTitle: String? = null,
    val name: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val thumbnailUrl: String? = null,
    val localPath: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val profileId: String = "caua",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
) : Serializable {

    val downloadStatus: DownloadStatus
        get() = try {
            DownloadStatus.valueOf(status)
        } catch (_: Exception) {
            DownloadStatus.FAILED
        }

    val progressPercent: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else 0

    val isComplete: Boolean
        get() = downloadStatus == DownloadStatus.COMPLETED
}
