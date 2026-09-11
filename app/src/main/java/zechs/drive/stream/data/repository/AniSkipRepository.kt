package zechs.drive.stream.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.remote.AniSkipApi
import zechs.drive.stream.utils.MatroskaChapterParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniSkipRepository @Inject constructor(
    private val aniSkipApi: Lazy<AniSkipApi>
) {
    companion object {
        private const val TAG = "AniSkipRepository"
    }

    suspend fun getSkipChapters(
        malId: Long,
        episodeNumber: Int,
        episodeLength: Double = 0.0
    ): List<MatroskaChapterParser.ParsedChapter> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching AniSkip timestamps for MAL ID: $malId, ep: $episodeNumber")
            val response = aniSkipApi.get().getSkipTimes(
                malId = malId,
                episodeNumber = episodeNumber,
                types = listOf("op", "ed", "mixed-ed", "recap"),
                episodeLength = episodeLength
            )

            if (!response.isSuccessful) {
                Log.d(TAG, "AniSkip returned code ${response.code()} for malId=$malId ep=$episodeNumber")
                return@withContext emptyList()
            }

            val body = response.body()
            if (body == null || !body.found || body.results.isEmpty()) {
                Log.d(TAG, "No AniSkip intervals found for malId=$malId ep=$episodeNumber")
                return@withContext emptyList()
            }

            val chapters = body.results.mapIndexed { index, item ->
                val type = when (item.skipType.lowercase()) {
                    "op" -> MatroskaChapterParser.ChapterType.OPENING
                    "ed", "mixed-ed" -> MatroskaChapterParser.ChapterType.ENDING
                    "recap" -> MatroskaChapterParser.ChapterType.RECAP
                    else -> MatroskaChapterParser.ChapterType.OTHER
                }
                val title = when (type) {
                    MatroskaChapterParser.ChapterType.OPENING -> "Abertura"
                    MatroskaChapterParser.ChapterType.ENDING -> "Créditos"
                    MatroskaChapterParser.ChapterType.RECAP -> "Recap"
                    else -> "Pular ${index + 1}"
                }
                MatroskaChapterParser.ParsedChapter(
                    index = index + 1,
                    title = title,
                    startTimeMs = (item.interval.startTime * 1000).toLong(),
                    endTimeMs = (item.interval.endTime * 1000).toLong(),
                    type = type
                )
            }

            Log.d(TAG, "AniSkip found ${chapters.size} skip intervals for malId=$malId ep=$episodeNumber")
            return@withContext chapters
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get AniSkip skip times", e)
            return@withContext emptyList()
        }
    }
}
