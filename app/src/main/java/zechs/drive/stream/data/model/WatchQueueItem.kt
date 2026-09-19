package zechs.drive.stream.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FEAT-03: Watch Queue ("Assistir Depois" / Fila de Reprodução).
 * Partitioned per user profile so each profile maintains their own watch queue.
 */
@Keep
@Entity(
    tableName = "watch_queue",
    indices = [
        Index(value = ["profileId", "fileId"], unique = true)
    ]
)
data class WatchQueueItem(
    val profileId: String = "",
    val fileId: String,
    val name: String,
    val posterUrl: String? = null,
    val orderIndex: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val id: Int? = null
)
