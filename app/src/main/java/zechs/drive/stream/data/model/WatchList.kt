package zechs.drive.stream.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import zechs.drive.stream.utils.ThumbnailUrl

@Keep
@Entity(tableName = "watch_list")
data class WatchList(
    val name: String,
    val videoId: String,
    val watchedDuration: Long,
    val totalDuration: Long,
    val thumbnailLink: String? = null,
    @PrimaryKey(autoGenerate = true) val id: Int? = null
) {

    fun watchProgress(): Int {
        if (totalDuration <= 0L) return 0
        return ((watchedDuration.toDouble() / totalDuration) * 100)
            .toInt()
            .coerceIn(0, 100)
    }

    /*
     * If video is watched at least 95% then we can say it is watched.
     */
    fun hasFinished() = watchProgress() >= 95
}

/** Upgrade old low-resolution Drive thumbnails before loading them on TV. */
val WatchList.thumbnailLarge: String?
    get() = ThumbnailUrl.large(thumbnailLink)


