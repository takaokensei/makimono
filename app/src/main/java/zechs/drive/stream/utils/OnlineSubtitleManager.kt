package zechs.drive.stream.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class OnlineSubtitle(
    val id: String,
    val lang: String,
    val langName: String,
    val downloadUrl: String,
    val isPortuguese: Boolean = false,
    val source: String = "OpenSubtitles v3",
    val confidenceScore: Int = 85,
    val isVerifiedLanguage: Boolean = false,
    val isOfficialQuality: Boolean = false,
    val cueCount: Int = 0,
    val previewSample: String? = null,
    val encodingLabel: String = "UTF-8",
    val matchedSeriesTitle: String? = null
) {
    val qualityBadge: String
        get() {
            val parts = mutableListOf<String>()
            if (isOfficialQuality) {
                parts.add("★ Oficial • ${confidenceScore}% Certeza")
            } else if (isVerifiedLanguage) {
                parts.add("✓ ${confidenceScore}% Certeza")
            } else {
                parts.add("${confidenceScore}% Relevância")
            }
            if (cueCount > 0) {
                parts.add("$cueCount falas")
            }
            parts.add(encodingLabel)
            parts.add(source)
            return parts.joinToString(" • ")
        }
}

data class SavedSubtitle(
    val file: File,
    val lang: String,
    val langName: String,
    val isPortuguese: Boolean
)

