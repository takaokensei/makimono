package zechs.drive.stream.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * FEAT-05: A folder the user is "following" so that [EpisodeCheckWorker] can
 * periodically compare the Drive child count against the locally cached value
 * and fire a notification when new files appear.
 *
 * Composite primary key (profileId, folderId) so each profile can follow its
 * own set of folders independently.
 */
@Entity(
    tableName = "followed_folder",
    primaryKeys = ["profileId", "folderId"],
    indices = [Index(value = ["profileId"])]
)
data class FollowedFolder(
    val profileId: String,
    val folderId: String,
    /** Human-readable name shown in the notification. */
    val folderName: String,
    /** Last known child-file count — compared against Drive on each check. */
    val lastKnownCount: Int = 0,
    /** Epoch ms when the folder was first followed. */
    val followedAt: Long = System.currentTimeMillis()
)
