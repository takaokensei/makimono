package zechs.drive.stream

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.ui.files.FilesViewModel
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.utils.state.ResourceErrorKind
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelQueryTest {
    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val driveRepository = mockk<DriveRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val folderMetadataRepository = mockk<FolderMetadataRepository>(relaxed = true)
    private val animePosterResolver = mockk<AnimePosterResolver>(relaxed = true)
    private val favoriteRepository = mockk<FavoriteRepository>(relaxed = true)
    private val localMediaRepository = mockk<zechs.drive.stream.data.repository.LocalMediaRepository>(relaxed = true)

    private lateinit var viewModel: FilesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { folderMetadataRepository.getAllMetadata() } returns emptyList()
        coEvery { favoriteRepository.getFavoriteIds(any()) } returns emptySet()
        viewModel = FilesViewModel(
            driveRepository,
            sessionManager,
            folderMetadataRepository,
            animePosterResolver,
            favoriteRepository,
            localMediaRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun changingFolderQueryCancelsOldRequestAndStartsAtFirstPage() = runBlocking {
        val oldQuery = "'old-folder' in parents and trashed=false"
        val newQuery = "'new-folder' in parents and trashed=false"
        val oldRequestStarted = CompletableDeferred<Unit>()
        val oldRequestCancelled = CompletableDeferred<Unit>()

        coEvery {
            driveRepository.getFiles(query = oldQuery, pageToken = null, pageSize = 25)
        } coAnswers {
            oldRequestStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                oldRequestCancelled.complete(Unit)
            }
        }
        coEvery {
            driveRepository.getFiles(query = newQuery, pageToken = null, pageSize = 25)
        } returns Resource.Success(
            FilesResponse(
                files = listOf(
                    File(
                        id = "new-episode",
                        name = "Episode 01.mkv",
                        size = 1_000L,
                        iconLink = "",
                        mimeType = "video/x-matroska"
                    )
                ),
                nextPageToken = null
            )
        )

        val oldJob = viewModel.queryFiles(oldQuery)
        withTimeout(3_000) { oldRequestStarted.await() }

        val newJob = viewModel.queryFiles(newQuery)
        withTimeout(3_000) { newJob.join() }
        withTimeout(3_000) { oldRequestCancelled.await() }
        withTimeout(3_000) {
            while (viewModel.filesList.value !is Resource.Success) delay(10)
        }

        assertTrue(oldJob.isCancelled)
        val items = (viewModel.filesList.value as Resource.Success).data.orEmpty()
        assertEquals(listOf("new-episode"), items.filterIsInstance<FilesDataModel.File>().map { it.driveFile.id })
        coVerify(exactly = 1) {
            driveRepository.getFiles(query = newQuery, pageToken = null, pageSize = 25)
        }
    }

    @Test
    fun leavingDuringNextPageKeepsLoadedFilesAndRetryUsesSameCursor() = runBlocking {
        val query = "'season-folder' in parents and trashed=false"
        val nextPageStarted = CompletableDeferred<Unit>()
        val nextPageCancelled = CompletableDeferred<Unit>()
        var nextPageAttempts = 0

        coEvery {
            driveRepository.getFiles(query = query, pageToken = null, pageSize = 25)
        } returns Resource.Success(
            FilesResponse(
                files = listOf(file("episode-1", "Episode 01.mkv")),
                nextPageToken = "page-2"
            )
        )
        coEvery {
            driveRepository.getFiles(query = query, pageToken = "page-2", pageSize = 25)
        } coAnswers {
            nextPageAttempts++
            if (nextPageAttempts == 1) {
                nextPageStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    nextPageCancelled.complete(Unit)
                }
            } else {
                Resource.Success(
                    FilesResponse(
                        files = listOf(file("episode-2", "Episode 02.mkv")),
                        nextPageToken = null
                    )
                )
            }
        }

        val firstPageJob = viewModel.queryFiles(query)
        withTimeout(3_000) { firstPageJob.join() }
        awaitFilesSuccess(listOf("episode-1"))
        assertTrue(!viewModel.isLastPage)

        val interruptedPageJob = viewModel.queryFiles(query)
        withTimeout(3_000) { nextPageStarted.await() }
        viewModel.cancelPendingPageRequest()
        withTimeout(3_000) { nextPageCancelled.await() }

        assertTrue(interruptedPageJob.isCancelled)
        assertTrue(viewModel.shouldResumePageRequest)
        awaitFilesSuccess(listOf("episode-1"))

        val resumedPageJob = viewModel.queryFiles(query)
        withTimeout(3_000) { resumedPageJob.join() }
        awaitFilesSuccess(listOf("episode-1", "episode-2"))
        assertTrue(viewModel.isLastPage)
        coVerify(exactly = 2) {
            driveRepository.getFiles(query = query, pageToken = "page-2", pageSize = 25)
        }
    }

    @Test
    fun failedNextPageKeepsVisibleFilesAndAddsRetryActionForSameCursor() = runBlocking {
        val query = "'season-folder' in parents and trashed=false"
        var nextPageAttempts = 0

        coEvery {
            driveRepository.getFiles(query = query, pageToken = null, pageSize = 25)
        } returns Resource.Success(
            FilesResponse(
                files = listOf(file("episode-1", "Episode 01.mkv")),
                nextPageToken = "page-2"
            )
        )
        coEvery {
            driveRepository.getFiles(query = query, pageToken = "page-2", pageSize = 25)
        } coAnswers {
            nextPageAttempts++
            if (nextPageAttempts == 1) {
                Resource.Error("offline", errorKind = ResourceErrorKind.NETWORK)
            } else {
                Resource.Success(
                    FilesResponse(
                        files = listOf(file("episode-2", "Episode 02.mkv")),
                        nextPageToken = null
                    )
                )
            }
        }

        viewModel.queryFiles(query).join()
        awaitFilesSuccess(listOf("episode-1"))

        viewModel.queryFiles(query).join()
        val failedPage = viewModel.filesList.value as? Resource.Error
        assertEquals(ResourceErrorKind.NETWORK, failedPage?.errorKind)
        assertEquals(
            listOf("episode-1"),
            failedPage?.data.orEmpty()
                .filterIsInstance<FilesDataModel.File>()
                .map { it.driveFile.id }
        )
        assertTrue(failedPage?.data.orEmpty().last() is FilesDataModel.PageError)
        assertTrue(viewModel.hasFailed)

        viewModel.queryFiles(query).join()
        awaitFilesSuccess(listOf("episode-1", "episode-2"))
        assertFalse(viewModel.hasFailed)
        assertTrue(viewModel.isLastPage)
        coVerify(exactly = 2) {
            driveRepository.getFiles(query = query, pageToken = "page-2", pageSize = 25)
        }
    }

    private suspend fun awaitFilesSuccess(expectedIds: List<String>) {
        withTimeout(3_000) {
            while (true) {
                val current = viewModel.filesList.value as? Resource.Success
                if (current == null) {
                    delay(10)
                    continue
                }
                val ids = current.data.orEmpty()
                    .filterIsInstance<FilesDataModel.File>()
                    .map { it.driveFile.id }
                if (ids == expectedIds) return@withTimeout
                delay(10)
            }
        }
    }

    private fun file(id: String, name: String) = File(
        id = id,
        name = name,
        size = 1_000L,
        iconLink = "",
        mimeType = "video/x-matroska"
    )
}
