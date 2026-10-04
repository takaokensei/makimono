package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.data.remote.ProfileArt
import zechs.drive.stream.data.remote.ProfileArtCatalog

class ProfileArtCatalogTest {
    private val avatars = listOf(
        ProfileArt("Luffy", "https://cdn.example/luffy.png", false),
        ProfileArt("Zoro", "https://cdn.example/zoro.png", false),
        ProfileArt("Frieren", "https://cdn.example/frieren.png", false)
    )

    @Test
    fun `profile fallback is stable and distinct for default profiles`() {
        val first = ProfileArtCatalog.defaultAvatarForProfile("profile-1", avatars)
        assertEquals(first, ProfileArtCatalog.defaultAvatarForProfile("profile-1", avatars))
        assertNotEquals(first, ProfileArtCatalog.defaultAvatarForProfile("profile-2", avatars))
    }

    @Test
    fun `empty catalog does not invent a fallback`() {
        assertNull(ProfileArtCatalog.defaultAvatarForProfile("profile-1", emptyList()))
    }
}
