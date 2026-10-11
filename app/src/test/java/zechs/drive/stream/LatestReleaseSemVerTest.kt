package zechs.drive.stream
 
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.LatestRelease

class LatestReleaseSemVerTest {

    @Test
    fun sameVersion_returnsTrue() {
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.5.3", "1.6.5.3"))
        assertTrue(LatestRelease.isInstalledVersionAtLeast("1.6.5.3", "1.6.5.3"))
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.5.3", "v1.6.5.3"))
    }

    @Test
    fun installedIsNewer_returnsTrue() {
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.5.2", "1.6.5.3"))
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.5.0", "1.6.5.3"))
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.4", "1.6.5.0"))
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.5.0", "1.6.5.3"))
    }

    @Test
    fun remoteIsNewer_returnsFalse() {
        assertFalse(LatestRelease.isInstalledVersionAtLeast("v1.6.5.4", "1.6.5.3"))
        assertFalse(LatestRelease.isInstalledVersionAtLeast("v1.6.6.0", "1.6.5.3"))
        assertFalse(LatestRelease.isInstalledVersionAtLeast("v2.0.0", "1.6.5.3"))
        assertFalse(LatestRelease.isInstalledVersionAtLeast("v1.6.5.2", "1.6.5.0"))
    }

    @Test
    fun versionWithSuffix_comparesCorrectly() {
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.5.3-release", "1.6.5.3"))
        assertTrue(LatestRelease.isInstalledVersionAtLeast("v1.6.5.2-hotfix", "1.6.5.3"))
    }
}
