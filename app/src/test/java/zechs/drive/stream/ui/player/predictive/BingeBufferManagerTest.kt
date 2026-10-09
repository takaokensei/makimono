package zechs.drive.stream.ui.player.predictive

import android.content.Context
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.TokenProvider

@OptIn(ExperimentalCoroutinesApi::class)
class BingeBufferManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val tokenProvider = mockk<TokenProvider>()
    private val aniSkipRepository = mockk<AniSkipRepository>()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var bingeBufferManager: BingeBufferManager

    @Before
    fun setUp() {
        coEvery { tokenProvider.validToken() } returns "mock_bearer_token_123"
        coEvery { aniSkipRepository.getSkipChapters(any(), any(), any()) } returns emptyList()

        bingeBufferManager = BingeBufferManager(
            context = context,
            tokenProvider = Lazy { tokenProvider },
            aniSkipRepository = Lazy { aniSkipRepository }
        ).apply {
            chapterExtractor = { _, _ -> emptyList() }
        }
    }

    @Test
    fun `consumePrewarmedData returns null when nothing pre-warmed`() {
        assertNull(bingeBufferManager.consumePrewarmedData("file_123"))
    }

    @Test
    fun `prewarmNextEpisode caches episode data and consumePrewarmedData returns it once`() = runTest(testDispatcher) {
        val nextEpisode = PlaylistItem(
            fileId = "file_next_456",
            title = "Dandadan - 02.mkv",
            thumbnailLink = "https://thumb.url/2"
        )
        val subs = listOf(
            SubtitleItem(id = "sub1", name = "Dandadan - 02.por.srt", languageLabel = "Português", languageCode = "por")
        )

        val job = bingeBufferManager.prewarmNextEpisode(
            scope = testScope,
            nextItem = nextEpisode,
            currentMalAnime = null,
            folderSubtitles = subs,
            dispatcher = testDispatcher
        )
        job?.join()

        // Verify prewarmed data is consumable
        val prewarmed = bingeBufferManager.consumePrewarmedData("file_next_456")
        assertNotNull(prewarmed)
        assertEquals("file_next_456", prewarmed!!.fileId)
        assertEquals("Dandadan - 02.mkv", prewarmed.title)
        assertEquals("mock_bearer_token_123", prewarmed.token)
        assertEquals(1, prewarmed.matchingSubtitles.size)

        // Second consumption for same file should be null (already consumed)
        assertNull(bingeBufferManager.consumePrewarmedData("file_next_456"))
    }

    @Test
    fun `consumePrewarmedData returns null for mismatched fileId`() = runTest(testDispatcher) {
        val nextEpisode = PlaylistItem(
            fileId = "file_target",
            title = "Episode 03.mkv",
            thumbnailLink = null
        )

        val job = bingeBufferManager.prewarmNextEpisode(
            scope = testScope,
            nextItem = nextEpisode,
            currentMalAnime = null,
            folderSubtitles = emptyList(),
            dispatcher = testDispatcher
        )
        job?.join()

        assertNull(bingeBufferManager.consumePrewarmedData("other_file_id"))
    }

    @Test
    fun `clear wipes cached data`() = runTest(testDispatcher) {
        val nextEpisode = PlaylistItem(
            fileId = "file_to_clear",
            title = "Episode 04.mkv",
            thumbnailLink = null
        )

        val job = bingeBufferManager.prewarmNextEpisode(
            scope = testScope,
            nextItem = nextEpisode,
            currentMalAnime = null,
            folderSubtitles = emptyList(),
            dispatcher = testDispatcher
        )
        job?.join()

        bingeBufferManager.clear()
        assertNull(bingeBufferManager.consumePrewarmedData("file_to_clear"))
    }
}
