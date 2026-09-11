package zechs.drive.stream.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import zechs.drive.stream.data.model.ReleaseAsset
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "AppUpdateManager"
    }

    /**
     * Downloads an APK asset with real-time byte progress reporting.
     * Saves the downloaded APK into internal cacheDir/updates.
     */
    suspend fun downloadApk(
        asset: ReleaseAsset,
        onProgress: (progress: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading APK asset: ${asset.name} from ${asset.browserDownloadUrl}")
            val request = Request.Builder()
                .url(asset.browserDownloadUrl)
                .header("Accept", "application/octet-stream")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP error ${response.code}: ${response.message}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                IOException("Corpo da resposta vazio ao baixar APK")
            )

            val contentLength = body.contentLength()
            val updateDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }

            // Clean up any previously downloaded apks
            updateDir.listFiles()?.forEach { file ->
                if (file.extension.equals("apk", ignoreCase = true)) {
                    file.delete()
                }
            }

            val targetFile = File(updateDir, asset.name.ifEmpty { "makimono-update.apk" })

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, totalRead, contentLength)
                            }
                        }
                    }
                    output.flush()
                }
            }

            Log.d(TAG, "Download finished successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update APK", e)
            Result.failure(e)
        }
    }

    /**
     * Triggers the Android package installer to install the downloaded APK.
     * Uses FileProvider to expose content:// URI with read permission.
     */
    fun installApk(apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "APK file does not exist or is empty: ${apkFile.absolutePath}")
                return false
            }

            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            Log.d(TAG, "Generated FileProvider URI: $apkUri")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer for ${apkFile.name}", e)
            false
        }
    }
}
