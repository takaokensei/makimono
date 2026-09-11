package zechs.drive.stream.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "folder_metadata")
data class FolderMetadata(
    @PrimaryKey
    val folderId: String,
    val folderName: String,
    val posterUrl: String? = null,
    val lastOpened: Long = 0L
)
