package zechs.drive.stream

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.local.FavoriteDao
import zechs.drive.stream.data.local.FavoriteFolder
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.utils.ProfileManager

class FavoriteRepositoryTest {

    private lateinit var fakeDao: FakeFavoriteDao
    private lateinit var repository: FavoriteRepository
    private val profileManager = mockk<ProfileManager>()
    private val defaultProfile = UserProfile("caua", "Cauã", isDefault = true)

    @Before
    fun setUp() {
        every { profileManager.getActiveProfile() } returns defaultProfile
        fakeDao = FakeFavoriteDao()
        repository = FavoriteRepository(fakeDao, profileManager)
    }

    @Test
    fun set_favoriteTrue_addsFavorite() = runBlocking {
        repository.set("folder_123", "Anime Series", true)
        assertTrue(repository.isFavorite("folder_123"))
        assertEquals("Anime Series", fakeDao.favorites[Pair("caua", "folder_123")]?.folderName)
    }

    @Test
    fun set_favoriteFalse_removesFavorite() = runBlocking {
        repository.set("folder_123", "Anime Series", true)
        repository.set("folder_123", "Anime Series", false)
        assertFalse(repository.isFavorite("folder_123"))
    }

    @Test
    fun observeFavoriteIds_emitsCurrentFavorites() = runBlocking {
        repository.set("folder_1", "Series 1", true)
        repository.set("folder_2", "Series 2", true)

        val ids = repository.observeFavoriteIds().first()
        assertEquals(setOf("folder_1", "folder_2"), ids)
    }

    private class FakeFavoriteDao : FavoriteDao {
        val favorites = mutableMapOf<Pair<String, String>, FavoriteFolder>()

        override suspend fun setFavorite(favorite: FavoriteFolder) {
            favorites[Pair(favorite.profileId, favorite.folderId)] = favorite
        }

        override suspend fun removeFavorite(folderId: String, profileId: String) {
            favorites.remove(Pair(profileId, folderId))
        }

        override suspend fun isFavorite(folderId: String, profileId: String): Boolean {
            return favorites[Pair(profileId, folderId)]?.isFavorite == true
        }

        override fun observeIsFavorite(folderId: String, profileId: String) =
            flowOf(favorites[Pair(profileId, folderId)]?.isFavorite == true)

        override fun observeAllFavoriteIds(profileId: String) =
            flowOf(favorites.values.filter { it.profileId == profileId && it.isFavorite }.map { it.folderId })

        override fun getAllFavorites(profileId: String) =
            flowOf(favorites.values.filter { it.profileId == profileId && it.isFavorite }.toList())

        override suspend fun getFavoritesSync(profileId: String): List<FavoriteFolder> {
            return favorites.values.filter { it.profileId == profileId && it.isFavorite }.toList()
        }
    }
}
