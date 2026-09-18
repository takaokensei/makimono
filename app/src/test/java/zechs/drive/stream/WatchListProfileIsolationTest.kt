package zechs.drive.stream

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.local.FavoriteDao
import zechs.drive.stream.data.local.FavoriteFolder
import zechs.drive.stream.data.local.WatchListDao
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.utils.ProfileManager

class WatchListProfileIsolationTest {

    private val watchListDao = mockk<WatchListDao>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val profileManager = mockk<ProfileManager>()

    private lateinit var watchListRepository: WatchListRepository
    private lateinit var favoriteRepository: FavoriteRepository

    private val profileCaua = UserProfile(id = "caua", name = "Cauã", isDefault = true)
    private val profileAnime = UserProfile(id = "anime", name = "Anime", isDefault = false)

    @Before
    fun setup() {
        every { profileManager.getActiveProfile() } returns profileCaua
        watchListRepository = WatchListRepository(watchListDao, profileManager)
        favoriteRepository = FavoriteRepository(favoriteDao, profileManager)
    }

    @Test
    fun insertWatch_usesActiveProfileIdWhenBlank() = runTest {
        val watch = WatchList(
            name = "Frieren 01.mkv",
            videoId = "vid-123",
            watchedDuration = 5000L,
            totalDuration = 24000L,
            profileId = ""
        )

        watchListRepository.insertWatch(watch)

        coVerify(exactly = 1) {
            watchListDao.upsertWatch(match { it.profileId == "caua" && it.videoId == "vid-123" })
        }
    }

    @Test
    fun getRecentWatches_queriesWithActiveProfileId() = runTest {
        val cauaWatches = listOf(
            WatchList(name = "Frieren 01", videoId = "vid-1", watchedDuration = 5000L, totalDuration = 24000L, profileId = "caua")
        )
        coEvery { watchListDao.getRecentWatches("caua", 20) } returns cauaWatches

        val result = watchListRepository.getRecentWatches(10)

        assertEquals(1, result.size)
        assertEquals("caua", result[0].profileId)
        coVerify(exactly = 1) { watchListDao.getRecentWatches("caua", 20) }
    }

    @Test
    fun switchingProfile_changesRepositoryPartition() = runTest {
        every { profileManager.getActiveProfile() } returns profileAnime
        val animeWatches = listOf(
            WatchList(name = "One Piece 1000", videoId = "vid-op", watchedDuration = 10000L, totalDuration = 24000L, profileId = "anime")
        )
        coEvery { watchListDao.getRecentWatches("anime", 20) } returns animeWatches

        val result = watchListRepository.getRecentWatches(10)

        assertEquals(1, result.size)
        assertEquals("anime", result[0].profileId)
        assertEquals("One Piece 1000", result[0].name)
        coVerify(exactly = 1) { watchListDao.getRecentWatches("anime", 20) }
    }

    @Test
    fun favoriteRepository_isolatesByProfileId() = runTest {
        // Active is Caua
        favoriteRepository.set(folderId = "folder-caua", folderName = "Frieren", isFavorite = true)

        coVerify(exactly = 1) {
            favoriteDao.setFavorite(match { it.profileId == "caua" && it.folderId == "folder-caua" })
        }

        // Switch to Anime profile
        every { profileManager.getActiveProfile() } returns profileAnime
        favoriteRepository.set(folderId = "folder-anime", folderName = "Bleach", isFavorite = true)

        coVerify(exactly = 1) {
            favoriteDao.setFavorite(match { it.profileId == "anime" && it.folderId == "folder-anime" })
        }
    }
}
