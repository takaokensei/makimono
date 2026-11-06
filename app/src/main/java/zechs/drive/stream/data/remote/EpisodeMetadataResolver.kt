package zechs.drive.stream.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import zechs.drive.stream.utils.AppLog
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class EpisodeDetails(
    val season: Int,
    val episodeNumber: Int,
    val name: String?,
    val synopsis: String?,
    val previewImageUrl: String?,
    val ageRating: String? = null
)

@Singleton
class EpisodeMetadataResolver @Inject constructor() {

    companion object {
        private const val TAG = "EpisodeMetadataResolver"
        private const val TVMAZE_BASE = "https://api.tvmaze.com"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val showEpisodesCache = ConcurrentHashMap<String, List<EpisodeDetails>>()

    suspend fun fetchShowEpisodes(title: String): List<EpisodeDetails> = withContext(Dispatchers.IO) {
        val clean = AnimePosterResolver.cleanAnimeTitle(title).trim()
        if (clean.isBlank()) return@withContext emptyList()
        val cacheKey = clean.lowercase(Locale.ROOT)
        showEpisodesCache[cacheKey]?.let { return@withContext it }

        val list = queryTvMaze(clean)
        if (list.isNotEmpty()) {
            showEpisodesCache[cacheKey] = list
        }
        list
    }

    private fun queryTvMaze(cleanTitle: String): List<EpisodeDetails> {
        val candidates = buildSearchQueries(cleanTitle)
        for (q in candidates) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = "$TVMAZE_BASE/singlesearch/shows?q=$encoded&embed=episodes"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Makimono-App/1.4.20")
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = JSONObject(body)
                    val epsArray = root.optJSONObject("_embedded")?.optJSONArray("episodes") ?: return@use

                    val result = mutableListOf<EpisodeDetails>()
                    for (i in 0 until epsArray.length()) {
                        val item = epsArray.getJSONObject(i)
                        val season = item.optInt("season", 1)
                        val number = item.optInt("number", i + 1)
                        val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" }
                        val summaryRaw = item.optString("summary")
                        val cleanSummary = if (summaryRaw.isNotBlank() && summaryRaw != "null") {
                            summaryRaw.replace(Regex("<.*?>"), "")
                                .replace("&amp;", "&")
                                .replace("&quot;", "\"")
                                .trim()
                        } else null

                        val imgObj = item.optJSONObject("image")
                        val preview = imgObj?.optString("medium")?.takeIf { it.startsWith("http") }
                            ?: imgObj?.optString("original")?.takeIf { it.startsWith("http") }

                        result.add(
                            EpisodeDetails(
                                season = season,
                                episodeNumber = number,
                                name = name,
                                synopsis = cleanSummary,
                                previewImageUrl = preview
                            )
                        )
                    }

                    if (result.isNotEmpty()) {
                        AppLog.d(TAG, "Resolved ${result.size} episodes from TVmaze for '$cleanTitle' (query='$q')")
                        return result
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "TVmaze query failed for '$q': ${e.message}")
            }
        }
        return emptyList()
    }

    internal fun buildSearchQueries(title: String): List<String> {
        val list = mutableListOf<String>()
        val lower = title.lowercase(Locale.ROOT)
        if (lower.contains("sennen kessen") || lower.contains("kashin-tan") || lower.contains("kashin tan") ||
            lower.contains("thousand-year") || lower.contains("soukoku")
        ) {
            list.add("Bleach: Thousand-Year Blood War")
        }
        list.add(title)
        if (lower.contains("bleach")) {
            list.add("Bleach")
        }
        if (lower.contains("bakemonogatari") || lower.contains("kizumonogatari") || lower.contains("monogatari")) {
            list.add("Monogatari Series")
            list.add("Bakemonogatari")
        }
        if (lower.contains("slime datta ken") || lower.contains("tensei shitara")) {
            list.add("That Time I Got Reincarnated as a Slime")
        }
        if (lower.contains("mentalist") || lower.contains("mentalista")) {
            list.add("The Mentalist")
        }
        return list.distinct()
    }

    /**
     * Resolves metadata for an individual episode.
     */
    fun findEpisode(
        episodes: List<EpisodeDetails>,
        season: Int?,
        episodeNumber: Int?,
        canonicalName: String? = null,
        filenameOrArc: String? = null
    ): EpisodeDetails? {
        if (episodes.isEmpty()) return null

        val inferredSeason = when {
            season != null && season > 1 -> season
            filenameOrArc != null -> {
                val lower = filenameOrArc.lowercase(Locale.ROOT)
                when {
                    lower.contains("kashin-tan") || lower.contains("kashin tan") || lower.contains("conflict") ||
                        lower.contains("soukoku") || lower.contains("part 3") || lower.contains("part iii") ||
                        lower.contains("season 3") || lower.contains("s03") || lower.contains("s3") -> 3
                    lower.contains("ketsubetsu-tan") || lower.contains("ketsubetsu tan") || lower.contains("separation") ||
                        lower.contains("part 2") || lower.contains("part ii") || lower.contains("season 2") ||
                        lower.contains("s02") || lower.contains("s2") -> 2
                    lower.contains("sennen kessen") || lower.contains("thousand-year") || lower.contains("blood warfare") ||
                        lower.contains("part 1") || lower.contains("part i") || lower.contains("season 1") ||
                        lower.contains("s01") || lower.contains("s1") -> 1
                    else -> season
                }
            }
            else -> season
        }

        // 1. Match by inferred season and episodeNumber
        if (inferredSeason != null && episodeNumber != null) {
            val match = episodes.firstOrNull { it.season == inferredSeason && it.episodeNumber == episodeNumber }
            if (match != null) return match
        }

        // Direct (season, episodeNumber) if different
        if (season != null && episodeNumber != null && season != inferredSeason) {
            val match = episodes.firstOrNull { it.season == season && it.episodeNumber == episodeNumber }
            if (match != null) return match
        }

        // 2. Match by canonical title if present
        if (!canonicalName.isNullOrBlank()) {
            val normCanon = canonicalName.lowercase(Locale.ROOT).trim()
            val match = episodes.firstOrNull { ep ->
                ep.name?.lowercase(Locale.ROOT)?.trim()?.let {
                    it == normCanon || it.contains(normCanon) || normCanon.contains(it)
                } == true
            }
            if (match != null) return match
        }

        // 3. Match by episodeNumber across single season or overall if season was not explicitly inferred > 1
        if (episodeNumber != null && (inferredSeason == null || inferredSeason <= 1)) {
            val match = episodes.firstOrNull { it.episodeNumber == episodeNumber }
            if (match != null) return match
        }

        return null
    }
}
