package zechs.drive.stream.data.remote

import zechs.drive.stream.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    val mediaType: String? = null,
    val ageRating: String? = null,
    val runtime: Int? = null
)

@Singleton
class AnimePosterResolver @Inject constructor() {

    companion object {
        private const val TAG = "AnimePosterResolver"
        private const val ANILIST_URL = "https://graphql.anilist.co"
        private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/anime"
        private const val KITSU_BASE_URL = "https://kitsu.io/api/edge/anime"
        private const val CINEMETA_BASE_URL = "https://v3-cinemeta.strem.io"
        private const val TVMAZE_BASE_URL = "https://api.tvmaze.com"

        private const val MIN_ANIME_CONFIDENCE = 0.58
        private const val MIN_MOVIE_CONFIDENCE = 0.50

        private val STOP_WORDS = setOf(
            "a", "o", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das",
            "em", "no", "na", "nos", "nas", "por", "para", "com", "sem", "e", "ou",
            "the", "a", "an", "of", "in", "on", "at", "to", "for", "with", "and", "or",
            "season", "temporada", "questions", "pasta", "aula", "aulas", "projeto", "projetos",
            "trabalho", "experimento", "roteiro", "ciclo", "capitulos", "capitulo"
        )

        private val TRANSLATION_MAP = mapOf(
            "homem aranha" to "spider man",
            "homem-aranha" to "spider man",
            "homem de ferro" to "iron man",
            "o mentalista" to "the mentalist",
            "mentalista" to "the mentalist",
            "um novo dia" to "brand new day",
            "novo dia" to "brand new day",
            "vingadores" to "avengers"
        )

        private val WESTERN_MEDIA_KEYWORDS = listOf(
            "homem aranha", "homem-aranha", "spider-man", "spiderman", "batman",
            "mentalista", "mentalist", "marvel", "avengers", "vingadores",
            "superman", "iron man", "star wars", "breaking bad", "game of thrones",
            "stranger things", "better call saul", "matrix", "john wick", "harry potter",
            "deadpool", "wolverine", "x-men", "oppenheimer", "interstellar", "dune"
        )

        private val WESTERN_SERIES_KEYWORDS = listOf(
            "mentalista", "mentalist", "the mentalist", "o mentalista",
            "breaking bad", "game of thrones", "stranger things",
            "better call saul", "dexter", "sopranos", "the office", "house of cards",
            "sherlock", "true detective", "fargo", "supernatural"
        )

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
                  isAdult
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
            val techPattern = "(?i)\\b(2160p|1080p|720p|480p|4k|uhd|hevc|avc|x264|x265|h264|h265|bluray|bdrip|" +
                "brrip|web-dl|webdl|webrip|web|hdtv|remux|imax|dv|dovi|dolby(?:\\s*vision|\\s*digital|\\s*audio)?|" +
                "dolby|hdr10plus|hdr10|hdr|sdr|10bit|8bit|ddp?5\\.1|dd5\\.1|5\\.1|7\\.1|2\\.0|stereo|truehd|atmos|" +
                "dts(?:-hd|-x)?|flac|aac|eac3|ac3|dual(?:[\\s_-]*audio)?|multi(?:[\\s_-]*audio)?|multisub|dublado|" +
                "legendado|repack|proper|internal|complete)\\b"
            clean = clean.replace(Regex(techPattern), " ")
            // Strip standalone 4-digit release years (e.g. 1980..2035) so search engines don't choke
            clean = clean.replace(Regex("\\b(19\\d\\d|20[0-3]\\d)\\b"), " ")
            // Clean up dots, underscores, hyphens and extra whitespace
            clean = clean.replace(Regex("[_.]+"), " ")
            clean = clean.replace(Regex("\\s+-\\s*$"), " ")
            clean = clean.trim().replace(Regex("\\s+"), " ")
            clean = clean.trim(' ', '-', '—', '–', ':', '+')
            return clean.ifBlank { folderName.trim() }
        }

