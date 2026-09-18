package zechs.drive.stream.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SEC-03: Local storage for favorited folders/files.
 * Replaces Google Drive API starred PATCH requests with local Room persistence,
 * allowing full functionality with readonly OAuth scopes (`drive.readonly`).
 */
@Keep
@Entity(
    tableName = "favorite_folder",
    primaryKeys = ["profileId", "folderId"]
)
data class FavoriteFolder(
    val profileId: String = "caua",
    val folderId: String,
    val folderName: String = "",
    val isFavorite: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
