package zechs.drive.stream.data.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class MalPicture(
    val medium: String? = null,
    val large: String? = null
)

@Keep
data class MalUserListStatus(
    val status: String? = null,
    val score: Int = 0,
    @Json(name = "num_episodes_watched") val numEpisodesWatched: Int = 0,
    @Json(name = "is_rewatching") val isRewatching: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@Keep
data class MalAnimeNode(
    val id: Long,
    val title: String,
    @Json(name = "main_picture") val mainPicture: MalPicture? = null,
    @Json(name = "num_episodes") val numEpisodes: Int = 0,
    val status: String? = null,
    val synopsis: String? = null,
    val mean: Double? = null,
    @Json(name = "my_list_status") val myListStatus: MalUserListStatus? = null
)

@Keep
data class MalAnimeSearchData(
    val node: MalAnimeNode
)

@Keep
data class MalAnimeSearchResponse(
    val data: List<MalAnimeSearchData> = emptyList()
)

@Keep
data class MalUserProfile(
    val id: Long,
    val name: String,
    val picture: String? = null
)
