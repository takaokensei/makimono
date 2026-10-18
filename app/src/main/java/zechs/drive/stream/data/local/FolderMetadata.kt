package zechs.drive.stream.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import zechs.drive.stream.data.remote.AnimeMetadata

@Keep
@Entity(tableName = "folder_metadata")
data class FolderMetadata(
    @PrimaryKey
    val folderId: String,
    val folderName: String,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val dominantColor: String? = null,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val synopsis: String? = null,
    val score: Float? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val genres: String? = null,
    val ageRating: String? = null,
    val clearLogoUrl: String? = null,
    val lastOpened: Long = 0L,
    val isMovie: Boolean = false,
    val mediaType: String? = null
)

fun FolderMetadata.toAnimeMetadata(): AnimeMetadata? {
    if (posterUrl.isNullOrBlank() && bannerUrl.isNullOrBlank() && synopsis.isNullOrBlank() && titleRomaji.isNullOrBlank()) {
        return null
    }
    return AnimeMetadata(
        posterUrl = posterUrl,
        bannerUrl = bannerUrl,
        dominantColor = dominantColor,
        titleRomaji = titleRomaji ?: folderName,
        titleEnglish = titleEnglish,
        titleNative = titleJapanese,
        synopsis = synopsis,
        score = score,
        year = year,
        episodeCount = episodeCount,
        genres = genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        clearLogoUrl = clearLogoUrl,
        isMovie = isMovie,
        isAnime = mediaType != "movie" && mediaType != "tv",
        mediaType = mediaType,
        ageRating = ageRating
    )
}
