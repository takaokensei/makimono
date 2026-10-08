package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.ui.player.PlaybackProfileSessionPolicy

class PlaybackProfileSessionPolicyTest {
    @Test
    fun `handoff keeps original playback profile after active profile changes`() {
        assertEquals("profile-original", PlaybackProfileSessionPolicy.resolve("profile-original", "profile-current"))
    }

    @Test
    fun `normal launch falls back to active profile`() {
        assertEquals("profile-current", PlaybackProfileSessionPolicy.resolve(null, "profile-current"))
    }

    @Test
    fun `blank handoff profile is treated as missing`() {
        assertEquals("profile-current", PlaybackProfileSessionPolicy.resolve("  ", "profile-current"))
    }

    @Test
    fun `handoff keeps a nonblank Drive source folder`() {
        assertEquals("shortcut-parent", PlaybackProfileSessionPolicy.siblingFolderIdForHandoff("shortcut-parent"))
    }

    @Test
    fun `blank Drive source folder is omitted`() {
        assertEquals(null, PlaybackProfileSessionPolicy.siblingFolderIdForHandoff("  "))
    }

    @Test
    fun `handoff keeps zero as an explicit playback position`() {
        assertEquals(0L, PlaybackProfileSessionPolicy.explicitPositionForHandoff(0L))
    }

    @Test
    fun `negative playback positions are normalized to the beginning`() {
        assertEquals(0L, PlaybackProfileSessionPolicy.explicitPositionForHandoff(-1L))
    }
}
