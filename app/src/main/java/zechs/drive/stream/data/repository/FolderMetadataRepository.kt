package zechs.drive.stream.data.repository

import zechs.drive.stream.data.local.FolderMetadata
import zechs.drive.stream.data.local.FolderMetadataDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderMetadataRepository @Inject constructor(
    private val folderMetadataDao: FolderMetadataDao
) {

    suspend fun getMetadata(folderId: String): FolderMetadata? {
        return folderMetadataDao.getMetadata(folderId)
    }

    suspend fun getAllMetadata(): List<FolderMetadata> {
        return folderMetadataDao.getAllMetadata()
    }

    suspend fun recordFolderOpened(folderId: String, folderName: String) {
        val existing = folderMetadataDao.getMetadata(folderId)
        val now = System.currentTimeMillis()
        if (existing != null) {
            folderMetadataDao.upsert(existing.copy(lastOpened = now))
        } else {
            folderMetadataDao.upsert(
                FolderMetadata(
                    folderId = folderId,
                    folderName = folderName,
                    posterUrl = null,
                    lastOpened = now
                )
            )
        }
    }

    suspend fun saveAnimeMetadata(
        folderId: String,
        folderName: String,
        metadata: zechs.drive.stream.data.remote.AnimeMetadata
    ) {
        val existing = folderMetadataDao.getMetadata(folderId)
        val updated = FolderMetadata(
            folderId = folderId,
            folderName = folderName,
            posterUrl = metadata.posterUrl ?: existing?.posterUrl,
            bannerUrl = metadata.bannerUrl ?: existing?.bannerUrl,
            lastOpened = existing?.lastOpened ?: 0L,
            isMovie = metadata.isMovie || (existing?.isMovie == true),
            mediaType = metadata.mediaType ?: existing?.mediaType,
            dominantColor = metadata.dominantColor ?: existing?.dominantColor,
            titleRomaji = metadata.titleRomaji ?: existing?.titleRomaji ?: folderName,
            titleEnglish = metadata.titleEnglish ?: existing?.titleEnglish,
            titleJapanese = metadata.titleNative ?: existing?.titleJapanese,
            synopsis = metadata.synopsis ?: existing?.synopsis,
            score = metadata.score ?: existing?.score,
            year = metadata.year ?: existing?.year,
            episodeCount = metadata.episodeCount ?: existing?.episodeCount,
            genres = if (metadata.genres.isNotEmpty()) metadata.genres.joinToString(", ") else existing?.genres,
            ageRating = existing?.ageRating,
            clearLogoUrl = metadata.clearLogoUrl ?: existing?.clearLogoUrl
        )
        folderMetadataDao.upsert(updated)
    }

    suspend fun updatePosterUrl(
        folderId: String,
        folderName: String,
        posterUrl: String,
        isMovie: Boolean = false,
        mediaType: String? = null
    ) {
        updateMetadataDetails(folderId, folderName, posterUrl, isMovie, mediaType)
    }

    suspend fun updateMetadataDetails(
        folderId: String,
        folderName: String,
        posterUrl: String,
        isMovie: Boolean = false,
        mediaType: String? = null,
        bannerUrl: String? = null,
        synopsis: String? = null,
        titleJapanese: String? = null,
        titleEnglish: String? = null,
        titleRomaji: String? = null,
        genres: String? = null,
        year: Int? = null,
        score: Float? = null,
        dominantColor: String? = null,
        clearLogoUrl: String? = null
    ) {
        val existing = folderMetadataDao.getMetadata(folderId)
        if (existing != null) {
            folderMetadataDao.upsert(
                existing.copy(
                    posterUrl = posterUrl.ifBlank { existing.posterUrl ?: "" },
                    isMovie = isMovie || existing.isMovie,
                    mediaType = mediaType ?: existing.mediaType,
                    bannerUrl = bannerUrl ?: existing.bannerUrl,
                    synopsis = synopsis ?: existing.synopsis,
                    titleJapanese = titleJapanese ?: existing.titleJapanese,
                    titleEnglish = titleEnglish ?: existing.titleEnglish,
                    titleRomaji = titleRomaji ?: existing.titleRomaji,
                    genres = genres ?: existing.genres,
                    year = year ?: existing.year,
                    score = score ?: existing.score,
                    dominantColor = dominantColor ?: existing.dominantColor,
                    clearLogoUrl = clearLogoUrl ?: existing.clearLogoUrl
                )
            )
        } else {
            folderMetadataDao.upsert(
                FolderMetadata(
                    folderId = folderId,
                    folderName = folderName,
                    posterUrl = posterUrl,
                    bannerUrl = bannerUrl,
                    dominantColor = dominantColor,
                    titleRomaji = titleRomaji,
                    titleEnglish = titleEnglish,
                    titleJapanese = titleJapanese,
                    synopsis = synopsis,
                    score = score,
                    year = year,
                    genres = genres,
                    clearLogoUrl = clearLogoUrl,
                    lastOpened = 0L,
                    isMovie = isMovie,
                    mediaType = mediaType
                )
            )
        }
    }
}
