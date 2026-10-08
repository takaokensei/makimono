package zechs.drive.stream.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import zechs.drive.stream.BuildConfig
import java.util.Locale

data class AnimeMetadata(
    val posterUrl: String?,
    val bannerUrl: String? = null,
    val dominantColor: String? = null,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val synopsis: String? = null,
    val score: Float? = null,
    val year: Int? = null,
    val episodeCount: Int? = null,
    val genres: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val clearLogoUrl: String? = null,
    val isMovie: Boolean = false,
    val isAnime: Boolean = true,
    val mediaType: String? = null
)

@Singleton
class AnimePosterResolver @Inject constructor() {

    companion object {
        private const val TAG = "AnimePosterResolver"
        private const val ANILIST_URL = "https://graphql.anilist.co"
        private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/anime"
        private const val KITSU_BASE_URL = "https://kitsu.io/api/edge/anime"

        private const val ANILIST_GRAPHQL_QUERY = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 8) {
                media(search: ${'$'}search, type: ANIME, sort: [SEARCH_MATCH, POPULARITY_DESC]) {
                  id
                  title {
                    romaji
                    english
                    native
                  }
                  coverImage {
                    extraLarge
                    large
                    color
                  }
                  bannerImage
                  description(asHtml: false)
                  genres
                  averageScore
                  seasonYear
                  episodes
                  popularity
                  format
                  status
                  trailer {
                    id
                    site
                  }
                }
              }
            }
        """

        /**
         * Cleans a Google Drive folder name to produce an optimal anime search query.
         * E.g.: "[Erai-raws] Teogonia [1080p CR WEB-DL]" -> "Teogonia"
         *       "Sousou no Frieren (2023)" -> "Sousou no Frieren"
         */
        fun cleanAnimeTitle(folderName: String): String {
            var clean = folderName
            // Remove brackets [ ... ] and ( ... )
            clean = clean.replace(Regex("\\[.*?\\]"), " ")
            clean = clean.replace(Regex("\\(.*?\\)"), " ")
            // Remove HDR10+ explicitly
            clean = clean.replace(Regex("(?i)hdr10\\+"), " ")
            // Remove technical keywords, codecs, HDR, audio channels, and release tags
            clean = clean.replace(
                Regex("(?i)\\b(2160p|1080p|720p|480p|4k|uhd|hevc|avc|x264|x265|h264|h265|bluray|bdrip|brrip|web-dl|webdl|webrip|web|hdtv|remux|imax|dv|dovi|dolby(?:\\s*vision|\\s*digital|\\s*audio)?|dolby|hdr10plus|hdr10|hdr|sdr|10bit|8bit|ddp?5\\.1|dd5\\.1|5\\.1|7\\.1|2\\.0|stereo|truehd|atmos|dts(?:-hd|-x)?|flac|aac|eac3|ac3|dual(?:[\\s_-]*audio)?|multi(?:[\\s_-]*audio)?|multisub|dublado|legendado|repack|proper|internal|complete)\\b"),
                " "
            )
            // Strip standalone 4-digit release years (e.g. 1980..2035) so search engines and titles don't choke on them
            clean = clean.replace(Regex("\\b(19\\d\\d|20[0-3]\\d)\\b"), " ")
            // Clean up dots, underscores, hyphens and extra whitespace
            clean = clean.replace(Regex("[_.]+"), " ")
            clean = clean.replace(Regex("\\s+-\\s*$"), " ")
            clean = clean.trim().replace(Regex("\\s+"), " ")
            clean = clean.trim(' ', '-', '—', '–', ':')
            return clean.ifBlank { folderName.trim() }
        }

        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val TMDB_IMAGE_BASE_POSTER = "https://image.tmdb.org/t/p/w500"
        private const val TMDB_IMAGE_BASE_BACKDROP = "https://image.tmdb.org/t/p/w1280"

        private val TMDB_GENRE_MAP = mapOf(
            28 to "Ação",
            12 to "Aventura",
            16 to "Animação",
            35 to "Comédia",
            80 to "Crime",
            99 to "Documentário",
            18 to "Drama",
            10751 to "Família",
            14 to "Fantasia",
            36 to "História",
            27 to "Terror",
            10402 to "Música",
            9648 to "Mistério",
            10749 to "Romance",
            878 to "Ficção Científica",
            10770 to "Cinema TV",
            53 to "Thriller",
            10752 to "Guerra",
            37 to "Faroeste",
            10759 to "Ação & Aventura",
            10762 to "Kids",
            10763 to "Notícias",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasia",
            10766 to "Novela",
            10767 to "Talk Show",
            10768 to "Guerra & Política"
        )

        fun calculateSimilarity(query: String, candidate: String): Double {
            val qTokens = cleanTokens(query)
            val cTokens = cleanTokens(candidate)
            if (qTokens.isEmpty() || cTokens.isEmpty()) return 0.0
            val qStr = qTokens.joinToString(" ")
            val cStr = cTokens.joinToString(" ")
            if (qStr.contains(cStr) || cStr.contains(qStr)) {
                val minLen = minOf(qStr.length, cStr.length).toDouble()
                val maxLen = maxOf(qStr.length, cStr.length).toDouble()
                return (minLen / maxLen).coerceAtLeast(0.70)
            }
            val common = qTokens.intersect(cTokens).size
            return (2.0 * common) / (qTokens.size + cTokens.size)
        }

        private fun cleanTokens(text: String): Set<String> {
            return text.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() && it.length > 1 }
                .toSet()
        }
    }

    fun cleanAnimeTitle(folderName: String): String = Companion.cleanAnimeTitle(folderName)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val metadataCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, AnimeMetadata>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnimeMetadata>?): Boolean {
                return size > 128
            }
        }
    )

    /**
     * Resolves complete metadata (high-res poster, banner, dominant color, canonical titles, score, synopsis)
     * for a folder using AniList GraphQL as the primary source, falling back to Jikan (MAL) and Kitsu.
     */
    suspend fun resolveMetadata(folderName: String): AnimeMetadata? = withContext(Dispatchers.IO) {
        val query = cleanAnimeTitle(folderName.trim())
        if (query.isBlank()) return@withContext null

        metadataCache[query]?.let { return@withContext it }

        Log.d(TAG, "Resolving metadata for: '$query' (original: '$folderName')")

        // 1. Primary: AniList GraphQL (extraLarge ~460x650 poster + dominant color + banner)
        val aniListMeta = fetchFromAniList(query)
        val aniSimilarity = if (aniListMeta != null) {
            maxOf(
                calculateSimilarity(query, aniListMeta.titleRomaji ?: ""),
                calculateSimilarity(query, aniListMeta.titleEnglish ?: ""),
                calculateSimilarity(query, aniListMeta.titleNative ?: "")
            )
        } else 0.0

        // If AniList returned a high-confidence match (>= 0.45) with a valid poster, use it directly
        if (aniListMeta != null && !aniListMeta.posterUrl.isNullOrBlank() && aniSimilarity >= 0.45) {
            Log.d(TAG, "Successfully matched anime on AniList (confidence $aniSimilarity): ${aniListMeta.titleRomaji}")
            metadataCache[query] = aniListMeta
            return@withContext aniListMeta
        }

        // 2. Fallback to TMDB: If match confidence with anime is very low (< 0.45) or AniList returned null,
        // consider that it is a movie or live-action series and fetch from TMDB
        Log.d(TAG, "AniList confidence low ($aniSimilarity) or not found for '$query'. Checking TMDB for movies/series...")
        val tmdbMeta = fetchFromTmdb(query)
        if (tmdbMeta != null && !tmdbMeta.posterUrl.isNullOrBlank()) {
            val tmdbSimilarity = maxOf(
                calculateSimilarity(query, tmdbMeta.titleRomaji ?: ""),
                calculateSimilarity(query, tmdbMeta.titleEnglish ?: "")
            )
            if (tmdbSimilarity >= 0.35 && (aniListMeta == null || tmdbSimilarity > aniSimilarity)) {
                Log.d(TAG, "Successfully resolved on TMDB (confidence $tmdbSimilarity, isMovie=${tmdbMeta.isMovie}): ${tmdbMeta.titleRomaji}")
                metadataCache[query] = tmdbMeta
                return@withContext tmdbMeta
            }
        }

        // If TMDB didn't match and AniList had something, use AniList
        if (aniListMeta != null && !aniListMeta.posterUrl.isNullOrBlank()) {
            Log.d(TAG, "Using AniList as best available: ${aniListMeta.titleRomaji}")
            metadataCache[query] = aniListMeta
            return@withContext aniListMeta
        }

        // 3. Secondary fallback: Jikan (MAL)
        Log.d(TAG, "AniList & TMDB unavailable or empty. Trying Jikan for '$query'...")
        val jikanMeta = fetchFromJikan(query)
        if (jikanMeta != null && !jikanMeta.posterUrl.isNullOrBlank()) {
            Log.d(TAG, "Successfully fetched metadata from Jikan: ${jikanMeta.posterUrl}")
            metadataCache[query] = jikanMeta
            return@withContext jikanMeta
        }

        // 4. Tertiary fallback: Kitsu
        Log.d(TAG, "Jikan unavailable or empty. Trying Kitsu for '$query'...")
        val kitsuMeta = fetchFromKitsu(query)
        if (kitsuMeta != null && !kitsuMeta.posterUrl.isNullOrBlank()) {
            Log.d(TAG, "Successfully fetched metadata from Kitsu: ${kitsuMeta.posterUrl}")
            metadataCache[query] = kitsuMeta
            return@withContext kitsuMeta
        }

        Log.w(TAG, "No metadata found for '$query'")
        null
    }

    /**
     * Resolves the official anime poster URL for a folder.
     */
    suspend fun resolvePoster(folderName: String): String? {
        return resolveMetadata(folderName)?.posterUrl
    }

    private fun fetchFromAniList(query: String): AnimeMetadata? {
        return try {
            val jsonPayload = JSONObject().apply {
                put("query", ANILIST_GRAPHQL_QUERY)
                put("variables", JSONObject().apply {
                    put("search", query)
                })
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(ANILIST_URL)
                .header("User-Agent", "Makimono/1.4.21 (Android)")
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "AniList API returned HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return null
                val page = data.optJSONObject("Page")
                val mediaList = page?.optJSONArray("media") ?: return null
                if (mediaList.length() == 0) return null

                var bestItem: JSONObject? = null
                var bestScore = -1.0

                for (i in 0 until mediaList.length()) {
                    val candidate = mediaList.optJSONObject(i) ?: continue
                    val tObj = candidate.optJSONObject("title")
                    val romaji = tObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
                    val english = tObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                    val native = tObj?.optString("native")?.takeIf { it.isNotBlank() && it != "null" }

                    val sim = maxOf(
                        calculateSimilarity(query, romaji ?: ""),
                        calculateSimilarity(query, english ?: ""),
                        calculateSimilarity(query, native ?: "")
                    )

                    // Skip candidates with very poor resemblance to the search query
                    if (sim < 0.20) continue

                    var score = sim

                    val status = candidate.optString("status")
                    if (status.equals("NOT_YET_RELEASED", ignoreCase = true) || status.equals("CANCELLED", ignoreCase = true)) {
                        score -= 0.25 // Heavy penalty for unreleased promotional flyers/teasers
                    } else if (status.equals("FINISHED", ignoreCase = true) || status.equals("RELEASING", ignoreCase = true)) {
                        score += 0.15
                    }

                    val pop = candidate.optDouble("popularity", 0.0)
                    score += (pop / 100000.0).coerceAtMost(0.20)

                    val format = candidate.optString("format")
                    if (format.equals("TV", ignoreCase = true) || format.equals("MOVIE", ignoreCase = true)) {
                        score += 0.05
                    } else if (format.equals("MUSIC", ignoreCase = true)) {
                        score -= 0.35
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestItem = candidate
                    }
                }

                val media = bestItem ?: mediaList.optJSONObject(0) ?: return null

                val titleObj = media.optJSONObject("title")
                val romaji = titleObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
                val english = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                val native = titleObj?.optString("native")?.takeIf { it.isNotBlank() && it != "null" }

                val coverObj = media.optJSONObject("coverImage")
                val extraLarge = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() && it != "null" }
                val large = coverObj?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
                val color = coverObj?.optString("color")?.takeIf { it.isNotBlank() && it != "null" }

                val banner = media.optString("bannerImage").takeIf { it.isNotBlank() && it != "null" }
                val desc = media.optString("description").takeIf { it.isNotBlank() && it != "null" }
                val avgScore = media.optInt("averageScore", -1).takeIf { it >= 0 }?.let { it / 10.0f }
                val year = media.optInt("seasonYear", -1).takeIf { it > 0 }
                val episodeCount = media.optInt("episodes", -1).takeIf { it > 0 }

                val genresList = mutableListOf<String>()
                media.optJSONArray("genres")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val g = arr.optString(i)
                        if (g.isNotBlank() && g != "null") genresList.add(g)
                    }
                }

                val trailerObj = media.optJSONObject("trailer")
                val trailerSite = trailerObj?.optString("site")
                val trailerId = trailerObj?.optString("id")
                val trailerUrl = if (trailerSite.equals("youtube", ignoreCase = true) && !trailerId.isNullOrBlank()) {
                    "https://www.youtube.com/watch?v=$trailerId"
                } else null

                AnimeMetadata(
                    posterUrl = extraLarge ?: large,
                    bannerUrl = banner,
                    dominantColor = color,
                    titleRomaji = romaji,
                    titleEnglish = english,
                    titleNative = native,
                    synopsis = desc,
                    score = avgScore,
                    year = year,
                    episodeCount = episodeCount,
                    genres = genresList,
                    trailerUrl = trailerUrl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniList GraphQL exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromJikan(query: String): AnimeMetadata? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$JIKAN_BASE_URL?q=$encodedQuery&limit=1&sfw=true"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Jikan API returned HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                val anime = data.getJSONObject(0)
                val images = anime.optJSONObject("images")
                val jpg = images?.optJSONObject("jpg")
                val webp = images?.optJSONObject("webp")

                val posterUrl = jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: webp?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() && it != "null" }

                val title = anime.optString("title").takeIf { it.isNotBlank() && it != "null" }
                val titleEng = anime.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
                val titleJap = anime.optString("title_japanese").takeIf { it.isNotBlank() && it != "null" }
                val synopsis = anime.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
                val score = anime.optDouble("score", -1.0).takeIf { it >= 0 }?.toFloat()
                val year = anime.optInt("year", -1).takeIf { it > 0 }

                val genresList = mutableListOf<String>()
                anime.optJSONArray("genres")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val g = arr.getJSONObject(i).optString("name")
                        if (g.isNotBlank() && g != "null") genresList.add(g)
                    }
                }

                val trailerUrl = anime.optJSONObject("trailer")?.optString("url")?.takeIf { it.isNotBlank() && it != "null" }

                AnimeMetadata(
                    posterUrl = posterUrl,
                    titleRomaji = title,
                    titleEnglish = titleEng,
                    titleNative = titleJap,
                    synopsis = synopsis,
                    score = score,
                    year = year,
                    genres = genresList,
                    trailerUrl = trailerUrl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jikan API exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromKitsu(query: String): AnimeMetadata? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$KITSU_BASE_URL?filter[text]=$encodedQuery&page[limit]=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Kitsu API returned HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                val anime = data.getJSONObject(0)
                val attributes = anime.optJSONObject("attributes") ?: return null
                val posterImage = attributes.optJSONObject("posterImage")
                val coverImage = attributes.optJSONObject("coverImage")

                val posterUrl = posterImage?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: posterImage?.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: posterImage?.optString("medium")?.takeIf { it.isNotBlank() && it != "null" }

                val bannerUrl = coverImage?.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: coverImage?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }

                val titles = attributes.optJSONObject("titles")
                val enJp = titles?.optString("en_jp")?.takeIf { it.isNotBlank() && it != "null" }
                val en = titles?.optString("en")?.takeIf { it.isNotBlank() && it != "null" }
                val jaJp = titles?.optString("ja_jp")?.takeIf { it.isNotBlank() && it != "null" }
                val synopsis = attributes.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
                val score = attributes.optString("averageRating").toFloatOrNull()?.let { it / 10.0f }
                val ytId = attributes.optString("youtubeVideoId").takeIf { it.isNotBlank() && it != "null" }
                val trailerUrl = ytId?.let { "https://www.youtube.com/watch?v=$it" }

                AnimeMetadata(
                    posterUrl = posterUrl,
                    bannerUrl = bannerUrl,
                    titleRomaji = enJp,
                    titleEnglish = en,
                    titleNative = jaJp,
                    synopsis = synopsis,
                    score = score,
                    trailerUrl = trailerUrl
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kitsu API exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromTmdb(query: String): AnimeMetadata? {
        val apiKey = BuildConfig.TMDB_API_KEY.ifBlank { "15d2ea6d0dc1d476efbca3eba2b9bbfb" }
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/multi?api_key=$apiKey&query=$encodedQuery&language=pt-BR&include_adult=false"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Makimono/1.6.4 (Android)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "TMDB search returned HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return null
                if (results.length() == 0) return null

                // Pick the candidate with highest title similarity among movies & TV shows
                var bestCandidate: JSONObject? = null
                var bestScore = -1.0

                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val mediaType = item.optString("media_type")
                    if (mediaType != "movie" && mediaType != "tv") continue

                    val title = item.optString("title").ifBlank { item.optString("name") }
                    val origTitle = item.optString("original_title").ifBlank { item.optString("original_name") }
                    val score = maxOf(
                        calculateSimilarity(query, title),
                        calculateSimilarity(query, origTitle)
                    )
                    if (score > bestScore) {
                        bestScore = score
                        bestCandidate = item
                    }
                }

                val item = bestCandidate ?: return null
                val mediaType = item.optString("media_type")
                val isMovie = mediaType == "movie"

                val title = item.optString("title").ifBlank { item.optString("name") }
                val origTitle = item.optString("original_title").ifBlank { item.optString("original_name") }
                val posterPath = item.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
                val backdropPath = item.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }

                val posterUrl = posterPath?.let { "$TMDB_IMAGE_BASE_POSTER$it" }
                val bannerUrl = backdropPath?.let { "$TMDB_IMAGE_BASE_BACKDROP$it" }

                val synopsis = item.optString("overview").takeIf { it.isNotBlank() && it != "null" }
                val voteAvg = item.optDouble("vote_average", -1.0).takeIf { it >= 0 }?.toFloat()

                val dateStr = item.optString("release_date").ifBlank { item.optString("first_air_date") }
                val year = if (dateStr.length >= 4) dateStr.substring(0, 4).toIntOrNull() else null

                val genreList = mutableListOf<String>()
                item.optJSONArray("genre_ids")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val gId = arr.optInt(i)
                        TMDB_GENRE_MAP[gId]?.let { genreList.add(it) }
                    }
                }

                AnimeMetadata(
                    posterUrl = posterUrl,
                    bannerUrl = bannerUrl,
                    titleRomaji = title,
                    titleEnglish = origTitle,
                    titleNative = origTitle,
                    synopsis = synopsis,
                    score = voteAvg,
                    year = year,
                    genres = genreList,
                    isMovie = isMovie,
                    isAnime = false,
                    mediaType = mediaType
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "TMDB search exception for '$query': ${e.message}")
            null
        }
    }

}
