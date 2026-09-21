package zechs.drive.stream

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.local.FavoriteFolder
import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.utils.*

/**
 * FEAT-07: Unit tests for BackupSerializer.
 * Validates export sanitization (zero secrets), import restoration, size limits,
 * corrupted JSON handling, and version validation.
 */
class BackupSerializerTest {

    private val profileManager: ProfileManager = mockk()
    private val watchListRepository: WatchListRepository = mockk()
    private val favoriteRepository: FavoriteRepository = mockk()
    private val watchQueueRepository: WatchQueueRepository = mockk()
    private val followedFolderRepository: FollowedFolderRepository = mockk()
    private val appSettings: AppSettings = mockk()

    private lateinit var backupSerializer: BackupSerializer

    @Before
    fun setUp() {
        coJustRun { profileManager.awaitReady() }
        backupSerializer = BackupSerializer(
            profileManager,
            watchListRepository,
            favoriteRepository,
            watchQueueRepository,
            followedFolderRepository,
            appSettings
        )
    }

    @Test
    fun `exportBackupJson produces sanitized valid JSON without secrets`() = runTest {
        val testProfiles = listOf(
            UserProfile("caua", "Cauã", "avatar_caua", true),
            UserProfile("anime", "Anime", "avatar_anime", false)
        )
        every { profileManager.getProfiles() } returns testProfiles
        coEvery { appSettings.fetchTheme(any()) } returns AppTheme.KODI_ESTUARY
        coEvery { appSettings.fetchPlayer(any()) } returns VideoPlayer.EXO_PLAYER
        coEvery { appSettings.fetchSubtitleSize(any()) } returns 16f

        coEvery { watchListRepository.getAllWatches("caua") } returns listOf(
            WatchList("Episode 1", "vid_1", 1200000L, 1440000L, "thumb1", "caua")
        )
        coEvery { watchListRepository.getAllWatches("anime") } returns emptyList()

        coEvery { favoriteRepository.getFavoritesSync("caua") } returns listOf(
            FavoriteFolder("caua", "fav_1", "One Piece", true, 1000L)
        )
        coEvery { favoriteRepository.getFavoritesSync("anime") } returns emptyList()

        coEvery { watchQueueRepository.getQueueSync("caua") } returns listOf(
            WatchQueueItem("caua", "q_1", "Attack on Titan", "thumb2", 0, 2000L)
        )
        coEvery { watchQueueRepository.getQueueSync("anime") } returns emptyList()

        coEvery { followedFolderRepository.getFollowedSync("caua") } returns listOf(
            FollowedFolder("caua", "f_1", "Naruto", 5, 3000L)
        )
        coEvery { followedFolderRepository.getFollowedSync("anime") } returns emptyList()

        val json = backupSerializer.exportBackupJson()

        assertNotNull(json)
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"name\": \"Cauã\""))
        assertTrue(json.contains("\"videoId\": \"vid_1\""))
        assertTrue(json.contains("\"folderId\": \"fav_1\""))

        // Critical security test: zero secrets or tokens in output
        assertFalse(BackupSerializer.containsSensitiveTokens(json))
        assertFalse(json.contains("client_secret", ignoreCase = true))
        assertFalse(json.contains("refreshToken", ignoreCase = true))
        assertFalse(json.contains("access_token", ignoreCase = true))
        assertFalse(json.contains("Bearer", ignoreCase = true))
    }

    @Test
    fun `importBackupJson successfully restores all profile entities`() = runTest {
        val validJson = """
            {
              "version": 1,
              "exportedAt": 1726700000000,
              "profiles": [
                {
                  "id": "caua",
                  "name": "Cauã",
                  "avatarResName": "avatar_caua",
                  "isDefault": true,
                  "settings": {
                    "theme": "kodi_estuary",
                    "player": "exoplayer",
                    "subtitleSize": 18.0
                  },
                  "watchList": [
                    {
                      "name": "Death Note 01",
                      "videoId": "vid_dn1",
                      "watchedDuration": 1200000,
                      "totalDuration": 1300000,
                      "thumbnailLink": "http://thumb"
                    }
                  ],
                  "favorites": [
                    {
                      "folderId": "folder_dn",
                      "folderName": "Death Note",
                      "addedAt": 1000
                    }
                  ],
                  "watchQueue": [
                    {
                      "fileId": "file_dn2",
                      "name": "Death Note 02",
                      "posterUrl": "http://poster",
                      "orderIndex": 0,
                      "addedAt": 2000
                    }
                  ],
                  "followedFolders": [
                    {
                      "folderId": "folder_dn",
                      "folderName": "Death Note",
                      "lastKnownCount": 37,
                      "followedAt": 3000
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        coJustRun { appSettings.saveTheme(any(), any()) }
        coJustRun { appSettings.savePlayer(any(), any()) }
        coJustRun { appSettings.saveSubtitleSize(any(), any()) }
        coEvery { watchListRepository.insertWatch(any(), any()) } returns 1L
        coJustRun { favoriteRepository.set(any(), any(), any(), any()) }
        coEvery { watchQueueRepository.enqueueItem(any()) } returns 1L
        coJustRun { followedFolderRepository.follow(any(), any(), any()) }
        coJustRun { followedFolderRepository.updateLastKnownCount(any(), any(), any()) }
        every { profileManager.getProfiles() } returns emptyList()
        coJustRun { profileManager.restoreProfiles(any()) }

        val result = backupSerializer.importBackupJson(validJson)

        assertTrue(result.isSuccess)
        assertEquals(1, result.importedProfiles)
        assertEquals(1, result.importedWatches)
        assertEquals(1, result.importedFavorites)
        assertEquals(1, result.importedQueue)
        assertEquals(1, result.importedFollowed)

        coVerify(exactly = 1) {
            watchListRepository.insertWatch(match { it.videoId == "vid_dn1" && it.profileId == "caua" }, "caua")
        }
        coVerify(exactly = 1) {
            favoriteRepository.set("folder_dn", "Death Note", true, "caua")
        }
        coVerify(exactly = 1) {
            watchQueueRepository.enqueueItem(match { it.fileId == "file_dn2" && it.profileId == "caua" })
        }
        coVerify(exactly = 1) {
            followedFolderRepository.follow("folder_dn", "Death Note", "caua")
        }
        coVerify(exactly = 1) {
            profileManager.restoreProfiles(match { it.size == 1 && it.first().id == "caua" })
        }
    }

    @Test
    fun `importBackupJson fails gracefully on malformed JSON`() = runTest {
        val result = backupSerializer.importBackupJson("{ not valid json :")
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `importBackupJson fails on unsupported future version`() = runTest {
        val futureJson = """
            {
              "version": 999,
              "profiles": []
            }
        """.trimIndent()

        val result = backupSerializer.importBackupJson(futureJson)
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("Versão do backup não suportada"))
    }

    @Test
    fun `containsSensitiveTokens correctly flags prohibited secrets`() {
        assertTrue(BackupSerializer.containsSensitiveTokens("{\"client_secret\": \"secret123\"}"))
        assertTrue(BackupSerializer.containsSensitiveTokens("{\"refreshToken\": \"token123\"}"))
        assertTrue(BackupSerializer.containsSensitiveTokens("{\"refresh_token\": \"token123\"}"))
        assertTrue(BackupSerializer.containsSensitiveTokens("{\"access_token\": \"token123\"}"))
        assertTrue(BackupSerializer.containsSensitiveTokens("{\"authorization\": \"Bearer abc\"}"))
        assertFalse(BackupSerializer.containsSensitiveTokens("{\"name\": \"Cauã\", \"theme\": \"kodi\"}"))
    }
}
