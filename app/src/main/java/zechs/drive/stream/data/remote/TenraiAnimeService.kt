package zechs.drive.stream.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenraiAnimeService @Inject constructor() {

    companion object {
        private const val TAG = "TenraiAnimeService"
        private const val BASE_URL = "https://api.tenrai.org/v1"
    }

    data class TenraiAnimeEntry(
        val malId: Int,
        val title: String,
        val titleEnglish: String?,
        val titleJapanese: String? = null,
        val type: String?,
        val episodes: Int?,
        val year: Int?,
        val imageUrl: String?,
        val score: Double? = null,
        val rating: String? = null,
        val status: String? = null,
        val synopsis: String? = null,
        val genres: List<String> = emptyList()
    )

    data class TenraiRelationEntry(
        val relation: String,
        val malId: Int,
        val name: String,
        val type: String
    )

    data class TenraiEpisodeEntry(
        val epNumber: Int,
        val title: String?,
        val titleJapanese: String?
    )

    data class TenraiFranchiseArc(
        val malId: Int,
        val title: String,
        val seasonNumber: Int?,
        val type: String,
        val episodeCount: Int?,
        val year: Int?,
        val episodes: Map<Int, String> = emptyMap()
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val animeSearchCache = ConcurrentHashMap<String, List<TenraiAnimeEntry>>()
    private val animeDetailsCache = ConcurrentHashMap<Int, TenraiAnimeEntry>()
    private val relationsCache = ConcurrentHashMap<Int, List<TenraiRelationEntry>>()
    private val episodesCache = ConcurrentHashMap<Int, List<TenraiEpisodeEntry>>()
    private val franchiseCache = ConcurrentHashMap<String, List<TenraiFranchiseArc>>()

    /**
     * Search anime on Tenrai API by title.
     */
    suspend fun searchAnime(query: String): List<TenraiAnimeEntry> = withContext(Dispatchers.IO) {
        val clean = AnimePosterResolver.cleanAnimeTitle(query).trim()
        if (clean.isBlank()) return@withContext emptyList()

        animeSearchCache[clean]?.let { return@withContext it }

        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "$BASE_URL/anime?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Makimono-App/1.4.20")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Search returned HTTP ${resp.code} for '$clean'")
                    return@withContext emptyList()
                }
                val body = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()

                val results = mutableListOf<TenraiAnimeEntry>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val malId = item.optInt("mal_id")
                    if (malId <= 0) continue

                    val title = item.optString("title")
                    val titleEnglish = item.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
                    val type = item.optString("type")
                    val eps = item.optInt("episodes", -1).takeIf { it > 0 }
                    val year = item.optInt("year", -1).takeIf { it > 0 }

                    val images = item.optJSONObject("images")
                    val jpg = images?.optJSONObject("jpg")
                    val imgUrl = jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() && it != "null" }

                    val titleJapanese = item.optString("title_japanese").takeIf { it.isNotBlank() && it != "null" }
                    val score = item.optDouble("score").takeIf { !it.isNaN() && it > 0.0 }
                    val rating = item.optString("rating").takeIf { it.isNotBlank() && it != "null" }
                    val status = item.optString("status").takeIf { it.isNotBlank() && it != "null" }
                    val synopsis = item.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
                    val genresArray = item.optJSONArray("genres")
                    val genreList = mutableListOf<String>()
                    if (genresArray != null) {
                        for (g in 0 until genresArray.length()) {
                            val gName = genresArray.getJSONObject(g).optString("name")
                            if (gName.isNotBlank()) genreList.add(gName)
                        }
                    }

                    val entry = TenraiAnimeEntry(
                        malId = malId,
                        title = title,
                        titleEnglish = titleEnglish,
                        titleJapanese = titleJapanese,
                        type = type,
                        episodes = eps,
                        year = year,
                        imageUrl = imgUrl,
                        score = score,
                        rating = rating,
                        status = status,
                        synopsis = synopsis,
                        genres = genreList
                    )
                    results.add(entry)
                    animeDetailsCache[malId] = entry
                }

                animeSearchCache[clean] = results
                results
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search error for '$clean': ${e.message}")
            emptyList()
        }
    }

    /**
     * Get anime relations (prequels, sequels, side stories, specials).
     */
    suspend fun getRelations(malId: Int): List<TenraiRelationEntry> = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext emptyList()
        relationsCache[malId]?.let { return@withContext it }

        try {
            val url = "$BASE_URL/anime/$malId/relations"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Makimono-App/1.4.20")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()

                val relations = mutableListOf<TenraiRelationEntry>()
                for (i in 0 until data.length()) {
                    val rObj = data.getJSONObject(i)
                    val relType = rObj.optString("relation")
                    val entries = rObj.optJSONArray("entry") ?: continue

                    for (j in 0 until entries.length()) {
                        val e = entries.getJSONObject(j)
                        val eType = e.optString("type")
                        if (eType.equals("anime", ignoreCase = true)) {
                            val eId = e.optInt("mal_id")
                            val eName = e.optString("name")
                            if (eId > 0) {
                                relations.add(TenraiRelationEntry(relType, eId, eName, eType))
                            }
                        }
                    }
                }
                relationsCache[malId] = relations
                relations
            }
        } catch (e: Exception) {
            Log.w(TAG, "Relations error for ID $malId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get canonical episode titles for an anime.
     */
    suspend fun getEpisodes(malId: Int): List<TenraiEpisodeEntry> = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext emptyList()
        episodesCache[malId]?.let { return@withContext it }

        try {
            val url = "$BASE_URL/anime/$malId/episodes"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Makimono-App/1.4.20")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()

                val epList = mutableListOf<TenraiEpisodeEntry>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val epNum = item.optInt("mal_id", i + 1)
                    val title = item.optString("title").takeIf { it.isNotBlank() && it != "null" }
                    val titleJp = item.optString("title_japanese").takeIf { it.isNotBlank() && it != "null" }
                    epList.add(TenraiEpisodeEntry(epNum, title, titleJp))
                }
                episodesCache[malId] = epList
                epList
            }
        } catch (e: Exception) {
            Log.w(TAG, "Episodes error for ID $malId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Resolves the full chronological franchise tree for a series folder name.
     */
    suspend fun resolveFranchiseArcs(seriesTitle: String): List<TenraiFranchiseArc> = withContext(Dispatchers.IO) {
        val clean = AnimePosterResolver.cleanAnimeTitle(seriesTitle).trim()
        if (clean.isBlank()) return@withContext emptyList()

        franchiseCache[clean]?.let { return@withContext it }

        val searchList = searchAnime(clean)
        if (searchList.isEmpty()) return@withContext emptyList()

        val root = searchList.firstOrNull {
            it.title.contains(clean, ignoreCase = true) || clean.contains(it.title, ignoreCase = true)
        } ?: searchList.first()

        val visited = mutableSetOf<Int>()
        val queue = mutableListOf(root.malId)
        val discovered = mutableMapOf<Int, TenraiAnimeEntry>()
        discovered[root.malId] = root

        while (queue.isNotEmpty() && visited.size < 10) {
            val curId = queue.removeAt(0)
            if (!visited.add(curId)) continue

            val relations = getRelations(curId)
            for (rel in relations) {
                if (!visited.contains(rel.malId) && !queue.contains(rel.malId)) {
                    queue.add(rel.malId)
                }
            }
        }

        for (id in visited) {
            if (!discovered.containsKey(id)) {
                val details = fetchAnimeDetails(id)
                if (details != null) {
                    discovered[id] = details
                }
            }
        }

        val tvAndSpecials = discovered.values.toList()
            .sortedWith { a, b ->
                val yearA = a.year ?: 9999
                val yearB = b.year ?: 9999
                if (yearA != yearB) yearA.compareTo(yearB)
                else a.malId.compareTo(b.malId)
            }

        var tvSeasonCounter = 1
        val arcs = mutableListOf<TenraiFranchiseArc>()
        for (anime in tvAndSpecials) {
            val isTv = anime.type.equals("TV", ignoreCase = true)
            val seasonNum = if (isTv) tvSeasonCounter++ else null

            val episodesMap = if (isTv) {
                getEpisodes(anime.malId).associate { it.epNumber to (it.title ?: "Episódio ${it.epNumber}") }
            } else emptyMap()

            arcs.add(
                TenraiFranchiseArc(
                    malId = anime.malId,
                    title = anime.title,
                    seasonNumber = seasonNum,
                    type = anime.type ?: "TV",
                    episodeCount = anime.episodes,
                    year = anime.year,
                    episodes = episodesMap
                )
            )
        }

        franchiseCache[clean] = arcs
        Log.d(TAG, "Resolved franchise for '$clean': ${arcs.size} arcs: ${arcs.map { "${it.title} (${it.type})" }}")
        arcs
    }

    private suspend fun fetchAnimeDetails(malId: Int): TenraiAnimeEntry? = withContext(Dispatchers.IO) {
        animeDetailsCache[malId]?.let { return@withContext it }
        try {
            val url = "$BASE_URL/anime/$malId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Makimono-App/1.4.20")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val item = json.optJSONObject("data") ?: return@withContext null

                val title = item.optString("title")
                val titleEnglish = item.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
                val type = item.optString("type")
                val eps = item.optInt("episodes", -1).takeIf { it > 0 }
                val year = item.optInt("year", -1).takeIf { it > 0 }

                val images = item.optJSONObject("images")
                val jpg = images?.optJSONObject("jpg")
                val imgUrl = jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() && it != "null" }

                val titleJapanese = item.optString("title_japanese").takeIf { it.isNotBlank() && it != "null" }
                val score = item.optDouble("score").takeIf { !it.isNaN() && it > 0.0 }
                val rating = item.optString("rating").takeIf { it.isNotBlank() && it != "null" }
                val status = item.optString("status").takeIf { it.isNotBlank() && it != "null" }
                val synopsis = item.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
                val genresArray = item.optJSONArray("genres")
                val genreList = mutableListOf<String>()
                if (genresArray != null) {
                    for (g in 0 until genresArray.length()) {
                        val gName = genresArray.getJSONObject(g).optString("name")
                        if (gName.isNotBlank()) genreList.add(gName)
                    }
                }

                val entry = TenraiAnimeEntry(
                    malId = malId,
                    title = title,
                    titleEnglish = titleEnglish,
                    titleJapanese = titleJapanese,
                    type = type,
                    episodes = eps,
                    year = year,
                    imageUrl = imgUrl,
                    score = score,
                    rating = rating,
                    status = status,
                    synopsis = synopsis,
                    genres = genreList
                )
                animeDetailsCache[malId] = entry
                entry
            }
        } catch (e: Exception) {
            Log.w(TAG, "Details error for ID $malId: ${e.message}")
            null
        }
    }

    suspend fun getAnimeDetails(malId: Int): TenraiAnimeEntry? = fetchAnimeDetails(malId)
}
