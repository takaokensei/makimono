package zechs.drive.stream

import androidx.lifecycle.SavedStateHandle
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.ui.player.PlayerViewModel
import zechs.drive.stream.utils.ProfileManager

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerProfileSessionTest {
    private val watchListRepository = mockk<WatchListRepository>(relaxed = true)
    private val profileManager = mockk<ProfileManager>()
    private val driveRepository = Lazy { mockk<DriveRepository>(relaxed = true) }
    private val originalProfile = UserProfile(id = "profile-a", name = "Perfil A")
    private val otherProfile = UserProfile(id = "profile-b", name = "Perfil B")

    @Before
    fun setUp() {
        every { profileManager.getActiveProfile() } returns originalProfile
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun playbackReadsAndWritesStayWithProfileThatStartedSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = PlayerViewModel(watchListRepository, driveRepository, SavedStateHandle(), profileManager)
        val existingWatch = WatchList(
            name = "Episode 01",
            videoId = "episode-1",
            watchedDuration = 10_000L,
            totalDuration = 24_000L,
            profileId = originalProfile.id
        )
        coEvery { watchListRepository.getWatch("episode-1", originalProfile.id) } returns existingWatch

        // Simulate changing the active profile while this player session remains alive in PiP.
        every { profileManager.getActiveProfile() } returns otherProfile
        viewModel.getWatch("episode-1")
        viewModel.saveWatch("Episode 01", "episode-1", 12_000L, 24_000L)
        advanceUntilIdle()

        coVerify(exactly = 2) { watchListRepository.getWatch("episode-1", originalProfile.id) }
        coVerify(exactly = 0) { watchListRepository.getWatch("episode-1", otherProfile.id) }
        coVerify(exactly = 1) {
            watchListRepository.insertWatch(
                match<WatchList> { it.profileId == originalProfile.id && it.watchedDuration == 12_000L },
                originalProfile.id
            )
        }
        assertEquals(otherProfile.id, profileManager.getActiveProfile().id)
    }
}
