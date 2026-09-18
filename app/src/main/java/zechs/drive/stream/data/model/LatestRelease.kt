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
data class ChecksumAsset(
    val name: String,
    @Json(name = "browser_download_url")
    val browserDownloadUrl: String
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

        val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()
        val primaryAbi = supportedAbis.firstOrNull() ?: ""

        // 1. Try matching device's primary ABI
        if (primaryAbi.isNotBlank()) {
            val abiMatch = apkAssets.firstOrNull { it.name.contains(primaryAbi, ignoreCase = true) }
            if (abiMatch != null) return abiMatch
        }

        // 2. Try any supported ABI in preference order
        for (abi in supportedAbis) {
            val match = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }

        // 3. Fallback to universal APK
        val universalMatch = apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        if (universalMatch != null) return universalMatch

        return apkAssets.firstOrNull()
    }

    /**
     * Looks for a published "<apk-name>.sha256" text asset matching [apkAsset].
     * Older releases (published before checksum generation was added to the
     * release workflow) won't have one; callers must treat a null result as
     * "verification unavailable", not as a failure.
     */
    fun findChecksumAsset(apkAsset: ReleaseAsset): ChecksumAsset? {
        val checksumName = "${apkAsset.name}.sha256"
        return assets.firstOrNull { it.name.equals(checksumName, ignoreCase = true) }
            ?.let { ChecksumAsset(it.name, it.browserDownloadUrl) }
    }

}