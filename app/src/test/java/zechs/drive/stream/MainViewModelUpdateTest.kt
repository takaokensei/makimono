package zechs.drive.stream

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import zechs.drive.stream.data.model.ChecksumAsset
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.model.ReleaseAsset
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.AppUpdateManager
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.data.repository.GithubRepository
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelUpdateTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MainViewModel
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val githubRepository: GithubRepository = mockk(relaxed = true)
    private val appSettings: AppSettings = mockk(relaxed = true)
    private val appUpdateManager: AppUpdateManager = mockk(relaxed = true)

    private lateinit var fakeApkFile: File

    private val apkAsset = ReleaseAsset(
        name = "app-universal-release.apk",
        browserDownloadUrl = "https://github.com/takaokensei/makimono/releases/download/v1.4.25/app-universal-release.apk",
        size = 40_000_000L
    )
    private val checksumName = "app-universal-release.apk.sha256"
    private val checksumAsset = ChecksumAsset(
        name = checksumName,
        browserDownloadUrl = "https://github.com/takaokensei/makimono/releases/download/v1.4.25/app-universal-release.apk.sha256"
    )
    private val validHash = "a".repeat(64)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeApkFile = File.createTempFile("makimono-test", ".apk").also { it.writeBytes(ByteArray(1024)) }
        coEvery { sessionManager.fetchClient() } returns null
        coEvery { githubRepository.getLatestRelease() } returns mockk(relaxed = true)
        coEvery { appSettings.fetchLastUpdated() } returns null
        coEvery { appSettings.fetchTheme() } returns mockk(relaxed = true)
        coEvery { appSettings.fetchPlayer() } returns mockk(relaxed = true)
        viewModel = MainViewModel(sessionManager, githubRepository, appSettings, appUpdateManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fakeApkFile.delete()
    }

    // Path 1: no .sha256 asset -> fail-closed: deletes APK and emits Failed
    @Test
    fun noChecksumAsset_failsClosedAndDeletesApk() = runTest {
        val release = buildRelease(hasChecksum = false)
        val tmpApk = File.createTempFile("no-checksum", ".apk").also { it.writeBytes(ByteArray(256)) }
        coEvery { appUpdateManager.downloadApk(apkAsset, any()) } returns Result.success(tmpApk)
        viewModel.updateDownloadState.test {
            awaitItem()
            viewModel.startUpdateDownload(release)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is MainViewModel.UpdateDownloadState.Failed)
            assertTrue(!tmpApk.exists())
            coVerify(exactly = 0) { appUpdateManager.fetchExpectedChecksum(any()) }
            coVerify(exactly = 0) { appUpdateManager.verifyChecksum(any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Path 2: checksum fetch fails -> fail-closed: deletes APK and emits Failed
    @Test
    fun checksumFetchFails_failsClosedAndDeletesApk() = runTest {
        val release = buildRelease(hasChecksum = true)
        val tmpApk = File.createTempFile("fetch-fail", ".apk").also { it.writeBytes(ByteArray(256)) }
        coEvery { appUpdateManager.downloadApk(apkAsset, any()) } returns Result.success(tmpApk)
        coEvery { appUpdateManager.fetchExpectedChecksum(checksumAsset) } returns null
        viewModel.updateDownloadState.test {
            awaitItem()
            viewModel.startUpdateDownload(release)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is MainViewModel.UpdateDownloadState.Failed)
            assertTrue(!tmpApk.exists())
            coVerify(exactly = 0) { appUpdateManager.verifyChecksum(any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Path 3: hash mismatch -> Failed + APK file deleted
    @Test
    fun checksumMismatch_emitsFailedAndDeletesApk() = runTest {
        val release = buildRelease(hasChecksum = true)
        val tmpApk = File.createTempFile("tampered", ".apk").also { it.writeBytes(ByteArray(512)) }
        coEvery { appUpdateManager.downloadApk(apkAsset, any()) } returns Result.success(tmpApk)
        coEvery { appUpdateManager.fetchExpectedChecksum(checksumAsset) } returns validHash
        coEvery { appUpdateManager.verifyChecksum(tmpApk, validHash) } returns false
        viewModel.updateDownloadState.test {
            awaitItem()
            viewModel.startUpdateDownload(release)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is MainViewModel.UpdateDownloadState.Failed)
            assertTrue(!tmpApk.exists())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Path 4: signature mismatch -> Failed + APK file deleted
    @Test
    fun signatureMismatch_emitsFailedAndDeletesApk() = runTest {
        val release = buildRelease(hasChecksum = true)
        val tmpApk = File.createTempFile("bad-sig", ".apk").also { it.writeBytes(ByteArray(512)) }
        coEvery { appUpdateManager.downloadApk(apkAsset, any()) } returns Result.success(tmpApk)
        coEvery { appUpdateManager.fetchExpectedChecksum(checksumAsset) } returns validHash
        coEvery { appUpdateManager.verifyChecksum(tmpApk, validHash) } returns true
        io.mockk.every { appUpdateManager.verifyApkSignature(tmpApk) } returns false

        viewModel.updateDownloadState.test {
            awaitItem()
            viewModel.startUpdateDownload(release)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is MainViewModel.UpdateDownloadState.Failed)
            assertTrue(!tmpApk.exists())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Path 5: valid hash and signature match -> ReadyToInstall
    @Test
    fun validChecksumAndSignature_emitsReadyToInstall() = runTest {
        val release = buildRelease(hasChecksum = true)
        coEvery { appUpdateManager.downloadApk(apkAsset, any()) } returns Result.success(fakeApkFile)
        coEvery { appUpdateManager.fetchExpectedChecksum(checksumAsset) } returns validHash
        coEvery { appUpdateManager.verifyChecksum(fakeApkFile, validHash) } returns true
        io.mockk.every { appUpdateManager.verifyApkSignature(fakeApkFile) } returns true

        viewModel.updateDownloadState.test {
            awaitItem()
            viewModel.startUpdateDownload(release)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state is MainViewModel.UpdateDownloadState.ReadyToInstall)
            assertEquals(fakeApkFile, (state as MainViewModel.UpdateDownloadState.ReadyToInstall).apkFile)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildRelease(hasChecksum: Boolean): LatestRelease {
        val assets = mutableListOf(apkAsset)
        if (hasChecksum) {
            assets.add(ReleaseAsset(name = checksumName, browserDownloadUrl = checksumAsset.browserDownloadUrl, size = 90L))
        }
        return LatestRelease(
            name = "v1.4.25",
            tagName = "v1.4.25",
            htmlUrl = "https://github.com/takaokensei/makimono/releases/tag/v1.4.25",
            assets = assets
        )
    }
}
