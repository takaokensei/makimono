package zechs.drive.stream.data.model

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class PlaylistItem(
    val fileId: String,
    val title: String,
    val thumbnailLink: String? = null
) : Serializable
