package zechs.drive.stream.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import zechs.drive.stream.utils.ThumbnailUrl

@Keep
@Entity(
    tableName = "watch_list",
    indices = [
        Index(value = ["profileId", "videoId"])
    ]
)
data class WatchList(
    val name: String,
    val videoId: String,
    val watchedDuration: Long,
    val totalDuration: Long,
    val thumbnailLink: String? = null,
    val profileId: String = "",
    @PrimaryKey(autoGenerate = true) val id: Int? = null
) {

    fun watchProgress(): Int =
        zechs.drive.stream.utils.PlaybackProgressPolicy.calculateProgress(watchedDuration, totalDuration)

    /*
     * If video is watched at least 95% (or within end window), it is marked finished.
     */
    fun hasFinished(): Boolean =
        zechs.drive.stream.utils.PlaybackProgressPolicy.isFinished(watchedDuration, totalDuration)
}

/** Upgrade old low-resolution Drive thumbnails before loading them on TV. */
val WatchList.thumbnailLarge: String?
    get() = ThumbnailUrl.large(thumbnailLink)