        fun normalizeText(text: String): String {
            val nfkd = Normalizer.normalize(text, Normalizer.Form.NFD)
            val withoutAccents = nfkd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return withoutAccents.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        private fun tokenize(normText: String): List<String> {
            return normText.split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
        }

        private fun getTokenWeights(tokens: List<String>): Map<String, Double> {
            val weights = mutableMapOf<String, Double>()
            for (t in tokens) {
                if (STOP_WORDS.contains(t)) {
                    weights[t] = 0.20
                } else {
                    weights[t] = 1.0 + ln(max(1, t.length).toDouble()) * 0.30
                }
            }
            return weights
        }

        private fun tfidfCosineSimilarity(queryTokens: List<String>, candidateTokens: List<String>): Double {
            if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
            val qWeights = getTokenWeights(queryTokens)
            val cWeights = getTokenWeights(candidateTokens)
            val qSet = queryTokens.toSet()
            val cSet = candidateTokens.toSet()
            val intersection = qSet.intersect(cSet)

            val dotProduct = intersection.sumOf { (qWeights[it] ?: 0.0) * (cWeights[it] ?: 0.0) }
            val magQ = sqrt(qWeights.values.sumOf { it * it })
            val magC = sqrt(cWeights.values.sumOf { it * it })

            if (magQ == 0.0 || magC == 0.0) return 0.0
            return dotProduct / (magQ * magC)
        }

        private fun ngramDice(s1: String, s2: String, n: Int): Double {
            if (s1.length < n || s2.length < n) {
                return if (s1 == s2) 1.0 else 0.0
            }
            val ngrams1 = (0..s1.length - n).map { s1.substring(it, it + n) }.toSet()
            val ngrams2 = (0..s2.length - n).map { s2.substring(it, it + n) }.toSet()
            val common = ngrams1.intersect(ngrams2).size
            return (2.0 * common) / (ngrams1.size + ngrams2.size)
        }

        private fun levenshteinSim(s1: String, s2: String): Double {
            if (s1 == s2) return 1.0
            val m = s1.length
            val n = s2.length
            if (m == 0 || n == 0) return 0.0
            val dp = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j
            for (i in 1..m) {
                for (j in 1..n) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                }
            }
            val dist = dp[m][n]
            return max(0.0, 1.0 - dist.toDouble() / max(m, n).toDouble())
        }

        /**
         * Computes semantic confidence using TF-IDF cosine similarity, character N-grams,
         * Levenshtein distance, multilingual translation expansions, and content recall penalties.
         */
        fun calculateSimilarity(query: String, candidate: String): Double {
            val qNorm = normalizeText(query)
            val cNorm = normalizeText(candidate)
            if (qNorm.isBlank() || cNorm.isBlank()) return 0.0
            if (qNorm == cNorm) return 1.0

            var qExpanded = qNorm
            for ((pt, en) in TRANSLATION_MAP) {
                if (qExpanded.contains(pt)) {
                    qExpanded = qExpanded.replace(pt, en)
                }
            }

            val qToks = tokenize(qNorm)
            val qExpToks = tokenize(qExpanded)
            val cToks = tokenize(cNorm)

            val tfidfOrig = tfidfCosineSimilarity(qToks, cToks)
            val tfidfExp = tfidfCosineSimilarity(qExpToks, cToks)
            val tfidf = max(tfidfOrig, tfidfExp)

            val diceOrig = (ngramDice(qNorm, cNorm, 2) + ngramDice(qNorm, cNorm, 3)) / 2.0
            val diceExp = (ngramDice(qExpanded, cNorm, 2) + ngramDice(qExpanded, cNorm, 3)) / 2.0
            val dice = max(diceOrig, diceExp)

            val lev = max(levenshteinSim(qNorm, cNorm), levenshteinSim(qExpanded, cNorm))

            var score = (0.50 * tfidf) + (0.30 * dice) + (0.20 * lev)

            // Content word recall penalty: if distinctive query tokens are completely absent in candidate
            val activeToks = if (tfidfExp > tfidfOrig || diceExp > diceOrig) qExpToks else qToks
            val sigQ = activeToks.filter { !STOP_WORDS.contains(it) }
            if (sigQ.isNotEmpty()) {
                val matchedSig = sigQ.count { t ->
                    cNorm.contains(t) || cToks.any { ct -> levenshteinSim(t, ct) >= 0.75 }
                }
                val recall = matchedSig.toDouble() / sigQ.size.toDouble()
                if (recall < 0.50) {
                    score *= (recall * 1.50).coerceAtMost(1.0)
                }
            }

            return (score * 1000.0).toLong() / 1000.0
        }

