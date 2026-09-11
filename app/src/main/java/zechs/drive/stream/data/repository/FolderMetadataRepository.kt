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

    suspend fun updatePosterUrl(folderId: String, folderName: String, posterUrl: String) {
        val existing = folderMetadataDao.getMetadata(folderId)
        if (existing != null) {
            folderMetadataDao.upsert(existing.copy(posterUrl = posterUrl))
        } else {
            folderMetadataDao.upsert(
                FolderMetadata(
                    folderId = folderId,
                    folderName = folderName,
                    posterUrl = posterUrl,
                    lastOpened = 0L
                )
            )
        }
    }

}
