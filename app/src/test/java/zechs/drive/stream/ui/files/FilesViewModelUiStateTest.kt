package zechs.drive.stream.ui.files

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelUiStateTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val driveRepository = mockk<DriveRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val folderMetadataRepository = mockk<FolderMetadataRepository>(relaxed = true)
    private val animePosterResolver = mockk<AnimePosterResolver>(relaxed = true)
    private val favoriteRepository = mockk<FavoriteRepository>(relaxed = true)

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
            favoriteRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() {
        assertEquals(FilesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun emitsContentStateOnSuccessfulQuery() = runBlocking {
        val testFiles = listOf(
            File(
                id = "id1",
                name = "Episode 1.mkv",
                size = 1000L,
                iconLink = "",
                mimeType = "video/x-matroska"
            ),
            File(
                id = "id2",
                name = "Episode 2.mkv",
                size = 1000L,
                iconLink = "",
                mimeType = "video/x-matroska"
            )
        )
        coEvery { driveRepository.getFiles(any(), any(), any()) } returns Resource.Success(
            FilesResponse(files = testFiles, nextPageToken = null)
        )

        viewModel.onEvent(FilesEvent.QueryFiles("'root' in parents"))

        withTimeout(3_000) {
            while (viewModel.uiState.value is FilesUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is FilesUiState.Content)
        val content = state as FilesUiState.Content
        assertEquals(2, content.fileCount)
    }

    @Test
    fun emitsEmptyStateWhenNoFilesFound() = runBlocking {
        coEvery { driveRepository.getFiles(any(), any(), any()) } returns Resource.Success(
            FilesResponse(files = emptyList(), nextPageToken = null)
        )

        viewModel.onEvent(FilesEvent.QueryFiles("'root' in parents"))

        withTimeout(3_000) {
            while (viewModel.uiState.value is FilesUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is FilesUiState.Empty)
    }

    @Test
    fun emitsErrorStateOnNetworkFailure() = runBlocking {
        coEvery { driveRepository.getFiles(any(), any(), any()) } returns Resource.Error(
            message = "Offline",
            errorKind = ResourceErrorKind.NETWORK
        )

        viewModel.onEvent(FilesEvent.QueryFiles("'root' in parents"))

        withTimeout(3_000) {
            while (viewModel.uiState.value is FilesUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is FilesUiState.Error)
        val errorState = state as FilesUiState.Error
        assertTrue(errorState.error is AppError.Network)
    }

    @Test
    fun saveFocusEventPreservesFocusedPositionInViewModel() {
        viewModel.onEvent(FilesEvent.SaveFocus(5))
        assertEquals(5, viewModel.lastFocusedPosition)
    }
}
