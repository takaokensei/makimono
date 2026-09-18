package zechs.drive.stream.utils

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class OnlineSubtitle(
    val id: String,
    val lang: String,
    val langName: String,
    val downloadUrl: String,
    val isPortuguese: Boolean = false,
    val source: String = "OpenSubtitles"
)

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
    }

    /**
     * Resolves the IMDB ID for an anime/movie title using the public Cinemeta API.
     */
    private suspend fun resolveImdbId(cleanTitle: String): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(cleanTitle, "UTF-8")

        // 1. Try series catalog first (most animes are episodic)
        try {
            val url = "$CINEMETA_BASE/catalog/series/top/search=$encoded.json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MakimonoPlayer/1.4")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                val metas = json.optJSONArray("metas")
                if (metas != null && metas.length() > 0) {
                    val first = metas.getJSONObject(0)
                    val imdbId = first.optString("imdb_id").ifBlank { first.optString("id") }
                    if (imdbId.startsWith("tt")) {
                        Log.d(TAG, "Resolved series IMDB ID for '$cleanTitle': $imdbId")
                        return@withContext Pair(imdbId, true)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Cinemeta series search error: ${e.message}")
        }

        // 2. Fallback to movie catalog (movies, OVAs)
        try {
            val url = "$CINEMETA_BASE/catalog/movie/top/search=$encoded.json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MakimonoPlayer/1.4")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                val metas = json.optJSONArray("metas")
                if (metas != null && metas.length() > 0) {
                    val first = metas.getJSONObject(0)
                    val imdbId = first.optString("imdb_id").ifBlank { first.optString("id") }
                    if (imdbId.startsWith("tt")) {
                        Log.d(TAG, "Resolved movie IMDB ID for '$cleanTitle': $imdbId")
                        return@withContext Pair(imdbId, false)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Cinemeta movie search error: ${e.message}")
        }

        null
    }

    /**
     * Searches for online subtitles, prioritizing Portuguese (Brazilian) subtitles.
     */
    suspend fun searchSubtitles(
        rawTitle: String,
        season: Int? = null,
        episode: Double? = null
    ): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        val parsed = EpisodeParser.parse(rawTitle)
        val showTitle = parsed.showTitle.ifBlank { rawTitle }
        val s = season ?: parsed.season ?: 1
        val ep = (episode ?: parsed.episode ?: 1.0).toInt().coerceAtLeast(1)

        Log.d(TAG, "Searching subtitles for '$showTitle' S${s}E${ep}...")

        val resolved = resolveImdbId(showTitle)
        if (resolved == null) {
            Log.w(TAG, "Could not resolve IMDB ID for '$showTitle'")
            return@withContext emptyList()
        }

        val (imdbId, isSeries) = resolved
        val subUrl = if (isSeries) {
            "$OPENSUBTITLES_BASE/subtitles/series/$imdbId:$s:$ep.json"
        } else {
            "$OPENSUBTITLES_BASE/subtitles/movie/$imdbId.json"
        }

        Log.d(TAG, "Fetching subtitles from $subUrl")
        try {
            val req = Request.Builder()
                .url(subUrl)
                .header("User-Agent", "MakimonoPlayer/1.4")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Failed to fetch subtitles: HTTP ${resp.code}")
                return@withContext emptyList()
            }

            val json = JSONObject(resp.body?.string().orEmpty())
            val subsArray = json.optJSONArray("subtitles") ?: return@withContext emptyList()

            val resultList = mutableListOf<OnlineSubtitle>()
            for (i in 0 until subsArray.length()) {
                val item = subsArray.getJSONObject(i)
                val lang = item.optString("lang", "unk").lowercase()
                val url = item.optString("url")
                val id = item.optString("id", url)

                if (url.isNotBlank()) {
                    val isPt = lang in listOf("pob", "por", "pt", "pt-br")
                    val langName = when (lang) {
                        "pob", "pt-br" -> "Português (Brasil)"
                        "por", "pt" -> "Português"
                        "eng", "en" -> "Inglês (English)"
                        "spa", "es" -> "Espanhol"
                        "jpn", "ja" -> "Japonês"
                        else -> lang.uppercase()
                    }

                    resultList.add(
                        OnlineSubtitle(
                            id = id,
                            lang = lang,
                            langName = langName,
                            downloadUrl = url,
                            isPortuguese = isPt
                        )
                    )
                }
            }

            // Sort: Portuguese (Brazil) first, then other Portuguese, then English, then others
            resultList.sortedWith(
                compareByDescending<OnlineSubtitle> { it.isPortuguese }
                    .thenByDescending { it.lang == "pob" || it.lang == "pt-br" }
                    .thenBy { it.langName }
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error fetching subtitles from OpenSubtitles", e)
            emptyList()
        }
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
                    } catch (e: Exception) {
                        null
                    }
                }
                val langName = rawLangName ?: when (langPart) {
                    "pob", "pt-br" -> "Português (Brasil)"
                    "por", "pt" -> "Português"
                    "eng", "en" -> "Inglês (English)"
                    "spa", "es" -> "Espanhol"
                    "jpn", "ja" -> "Japonês"
                    else -> langPart.uppercase()
                }
                val isPt = langPart in listOf("pob", "por", "pt", "pt-br") || langName.contains("Português", ignoreCase = true)
                SavedSubtitle(
                    file = file,
                    lang = langPart,
                    langName = langName,
                    isPortuguese = isPt
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing saved subtitle file: ${file.name}", e)
                null
            }
        }
    }

    /**
     * Downloads an online subtitle file to persistent internal storage.
     */
    suspend fun downloadSubtitle(
        sub: OnlineSubtitle,
        filenamePrefix: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading subtitle: ${sub.langName} from ${sub.downloadUrl}")
            val req = Request.Builder()
                .url(sub.downloadUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${resp.code}: ${resp.message}")
                )
            }

            val body = resp.body ?: return@withContext Result.failure(
                IOException("Corpo da resposta vazio ao baixar legenda")
            )

            val dir = subtitlesDir
            val cleanPrefix = sanitizePrefix(filenamePrefix)
            val encodedLangName = URLEncoder.encode(sub.langName, "UTF-8")
            val targetFile = File(dir, "${cleanPrefix}__online_${sub.lang}__${encodedLangName}.srt")

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Subtitle downloaded and permanently saved: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to download online subtitle", e)
            Result.failure(e)
        }
    }
}
