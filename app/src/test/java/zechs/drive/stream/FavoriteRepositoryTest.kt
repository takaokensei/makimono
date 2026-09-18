package zechs.drive.stream

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
import zechs.drive.stream.data.repository.FavoriteRepository

class FavoriteRepositoryTest {

    private lateinit var fakeDao: FakeFavoriteDao
    private lateinit var repository: FavoriteRepository

    @Before
    fun setUp() {
        fakeDao = FakeFavoriteDao()
        repository = FavoriteRepository(fakeDao)
    }

    @Test
    fun set_favoriteTrue_addsFavorite() = runBlocking {
        repository.set("folder_123", "Anime Series", true)
        assertTrue(repository.isFavorite("folder_123"))
        assertEquals("Anime Series", fakeDao.favorites["folder_123"]?.folderName)
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
        val favorites = mutableMapOf<String, FavoriteFolder>()

        override suspend fun setFavorite(favorite: FavoriteFolder) {
            favorites[favorite.folderId] = favorite
        }

        override suspend fun removeFavorite(folderId: String) {
            favorites.remove(folderId)
        }

        override suspend fun isFavorite(folderId: String): Boolean {
            return favorites[folderId]?.isFavorite == true
        }

        override fun observeIsFavorite(folderId: String) =
            flowOf(favorites[folderId]?.isFavorite == true)

        override fun observeAllFavoriteIds() =
            flowOf(favorites.values.filter { it.isFavorite }.map { it.folderId })

        override fun getAllFavorites() =
            flowOf(favorites.values.filter { it.isFavorite }.toList())

        override suspend fun getFavoritesSync(): List<FavoriteFolder> {
            return favorites.values.filter { it.isFavorite }.toList()
        }
    }
}
