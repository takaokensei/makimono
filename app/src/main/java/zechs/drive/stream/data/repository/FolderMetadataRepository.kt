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
        val isSameEntity = existing != null && (
            existing.folderId == folderId &&
            (metadata.titleRomaji == null || existing.titleRomaji == null ||
             zechs.drive.stream.data.remote.AnimePosterResolver.calculateSimilarity(metadata.titleRomaji, existing.titleRomaji) >= 0.50)
        ) && (metadata.isMovie == existing.isMovie)

        val updated = FolderMetadata(
            folderId = folderId,
            folderName = folderName,
            posterUrl = metadata.posterUrl ?: (if (isSameEntity) existing?.posterUrl else null),
            bannerUrl = metadata.bannerUrl ?: (if (isSameEntity) existing?.bannerUrl else null),
            lastOpened = existing?.lastOpened ?: 0L,
            isMovie = metadata.isMovie,
            mediaType = metadata.mediaType ?: (if (isSameEntity) existing?.mediaType else null),
            dominantColor = metadata.dominantColor ?: (if (isSameEntity) existing?.dominantColor else null),
            titleRomaji = metadata.titleRomaji ?: (if (isSameEntity) existing?.titleRomaji else null) ?: folderName,
            titleJapanese = if (metadata.isMovie && !metadata.isAnime) {
                null
            } else {
                metadata.titleNative ?: (if (isSameEntity) existing?.titleJapanese else null)
            },
            synopsis = metadata.synopsis ?: (if (isSameEntity) existing?.synopsis else null),
            score = metadata.score ?: (if (isSameEntity) existing?.score else null),
            year = metadata.year ?: (if (isSameEntity) existing?.year else null),
            episodeCount = if (metadata.isMovie) {
                null
            } else {
                metadata.episodeCount ?: (if (isSameEntity) existing?.episodeCount else null)
            },
            genres = if (metadata.genres.isNotEmpty()) {
                metadata.genres.joinToString(", ")
            } else {
                if (isSameEntity) existing?.genres else null
            },
            ageRating = metadata.ageRating ?: (if (isSameEntity) existing?.ageRating else null),
            clearLogoUrl = metadata.clearLogoUrl ?: (if (isSameEntity) existing?.clearLogoUrl else null)
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

    suspend fun cleanCorruptedMetadata(animePosterResolver: zechs.drive.stream.data.remote.AnimePosterResolver) {
        val all = folderMetadataDao.getAllMetadata()
        for (meta in all) {
            val title = meta.titleRomaji ?: meta.titleEnglish
            // Purge entries with no title or with no posters, banners, or synopsis
            if (title.isNullOrBlank() || (meta.posterUrl.isNullOrBlank() && meta.bannerUrl.isNullOrBlank() && meta.synopsis.isNullOrBlank())) {
                folderMetadataDao.delete(meta.folderId)
                continue
            }
            val clean = zechs.drive.stream.data.remote.AnimePosterResolver.cleanAnimeTitle(meta.folderName)
            val confidence = zechs.drive.stream.data.remote.AnimePosterResolver.calculateSimilarity(clean, title)
            val isCorrupted = confidence < 0.50 ||
                (meta.isMovie && (meta.episodeCount ?: 0) > 1) ||
                (meta.synopsis?.contains("Tsunayoshi Sawada", ignoreCase = true) == true && !clean.contains("reborn", ignoreCase = true)) ||
                (meta.titleJapanese?.contains("REBORN", ignoreCase = true) == true && !clean.contains("reborn", ignoreCase = true)) ||
                (clean.equals("Monogatari Series", ignoreCase = true) && meta.titleRomaji?.contains("Second Season", ignoreCase = true) == true)

            if (isCorrupted) {
                zechs.drive.stream.utils.AppLog.w(
                    "FolderMetadataRepository",
                    "Detected corrupted metadata for '${meta.folderName}': was '$title' (confidence $confidence). Re-resolving..."
                )
                folderMetadataDao.delete(meta.folderId)
                val resolved = animePosterResolver.resolveMetadata(meta.folderName)
                if (resolved != null && !resolved.posterUrl.isNullOrBlank()) {
                    saveAnimeMetadata(meta.folderId, meta.folderName, resolved)
                    zechs.drive.stream.utils.AppLog.i(
                        "FolderMetadataRepository",
                        "Successfully corrected '${meta.folderName}' -> '${resolved.titleRomaji ?: resolved.titleEnglish}'"
                    )
                } else {
                    folderMetadataDao.delete(meta.folderId)
                    zechs.drive.stream.utils.AppLog.i(
                        "FolderMetadataRepository",
                        "Deleted non-media corrupt metadata for '${meta.folderName}'"
                    )
                }
            }
        }
    }

    suspend fun retainOnlyLibraryFolders(validFolderIds: Set<String>) {
        if (validFolderIds.isEmpty()) return
        folderMetadataDao.deleteExcept(validFolderIds.toList())
        zechs.drive.stream.utils.AppLog.i(
            "FolderMetadataRepository",
            "Retained only ${validFolderIds.size} folders belonging to active anime library"
        )
    }
}


