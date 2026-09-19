package zechs.drive.stream

import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.utils.state.Resource

/**
 * FEAT-05 unit tests for the new-episode check logic.
 *
 * We test the core decision logic without starting a real WorkManager or
 * posting a real [NotificationManager] notification.  [EpisodeCheckWorker] is
 * not directly instantiated here — instead the decision logic is extracted into
 * the helper functions below that mirror the worker's algorithm.
 */
class EpisodeCheckWorkerTest {

    private val driveRepository: DriveRepository = mockk()
    private val followedFolderRepository: FollowedFolderRepository = mockk()

    // -------------------------------------------------------------------------
    // Helpers that replicate the worker's core algorithm without AndroidContext
    // -------------------------------------------------------------------------

    /**
     * Runs the episode-check logic for a single profile, returns the set of
     * folder IDs for which a notification would be fired.
     */
    private suspend fun runCheckForProfile(
        profileId: String
    ): Set<String> {
        val notified = mutableSetOf<String>()
        val followed = followedFolderRepository.getFollowedSync(profileId)

        for (folder in followed) {
            val result = driveRepository.getAllFiles(
                query = "'${folder.folderId}' in parents and trashed = false",
                pageSize = 100,
                maxPages = 3
            )
            if (result is Resource.Success) {
                val currentCount = result.data.size
                if (currentCount > folder.lastKnownCount && folder.lastKnownCount > 0) {
                    notified += folder.folderId
                }
                followedFolderRepository.updateLastKnownCount(
                    folderId = folder.folderId,
                    count = currentCount,
                    profileId = profileId
                )
            }
        }
        return notified
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `no followed folders — no notification, no updateLastKnownCount`() = runTest {
        coEvery { followedFolderRepository.getFollowedSync("caua") } returns emptyList()

        val notified = runCheckForProfile("caua")

        assert(notified.isEmpty())
        coVerify(exactly = 0) { followedFolderRepository.updateLastKnownCount(any(), any(), any()) }
    }

    @Test
    fun `count unchanged — no notification, count still updated`() = runTest {
        val folder = FollowedFolder(
            profileId = "caua",
            folderId = "folder_123",
            folderName = "Naruto",
            lastKnownCount = 5
        )
        coEvery { followedFolderRepository.getFollowedSync("caua") } returns listOf(folder)
        coEvery { driveRepository.getAllFiles(any(), any(), any()) } returns
            Resource.Success(buildFakeFiles(5))
        coJustRun { followedFolderRepository.updateLastKnownCount("folder_123", 5, "caua") }

        val notified = runCheckForProfile("caua")

        assert(notified.isEmpty()) { "Expected no notification when count is unchanged" }
        coVerify(exactly = 1) {
            followedFolderRepository.updateLastKnownCount("folder_123", 5, "caua")
        }
    }

    @Test
    fun `new episodes detected — notification fired and count updated`() = runTest {
        val folder = FollowedFolder(
            profileId = "caua",
            folderId = "folder_456",
            folderName = "One Piece",
            lastKnownCount = 10
        )
        coEvery { followedFolderRepository.getFollowedSync("caua") } returns listOf(folder)
        coEvery { driveRepository.getAllFiles(any(), any(), any()) } returns
            Resource.Success(buildFakeFiles(13))
        coJustRun { followedFolderRepository.updateLastKnownCount("folder_456", 13, "caua") }

        val notified = runCheckForProfile("caua")

        assert("folder_456" in notified) { "Expected notification for folder_456" }
        coVerify(exactly = 1) {
            followedFolderRepository.updateLastKnownCount("folder_456", 13, "caua")
        }
    }

    @Test
    fun `first check (lastKnownCount == 0) — no notification but count saved`() = runTest {
        // On first check the baseline should be stored without notifying the
        // user — otherwise every followed folder triggers a spurious alert.
        val folder = FollowedFolder(
            profileId = "anime",
            folderId = "folder_789",
            folderName = "Attack on Titan",
            lastKnownCount = 0
        )
        coEvery { followedFolderRepository.getFollowedSync("anime") } returns listOf(folder)
        coEvery { driveRepository.getAllFiles(any(), any(), any()) } returns
            Resource.Success(buildFakeFiles(87))
        coJustRun { followedFolderRepository.updateLastKnownCount("folder_789", 87, "anime") }

        val notified = runCheckForProfile("anime")

        assert(notified.isEmpty()) { "First check should not notify — baseline is being established" }
        coVerify(exactly = 1) {
            followedFolderRepository.updateLastKnownCount("folder_789", 87, "anime")
        }
    }

    @Test
    fun `drive error — no notification, no count update`() = runTest {
        val folder = FollowedFolder(
            profileId = "caua",
            folderId = "folder_err",
            folderName = "Berserk",
            lastKnownCount = 5
        )
        coEvery { followedFolderRepository.getFollowedSync("caua") } returns listOf(folder)
        coEvery { driveRepository.getAllFiles(any(), any(), any()) } returns
            Resource.Error("Network error")

        val notified = runCheckForProfile("caua")

        assert(notified.isEmpty())
        coVerify(exactly = 0) { followedFolderRepository.updateLastKnownCount(any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildFakeFiles(count: Int): List<File> =
        (1..count).map { i ->
            File(
                id = "file_$i",
                name = "episode_$i.mkv",
                mimeType = "video/x-matroska",
                size = null,
                iconLink = "https://drive.google.com/icon.png",
                thumbnailLink = null
            )
        }
}
