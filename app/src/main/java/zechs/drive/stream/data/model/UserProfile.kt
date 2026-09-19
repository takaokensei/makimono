package zechs.drive.stream.data.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String,
    val name: String,
    val avatarResName: String = "avatar_caua",
    val isDefault: Boolean = false,
    val libraryRootId: String? = null,
    val libraryRootName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
