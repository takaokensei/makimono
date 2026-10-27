package zechs.drive.stream.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.local.DownloadDao
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.worker.DownloadWorker
import java.io.File

class DownloadRepositoryTest {

    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val profileManager = mockk<ProfileManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var repository: DownloadRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "makimono_test_downloads_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        every { context.getExternalFilesDir("downloads") } returns tempDir
        every { context.filesDir } returns tempDir
        every { profileManager.getActiveProfile() } returns UserProfile(id = "test_user", name = "Test User")

        repository = DownloadRepository(
            downloadDao = downloadDao,
            profileManager = profileManager,
            context = context
        )
    }

    @Test
    fun getDownloadReturnsFromDao() = runBlocking {
        val item = DownloadItem(
            fileId = "file123",
            name = "Episode 01.mkv",
            localPath = "/fake/path",
            status = DownloadStatus.COMPLETED.name
        )
        coEvery { downloadDao.getDownload("file123") } returns item

        val result = repository.getDownload("file123")
        assertNotNull(result)
        assertEquals("Episode 01.mkv", result?.name)
        assertEquals(DownloadStatus.COMPLETED, result?.downloadStatus)
    }

    @Test
    fun deleteDownloadRemovesFileAndCallsDao() = runBlocking {
        val fakeFile = File(tempDir, "file_to_delete.mkv")
        fakeFile.writeText("test video bytes")

        val item = DownloadItem(
            fileId = "del123",
            name = "file_to_delete.mkv",
            localPath = fakeFile.absolutePath,
            status = DownloadStatus.COMPLETED.name
        )
        coEvery { downloadDao.getDownload("del123") } returns item
        coEvery { downloadDao.countReferencesForFile("del123") } returns 0

        repository.deleteDownload("del123")

        coVerify(exactly = 1) { downloadDao.deleteDownload("del123") }
        assertEquals(false, fakeFile.exists())
    }

    @Test
    fun deleteDownloadPreservesPhysicalFileWhenAnotherProfileReferencesIt() = runBlocking {
        val fakeFile = File(tempDir, "shared_ep.mkv")
        fakeFile.writeText("shared video bytes")

        val item = DownloadItem(
            fileId = "shared123",
            name = "shared_ep.mkv",
            localPath = fakeFile.absolutePath,
            status = DownloadStatus.COMPLETED.name,
            profileId = "test_user"
        )
        coEvery { downloadDao.getDownloadForProfile("shared123", "test_user") } returns item
        coEvery { downloadDao.countReferencesForFile("shared123") } returns 1

        repository.deleteDownload("shared123", "test_user")

        coVerify(exactly = 1) { downloadDao.deleteDownloadForProfile("shared123", "test_user") }
        coVerify(exactly = 0) { downloadDao.deleteDownload("shared123") }
        assertTrue(fakeFile.exists())
    }

    @Test
    fun getDownloadedSubtitlesForEpisodeReconstructsAndPrioritizesAssPtBr() {
        val subsDir = File(tempDir, DownloadWorker.SUBTITLES_DIR_NAME).apply { mkdirs() }
        File(subsDir, "ep5__sub_en__Frieren_-_05.en.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nHello")
        File(subsDir, "ep5__sub_pt__Frieren_-_05.pt-BR.ass").writeText("[Script Info]\nTitle: Frieren")

        val subs = repository.getDownloadedSubtitlesForEpisode("ep5")

        assertEquals(2, subs.size)
        assertEquals("sub_pt", subs[0].id)
        assertEquals("por", subs[0].languageCode)
        assertEquals("ass", subs[0].ext)
        assertEquals("sub_en", subs[1].id)
    }

    @Test
    fun getDownloadsForCurrentProfileUsesProfileManagerId() = runBlocking {
        every { profileManager.getActiveProfile() } returns UserProfile(id = "caua", name = "Cauã")
        every { downloadDao.getDownloadsForProfile("caua") } returns flowOf(emptyList())

        repository.getDownloadsForCurrentProfile()

        io.mockk.verify(exactly = 1) { downloadDao.getDownloadsForProfile("caua") }
    }
}
