package zechs.drive.stream.data.repository

import android.content.Context
import android.os.StatFs
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.local.DownloadDao
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.SmartDownloadPolicy
import zechs.drive.stream.worker.DownloadWorker
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DownloadRepository"
    }

    data class StorageUsage(
        val makimonoBytes: Long,
        val freeBytes: Long,
        val totalBytes: Long
    ) {
        val makimonoPercent: Int
            get() = if (totalBytes > 0) ((makimonoBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

        val freePercent: Int
            get() = if (totalBytes > 0) ((freeBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
    }

    private val downloadDir: File
        get() = (context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")).also {
            if (!it.exists()) it.mkdirs()
        }

    private fun currentProfileId(): String = profileManager.getActiveProfile().id

    private fun getWorkManager(): WorkManager? = runCatching {
        WorkManager.getInstance(context)
    }.getOrNull()

    suspend fun enqueueDownload(
        fileId: String,
        folderId: String? = null,
        seriesTitle: String? = null,
        name: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        thumbnailUrl: String? = null,
        expectedSize: Long = 0L
    ) = withContext(Dispatchers.IO) {
        val profileId = currentProfileId()
        val safeName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val finalFile = File(downloadDir, "${fileId}_$safeName")

        if (finalFile.exists() && finalFile.length() > 0) {
            val len = finalFile.length()
            val existingItem = DownloadItem(
                fileId = fileId,
                folderId = folderId,
                seriesTitle = seriesTitle,
                name = name,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                thumbnailUrl = thumbnailUrl,
                localPath = finalFile.absolutePath,
                totalBytes = len,
                downloadedBytes = len,
                status = DownloadStatus.COMPLETED.name,
                profileId = profileId,
                completedAt = System.currentTimeMillis()
            )
            downloadDao.insertDownload(existingItem)
            AppLog.d(TAG, "File already completed on disk: ${finalFile.absolutePath}")
            return@withContext
        }

        val item = DownloadItem(
            fileId = fileId,
            folderId = folderId,
            seriesTitle = seriesTitle,
            name = name,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            thumbnailUrl = thumbnailUrl,
            localPath = finalFile.absolutePath,
            totalBytes = expectedSize,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED.name,
            profileId = profileId
        )
        downloadDao.insertDownload(item)

        val inputData = workDataOf(
            DownloadWorker.KEY_FILE_ID to fileId,
            DownloadWorker.KEY_FOLDER_ID to (folderId ?: ""),
            DownloadWorker.KEY_SERIES_TITLE to (seriesTitle ?: ""),
            DownloadWorker.KEY_NAME to name,
            DownloadWorker.KEY_SEASON_NUMBER to (seasonNumber ?: -1),
            DownloadWorker.KEY_EPISODE_NUMBER to (episodeNumber ?: -1),
            DownloadWorker.KEY_THUMBNAIL_URL to (thumbnailUrl ?: ""),
            DownloadWorker.KEY_PROFILE_ID to profileId,
            DownloadWorker.KEY_EXPECTED_SIZE to expectedSize
        )

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("download_$fileId")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        getWorkManager()?.enqueueUniqueWork(
            "download_$fileId",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        AppLog.i(TAG, "Enqueued download for $name ($fileId)")
    }

    suspend fun deleteDownload(fileId: String) = withContext(Dispatchers.IO) {
        getWorkManager()?.cancelUniqueWork("download_$fileId")

        val item = downloadDao.getDownload(fileId)
        if (item != null) {
            val file = File(item.localPath)
            if (file.exists()) file.delete()
            val partFile = File("${item.localPath}.part")
            if (partFile.exists()) partFile.delete()
        }
        downloadDao.deleteDownload(fileId)
        AppLog.i(TAG, "Deleted download $fileId")
    }

    suspend fun deleteAllDownloadsForProfile(profileId: String) = withContext(Dispatchers.IO) {
        val active = downloadDao.getActiveDownloads()
        active.forEach { getWorkManager()?.cancelUniqueWork("download_${it.fileId}") }

        downloadDir.listFiles()?.forEach { file ->
            try { file.delete() } catch (_: Exception) {}
        }
        downloadDao.deleteAllForProfile(profileId)
        AppLog.i(TAG, "Deleted all downloads for profile $profileId")
    }

    suspend fun isEpisodeDownloaded(fileId: String): Boolean = withContext(Dispatchers.IO) {
        downloadDao.getCompletedDownload(fileId) != null
    }

    suspend fun handleSmartDownloads(
        finishedFileId: String,
        nextEpisode: PlaylistItem?,
        folderId: String? = null,
        seriesTitle: String? = null,
        isSmartDownloadsEnabled: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val isFinishedDownloaded = isEpisodeDownloaded(finishedFileId)
        val isNextDownloaded = nextEpisode?.let { isEpisodeDownloaded(it.fileId) } ?: false

        val action = SmartDownloadPolicy.evaluate(
            isSmartDownloadsEnabled = isSmartDownloadsEnabled,
            isPlaybackFinished = true,
            currentEpisodeFileId = finishedFileId,
            isCurrentEpisodeDownloaded = isFinishedDownloaded,
            nextEpisode = nextEpisode,
            isNextEpisodeDownloaded = isNextDownloaded
        )

        when (action) {
            is SmartDownloadPolicy.Action.None -> false
            is SmartDownloadPolicy.Action.Execute -> {
                action.deleteFileId?.let { delId ->
                    deleteDownload(delId)
                    AppLog.i(TAG, "Smart Downloads: removed completed episode $delId")
                }
                action.enqueueNextEpisode?.let { next ->
                    enqueueDownload(
                        fileId = next.fileId,
                        folderId = folderId,
                        seriesTitle = seriesTitle,
                        name = next.title,
                        thumbnailUrl = next.thumbnailLink
                    )
                    AppLog.i(TAG, "Smart Downloads: enqueued next episode ${next.title} (${next.fileId})")
                }
                true
            }
        }
    }

    suspend fun getDownload(fileId: String): DownloadItem? = withContext(Dispatchers.IO) {
        downloadDao.getDownload(fileId)
    }

    fun getDownloadFlow(fileId: String): Flow<DownloadItem?> {
        return downloadDao.getDownloadFlow(fileId)
    }

    fun getDownloadsForCurrentProfile(): Flow<List<DownloadItem>> {
        val profileId = currentProfileId()
        return downloadDao.getDownloadsForProfile(profileId)
    }

    fun getDownloadsForFolderFlow(folderId: String): Flow<List<DownloadItem>> {
        val profileId = currentProfileId()
        return downloadDao.getDownloadsForFolderFlow(folderId, profileId)
    }

    fun getCompletedDownloadsForCurrentProfile(): Flow<List<DownloadItem>> {
        val profileId = currentProfileId()
        return downloadDao.getCompletedDownloadsForProfile(profileId)
    }

    suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        var makimonoBytes = 0L
        downloadDir.listFiles()?.forEach { f ->
            makimonoBytes += f.length()
        }

        var freeBytes = 0L
        var totalBytes = 0L
        try {
            val stat = StatFs(downloadDir.path)
            freeBytes = stat.availableBytes
            totalBytes = stat.totalBytes
        } catch (_: Exception) {}

        StorageUsage(
            makimonoBytes = makimonoBytes,
            freeBytes = freeBytes,
            totalBytes = totalBytes
        )
    }
}
