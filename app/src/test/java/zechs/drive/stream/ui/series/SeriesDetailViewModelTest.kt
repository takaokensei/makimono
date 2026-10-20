package zechs.drive.stream.ui.series

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.DriveQueryContext
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FileCollectionResult
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.data.remote.AnimeMetadata
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.EpisodeMetadataResolver
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.data.repository.FranchiseGroupingManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val driveRepository = mockk<DriveRepository>(relaxed = true)
    private val tenraiAnimeService = mockk<TenraiAnimeService>(relaxed = true)
    private val watchListRepository = mockk<WatchListRepository>(relaxed = true)
    private val folderMetadataRepository = mockk<FolderMetadataRepository>(relaxed = true)
    private val animePosterResolver = mockk<AnimePosterResolver>(relaxed = true)
    private val favoriteRepository = mockk<FavoriteRepository>(relaxed = true)
    private val followedFolderRepository = mockk<FollowedFolderRepository>(relaxed = true)
    private val watchQueueRepository = mockk<WatchQueueRepository>(relaxed = true)
    private val franchiseGroupingManager = mockk<FranchiseGroupingManager>(relaxed = true)
    private val episodeMetadataResolver = mockk<EpisodeMetadataResolver>(relaxed = true)
    private val catalogRepository = mockk<zechs.drive.stream.data.repository.CatalogRepository>(relaxed = true)

    private lateinit var viewModel: SeriesDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SeriesDetailViewModel.clearCache()
        coEvery { tenraiAnimeService.searchAnime(any()) } returns emptyList()
        coEvery { animePosterResolver.resolveMetadata(any()) } returns null
        coEvery { folderMetadataRepository.getMetadata(any()) } returns null
        coEvery { watchListRepository.getAllWatches(any()) } returns emptyList()
        coEvery { episodeMetadataResolver.fetchShowEpisodes(any()) } returns emptyList()
        coEvery { catalogRepository.getChildren(any()) } returns emptyList()

        viewModel = SeriesDetailViewModel(
            driveRepository,
            tenraiAnimeService,
            watchListRepository,
            folderMetadataRepository,
            animePosterResolver,
            favoriteRepository,
            followedFolderRepository,
            watchQueueRepository,
            franchiseGroupingManager,
            episodeMetadataResolver,
            catalogRepository
        )
    }

    @After
    fun tearDown() {
        SeriesDetailViewModel.clearCache()
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() {
        assertTrue(viewModel.uiState.value is SeriesDetailUiState.Loading)
    }

    @Test
    fun emitsTypedAppErrorOnNetworkFailure() = runBlocking {
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Error(
            message = "Offline",
            errorKind = ResourceErrorKind.NETWORK
        )

        viewModel.loadSeriesDetails("folder1", "Frieren")

        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is SeriesDetailUiState.Error)
        val errorState = state as SeriesDetailUiState.Error
        assertTrue(errorState.error is AppError.Network)
    }

    @Test
    fun emitsEmptyAppErrorWhenFolderHasNoVideoFiles() = runBlocking {
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Success(
            FileCollectionResult(
                files = listOf(
                    File(
                        id = "text1",
                        name = "readme.txt",
                        size = 100L,
                        iconLink = "",
                        mimeType = "text/plain"
                    )
                ),
                isComplete = true,
                nextPageToken = null,
                pagesFetched = 1,
                queryContext = DriveQueryContext("query", 100, "name")
            )
        )

        viewModel.loadSeriesDetails("folder1", "Frieren")

        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is SeriesDetailUiState.Error)
        val errorState = state as SeriesDetailUiState.Error
        assertTrue(errorState.error is AppError.Empty)
    }

    @Test
    fun emitsSuccessStateWhenValidVideosExist() = runBlocking {
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Success(
            FileCollectionResult(
                files = listOf(
                    File(
                        id = "ep1",
                        name = "Frieren E01.mkv",
                        size = 1000L,
                        iconLink = "",
                        mimeType = "video/x-matroska"
                    )
                ),
                isComplete = true,
                nextPageToken = null,
                pagesFetched = 1,
                queryContext = DriveQueryContext("query", 100, "name")
            )
        )

        viewModel.loadSeriesDetails("folder1", "Frieren")

        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is SeriesDetailUiState.Success)
        val successState = state as SeriesDetailUiState.Success
        assertEquals("Frieren", successState.seriesTitle)
        assertEquals(1, successState.currentEpisodes.size)
    }

    @Test
    fun saveFocusPersistsLastFocusedPositionInViewModel() {
        viewModel.onEvent(SeriesDetailEvent.SaveFocus(3))
        assertEquals(3, viewModel.lastFocusedPosition)
    }

    @Test
    fun movieDetailsDistinguishMovieFromSeriesWithCorrectSubtitleAndNoSeasonTabs() = runBlocking {
        coEvery {
            animePosterResolver.resolveMetadata(any())
        } returns AnimeMetadata(
            posterUrl = "https://example.com/poster.jpg",
            isMovie = true,
            titleEnglish = "Your Name"
        )
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Success(
            FileCollectionResult(
                files = listOf(
                    File(
                        id = "movie1",
                        name = "Your Name (2016) 1080p.mkv",
                        size = 5_000_000L,
                        iconLink = "",
                        mimeType = "video/x-matroska"
                    )
                ),
                isComplete = true,
                nextPageToken = null,
                pagesFetched = 1,
                queryContext = DriveQueryContext("query", 100, "name")
            )
        )

        viewModel.loadSeriesDetails("movieFolder", "Your Name")

        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is SeriesDetailUiState.Success)
        val successState = state as SeriesDetailUiState.Success
        assertTrue(successState.isMovie)
        assertEquals("Filme", successState.continueWatchingSubtitle)
        assertTrue(successState.seasonTabs.isEmpty())
    }

    @Test
    fun strictCacheFirstBypassesNetworkWhenRoomMetadataIsComplete() = runBlocking {
        val completeMeta = zechs.drive.stream.data.local.FolderMetadata(
            folderId = "folder1",
            folderName = "Frieren",
            posterUrl = "https://example.com/poster.jpg",
            bannerUrl = "https://example.com/banner.jpg",
            synopsis = "Frieren journeys beyond journey's end.",
            score = 9.2f,
            titleRomaji = "Sousou no Frieren",
            year = 2023
        )
        coEvery { folderMetadataRepository.getMetadata("folder1") } returns completeMeta
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Success(
            FileCollectionResult(
                files = listOf(
                    File(
                        id = "ep1",
                        name = "Frieren E01.mkv",
                        size = 1000L,
                        iconLink = "",
                        mimeType = "video/x-matroska"
                    )
                ),
                isComplete = true,
                nextPageToken = null,
                pagesFetched = 1,
                queryContext = DriveQueryContext("query", 100, "name")
            )
        )

        viewModel.loadSeriesDetails("folder1", "Frieren")

        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading) {
                delay(10)
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state is SeriesDetailUiState.Success)
        val successState = state as SeriesDetailUiState.Success
        assertEquals("Frieren journeys beyond journey's end.", successState.aniListMetadata?.synopsis)
        assertEquals(9.2f, successState.aniListMetadata?.score)

        // Strict cache-first verification: zero network calls to Tenrai or AniList!
        io.mockk.coVerify(exactly = 0) { tenraiAnimeService.searchAnime(any()) }
        io.mockk.coVerify(exactly = 0) { animePosterResolver.resolveMetadata(any()) }
    }

    @Test
    fun getCompletedDownloadPathReturnsNullWhenRepositoryNull() {
        org.junit.Assert.assertNull(viewModel.getCompletedDownloadPath("test_file"))
        org.junit.Assert.assertFalse(viewModel.isEpisodeDownloaded("test_file"))
        org.junit.Assert.assertFalse(viewModel.isEpisodeDownloading("test_file"))
    }
}
