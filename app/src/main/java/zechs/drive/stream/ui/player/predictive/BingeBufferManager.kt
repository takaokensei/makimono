package zechs.drive.stream.ui.player.predictive

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.MatroskaChapterParser
import zechs.drive.stream.utils.util.Constants.Companion.DRIVE_API
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apple TV / Netflix Grade Predictive Binge-Watch Buffer Manager.
 *
 * Automatically monitors playback progress and pre-warms the next episode
 * when approaching the end of the current episode (~75-80% progress or during
 * the binge countdown card).
 *
 * Pre-warming pipeline:
 * 1. Pre-warms the Google Drive OAuth Bearer token in memory (0ms access latency).
 * 2. Issues an HTTP Range request for the first 1MB of the next episode container,
 *    establishing TCP/TLS keep-alive connections to Google Drive edge servers
 *    and priming the CDN chunk cache.
 * 3. Extracts MKV EBML chapters ahead of time so they are instantly ready at switchover.
 * 4. Pre-fetches AniSkip skip timestamps for the next episode.
 * 5. Pre-matches subtitles from the folder subtitle list.
 */
@Singleton
class BingeBufferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenProvider: Lazy<TokenProvider>,
    private val aniSkipRepository: Lazy<AniSkipRepository>
) {
    companion object {
        private const val TAG = "BingeBufferManager"
        private const val EXPIRY_MS = 300_000L // 5 minutes validity
    }

    data class PrewarmedEpisode(
        val fileId: String,
        val title: String,
        val token: String?,
        val streamUri: String,
        val chapters: List<MatroskaChapterParser.ParsedChapter>,
        val aniSkipChapters: List<MatroskaChapterParser.ParsedChapter>,
        val matchingSubtitles: List<SubtitleItem>,
        val isHeadBuffered: Boolean,
        val createdAt: Long = System.currentTimeMillis()
    )

    private var activePrewarmJob: Job? = null
    @Volatile
    private var prewarmedEpisode: PrewarmedEpisode? = null

    @VisibleForTesting
    internal var chapterExtractor: suspend (streamUrl: String, token: String?) -> List<MatroskaChapterParser.ParsedChapter> =
        { url, tok -> MatroskaChapterParser.extractChapters(url, tok) }

    /**
     * Triggers predictive pre-warming of the next episode in the background.
     */
    fun prewarmNextEpisode(
        scope: CoroutineScope,
        nextItem: PlaylistItem,
        currentMalAnime: MalAnimeNode?,
        folderSubtitles: List<SubtitleItem>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Job? {
        val targetFileId = nextItem.fileId
        if (targetFileId.isBlank()) return null

        val existing = prewarmedEpisode
        if (existing != null && existing.fileId == targetFileId && (System.currentTimeMillis() - existing.createdAt < EXPIRY_MS)) {
            Log.d(TAG, "Next episode '${nextItem.title}' is already pre-warmed and warm in cache.")
            return null
        }

        activePrewarmJob?.cancel()
        val job = scope.launch(dispatcher) {
            try {
                Log.d(TAG, "Starting predictive binge buffer pre-warm for: ${nextItem.title} ($targetFileId)")
                val token = tokenProvider.get().validToken()
                val streamUri = "$DRIVE_API/files/${targetFileId}?supportsAllDrives=True&alt=media"

                // 1. Pre-warm HTTP connection & parse Matroska chapters from initial 1MB
                val chapters = try {
                    withTimeoutOrNull(6_000L) {
                        chapterExtractor(streamUri, token)
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "Predictive MKV chapter extraction error", e)
                    emptyList()
                }

                // 2. Pre-fetch AniSkip skip chapters if anime is matched
                val parsed = EpisodeParser.parse(nextItem.title)
                val nextEpNum = parsed.episode?.toInt() ?: 0
                val aniSkipChapters = if (currentMalAnime != null && nextEpNum > 0) {
                    try {
                        withTimeoutOrNull(4_000L) {
                            aniSkipRepository.get().getSkipChapters(
                                malId = currentMalAnime.id,
                                episodeNumber = nextEpNum,
                                episodeLength = 0.0
                            )
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.w(TAG, "Predictive AniSkip pre-fetch error", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // 3. Pre-match subtitles
                val matchingSubs = folderSubtitles.filter { it.matchesVideo(nextItem.title) }

                prewarmedEpisode = PrewarmedEpisode(
                    fileId = targetFileId,
                    title = nextItem.title,
                    token = token,
                    streamUri = streamUri,
                    chapters = chapters,
                    aniSkipChapters = aniSkipChapters,
                    matchingSubtitles = matchingSubs,
                    isHeadBuffered = chapters.isNotEmpty()
                )
                Log.d(TAG, "Predictive binge pre-warm SUCCESS for '${nextItem.title}' (${chapters.size} mkv chapters, ${aniSkipChapters.size} aniskip chapters, ${matchingSubs.size} subtitles)")
            } catch (e: CancellationException) {
                Log.d(TAG, "Predictive pre-warming cancelled")
            } catch (e: Exception) {
                Log.w(TAG, "Predictive pre-warming failed", e)
            }
        }
        activePrewarmJob = job
        return job
    }

    /**
     * Consumes pre-warmed data for the specified fileId if available.
     * Consuming removes it from the cache so each file is consumed once.
     */
    fun consumePrewarmedData(fileId: String): PrewarmedEpisode? {
        val existing = prewarmedEpisode
        if (existing != null && existing.fileId == fileId && (System.currentTimeMillis() - existing.createdAt < EXPIRY_MS)) {
            prewarmedEpisode = null
            Log.d(TAG, "Consumed pre-warmed data for '$fileId' (instant startup achieved!)")
            return existing
        }
        return null
    }

    /**
     * Clears any cached pre-warmed episode and cancels background work.
     */
    fun clear() {
        activePrewarmJob?.cancel()
        activePrewarmJob = null
        prewarmedEpisode = null
    }
}
