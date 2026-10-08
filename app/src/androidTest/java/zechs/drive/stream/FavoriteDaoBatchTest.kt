package zechs.drive.stream

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.local.FavoriteFolder
import zechs.drive.stream.data.local.WatchListDatabase

class FavoriteDaoBatchTest {
    @Test
    fun batchFavoriteLookupUsesRoomSqlAndKeepsProfilesIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, WatchListDatabase::class.java).build()
        val dao = database.getFavoriteDao()
        try {
            dao.setFavorite(FavoriteFolder(profileId = "profile-a", folderId = "series-1"))
            dao.setFavorite(FavoriteFolder(profileId = "profile-a", folderId = "shortcut-target"))
            dao.setFavorite(FavoriteFolder(profileId = "profile-b", folderId = "series-2"))

            assertEquals(
                listOf("series-1", "shortcut-target"),
                dao.getFavoriteIds(listOf("series-1", "shortcut-target", "series-2"), "profile-a").sorted()
            )
            assertEquals(listOf("series-2"), dao.getFavoriteIds(listOf("series-1", "series-2"), "profile-b"))
            assertTrue(dao.getFavoriteIds(emptyList(), "profile-a").isEmpty())
        } finally {
            database.close()
        }
    }
}
