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
    val genres: List<String> = emptyList(),
    val trailerUrl: String? = null
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
              Media(search: ${'$'}search, type: ANIME) {
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
                trailer {
                  id
                  site
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
            // Remove common resolution / codec / source keywords
            clean = clean.replace(
                Regex("(?i)\\b(1080p|720p|480p|2160p|4k|hevc|avc|x264|x265|bluray|bdrip|web-dl|webrip|multisub|dual|audio)\\b"),
                " "
            )
            // Clean extra spacing
            clean = clean.trim().replace(Regex("\\s+"), " ")
            return clean.ifBlank { folderName.trim() }
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

        Log.d(TAG, "Resolving anime metadata for: '$query' (original: '$folderName')")

        // 1. Primary: AniList GraphQL (extraLarge ~460x650 poster + dominant color + banner)
        val aniListMeta = fetchFromAniList(query)
        if (aniListMeta != null && !aniListMeta.posterUrl.isNullOrBlank()) {
            Log.d(TAG, "Successfully fetched metadata from AniList: ${aniListMeta.titleRomaji}, poster: ${aniListMeta.posterUrl}, color: ${aniListMeta.dominantColor}")
            metadataCache[query] = aniListMeta
            return@withContext aniListMeta
        }

        // 2. Secondary fallback: Jikan (MAL)
        Log.d(TAG, "AniList unavailable or empty. Trying Jikan for '$query'...")
        val jikanMeta = fetchFromJikan(query)
        if (jikanMeta != null && !jikanMeta.posterUrl.isNullOrBlank()) {
            Log.d(TAG, "Successfully fetched metadata from Jikan: ${jikanMeta.posterUrl}")
            metadataCache[query] = jikanMeta
            return@withContext jikanMeta
        }

        // 3. Tertiary fallback: Kitsu
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
                val media = data.optJSONObject("Media") ?: return null

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

}
