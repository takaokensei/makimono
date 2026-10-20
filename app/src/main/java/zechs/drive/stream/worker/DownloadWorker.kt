package zechs.drive.stream.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import zechs.drive.stream.R
import zechs.drive.stream.data.local.DownloadDao
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.data.remote.DriveApi
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.utils.AppLog
import java.io.File
import java.io.RandomAccessFile

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val driveApi: DriveApi,
    private val downloadDao: DownloadDao,
    private val driveRepository: DriveRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_FILE_ID = "key_file_id"
        const val KEY_FOLDER_ID = "key_folder_id"
        const val KEY_SERIES_TITLE = "key_series_title"
        const val KEY_NAME = "key_name"
        const val KEY_SEASON_NUMBER = "key_season_number"
        const val KEY_EPISODE_NUMBER = "key_episode_number"
        const val KEY_THUMBNAIL_URL = "key_thumbnail_url"
        const val KEY_PROFILE_ID = "key_profile_id"
        const val KEY_EXPECTED_SIZE = "key_expected_size"

        const val CHANNEL_ID = "downloads_channel"
        const val NOTIFICATION_ID = 40001
        private const val TAG = "DownloadWorker"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MIN_UPDATE_INTERVAL_MS = 500L
    }

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileId = inputData.getString(KEY_FILE_ID) ?: return@withContext Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return@withContext Result.failure()
        val seriesTitle = inputData.getString(KEY_SERIES_TITLE)
        val expectedSize = inputData.getLong(KEY_EXPECTED_SIZE, 0L)

        ensureChannel()

        val downloadDir = context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val safeName = name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val finalFile = File(downloadDir, "${fileId}_$safeName")
        val partFile = File(downloadDir, "${fileId}_$safeName.part")

        // 1. If final file already exists with valid content, mark completed
        if (finalFile.exists() && finalFile.length() > 0) {
            val len = finalFile.length()
            downloadDao.updateProgress(fileId, len, len, DownloadStatus.COMPLETED.name)
            downloadDao.updateStatus(fileId, DownloadStatus.COMPLETED.name, completedAt = System.currentTimeMillis())
            AppLog.i(TAG, "File already downloaded: ${finalFile.absolutePath}")
            return@withContext Result.success()
        }

        // 2. Storage capacity check
        try {
            val stat = StatFs(downloadDir.path)
            val available = stat.availableBytes
            if (expectedSize > 0 && available < (expectedSize + 100L * 1024 * 1024)) {
                val err = context.getString(R.string.download_insufficient_storage)
                downloadDao.updateStatus(fileId, DownloadStatus.FAILED.name, errorMessage = err)
                AppLog.w(TAG, "Insufficient storage for $name: available=$available, needed=$expectedSize")
                return@withContext Result.failure()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Could not verify StatFs: ${e.message}")
        }

        // 3. Mark DOWNLOADING in DB & register foreground
        downloadDao.updateStatus(fileId, DownloadStatus.DOWNLOADING.name)
        val initialInfo = createForegroundInfo(0, seriesTitle ?: name, context.getString(R.string.download_status_downloading, 0))
        setForeground(initialInfo)

        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        try {
            executeDownloadWithToken(
                fileId = fileId,
                name = name,
                seriesTitle = seriesTitle,
                partFile = partFile,
                finalFile = finalFile,
                initialExistingBytes = existingBytes,
                expectedSize = expectedSize
            )
        } catch (e: Exception) {
            if (e is CancellationException || isStopped) {
                AppLog.d(TAG, "Download cancelled for $name")
                downloadDao.updateStatus(fileId, DownloadStatus.PAUSED.name)
                return@withContext Result.failure()
            }
            AppLog.e(TAG, "Error downloading $name: ${e.message}", e)
            downloadDao.updateStatus(fileId, DownloadStatus.FAILED.name, errorMessage = e.message)
            return@withContext Result.failure()
        }
    }

    private suspend fun executeDownloadWithToken(
        fileId: String,
        name: String,
        seriesTitle: String?,
        partFile: File,
        finalFile: File,
        initialExistingBytes: Long,
        expectedSize: Long
    ): Result {
        var existingBytes = initialExistingBytes
        val rangeHeader = if (existingBytes > 0) "bytes=$existingBytes-" else null

        var token = driveRepository.getValidAccessToken()

        if (token == null) {
            val err = "Token indisponível"
            downloadDao.updateStatus(fileId, DownloadStatus.FAILED.name, errorMessage = err)
            return Result.failure()
        }

        var response = driveApi.downloadFile("Bearer $token", fileId, rangeHeader)

        if (response.code() == 401) {
            AppLog.d(TAG, "Access token expired during download, refreshing...")
            token = driveRepository.getValidAccessToken(forceRefresh = true)
            if (token != null) {
                response = driveApi.downloadFile("Bearer $token", fileId, rangeHeader)
            }
        }

        if (response.code() == 416) {
            // Range not satisfiable: partial file is already complete or oversized
            if (partFile.exists() && partFile.length() > 0) {
                partFile.renameTo(finalFile)
                val len = finalFile.length()
                downloadDao.updateProgress(fileId, len, len, DownloadStatus.COMPLETED.name)
                downloadDao.updateStatus(fileId, DownloadStatus.COMPLETED.name, completedAt = System.currentTimeMillis())
                return Result.success()
            }
        }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            val errorMsg = "HTTP ${response.code()}: ${response.message()}"
            downloadDao.updateStatus(fileId, DownloadStatus.FAILED.name, errorMessage = errorMsg)
            return Result.failure()
        }

        val isPartial = response.code() == 206
        if (!isPartial && existingBytes > 0) {
            // Server did not support Range, starting fresh
            partFile.delete()
            existingBytes = 0L
        }

        val totalBytes = if (isPartial) {
            existingBytes + body.contentLength()
        } else {
            val cl = body.contentLength()
            if (cl > 0) cl else expectedSize
        }

        var downloadedBytes = existingBytes
        var lastUpdateMs = System.currentTimeMillis()
        var lastReportedPercent = -1

        val raf = RandomAccessFile(partFile, "rw")
        raf.seek(existingBytes)

        val inputStream = body.byteStream()
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            inputStream.use { input ->
                raf.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            AppLog.d(TAG, "Download stopped by worker: $name")
                            downloadDao.updateProgress(fileId, downloadedBytes, totalBytes, DownloadStatus.PAUSED.name)
                            return Result.failure()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        val currentPercent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else 0

                        if (now - lastUpdateMs >= MIN_UPDATE_INTERVAL_MS || currentPercent != lastReportedPercent) {
                            lastUpdateMs = now
                            lastReportedPercent = currentPercent
                            downloadDao.updateProgress(fileId, downloadedBytes, totalBytes, DownloadStatus.DOWNLOADING.name)
                            val statusText = context.getString(R.string.download_status_downloading, currentPercent)
                            setForeground(createForegroundInfo(currentPercent, seriesTitle ?: name, statusText))
                        }
                    }
                }
            }

            // Finished reading
            if (partFile.exists() && partFile.length() > 0) {
                if (finalFile.exists()) finalFile.delete()
                val renamed = partFile.renameTo(finalFile)
                if (renamed) {
                    val finalLen = finalFile.length()
                    downloadDao.updateProgress(fileId, finalLen, finalLen, DownloadStatus.COMPLETED.name)
                    downloadDao.updateStatus(fileId, DownloadStatus.COMPLETED.name, completedAt = System.currentTimeMillis())
                    AppLog.i(TAG, "Download completed successfully: ${finalFile.absolutePath} ($finalLen bytes)")
                    notifyDownloadCompleted(seriesTitle ?: name, name)
                    return Result.success()
                } else {
                    AppLog.e(TAG, "Failed to rename .part to finalFile")
                    downloadDao.updateStatus(fileId, DownloadStatus.FAILED.name, errorMessage = "Falha ao salvar arquivo")
                    return Result.failure()
                }
            } else {
                downloadDao.updateStatus(fileId, DownloadStatus.FAILED.name, errorMessage = "Arquivo vazio")
                return Result.failure()
            }
        } catch (e: Exception) {
            if (e is CancellationException || isStopped) {
                downloadDao.updateProgress(fileId, downloadedBytes, totalBytes, DownloadStatus.PAUSED.name)
                return Result.failure()
            }
            throw e
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.download_channel_name)
            val description = context.getString(R.string.download_channel_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                this.description = description
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(
        progress: Int,
        title: String,
        statusText: String
    ): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_download_24)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .setColor(ContextCompat.getColor(context, R.color.obsidian_accent))
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyDownloadCompleted(seriesTitle: String, episodeName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(seriesTitle)
            .setContentText(context.getString(R.string.download_status_completed) + " • " + episodeName)
            .setSmallIcon(R.drawable.ic_download_done_24)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.obsidian_accent))
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}
