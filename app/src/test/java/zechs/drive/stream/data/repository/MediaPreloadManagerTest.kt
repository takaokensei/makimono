package zechs.drive.stream.data.repository

import android.content.Context
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.local.CatalogEntry
import zechs.drive.stream.data.local.FolderMetadata
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FileCollectionResult
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.remote.AnimeMetadata
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.EpisodeDetails
import zechs.drive.stream.data.remote.EpisodeMetadataResolver
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.state.Resource

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPreloadManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val driveRepository = mockk<DriveRepository>(relaxed = true)
    private val folderMetadataRepository = mockk<FolderMetadataRepository>(relaxed = true)
    private val animePosterResolver = mockk<AnimePosterResolver>(relaxed = true)
    private val episodeMetadataResolver = mockk<EpisodeMetadataResolver>(relaxed = true)
    private val catalogRepository = mockk<CatalogRepository>(relaxed = true)
    private val watchListRepository = mockk<WatchListRepository>(relaxed = true)
    private val franchiseGroupingManager = mockk<FranchiseGroupingManager>(relaxed = true)
    private val profileManager = mockk<ProfileManager>(relaxed = true)

    private lateinit var preloadManager: MediaPreloadManager

    @Before
    fun setUp() {
        preloadManager = MediaPreloadManager(
            context = context,
            driveRepository = Lazy { driveRepository },
            folderMetadataRepository = folderMetadataRepository,
            animePosterResolver = animePosterResolver,
            episodeMetadataResolver = episodeMetadataResolver,
            catalogRepository = catalogRepository,
            watchListRepository = watchListRepository,
            franchiseGroupingManager = franchiseGroupingManager,
            profileManager = profileManager
        )
    }

    @Test
    fun `strict cache-first policy skips network resolver when Room metadata is complete`() = runTest {
        val cachedMeta = FolderMetadata(
            folderId = "folder_frieren",
            folderName = "Sousou no Frieren",
            posterUrl = "https://img/frieren_poster.jpg",
            bannerUrl = "https://img/frieren_banner.jpg",
            synopsis = "Frieren travels after the hero party defeat the demon king.",
            score = 9.1f
        )
        coEvery { folderMetadataRepository.getAllMetadata() } returns listOf(cachedMeta)
        coEvery { folderMetadataRepository.getMetadata("folder_frieren") } returns cachedMeta

        val files = listOf(
            DriveFile(
                id = "folder_frieren",
                name = "Sousou no Frieren",
                size = null,
                mimeType = "application/vnd.google-apps.folder",
                iconLink = null,
                shortcutDetails = ShortcutDetails(),
                starred = Starred.UNSTARRED
            )
        )

        preloadManager.preloadMetadataParallel(files)

        // Strict cache-first verification: animePosterResolver MUST NOT be called!
        coVerify(exactly = 0) { animePosterResolver.resolveMetadata(any()) }
    }

    @Test
    fun `concurrent metadata preloads resolve a folder only once`() = runTest {
        val resolverEntered = CompletableDeferred<Unit>()
        val releaseResolver = CompletableDeferred<Unit>()
        val folder = DriveFile(
            id = "folder_shared",
            name = "Shared Show",
            size = null,
            mimeType = "application/vnd.google-apps.folder",
            iconLink = null,
            shortcutDetails = ShortcutDetails(),
            starred = Starred.UNSTARRED
        )
        coEvery { folderMetadataRepository.getAllMetadata() } returns emptyList()
        coEvery { folderMetadataRepository.getMetadata("folder_shared") } returns null
        coEvery { animePosterResolver.resolveMetadata("Shared Show") } coAnswers {
            resolverEntered.complete(Unit)
            releaseResolver.await()
            null
        }

        val first = async { preloadManager.preloadMetadataParallel(listOf(folder)) }
        resolverEntered.await()
        val second = async { preloadManager.preloadMetadataParallel(listOf(folder)) }
        runCurrent()

        coVerify(exactly = 1) { animePosterResolver.resolveMetadata("Shared Show") }

        releaseResolver.complete(Unit)
        awaitAll(first, second)
    }

    @Test
    fun `resolves and saves metadata when Room metadata is missing`() = runTest {
        coEvery { folderMetadataRepository.getAllMetadata() } returns emptyList()
        coEvery { folderMetadataRepository.getMetadata("folder_bleach") } returns null

        val resolved = AnimeMetadata(
            posterUrl = "https://img/bleach_poster.jpg",
            bannerUrl = "https://img/bleach_banner.jpg",
            synopsis = "Ichigo fights the Quincy empire.",
            score = 8.9f
        )
        coEvery { animePosterResolver.resolveMetadata("Bleach TYBW") } returns resolved

        val files = listOf(
            DriveFile(
                id = "folder_bleach",
                name = "Bleach TYBW",
                size = null,
                mimeType = "application/vnd.google-apps.folder",
                iconLink = null,
                shortcutDetails = ShortcutDetails(),
                starred = Starred.UNSTARRED
            )
        )

        preloadManager.preloadMetadataParallel(files)

        coVerify(exactly = 1) { animePosterResolver.resolveMetadata("Bleach TYBW") }
        coVerify(exactly = 1) {
            folderMetadataRepository.saveAnimeMetadata("folder_bleach", "Bleach TYBW", resolved)
        }
    }

    @Test
    fun `preloads episodes and thumbnail cards for priority series`() = runTest {
        coEvery { catalogRepository.getChildren("folder_1") } returns emptyList()
        coEvery {
            driveRepository.getAllFilesDetailed(any(), any())
        } returns Resource.Success(
            FileCollectionResult(
                files = listOf(
                    File(
                        id = "ep1",
                        name = "Bleach E01.mkv",
                        size = 1000L,
                        iconLink = "",
                        mimeType = "video/x-matroska"
                    )
                ),
                isComplete = true,
                nextPageToken = null,
                pagesFetched = 1,
                queryContext = zechs.drive.stream.data.model.DriveQueryContext("query", 100, "name")
            )
        )

        val epDetails = listOf(
            EpisodeDetails(
                season = 1,
                episodeNumber = 1,
                name = "The Blood Warfare",
                synopsis = "War begins",
                previewImageUrl = "https://img/ep1_thumb.jpg"
            )
        )
        coEvery { episodeMetadataResolver.fetchShowEpisodes(any()) } returns epDetails

        preloadManager.preloadSingleSeriesEpisodesAndThumbnails("folder_1", "Bleach")

        coVerify(exactly = 1) {
            catalogRepository.cacheFolderContents("folder_1", any())
        }
        coVerify(exactly = 1) {
            episodeMetadataResolver.fetchShowEpisodes("Bleach")
        }
    }
}
