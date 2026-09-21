package zechs.drive.stream

import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.ui.profile.ProfileEditorDialog

class ProfileImageUrlTest {
    @Test fun customImagesRequireValidHttpsWithoutCredentials() {
        assertTrue(ProfileEditorDialog.validImageUrl(null))
        assertTrue(ProfileEditorDialog.validImageUrl("https://cdn.example.org/avatar.png?size=large"))
        listOf("file:///sdcard/private", "http://example.org/a", "javascript:alert(1)",
            "https://", "https://user:password@example.org/a", "https://example.org/an image.jpg")
            .forEach { assertFalse(it, ProfileEditorDialog.validImageUrl(it)) }
    }
}
