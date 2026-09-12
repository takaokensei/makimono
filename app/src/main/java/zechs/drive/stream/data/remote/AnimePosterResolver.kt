package zechs.drive.stream.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimePosterResolver @Inject constructor() {

    companion object {
        private const val TAG = "AnimePosterResolver"
        private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/anime"
        private const val KITSU_BASE_URL = "https://kitsu.io/api/edge/anime"

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

    /**
     * Resolves the official anime poster URL for a folder.
     * Tries Jikan API (MyAnimeList) first; if Jikan times out or returns 504,
     * seamlessly falls back to Kitsu API to ensure the user never gets an empty card.
     */
    suspend fun resolvePoster(folderName: String): String? = withContext(Dispatchers.IO) {
        val trimmed = folderName.trim()
        if (trimmed.equals("oneblacki", ignoreCase = true) ||
            (trimmed.contains("oneblacki", ignoreCase = true) && !trimmed.contains("1oneblacki", ignoreCase = true) && !trimmed.startsWith("1"))) {
            Log.d(TAG, "Using custom built-in cover for oneblacki")
            return@withContext "android.resource://zechs.drive.stream/drawable/oneblacki_cover"
        }

        val query = cleanAnimeTitle(folderName)
        if (query.isBlank()) return@withContext null

        Log.d(TAG, "Resolving anime poster for: '$query' (original: '$folderName')")

        // 1. Primary: Try Jikan API (MyAnimeList)
        val jikanPoster = fetchFromJikan(query)
        if (!jikanPoster.isNullOrBlank()) {
            Log.d(TAG, "Successfully fetched MAL poster from Jikan: $jikanPoster")
            return@withContext jikanPoster
        }

        // 2. Resilient Fallback: Try Kitsu API if Jikan failed or timed out
        Log.d(TAG, "Jikan unavailable or returned empty. Trying fallback to Kitsu for '$query'...")
        val kitsuPoster = fetchFromKitsu(query)
        if (!kitsuPoster.isNullOrBlank()) {
            Log.d(TAG, "Successfully fetched anime poster from Kitsu: $kitsuPoster")
            return@withContext kitsuPoster
        }

        Log.w(TAG, "No poster found for '$query'")
        null
    }

    private fun fetchFromJikan(query: String): String? {
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
                val images = anime.optJSONObject("images") ?: return null

                // Prefer large JPG, then large WebP, then standard JPG
                val jpg = images.optJSONObject("jpg")
                val webp = images.optJSONObject("webp")

                jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: webp?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() && it != "null" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jikan API exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromKitsu(query: String): String? {
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
                val posterImage = attributes.optJSONObject("posterImage") ?: return null

                posterImage.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: posterImage.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: posterImage.optString("medium")?.takeIf { it.isNotBlank() && it != "null" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kitsu API exception for '$query': ${e.message}")
            null
        }
    }

}
