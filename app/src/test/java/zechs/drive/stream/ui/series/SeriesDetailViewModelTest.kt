package zechs.drive.stream.ui.series

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
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
        runBlocking { viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin() }
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
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) {
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
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) {
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
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) {
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
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) {
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
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) {
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
        org.junit.Assert.assertNull(viewModel.getDownloadedSubtitles("test_file"))
    }

    @Test
    fun humanizeFolderTitleReplacesUnderscoresAndCleansBrackets() {
        assertEquals("Downloads Diretos", SeriesDetailViewModel.humanizeFolderTitle("Downloads_Diretos"))
        assertEquals("Frieren", SeriesDetailViewModel.humanizeFolderTitle("[SubsPlease] Frieren (1080p)"))
    }
    @Test
    fun traversesNestedSeasonsEvenWithDirectVideosAndShortcutCycles() = runBlocking {
        fun video(id: String) = File(id, "BLEACH - $id.mkv", 1000L, "", "video/x-matroska")
        fun folder(id: String) = File(id, id, null, "", "application/vnd.google-apps.folder")
        fun response(vararg files: File) = Resource.Success(FileCollectionResult(
            files.toList(), true, null, 1, DriveQueryContext("query", 100, "name")))
        coEvery { driveRepository.getAllFilesDetailed(any(), any()) } answers {
            when {
                firstArg<String>().startsWith("'folder1'") -> response(video("01"), video("02"), video("03"), folder("season"))
                firstArg<String>().startsWith("'season'") -> response(folder("language"))
                firstArg<String>().startsWith("'language'") -> response(video("04"), folder("folder1"))
                else -> response()
            }
        }
        viewModel.loadSeriesDetails("folder1", "BLEACH")
        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) delay(10)
        }
        val state = viewModel.uiState.value as SeriesDetailUiState.Success
        assertEquals(4, state.currentEpisodes.size)
        io.mockk.coVerify(exactly = 1) {
            driveRepository.getAllFilesDetailed("'folder1' in parents and trashed = false", 100)
        }
    }

    @Test
    fun failedChildListingNeverClaimsTheFolderIsEmpty() = runBlocking {
        coEvery { driveRepository.getAllFilesDetailed(any(), any()) } answers {
            if (firstArg<String>().startsWith("'folder1'")) Resource.Success(FileCollectionResult(
                listOf(File("season", "Season", null, "", "application/vnd.google-apps.folder")),
                true, null, 1, DriveQueryContext("query", 100, "name")))
            else Resource.Error("offline", errorKind = ResourceErrorKind.NETWORK)
        }
        viewModel.loadSeriesDetails("folder1", "BLEACH")
        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) delay(10)
        }
        assertTrue((viewModel.uiState.value as SeriesDetailUiState.Error).error is AppError.Network)
    }


    @Test fun cachedTransportStreamWithIncorrectDriveMimeRemainsAvailableOffline() = runBlocking {
        coEvery { driveRepository.getAllFilesDetailed(any(), any()) } returns
            Resource.Error(message = "Offline", errorKind = ResourceErrorKind.NETWORK)
        coEvery { catalogRepository.getChildren("bleach-season") } returns listOf(
            zechs.drive.stream.data.local.CatalogEntry("ts-01",
                "[shincaps] BLEACH Sennen Kessen-hen ~Kashin-tan~ - 01 (BS-TX 1440x1080 MPEG2 AAC).ts",
                "application/x-tex-tfm", "bleach-season"))
        viewModel.loadSeriesDetails("bleach-season", "BLEACH Sennen Kessen-hen - Kashin-tan")
        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) delay(10)
        }
        val state = viewModel.uiState.value
        assertTrue(state is SeriesDetailUiState.Success)
        assertEquals(listOf("ts-01"), (state as SeriesDetailUiState.Success).currentEpisodes.map { it.file.id })
        assertTrue(state.hasIncompleteDriveListing)
    }

    private fun driveEpisode(id: String) = Resource.Success(FileCollectionResult(
        listOf(File(id, "BLEACH - 01.mkv", 1000L, "", "video/x-matroska")), true, null, 1,
        DriveQueryContext("query", 100, "name")))

    private suspend fun awaitDetailsIdle() {
        withTimeout(3_000) {
            while (viewModel.uiState.value is SeriesDetailUiState.Loading ||
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.any { it.isActive } == true) delay(10)
        }
    }

    private suspend fun loadTwoPartFranchise(): SeriesDetailUiState.Success {
        val first = FranchiseGroupingManager.FranchiseMember("folder1", "Bleach", "Bleach", 2004, 366, false, score = 7.9f)
        val second = first.copy(folderId = "folder2", folderName = "Kashin-tan", arcTitle = "Kashin-tan", releaseYear = 2026, score = 8.8f)
        coEvery { franchiseGroupingManager.getOrCreateFranchiseHub(any(), any()) } returns
            FranchiseGroupingManager.FranchiseHub("bleach", "BLEACH", "folder1", null, null, members = listOf(first, second))
        coEvery { driveRepository.getAllFilesDetailed(any(), any()) } returns driveEpisode("first-episode")
        viewModel.loadSeriesDetails("folder1", "Bleach")
        awaitDetailsIdle()
        return viewModel.uiState.value as SeriesDetailUiState.Success
    }

    @Test fun switchingSeasonChangesPrimaryPlaybackTargetAndShowsActualAvailableCount() = runBlocking {
        val initial = loadTwoPartFranchise()
        coEvery { driveRepository.getAllFilesDetailed("'folder2' in parents and trashed = false", 100) } returns driveEpisode("second-episode")
        viewModel.selectSeasonTab(initial.seasonTabs.last())
        awaitDetailsIdle()
        val selected = viewModel.uiState.value as SeriesDetailUiState.Success
        assertEquals("folder2", selected.activeMemberFolderId)
        assertEquals("second-episode", selected.continueWatchingItem?.file?.id)
        assertEquals(8.8f, selected.activeArcScore)
        assertEquals("1 Episódio", selected.seasonTabs.last().subtitle)
        assertTrue(!selected.isSeasonLoading)
    }

    @Test fun returningToCachedSeasonCancelsPendingSeasonAndClearsPreviousPlaybackWhileLoading() = runBlocking {
        val initial = loadTwoPartFranchise()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { driveRepository.getAllFilesDetailed("'folder2' in parents and trashed = false", 100) } coAnswers {
            started.complete(Unit); release.await(); driveEpisode("late-episode")
        }
        viewModel.selectSeasonTab(initial.seasonTabs.last())
        withTimeout(3_000) { started.await() }
        val pending = viewModel.uiState.value as SeriesDetailUiState.Success
        assertTrue(pending.isSeasonLoading)
        assertTrue(pending.currentEpisodes.isEmpty())
        assertEquals(null, pending.continueWatchingItem)
        viewModel.selectSeasonTab(initial.seasonTabs.first())
        release.complete(Unit)
        awaitDetailsIdle()
        val restored = viewModel.uiState.value as SeriesDetailUiState.Success
        assertEquals("folder1", restored.activeMemberFolderId)
        assertEquals("first-episode", restored.continueWatchingItem?.file?.id)
        assertTrue(!restored.isSeasonLoading)
    }

    @Test fun seasonNetworkFailureStaysRetryableWithoutPlayingPreviousSeason() = runBlocking {
        val initial = loadTwoPartFranchise()
        coEvery { driveRepository.getAllFilesDetailed("'folder2' in parents and trashed = false", 100) } returns
            Resource.Error("Offline", errorKind = ResourceErrorKind.NETWORK)
        viewModel.selectSeasonTab(initial.seasonTabs.last())
        awaitDetailsIdle()
        val failed = viewModel.uiState.value as SeriesDetailUiState.Success
        assertTrue(failed.seasonLoadError is AppError.Network)
        assertEquals(null, failed.continueWatchingItem)
        assertTrue(!failed.isSeasonLoading)
        coEvery { driveRepository.getAllFilesDetailed("'folder2' in parents and trashed = false", 100) } returns driveEpisode("retried-episode")
        viewModel.selectSeasonTab(failed.seasonTabs.last())
        awaitDetailsIdle()
        assertEquals("retried-episode", (viewModel.uiState.value as SeriesDetailUiState.Success).continueWatchingItem?.file?.id)
    }
}
