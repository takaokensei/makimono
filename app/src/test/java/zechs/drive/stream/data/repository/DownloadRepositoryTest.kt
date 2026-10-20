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
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.local.DownloadDao
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.utils.ProfileManager
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

        repository.deleteDownload("del123")

        coVerify(exactly = 1) { downloadDao.deleteDownload("del123") }
        assertEquals(false, fakeFile.exists())
    }

    @Test
    fun getDownloadsForCurrentProfileUsesProfileManagerId() = runBlocking {
        every { profileManager.getActiveProfile() } returns UserProfile(id = "caua", name = "Cauã")
        every { downloadDao.getDownloadsForProfile("caua") } returns flowOf(emptyList())

        repository.getDownloadsForCurrentProfile()

        io.mockk.verify(exactly = 1) { downloadDao.getDownloadsForProfile("caua") }
    }
}