@Singleton
class OnlineSubtitleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "OnlineSubtitleManager"
        private const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"
        private const val OPENSUBTITLES_BASE = "https://opensubtitles-v3.strem.io"
        private const val PREVERIFY_COUNT = 3
        private const val PREVERIFY_TIMEOUT_MS = 3_200L
        private val SEASON_SUFFIX_REGEX = Regex("(?i)\\s*(?:[-–:]?\\s*(?:season|temporada|part|parte|cour)\\s*\\d+|\\b(?:2nd|3rd|4th|5th)\\s+season).*$")
    }

    private data class ResolvedMediaMeta(
        val imdbId: String,
        val isSeries: Boolean,
        val canonicalName: String
    )

    private val imdbCache = ConcurrentHashMap<String, ResolvedMediaMeta>()
    private val preverifiedUtf8Cache = ConcurrentHashMap<String, LanguageVerificationResult>()
    private val searchResultsCache = ConcurrentHashMap<String, List<OnlineSubtitle>>()

    /**
     * Generates prioritized title candidates from seriesTitle and raw filename so that
     * episodes named "01.ts" or "[SubsPlease] Title - 01 (1080p).mkv" resolve reliably.
     */
    internal fun buildTitleCandidates(rawTitle: String, seriesTitle: String? = null): List<String> {
        val candidates = LinkedHashSet<String>()

        fun addCandidate(raw: String?) {
            if (raw.isNullOrBlank()) return
            val parsed = EpisodeParser.parse(raw)
            val cleanedShow = parsed.showTitle.ifBlank { EpisodeParser.cleanShowTitle(raw) }.trim()
            if (cleanedShow.length >= 2 && !cleanedShow.all { it.isDigit() }) {
                candidates.add(cleanedShow)
                val strippedSeason = cleanedShow.replace(SEASON_SUFFIX_REGEX, "").trim()
                if (strippedSeason.length >= 2 && strippedSeason != cleanedShow) {
                    candidates.add(strippedSeason)
                }
            }
        }

        addCandidate(seriesTitle)
        addCandidate(rawTitle)

        // Also split dual titles like "Romaji Title / English Title"
        val snapshot = candidates.toList()
        for (c in snapshot) {
            if (c.contains(" / ") || c.contains(" | ")) {
                c.split(" / ", " | ").map { it.trim() }.filter { it.length >= 2 }.forEach {
                    candidates.add(it)
                }
            }
        }

        return candidates.toList()
    }

    /**
     * Resolves the IMDB ID for an anime/movie title using the public Cinemeta API,
     * testing multiple title candidates and caching the result in memory.
     */
    private suspend fun resolveImdbMeta(
        rawTitle: String,
        seriesTitle: String? = null
    ): ResolvedMediaMeta? = withContext(Dispatchers.IO) {
        val candidates = buildTitleCandidates(rawTitle, seriesTitle)
        for (candidate in candidates) {
            val cacheKey = candidate.lowercase(Locale.ROOT)
            imdbCache[cacheKey]?.let { return@withContext it }

            val resolved = queryCinemetaSingle(candidate)
            if (resolved != null) {
                imdbCache[cacheKey] = resolved
                return@withContext resolved
            }
        }
        null
    }

    private fun queryCinemetaSingle(cleanTitle: String): ResolvedMediaMeta? {
        val encoded = URLEncoder.encode(cleanTitle, "UTF-8")

        // 1. Try series catalog first (most animes/shows are episodic)
        for (catalogType in listOf("series", "movie")) {
            try {
                val url = "$CINEMETA_BASE/catalog/$catalogType/top/search=$encoded.json"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MakimonoPlayer/1.9")
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val metas = json.optJSONArray("metas")
                        if (metas != null && metas.length() > 0) {
                            val first = metas.getJSONObject(0)
                            val imdbId = first.optString("imdb_id").ifBlank { first.optString("id") }
                            val canonicalName = first.optString("name").ifBlank { cleanTitle }
                            if (imdbId.startsWith("tt")) {
                                AppLog.d(TAG, "Resolved $catalogType IMDB ID for '$cleanTitle' -> $imdbId ($canonicalName)")
                                return ResolvedMediaMeta(
                                    imdbId = imdbId,
                                    isSeries = catalogType == "series",
                                    canonicalName = canonicalName
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Cinemeta $catalogType search error for '$cleanTitle': ${e.message}")
            }
        }
        return null
    }

    /**
     * Searches for online subtitles, prioritizing the selected [preferredLanguage] and running
     * parallel lexical verification on the top candidates to guarantee language certainty.
     */
    suspend fun searchSubtitles(
        rawTitle: String,
        season: Int? = null,
        episode: Double? = null,
        seriesTitle: String? = null,
        preferredLanguage: SubtitleLanguage = SubtitleLanguage.PT_BR
    ): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        val parsed = EpisodeParser.parse(rawTitle)
        val s = (season ?: parsed.season ?: 1).coerceAtLeast(1)
        val ep = (episode ?: parsed.episode ?: 1.0).toInt().coerceAtLeast(1)

        val cacheKey = "${rawTitle.trim().lowercase(Locale.ROOT)}:$s:$ep:${preferredLanguage.primaryCode}"
        searchResultsCache[cacheKey]?.let { cached ->
            AppLog.d(TAG, "Returning ${cached.size} cached subtitles for key=$cacheKey (0ms)")
            return@withContext cached
        }

        AppLog.d(
            TAG,
            "Searching subtitles for raw='$rawTitle', series='$seriesTitle' S${s}E${ep} [lang=${preferredLanguage.shortLabel}]..."
        )

        val resolved = resolveImdbMeta(rawTitle, seriesTitle)
        val openSubCandidates = if (resolved != null) {
            fetchFromOpenSubtitles(resolved, s, ep).ifEmpty {
                // Fallback: if season > 1 returned empty on anime with absolute numbering, try S1
                if (resolved.isSeries && s > 1) {
                    fetchFromOpenSubtitles(resolved, 1, ep)
                } else {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }

        // Multi-source fallback: if OpenSubtitles has few/no results, query Animetosho / Jimaku in parallel
        val animeCandidates = if (openSubCandidates.size < 4) {
            val queryTitle = resolved?.canonicalName ?: seriesTitle ?: parsed.showTitle.ifBlank { rawTitle }
            fetchFromAnimetosho(queryTitle, ep)
        } else {
            emptyList()
        }

        val rawCandidates = (openSubCandidates + animeCandidates).distinctBy { it.downloadUrl }
        if (rawCandidates.isEmpty()) return@withContext emptyList()

        // Smart release tag detection: prioritize Blu-ray/Hi10P/Web-DL matches
        val releaseKeywords = listOf("bdrip", "bd-rip", "bluray", "blu-ray", "1080p", "720p", "web-dl", "webrip", "hevc", "x264", "hi10p")
        val detectedTags = releaseKeywords.filter { rawTitle.contains(it, ignoreCase = true) }

        val boostedCandidates = rawCandidates.map { candidate ->
            val matchesRelease = detectedTags.any { tag ->
                candidate.downloadUrl.contains(tag, ignoreCase = true) ||
                    candidate.id.contains(tag, ignoreCase = true) ||
                    (candidate.previewSample?.contains(tag, ignoreCase = true) == true)
            }
            if (matchesRelease) {
                candidate.copy(confidenceScore = (candidate.confidenceScore + 10).coerceAtMost(100))
            } else {
                candidate
            }
        }

        // Filter or prioritize by preferredLanguage
        val matchingLangSubs = boostedCandidates.filter { preferredLanguage.matchesLanguageCode(it.lang) }
        val otherLangSubs = boostedCandidates.filterNot { preferredLanguage.matchesLanguageCode(it.lang) }

        // Pre-verify top N candidates of the preferred language in parallel for 100% language certainty!
        val toVerify = matchingLangSubs.take(PREVERIFY_COUNT)
        val unverifiedRest = matchingLangSubs.drop(PREVERIFY_COUNT)

        val verifiedTop = withTimeoutOrNull(PREVERIFY_TIMEOUT_MS) {
            coroutineScope {
                toVerify.map { candidate ->
                    async(Dispatchers.IO) {
                        verifyCandidateOnline(candidate, preferredLanguage)
                    }
                }.awaitAll()
            }
        } ?: toVerify

        val combined = verifiedTop + unverifiedRest + otherLangSubs

        // Sort:
        // 1. Verified high-certainty match in preferredLanguage
        // 2. Matches preferredLanguage code
        // 3. Official quality & confidence score descending
        // 4. Portuguese fallback
        val sorted = combined.sortedWith(
            compareByDescending<OnlineSubtitle> {
                preferredLanguage.matchesLanguageCode(it.lang) && (it.isVerifiedLanguage || it.confidenceScore >= 80)
            }
                .thenByDescending { preferredLanguage.isExactCodeMatch(it.lang) }
                .thenByDescending { it.isOfficialQuality }
                .thenByDescending { it.confidenceScore }
                .thenByDescending { it.isPortuguese }
                .thenBy { it.langName }
        )

        if (sorted.isNotEmpty()) {
            searchResultsCache[cacheKey] = sorted
        }
        sorted
    }

    private fun fetchFromAnimetosho(
        cleanTitle: String,
        episode: Int
    ): List<OnlineSubtitle> {
        val sanitized = cleanTitle.replace(Regex("(?i)\\s*(?:season|temporada|part|cour)\\s*\\d+"), "").trim()
        val query = "$sanitized $episode"
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            return emptyList()
        }
        val url = "https://animetosho.org/api/terms?q=$encoded"
        AppLog.d(TAG, "Fetching anime fallback subtitles from Animetosho: $url")
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MakimonoPlayer/1.9")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val array = org.json.JSONArray(bodyStr)
                val results = mutableListOf<OnlineSubtitle>()
                for (i in 0 until array.length().coerceAtMost(6)) {
                    val entry = array.getJSONObject(i)
                    val entryTitle = entry.optString("title")
                    val attachments = entry.optJSONArray("attachments")
                    if (attachments != null) {
                        for (j in 0 until attachments.length()) {
                            val att = attachments.getJSONObject(j)
                            val attUrl = att.optString("url")
                            val attDesc = att.optString("desc", entryTitle)
                            if (attUrl.isNotBlank() && (attDesc.endsWith(".ass", true) || attDesc.endsWith(".srt", true))) {
                                val isPt = attDesc.contains("pt", true) || attDesc.contains("por", true) || attDesc.contains("brazilian", true)
                                val lang = if (isPt) "pob" else "eng"
                                val langName = if (isPt) "Português (Brasil)" else "English"
                                results.add(
                                    OnlineSubtitle(
                                        id = "animetosho_${attUrl.hashCode()}",
                                        lang = lang,
                                        langName = langName,
                                        downloadUrl = attUrl,
                                        isPortuguese = isPt,
                                        source = "Jimaku / Animetosho",
                                        confidenceScore = 88,
                                        matchedSeriesTitle = cleanTitle
                                    )
                                )
                            }
                        }
                    }
                }
                results
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Animetosho fetch skipped: ${e.message}")
            emptyList()
        }
    }

    private fun fetchFromOpenSubtitles(
        meta: ResolvedMediaMeta,
        season: Int,
        episode: Int
    ): List<OnlineSubtitle> {
        val subUrl = if (meta.isSeries) {
            "$OPENSUBTITLES_BASE/subtitles/series/${meta.imdbId}:$season:$episode.json"
        } else {
            "$OPENSUBTITLES_BASE/subtitles/movie/${meta.imdbId}.json"
        }

        AppLog.d(TAG, "Fetching subtitles from $subUrl")
        return try {
            val req = Request.Builder()
                .url(subUrl)
                .header("User-Agent", "MakimonoPlayer/1.9")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.w(TAG, "Failed to fetch subtitles: HTTP ${resp.code}")
                    return emptyList()
                }

                val json = JSONObject(resp.body?.string().orEmpty())
                val subsArray = json.optJSONArray("subtitles") ?: return emptyList()

                val resultList = mutableListOf<OnlineSubtitle>()
                for (i in 0 until subsArray.length()) {
                    val item = subsArray.getJSONObject(i)
                    val lang = item.optString("lang", "unk").lowercase(Locale.ROOT)
                    val url = item.optString("url")
                    val id = item.optString("id", url)

                    if (url.isNotBlank()) {
                        val isPt = lang in listOf("pob", "por", "pt", "pt-br", "pt_br")
                        val langName = formatLanguageDisplayName(lang)
                        val baseConfidence = when (lang) {
                            "pob", "pt-br", "pt_br" -> 90
                            "por", "pt", "eng", "en", "spa", "es", "jpn", "ja" -> 86
                            else -> 75
                        }

                        resultList.add(
                            OnlineSubtitle(
                                id = id,
                                lang = lang,
                                langName = langName,
                                downloadUrl = url,
                                isPortuguese = isPt,
                                source = "OpenSubtitles v3",
                                confidenceScore = baseConfidence,
                                matchedSeriesTitle = meta.canonicalName
                            )
                        )
                    }
                }
                resultList
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error fetching subtitles from OpenSubtitles", e)
            emptyList()
        }
    }

    private fun verifyCandidateOnline(
        sub: OnlineSubtitle,
        targetLanguage: SubtitleLanguage
    ): OnlineSubtitle {
        preverifiedUtf8Cache[sub.downloadUrl]?.let { cached ->
            return applyVerificationToSubtitle(sub, cached)
        }

        return try {
            val req = Request.Builder()
                .url(sub.downloadUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return sub
                val bytes = resp.body?.bytes() ?: return sub
                if (bytes.isEmpty()) return sub

                val verification = SubtitleLanguageVerifier.verifySubtitleBytes(
                    bytes = bytes,
                    targetLanguage = targetLanguage,
                    declaredLangCode = sub.lang
                )
                if (verification.cueCount > 0 && verification.normalizedUtf8Text.isNotBlank()) {
                    preverifiedUtf8Cache[sub.downloadUrl] = verification
                }
                applyVerificationToSubtitle(sub, verification)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Pre-verification skipped for ${sub.id}: ${e.message}")
            sub
        }
    }

    private fun applyVerificationToSubtitle(
        sub: OnlineSubtitle,
        verification: LanguageVerificationResult
    ): OnlineSubtitle {
        val updatedLangName = when {
            verification.detectedLanguage == SubtitleLanguage.PT_BR -> "Português (Brasil)"
            verification.detectedLanguage == SubtitleLanguage.PT_PT -> "Português (Portugal)"
            verification.detectedLanguage != null && !verification.isHighCertaintyMatch ->
                "${sub.langName} [Detectado: ${verification.detectedLanguage.shortLabel}]"
            else -> sub.langName
        }
        return sub.copy(
            langName = updatedLangName,
            confidenceScore = verification.certaintyScore,
            isVerifiedLanguage = verification.isHighCertaintyMatch,
            isOfficialQuality = verification.isOfficialGrade,
            cueCount = verification.cueCount,
            previewSample = verification.previewSample,
            encodingLabel = verification.encodingLabel
        )
    }

    private fun formatLanguageDisplayName(lang: String): String = when (lang.lowercase(Locale.ROOT)) {
        "pob", "pt-br", "pt_br" -> "Português (Brasil)"
        "por", "pt", "pt-pt" -> "Português (Portugal)"
        "eng", "en" -> "English (Oficial)"
        "spa", "es" -> "Español"
        "jpn", "ja" -> "日本語 (Japonês)"
        "fre", "fra", "fr" -> "Français"
        "ita", "it" -> "Italiano"
        "ger", "deu", "de" -> "Deutsch"
        else -> lang.uppercase(Locale.ROOT)
    }

    /**
     * One-tap Apple-grade auto-download: finds, verifies, normalizes to UTF-8, and saves
     * the highest-certainty official subtitle for the requested language.
     */
    suspend fun downloadBestSubtitle(
        rawTitle: String,
        season: Int? = null,
        episode: Double? = null,
        seriesTitle: String? = null,
        preferredLanguage: SubtitleLanguage = SubtitleLanguage.PT_BR
    ): Result<Pair<File, OnlineSubtitle>> = withContext(Dispatchers.IO) {
        val results = searchSubtitles(
            rawTitle = rawTitle,
            season = season,
            episode = episode,
            seriesTitle = seriesTitle,
            preferredLanguage = preferredLanguage
        )
        val best = results.firstOrNull {
            preferredLanguage.matchesLanguageCode(it.lang) && it.confidenceScore >= 75
        } ?: results.firstOrNull()
            ?: return@withContext Result.failure(
                IOException("Nenhuma legenda encontrada para ${preferredLanguage.displayName}")
            )

        val fileResult = downloadSubtitle(best, rawTitle, preferredLanguage)
        fileResult.map { file -> Pair(file, best) }
    }

    /**
     * Directory where downloaded subtitles are permanently stored in internal storage.
     */
    val subtitlesDir: File
        get() = File(context.filesDir, "subtitles").apply { if (!exists()) mkdirs() }

    fun sanitizePrefix(title: String): String {
        val parsed = EpisodeParser.parse(title)
        val base = if (parsed.showTitle.isNotBlank() && parsed.episode != null) {
            "${parsed.showTitle}_E${parsed.episode.toInt()}"
        } else {
            parsed.cleanTitle.ifBlank { title }
        }
        return base.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
    }

    /**
     * Returns all permanently saved subtitles matching the given video title or episode.
     */
    fun getSavedSubtitles(videoTitle: String): List<SavedSubtitle> {
        val dir = subtitlesDir
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val cleanPrefix = sanitizePrefix(videoTitle)
        val rawClean = videoTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')

        val files = dir.listFiles { f ->
            f.isFile && f.extension.equals("srt", ignoreCase = true) &&
                (f.name.startsWith(cleanPrefix) || f.name.startsWith(rawClean))
        } ?: return emptyList()

        return files.mapNotNull { file ->
            try {
                val withoutExt = file.nameWithoutExtension
                val parts = withoutExt.split("__")
                val langPart = parts.firstOrNull { it.startsWith("online_") }?.removePrefix("online_") ?: "pt"
                val rawLangName = parts.lastOrNull()?.takeIf { it != withoutExt && !it.startsWith("online_") }?.let {
                    try {
                        java.net.URLDecoder.decode(it, "UTF-8")
                    } catch (_: Exception) {
                        null
                    }
                }
                val langName = rawLangName ?: formatLanguageDisplayName(langPart)
                val isPt = langPart in listOf("pob", "por", "pt", "pt-br") ||
                    langName.contains("Português", ignoreCase = true)
                SavedSubtitle(
                    file = file,
                    lang = langPart,
                    langName = langName,
                    isPortuguese = isPt
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "Error parsing saved subtitle file: ${file.name}", e)
                null
            }
        }
    }

    /**
     * Downloads an online subtitle file to persistent internal storage, using the
     * pre-verified in-memory UTF-8 cache (0ms latency) if available, or normalizing
     * charset (Windows-1252 / UTF-16 -> UTF-8) on the fly.
     */
    suspend fun downloadSubtitle(
        sub: OnlineSubtitle,
        filenamePrefix: String,
        targetLanguage: SubtitleLanguage = SubtitleLanguage.fromCode(sub.lang)
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = subtitlesDir
            val cleanPrefix = sanitizePrefix(filenamePrefix)
            val encodedLangName = URLEncoder.encode(sub.langName, "UTF-8")
            val targetFile = File(dir, "${cleanPrefix}__online_${sub.lang}__${encodedLangName}.srt")

            // 1. Instant 0ms write if already pre-verified in memory!
            val preverified = preverifiedUtf8Cache[sub.downloadUrl]
            if (preverified != null && preverified.normalizedUtf8Text.isNotBlank()) {
                targetFile.writeText(preverified.normalizedUtf8Text, Charsets.UTF_8)
                AppLog.d(
                    TAG,
                    "Instant 0ms write from pre-verified UTF-8 cache: ${targetFile.absolutePath} (${targetFile.length()} bytes)"
                )
                return@withContext Result.success(targetFile)
            }

            // 2. Otherwise download, verify & normalize to clean UTF-8
            AppLog.d(TAG, "Downloading subtitle: ${sub.langName} from ${sub.downloadUrl}")
            val req = Request.Builder()
                .url(sub.downloadUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${resp.code}: ${resp.message}")
                    )
                }

                val bytes = resp.body?.bytes() ?: return@withContext Result.failure(
                    IOException("Corpo da resposta vazio ao baixar legenda")
                )

                val verification = SubtitleLanguageVerifier.verifySubtitleBytes(
                    bytes = bytes,
                    targetLanguage = targetLanguage,
                    declaredLangCode = sub.lang
                )
                if (verification.cueCount == 0 || verification.normalizedUtf8Text.isBlank()) {
                    return@withContext Result.failure(
                        IOException("Arquivo de legenda inválido ou sem falas válidas")
                    )
                }

                preverifiedUtf8Cache[sub.downloadUrl] = verification
                targetFile.writeText(verification.normalizedUtf8Text, Charsets.UTF_8)
            }

            AppLog.d(
                TAG,
                "Subtitle downloaded, UTF-8 normalized, and saved: ${targetFile.absolutePath} (${targetFile.length()} bytes)"
            )
            Result.success(targetFile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLog.e(TAG, "Failed to download online subtitle", e)
            Result.failure(e)
        }
    }
}
