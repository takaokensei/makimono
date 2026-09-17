package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.model.ReleaseAsset

class LatestReleaseChecksumTest {

    private val universalApk = ReleaseAsset(
        name = "app-universal-release.apk",
        browserDownloadUrl = "https://github.com/takaokensei/makimono/releases/download/v1.4.24/app-universal-release.apk",
        size = 50_000_000L
    )

    private val arm64Apk = ReleaseAsset(
        name = "app-arm64-v8a-release.apk",
        browserDownloadUrl = "https://github.com/takaokensei/makimono/releases/download/v1.4.24/app-arm64-v8a-release.apk",
        size = 35_000_000L
    )

    private val arm64Checksum = ReleaseAsset(
        name = "app-arm64-v8a-release.apk.sha256",
        browserDownloadUrl = "https://github.com/takaokensei/makimono/releases/download/v1.4.24/app-arm64-v8a-release.apk.sha256",
        size = 90L
    )

    private val universalChecksum = ReleaseAsset(
        name = "app-universal-release.apk.sha256",
        browserDownloadUrl = "https://github.com/takaokensei/makimono/releases/download/v1.4.24/app-universal-release.apk.sha256",
        size = 90L
    )

    @Test
    fun findChecksumAsset_returnsMatchingSha256Asset() {
        val release = LatestRelease(
            name = "v1.4.24",
            tagName = "v1.4.24",
            htmlUrl = "https://github.com/takaokensei/makimono/releases/tag/v1.4.24",
            assets = listOf(universalApk, arm64Apk, universalChecksum, arm64Checksum)
        )

        val foundArm64Checksum = release.findChecksumAsset(arm64Apk)
        assertNotNull(foundArm64Checksum)
        assertEquals("app-arm64-v8a-release.apk.sha256", foundArm64Checksum?.name)
        assertEquals(arm64Checksum.browserDownloadUrl, foundArm64Checksum?.browserDownloadUrl)

        val foundUniversalChecksum = release.findChecksumAsset(universalApk)
        assertNotNull(foundUniversalChecksum)
        assertEquals("app-universal-release.apk.sha256", foundUniversalChecksum?.name)
    }

    @Test
    fun findChecksumAsset_returnsNullWhenNoSha256AssetPublished() {
        val legacyRelease = LatestRelease(
            name = "v1.4.23",
            tagName = "v1.4.23",
            htmlUrl = "https://github.com/takaokensei/makimono/releases/tag/v1.4.23",
            assets = listOf(universalApk, arm64Apk)
        )

        assertNull(legacyRelease.findChecksumAsset(arm64Apk))
        assertNull(legacyRelease.findChecksumAsset(universalApk))
    }

    @Test
    fun findChecksumAsset_isCaseInsensitive() {
        val upperChecksum = ReleaseAsset(
            name = "APP-ARM64-V8A-RELEASE.APK.SHA256",
            browserDownloadUrl = "https://example.com/check.sha256",
            size = 90L
        )
        val release = LatestRelease(
            name = "v1.4.24",
            tagName = "v1.4.24",
            htmlUrl = "https://github.com/takaokensei/makimono/releases/tag/v1.4.24",
            assets = listOf(arm64Apk, upperChecksum)
        )

        val found = release.findChecksumAsset(arm64Apk)
        assertNotNull(found)
        assertEquals("APP-ARM64-V8A-RELEASE.APK.SHA256", found?.name)
    }

    @Test
    fun checksumString_parsesStandardSha256sumOutput() {
        val rawSha256sumOutput = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  app-release.apk`n"
        val parsed = rawSha256sumOutput
            .trim()
            .substringBefore(' ')
            .takeIf { it.length == 64 }

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", parsed)
    }
}
