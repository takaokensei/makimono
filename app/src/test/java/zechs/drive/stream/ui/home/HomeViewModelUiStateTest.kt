package zechs.drive.stream.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.FileCollectionResult
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.data.repository.CatalogRepository
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelUiStateTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val driveRepository = mockk<DriveRepository>()
    private val watchListRepository = mockk<WatchListRepository>(relaxed = true)
    private val folderMetadataRepository = mockk<FolderMetadataRepository>(relaxed = true)
    private val animePosterResolver = mockk<AnimePosterResolver>(relaxed = true)
    private val tenraiAnimeService = mockk<TenraiAnimeService>(relaxed = true)
    private val favoriteRepository = mockk<FavoriteRepository>(relaxed = true)
    private val catalogRepository = mockk<CatalogRepository>(relaxed = true)
    private val watchQueueRepository = mockk<WatchQueueRepository>(relaxed = true)
    private val profileManager = mockk<ProfileManager>(relaxed = true)
    private val franchiseGroupingManager = mockk<zechs.drive.stream.data.repository.FranchiseGroupingManager>(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            sessionManager = Lazy { sessionManager },
            driveRepository = Lazy { driveRepository },
            watchListRepository = watchListRepository,
            folderMetadataRepository = folderMetadataRepository,
            animePosterResolver = animePosterResolver,
            tenraiAnimeService = tenraiAnimeService,
            favoriteRepository = favoriteRepository,
            catalogRepository = catalogRepository,
            watchQueueRepository = watchQueueRepository,
            profileManager = profileManager,
            franchiseGroupingManager = franchiseGroupingManager
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.every { watchQueueRepository.observeQueue(any()) } returns flowOf(emptyList())
        coEvery { profileManager.getActiveProfile() } returns UserProfile(id = "user1", name = "Test User")
        coEvery { profileManager.getLibraryRoot() } returns Pair("root_folder", "Anime")
        coEvery { folderMetadataRepository.getAllMetadata() } returns emptyList()
        coEvery { favoriteRepository.getFavoritesSync() } returns emptyList()
        coEvery { catalogRepository.getChildren(any()) } returns emptyList()
        coEvery { watchListRepository.getLastWatched() } returns null
        coEvery { watchListRepository.getRecentWatches(any()) } returns emptyList()
        coEvery { watchListRepository.getAllWatches() } returns emptyList()
        coEvery { franchiseGroupingManager.groupLibrary(any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is HomeUiState.Loading)
    }

    @Test
    fun saveFocusUpdatesLastFocusedCoordinates() {
        val viewModel = createViewModel()
        viewModel.onEvent(HomeEvent.SaveFocus(shelfIndex = 2, itemIndex = 4))

        assertEquals(2, viewModel.lastFocusedShelfIndex)
        assertEquals(4, viewModel.lastFocusedItemIndex)
    }

    @Test
    fun searchEventUpdatesSearchQuery() {
        val viewModel = createViewModel()
        viewModel.onEvent(HomeEvent.Search("Sousou no Frieren"))

        assertEquals("Sousou no Frieren", viewModel.searchQuery.value)
    }

    @Test
    fun loadAnimeLibraryErrorEmitsErrorUiState() = kotlinx.coroutines.runBlocking {
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Error("Falha de rede")

        val viewModel = createViewModel()
        viewModel.loadAnimeLibrary()

        kotlinx.coroutines.withTimeout(3_000) {
            while (viewModel.uiState.value is HomeUiState.Loading) {
                kotlinx.coroutines.delay(20)
            }
        }

        val state = viewModel.uiState.value
        assertTrue("Expected HomeUiState.Error but was $state", state is HomeUiState.Error)
        val errorState = state as HomeUiState.Error
        assertEquals("Falha de rede", errorState.message)
    }

    @Test
    fun loadAnimeLibrarySuccessEmitsContentUiState() = kotlinx.coroutines.runBlocking {
        val fakeFiles = listOf(
            zechs.drive.stream.data.model.File(
                id = "anime_1",
                name = "Frieren",
                size = null,
                iconLink = "",
                mimeType = "application/vnd.google-apps.folder",
                thumbnailLink = null,
                shortcutDetails = ShortcutDetails(),
                starred = Starred.UNSTARRED
            )
        )
        val collection = FileCollectionResult(
            files = fakeFiles,
            isComplete = true,
            nextPageToken = null,
            pagesFetched = 1,
            queryContext = zechs.drive.stream.data.model.DriveQueryContext(
                query = "test",
                pageSize = 100,
                orderBy = "folder,name"
            )
        )
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Success(collection)

        val viewModel = createViewModel()
        viewModel.loadAnimeLibrary()

        kotlinx.coroutines.withTimeout(3_000) {
            while (viewModel.uiState.value is HomeUiState.Loading) {
                kotlinx.coroutines.delay(20)
            }
        }

        val state = viewModel.uiState.value
        assertTrue("Expected HomeUiState.Content but was $state", state is HomeUiState.Content)
        val contentState = state as HomeUiState.Content
        assertEquals(1, contentState.libraryFiles.size)
        assertEquals("Frieren", contentState.libraryFiles.first().name)
    }
}
