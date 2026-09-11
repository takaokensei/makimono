package zechs.drive.stream.data.model

import android.os.Build
import androidx.annotation.Keep
import com.squareup.moshi.Json
import zechs.drive.stream.BuildConfig

@Keep
data class ReleaseAsset(
    val name: String,
    @Json(name = "browser_download_url")
    val browserDownloadUrl: String,
    val size: Long = 0L
)

@Keep
data class LatestRelease(
    val name: String,
    @Json(name = "tag_name")
    val tagName: String,
    @Json(name = "html_url")
    val htmlUrl: String,
    val assets: List<ReleaseAsset> = emptyList()
) {

    fun isLatest(): Boolean {
        val cleanTag = tagName.removePrefix("v").trim()
        val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()
        return cleanTag == currentVersion
    }

    /**
     * Resolves the best matching APK asset for the current device's primary ABI.
     * Prioritizes specific architecture (e.g. armeabi-v7a on TCL TV, arm64-v8a on phone)
     * and falls back to universal APK if available.
     */
    fun getBestApkAsset(): ReleaseAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else ""

        // 1. Try matching device's primary ABI
        val abiMatch = apkAssets.firstOrNull { it.name.contains(primaryAbi, ignoreCase = true) }
        if (abiMatch != null) return abiMatch

        // 2. Try any supported ABI in preference order
        for (abi in Build.SUPPORTED_ABIS) {
            val match = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }

        // 3. Fallback to universal APK
        val universalMatch = apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        if (universalMatch != null) return universalMatch

        return apkAssets.firstOrNull()
    }

}