package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.remote.ProfileArt
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.data.remote.ProfileArtCatalogPolicy

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

    @Test
    fun `preset wallpapers include essential popular franchises`() {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val catalog = ProfileArtCatalog(context)
        val wallpapers = catalog.getPresetWallpapers()
        val labels = wallpapers.map { it.label }
        assertTrue(labels.any { it.contains("One Piece") })
        assertTrue(labels.any { it.contains("Bleach") })
        assertTrue(labels.any { it.contains("Naruto") })
        assertTrue(labels.any { it.contains("Chainsaw Man") })
        assertTrue(labels.any { it.contains("Hunter x Hunter") })
        assertTrue(wallpapers.all { it.isWallpaper })
        assertTrue(wallpapers.all { it.url.startsWith("https://") })
    }

    @Test
    fun `preset avatars group into franchises and organize cleanly`() {
        val catalog = ProfileArtCatalog(io.mockk.mockk(relaxed = true))
        val presetAvatars = catalog.getPresetAvatars()
        val allSections = mutableListOf<Pair<String, List<ProfileArt>>>()
        val seenFranchises = mutableSetOf<String>()
        val groupedPresets = presetAvatars.groupBy { art ->
            art.label.substringAfterLast("(", "").substringBefore(")", "").ifBlank { "Destaques" }
        }
        groupedPresets.forEach { (franchise, arts) ->
            val descriptor = ProfileArtCatalogPolicy.resolveFranchise(franchise)
            if (seenFranchises.add(descriptor.franchiseKey)) {
                allSections.add(Pair(franchise, arts))
            }
        }
        val organized = ProfileArtCatalogPolicy.organizeSections(allSections)
        assertTrue(organized.isNotEmpty())
        assertTrue(organized.any { it.first.contains("One Piece") })
        assertTrue(organized.any { it.first.contains("Monogatari") })
    }
}
