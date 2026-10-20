package zechs.drive.stream.ui.downloads

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.repository.DownloadRepository
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.ProfileManager

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val profileManager = mockk<ProfileManager>(relaxed = true)
    private val appSettings = mockk<AppSettings>(relaxed = true)

    private lateinit var viewModel: DownloadsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { profileManager.getActiveProfile() } returns UserProfile(id = "caua", name = "Cauã")
        coEvery { appSettings.fetchSmartDownloads("caua") } returns true
        every { downloadRepository.getDownloadsForCurrentProfile() } returns flowOf(
            listOf(
                DownloadItem(
                    fileId = "f1",
                    name = "Ep 01.mkv",
                    localPath = "/downloads/f1_Ep_01.mkv",
                    status = DownloadStatus.COMPLETED.name,
                    totalBytes = 500L,
                    downloadedBytes = 500L
                )
            )
        )
        coEvery { downloadRepository.getStorageUsage() } returns DownloadRepository.StorageUsage(
            makimonoBytes = 1000L,
            freeBytes = 5000L,
            totalBytes = 6000L
        )

        viewModel = DownloadsViewModel(
            downloadRepository = downloadRepository,
            profileManager = profileManager,
            appSettings = appSettings
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deleteDownloadDelegatesToRepository() = runTest {
        viewModel.deleteDownload("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("f1") }
    }

    @Test
    fun deleteAllDelegatesToRepositoryWithProfileId() = runTest {
        viewModel.deleteAll()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteAllDownloadsForProfile("caua") }
    }

    @Test
    fun storageUsageCalculatesPercentsAccurately() {
        val usage = DownloadRepository.StorageUsage(
            makimonoBytes = 200L,
            freeBytes = 800L,
            totalBytes = 1000L
        )
        assertEquals(20, usage.makimonoPercent)
        assertEquals(80, usage.freePercent)
    }

    @Test
    fun setSmartDownloadsEnabledSavesToAppSettings() = runTest {
        viewModel.setSmartDownloadsEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appSettings.saveSmartDownloads(false, "caua") }
        assertEquals(false, viewModel.isSmartDownloadsEnabled.value)
    }
}