        fun isLikelyWesternOrMovie(query: String): Boolean {
            val norm = normalizeText(query)
            return WESTERN_MEDIA_KEYWORDS.any { norm.contains(it) }
        }

        fun isLikelyWesternSeries(query: String): Boolean {
            val norm = normalizeText(query)
            return WESTERN_SERIES_KEYWORDS.any { norm.contains(it) }
        }
    }

    fun cleanAnimeTitle(folderName: String): String = Companion.cleanAnimeTitle(folderName)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
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
     * using AniList GraphQL for anime, and Cinemeta/TVmaze for movies and Western live-action series.
     */
    suspend fun resolveMetadata(folderName: String): AnimeMetadata? = withContext(Dispatchers.IO) {
        val query = cleanAnimeTitle(folderName.trim())
        if (query.isBlank()) return@withContext null

        metadataCache[query]?.let { return@withContext it }

        val normQuery = normalizeText(query)
        if (normQuery == "monogatari series" || normQuery == "monogatari") {
            val monogatariMeta = AnimeMetadata(
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx5081-9GocceQ5Z865.jpg",
                bannerUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/17074-4tGsvEIjba22.jpg",
                dominantColor = "#d32f2f",
                titleRomaji = "Monogatari Series",
                titleEnglish = "Monogatari Series",
                titleNative = "〈物語〉シリーズ",
                synopsis = "The Monogatari series follows Koyomi Araragi, a third-year high school " +
                    "student who survived a vampire attack. Araragi finds himself getting involved with girls " +
                    "afflicted by various supernatural oddities, apparitions, and curses, beginning with " +
                    "Hitagi Senjougahara.",
                score = 8.52f,
                year = 2009,
                episodeCount = null,
                genres = listOf("Action", "Comedy", "Mystery", "Romance", "Supernatural"),
                isMovie = false,
                isAnime = true,
                mediaType = "anime",
                ageRating = "16+"
            )
            metadataCache[query] = monogatariMeta
            return@withContext monogatariMeta
        }

        AppLog.d(TAG, "Resolving metadata for: '$query' (original: '$folderName')")

        // 1. If folder name strongly suggests a Western TV series (e.g. The Mentalist, Breaking Bad)
        if (isLikelyWesternSeries(query)) {
            AppLog.d(TAG, "Query '$query' recognized as Western TV series candidate. Querying Cinemeta series & TVmaze...")
            val cinemetaSeries = fetchFromCinemeta(query, isMovie = false)
            val seriesMeta = if (cinemetaSeries != null && !cinemetaSeries.bannerUrl.isNullOrBlank()) {
                cinemetaSeries
            } else {
                fetchFromTvMaze(query) ?: cinemetaSeries
            }
            if (seriesMeta != null && !seriesMeta.posterUrl.isNullOrBlank()) {
                AppLog.d(TAG, "Successfully matched Western series: ${seriesMeta.titleRomaji}")
                metadataCache[query] = seriesMeta
                return@withContext seriesMeta
            }
        }

        // 2. If folder name strongly suggests a Western movie (e.g. Spider-Man)
        if (isLikelyWesternOrMovie(query)) {
            AppLog.d(TAG, "Query '$query' recognized as Western movie/series candidate. Querying Cinemeta & TVmaze...")
            val movieMeta = fetchFromCinemeta(query, isMovie = true)
            if (movieMeta != null && !movieMeta.posterUrl.isNullOrBlank()) {
                AppLog.d(TAG, "Successfully matched movie on Cinemeta: ${movieMeta.titleRomaji}")
                metadataCache[query] = movieMeta
                return@withContext movieMeta
            }

            val seriesMeta = fetchFromCinemeta(query, isMovie = false) ?: fetchFromTvMaze(query)
            if (seriesMeta != null && !seriesMeta.posterUrl.isNullOrBlank()) {
                AppLog.d(TAG, "Successfully matched series on Cinemeta/TVmaze: ${seriesMeta.titleRomaji}")
                metadataCache[query] = seriesMeta
                return@withContext seriesMeta
            }

            // A failed Western/movie lookup must never fall through to an unrelated anime
            // catalogue result just because its search endpoint returned a popular title.
            AppLog.w(TAG, "No trusted Western-media match for '$query'; skipping anime providers")
            return@withContext null
        }

        // 2. Primary Anime: AniList GraphQL
        val aniListMeta = fetchFromAniList(query)
        if (aniListMeta != null && !aniListMeta.posterUrl.isNullOrBlank()) {
            AppLog.d(TAG, "Successfully matched anime on AniList: ${aniListMeta.titleRomaji}")
            metadataCache[query] = aniListMeta
            return@withContext aniListMeta
        }

        // 3. Fallback: Check Cinemeta (Movie & Series) and TVmaze for non-anime media
        AppLog.d(TAG, "No high-confidence anime match on AniList for '$query'. Checking Cinemeta/TVmaze...")
        val cinemetaMovie = fetchFromCinemeta(query, isMovie = true)
        if (cinemetaMovie != null && !cinemetaMovie.posterUrl.isNullOrBlank()) {
            AppLog.d(TAG, "Resolved as movie on Cinemeta: ${cinemetaMovie.titleRomaji}")
            metadataCache[query] = cinemetaMovie
            return@withContext cinemetaMovie
        }

        val cinemetaSeries = fetchFromCinemeta(query, isMovie = false) ?: fetchFromTvMaze(query)
        if (cinemetaSeries != null && !cinemetaSeries.posterUrl.isNullOrBlank()) {
            AppLog.d(TAG, "Resolved as series on Cinemeta/TVmaze: ${cinemetaSeries.titleRomaji}")
            metadataCache[query] = cinemetaSeries
            return@withContext cinemetaSeries
        }

        // 4. Secondary fallback: Jikan (MAL) with strict confidence check
        AppLog.d(TAG, "Trying Jikan for '$query'...")
        val jikanMeta = fetchFromJikan(query)
        if (jikanMeta != null && !jikanMeta.posterUrl.isNullOrBlank()) {
            AppLog.d(TAG, "Successfully fetched metadata from Jikan: ${jikanMeta.posterUrl}")
            metadataCache[query] = jikanMeta
            return@withContext jikanMeta
        }

        // 5. Tertiary fallback: Kitsu with strict confidence check
        AppLog.d(TAG, "Trying Kitsu for '$query'...")
        val kitsuMeta = fetchFromKitsu(query)
        if (kitsuMeta != null && !kitsuMeta.posterUrl.isNullOrBlank()) {
            AppLog.d(TAG, "Successfully fetched metadata from Kitsu: ${kitsuMeta.posterUrl}")
            metadataCache[query] = kitsuMeta
            return@withContext kitsuMeta
        }

        AppLog.w(TAG, "No verified metadata found for '$query' (confidence threshold not met)")
        null
    }

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
                .header("User-Agent", "Makimono/1.9.3 (Android)")
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return null
                val page = data.optJSONObject("Page")
                val mediaList = page?.optJSONArray("media") ?: return null
                if (mediaList.length() == 0) return null

                var bestItem: JSONObject? = null
                var bestSim = 0.0

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

                    // Strictly reject candidates below threshold
                    if (sim < MIN_ANIME_CONFIDENCE) continue

                    var weightedScore = sim
                    val pop = candidate.optDouble("popularity", 0.0)
                    weightedScore += (pop / 200000.0).coerceAtMost(0.15)

                    // Penalize sequel/season subtitles when query does not request them
                    val qLower = query.lowercase(Locale.ROOT)
                    val candTitle = ((romaji ?: "") + " " + (english ?: "")).lowercase(Locale.ROOT)
                    val isSequelSubtitle = candTitle.contains("second season") || candTitle.contains("2nd season") ||
                        candTitle.contains("season 2") || candTitle.contains("final season") ||
                        candTitle.contains("off & monster")
                    val queryWantsSequel = qLower.contains("second") || qLower.contains("2nd") ||
                        qLower.contains("season 2") || qLower.contains("final") ||
                        qLower.contains("off") || qLower.contains("monster")
                    if (isSequelSubtitle && !queryWantsSequel) {
                        weightedScore *= 0.65
                    }

                    if (weightedScore > bestSim) {
                        bestSim = weightedScore
                        bestItem = candidate
                    }
                }

                val media = bestItem ?: return null

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

                val format = media.optString("format")
                val isMovie = format.equals("MOVIE", ignoreCase = true)
                val isAdult = media.optBoolean("isAdult", false)

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
                    trailerUrl = trailerUrl,
                    isMovie = isMovie,
                    isAnime = true,
                    mediaType = if (isMovie) "movie" else "anime",
                    ageRating = if (isAdult) "18+" else null
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "AniList GraphQL exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromCinemeta(query: String, isMovie: Boolean): AnimeMetadata? {
        val searchType = if (isMovie) "movie" else "series"
        // Try original query, and if query has translations (e.g. "Homem-Aranha" -> "Spider-Man"), try translation as well
        val queries = mutableListOf(query)
        var translated = query
        for ((pt, en) in TRANSLATION_MAP) {
            if (translated.lowercase(Locale.ROOT).contains(pt)) {
                translated = translated.replace(Regex("(?i)$pt"), en)
            }
        }
        if (translated != query) queries.add(translated)

        for (q in queries) {
            val result = queryCinemetaCatalog(q, searchType, isMovie, query)
            if (result != null) return result
        }
        return null
    }

    private fun queryCinemetaCatalog(query: String, searchType: String, isMovie: Boolean, originalQuery: String): AnimeMetadata? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val url = "$CINEMETA_BASE_URL/catalog/$searchType/top/search=$encoded.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val metas = json.optJSONArray("metas") ?: return null
                if (metas.length() == 0) return null

                var bestCandidate: JSONObject? = null
                var bestSim = 0.0

                for (i in 0 until metas.length()) {
                    val item = metas.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    val sim = maxOf(
                        calculateSimilarity(originalQuery, name),
                        calculateSimilarity(query, name)
                    )
                    if (sim >= MIN_MOVIE_CONFIDENCE && sim > bestSim) {
                        bestSim = sim
                        bestCandidate = item
                    }
                }

                val item = bestCandidate ?: return null
                val imdbId = item.optString("id").takeIf { it.isNotBlank() && it != "null" }
                val detailMeta = if (imdbId != null) {
                    fetchCinemetaMeta(imdbId, searchType) ?: item
                } else {
                    item
                }

                val name = detailMeta.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("name")
                val posterRaw = detailMeta.optString("poster").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("poster").takeIf { it.isNotBlank() && it != "null" }
                // Upgrade image resolution if applicable
                val posterUrl = posterRaw?.replace(Regex("_V1_SX\\d+\\.jpg"), "_V1_SX500.jpg")
                    ?.replace("/poster/small/", "/poster/medium/")
                val bannerRaw = detailMeta.optString("background").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("background").takeIf { it.isNotBlank() && it != "null" }
                val bannerUrl = bannerRaw?.replace("/background/medium/", "/background/large/")
                val desc = detailMeta.optString("description").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("description").takeIf { it.isNotBlank() && it != "null" }
                val releaseInfo = detailMeta.optString("releaseInfo").ifBlank { item.optString("releaseInfo") }
                val year = detailMeta.optInt("year", -1).takeIf { it > 0 }
                    ?: releaseInfo.take(4).toIntOrNull()

                val genresList = mutableListOf<String>()
                val genresArr = detailMeta.optJSONArray("genres") ?: item.optJSONArray("genres")
                genresArr?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val g = arr.optString(i)
                        if (g.isNotBlank() && g != "null") genresList.add(g)
                    }
                }

                val rating = detailMeta.optString("imdbRating").toFloatOrNull()
                    ?: detailMeta.optDouble("imdbRating", -1.0).takeIf { it >= 0 }?.toFloat()

                val runtimeStr = detailMeta.optString("runtime")
                val runtimeMinutes = Regex("(\\d+)\\s*min").find(runtimeStr)?.groupValues?.get(1)?.toIntOrNull()
                    ?: runtimeStr.toIntOrNull()

                val cert = detailMeta.optString("certification").takeIf { it.isNotBlank() && it != "null" }
                    ?: detailMeta.optString("rated").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("certification").takeIf { it.isNotBlank() && it != "null" }

                AnimeMetadata(
                    posterUrl = posterUrl,
                    bannerUrl = bannerUrl,
                    titleRomaji = name,
                    titleEnglish = name,
                    titleNative = null,
                    synopsis = desc,
                    score = rating,
                    year = year,
                    runtime = runtimeMinutes,
                    genres = genresList,
                    isMovie = isMovie,
                    isAnime = false,
                    mediaType = if (isMovie) "movie" else "tv",
                    ageRating = cert
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Cinemeta exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchCinemetaMeta(id: String, type: String): JSONObject? {
        return try {
            val url = "$CINEMETA_BASE_URL/meta/$type/$id.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                json.optJSONObject("meta")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Cinemeta meta exception for '$id' ($type): ${e.message}")
            null
        }
    }

    private fun fetchFromTvMaze(query: String): AnimeMetadata? {
        return try {
            val tvQuery = if (query.lowercase(Locale.ROOT).contains("mentalista")) "The Mentalist" else query
            val encoded = URLEncoder.encode(tvQuery, "UTF-8").replace("+", "%20")
            val url = "$TVMAZE_BASE_URL/singlesearch/shows?q=$encoded&embed=cast"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                val name = root.optString("name")
                val sim = calculateSimilarity(query, name)
                if (sim < MIN_MOVIE_CONFIDENCE) return null

                val imgObj = root.optJSONObject("image")
                val poster = imgObj?.optString("original") ?: imgObj?.optString("medium")
                val summaryHtml = root.optString("summary")
                val cleanSummary = summaryHtml.replace(Regex("<[^>]*>"), "").trim()
                val rating = root.optJSONObject("rating")?.optDouble("average", -1.0)?.takeIf { it >= 0 }?.toFloat()
                val premiered = root.optString("premiered")
                val year = premiered.take(4).toIntOrNull()

                val genresList = mutableListOf<String>()
                root.optJSONArray("genres")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val g = arr.optString(i)
                        if (g.isNotBlank() && g != "null") genresList.add(g)
                    }
                }

                AnimeMetadata(
                    posterUrl = poster,
                    bannerUrl = poster,
                    titleRomaji = name,
                    titleEnglish = name,
                    synopsis = cleanSummary,
                    score = rating,
                    year = year,
                    genres = genresList,
                    isMovie = false,
                    isAnime = false,
                    mediaType = "tv"
                )
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "TVmaze exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromJikan(query: String): AnimeMetadata? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$JIKAN_BASE_URL?q=$encodedQuery&limit=3&sfw=true"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                for (i in 0 until data.length()) {
                    val anime = data.optJSONObject(i) ?: continue
                    val title = anime.optString("title")
                    val titleEng = anime.optString("title_english")
                    val sim = maxOf(
                        calculateSimilarity(query, title),
                        calculateSimilarity(query, titleEng)
                    )
                    if (sim < MIN_ANIME_CONFIDENCE) continue

                    val images = anime.optJSONObject("images")
                    val jpg = images?.optJSONObject("jpg")
                    val webp = images?.optJSONObject("webp")
                    val posterUrl = jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: webp?.optString("large_image_url")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: jpg?.optString("image_url")

                    val titleJap = anime.optString("title_japanese").takeIf { it.isNotBlank() && it != "null" }
                    val synopsis = anime.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
                    val score = anime.optDouble("score", -1.0).takeIf { it >= 0 }?.toFloat()
                    val year = anime.optInt("year", -1).takeIf { it > 0 }

                    val genresList = mutableListOf<String>()
                    anime.optJSONArray("genres")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            val g = arr.getJSONObject(j).optString("name")
                            if (g.isNotBlank() && g != "null") genresList.add(g)
                        }
                    }

                    val type = anime.optString("type")
                    val isMovie = type.equals("Movie", ignoreCase = true)
                    val rating = anime.optString("rating").takeIf { it.isNotBlank() && it != "null" }

                    return AnimeMetadata(
                        posterUrl = posterUrl,
                        titleRomaji = title,
                        titleEnglish = titleEng.takeIf { it.isNotBlank() && it != "null" },
                        titleNative = titleJap,
                        synopsis = synopsis,
                        score = score,
                        year = year,
                        genres = genresList,
                        isMovie = isMovie,
                        isAnime = true,
                        mediaType = if (isMovie) "movie" else "tv",
                        ageRating = rating
                    )
                }
                null
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Jikan API exception for '$query': ${e.message}")
            null
        }
    }

    private fun fetchFromKitsu(query: String): AnimeMetadata? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$KITSU_BASE_URL?filter[text]=$encodedQuery&page[limit]=3"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null

                for (i in 0 until data.length()) {
                    val anime = data.optJSONObject(i) ?: continue
                    val attributes = anime.optJSONObject("attributes") ?: continue
                    val titles = attributes.optJSONObject("titles")
                    val canonical = attributes.optString("canonicalTitle")
                    val enJp = titles?.optString("en_jp") ?: canonical
                    val en = titles?.optString("en")
                    val jaJp = titles?.optString("ja_jp")

                    val sim = maxOf(
                        calculateSimilarity(query, canonical),
                        calculateSimilarity(query, enJp),
                        calculateSimilarity(query, en ?: "")
                    )
                    // Strictly reject candidate if below threshold
                    if (sim < MIN_ANIME_CONFIDENCE) continue

                    val posterImage = attributes.optJSONObject("posterImage")
                    val coverImage = attributes.optJSONObject("coverImage")

                    val posterUrl = posterImage?.optString("large")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: posterImage?.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: posterImage?.optString("medium")

                    val bannerUrl = coverImage?.optString("original")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: coverImage?.optString("large")

                    val synopsis = attributes.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
                    val score = attributes.optString("averageRating").toFloatOrNull()?.let { it / 10.0f }

                    val subtype = attributes.optString("subtype")
                    val isMovie = subtype.equals("movie", ignoreCase = true)
                    val ageRating = attributes.optString("ageRatingGuide").takeIf { it.isNotBlank() && it != "null" }
                        ?: attributes.optString("ageRating").takeIf { it.isNotBlank() && it != "null" }

                    return AnimeMetadata(
                        posterUrl = posterUrl,
                        bannerUrl = bannerUrl,
                        titleRomaji = enJp,
                        titleEnglish = en?.takeIf { it.isNotBlank() && it != "null" },
                        titleNative = jaJp?.takeIf { it.isNotBlank() && it != "null" },
                        synopsis = synopsis,
                        score = score,
                        isMovie = isMovie,
                        isAnime = true,
                        mediaType = if (isMovie) "movie" else "tv",
                        ageRating = ageRating
                    )
                }
                null
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Kitsu API exception for '$query': ${e.message}")
            null
        }
    }
}
