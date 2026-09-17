package zechs.drive.stream.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.data.model.ChecksumAsset
import zechs.drive.stream.data.model.ReleaseAsset
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
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
                .apply {
                    val token = BuildConfig.GITHUB_API_TOKEN.trim()
                    if (token.isNotBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
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
     * Downloads the ".sha256" checksum asset published alongside an APK release
     * asset, if the release workflow generated one. Returns null (not a failure)
     * when no checksum asset exists, e.g. for releases published before this
     * check was added — callers should treat null as "unverifiable", not
     * "invalid".
     */
    suspend fun fetchExpectedChecksum(checksumAsset: ChecksumAsset): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(checksumAsset.browserDownloadUrl)
                    .header("Accept", "application/octet-stream")
                    .apply {
                        val token = BuildConfig.GITHUB_API_TOKEN.trim()
                        if (token.isNotBlank()) {
                            header("Authorization", "Bearer $token")
                        }
                    }
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                // sha256sum output format is "<hex digest>  <filename>"
                response.body?.string()
                    ?.trim()
                    ?.substringBefore(' ')
                    ?.takeIf { it.length == 64 }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch checksum asset, skipping verification", e)
                null
            }
        }

    /**
     * Verifies [apkFile] against [expectedSha256Hex]. Computed locally so the
     * downloaded APK is never trusted purely on the strength of an HTTPS
     * transport; this catches a compromised release asset or a corrupted
     * download that HTTPS alone would not.
     */
    suspend fun verifyChecksum(apkFile: File, expectedSha256Hex: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                apkFile.inputStream().use { input ->
                    val buffer = ByteArray(32768)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                val matches = actual.equals(expectedSha256Hex.trim(), ignoreCase = true)
                if (!matches) {
                    Log.e(TAG, "APK checksum mismatch: expected=$expectedSha256Hex actual=$actual")
                }
                matches
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute APK checksum", e)
                false
            }
        }

    /**
     * Triggers the Android package installer to install the downloaded APK.
     * Uses FileProvider to expose content:// URI with read permission.
     * If permission to install unknown apps is missing on Android 8+, redirects to Settings.
     */
    fun installApk(apkFile: File, activity: Activity? = null): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "APK file does not exist or is empty: ${apkFile.absolutePath}")
                return false
            }

            // On Android 8.0+ (Oreo), verify if app can request package installs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "Permission to install unknown apps not granted, redirecting to Settings")
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        if (activity == null) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    if (activity != null) {
                        activity.startActivity(settingsIntent)
                    } else {
                        context.startActivity(settingsIntent)
                    }
                    return false
                }
            }

            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            Log.d(TAG, "Generated FileProvider URI: $apkUri")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (activity != null) {
                activity.startActivity(installIntent)
            } else {
                context.startActivity(installIntent)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer for ${apkFile.name}", e)
            false
        }
    }
}
