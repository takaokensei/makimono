package zechs.drive.stream.utils

import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.data.model.UserProfile

class ProfileBootstrapPolicyTest {
    @Test fun freshIdentityHasNoAssignedNameOrArtwork() {
        val pending = ProfileBootstrapPolicy.pendingProfile()
        assertTrue(pending.name.isBlank())
        assertNull(pending.avatarUrl)
        assertNull(pending.backgroundUrl)
        assertFalse(pending.isAdmin)
    }

    @Test fun firstCreatedProfileOwnsPreparedRootAndIsAdministrator() {
        val pending = ProfileBootstrapPolicy.pendingProfile().copy(
            libraryRootId = "library-id", libraryRootName = "oneblacki"
        )
        val result = ProfileBootstrapPolicy.firstProfile(UserProfile("chosen", "  Nome  "), pending)
        assertEquals("chosen", result.id)
        assertEquals("Nome", result.name)
        assertEquals("library-id", result.libraryRootId)
        assertEquals("oneblacki", result.libraryRootName)
        assertTrue(result.isAdmin)
        assertTrue(result.isDefault)
    }

    @Test fun explicitlyChosenRootAndArtworkArePreserved() {
        val chosen = UserProfile("chosen", "Nome", libraryRootId = "chosen-root",
            libraryRootName = "Filmes", avatarUrl = "https://example/avatar",
            backgroundUrl = "https://example/background")
        val result = ProfileBootstrapPolicy.firstProfile(chosen,
            ProfileBootstrapPolicy.pendingProfile().copy(libraryRootId = "detected-root"))
        assertEquals(chosen.libraryRootId, result.libraryRootId)
        assertEquals(chosen.libraryRootName, result.libraryRootName)
        assertEquals(chosen.avatarUrl, result.avatarUrl)
        assertEquals(chosen.backgroundUrl, result.backgroundUrl)
    }

    @Test fun recognizesOnlyTheUntouchedLegacyStarterPair() {
        val starterPair = listOf(
            UserProfile("profile-1", "Perfil principal", "avatar_anime", isDefault = true,
                libraryRootId = "drive-root", libraryRootName = "Animes", createdAt = 42,
                avatarUrl = "https://example.org/avatar.jpg", backgroundUrl = "https://example.org/banner.jpg",
                isAdmin = true),
            UserProfile("profile-2", "Perfil secundário", "avatar_anime", createdAt = 42)
        )
        assertTrue(ProfileBootstrapPolicy.isLegacyStarterPair(starterPair))
        assertFalse(ProfileBootstrapPolicy.isLegacyStarterPair(starterPair + UserProfile("third", "Outro")))
        assertFalse(ProfileBootstrapPolicy.isLegacyStarterPair(starterPair.map {
            if (it.id == "profile-2") it.copy(name = "Perfil da família") else it
        }))
        assertFalse(ProfileBootstrapPolicy.isLegacyStarterPair(starterPair.map {
            if (it.id == "profile-2") it.copy(libraryRootId = "drive-root") else it
        }))
        assertFalse(ProfileBootstrapPolicy.isLegacyStarterPair(starterPair.map {
            if (it.id == "profile-2") it.copy(createdAt = 43) else it
        }))
    }
}
