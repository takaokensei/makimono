package zechs.drive.stream.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.Lazy
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.data.local.ProfileDao
import zechs.drive.stream.data.local.ProfileDataCleaner
import zechs.drive.stream.data.local.ProfileEntity
import zechs.drive.stream.data.local.WatchListDatabase
import zechs.drive.stream.data.model.UserProfile

class ProfileManagerBootstrapTest {
    private val prefs = mockk<SharedPreferences>(relaxed = true) {
        every { getString(any(), any()) } returns null
    }
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns prefs
    }
    private val dao = mockk<ProfileDao>(relaxed = true)
    private val database = mockk<WatchListDatabase> { every { getProfileDao() } returns dao }
    private val cleaner = mockk<Lazy<ProfileDataCleaner>>(relaxed = true)

    @Test fun freshInstallDoesNotPersistAutomaticProfiles() = runTest {
        coEvery { dao.getAll() } returns emptyList()
        val manager = ProfileManager(context, cleaner, database)
        manager.awaitReady()
        assertTrue(manager.getProfiles().isEmpty())
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test fun existingProfilesAndTheirArtworkArePreserved() = runTest {
        val existing = UserProfile("existing", "Meu perfil", avatarUrl = "https://example/avatar",
            backgroundUrl = "https://example/background", isAdmin = true)
        coEvery { dao.getAll() } returns listOf(ProfileEntity.from(existing))
        val manager = ProfileManager(context, cleaner, database)
        manager.awaitReady()
        assertEquals(listOf(existing), manager.getProfiles())
        assertEquals(existing, manager.getActiveProfile())
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test fun firstSaveUsesThePreparedLibraryWithoutPersistingAPendingProfile() = runTest {
        coEvery { dao.getAll() } returns emptyList()
        val manager = ProfileManager(context, cleaner, database)
        manager.awaitReady()
        manager.setLibraryRoot("root-id", "oneblacki")
        coVerify(exactly = 0) { dao.replaceAll(any()) }
        manager.saveProfile(UserProfile("created", "Minha escolha", avatarResName = "profile_placeholder"))
        assertEquals("created", manager.getActiveProfile().id)
        assertEquals("root-id", manager.getActiveProfile().libraryRootId)
        assertTrue(manager.getActiveProfile().isAdmin)
        assertEquals(1, manager.getProfiles().size)
        coVerify(exactly = 1) { dao.replaceAll(match { it.size == 1 && it.single().id == "created" }) }
    }
}
