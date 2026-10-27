package zechs.drive.stream.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import zechs.drive.stream.utils.AppLog
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface SubtitleTranslator {
    val providerId: String
    val displayName: String
    suspend fun translateBatch(
        cues: List<Pair<Int, String>>,
        sourceLang: String = "auto",
        targetLang: String = "pt-BR"
    ): Result<Map<Int, String>>
}

@Singleton
class GroqSubtitleTranslator @Inject constructor(
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) : SubtitleTranslator {

    companion object {
        private const val TAG = "GroqSubtitleTranslator"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
    }

    override val providerId: String = "groq"
    override val displayName: String = "Groq LPU (Llama 3.3 70B)"

    var apiKey: String = ""

    override suspend fun translateBatch(
        cues: List<Pair<Int, String>>,
        sourceLang: String,
        targetLang: String
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Groq API key is not configured"))
        }
        if (cues.isEmpty()) return@withContext Result.success(emptyMap())

        try {
            val systemPrompt = """
                You are an expert anime subtitle translator into natural Brazilian Portuguese (PT-BR).
                Translate each dialogue line. Keep tone, honorifics, and character intent natural for anime.
                STRICT REQUIREMENT: NEVER remove, alter, or translate placeholders formatted like [[TAG_0]], [[TAG_1]], etc.
                Output ONLY a JSON object formatted as: {"translations": [{"id": 1, "text": "..."}]}
            """.trimIndent()

            val cuesJson = JSONArray()
            for ((id, text) in cues) {
                val item = JSONObject()
                item.put("id", id)
                item.put("text", text)
                cuesJson.put(item)
            }

            val requestJson = JSONObject()
            requestJson.put("model", MODEL)
            requestJson.put("temperature", 0.3)
            requestJson.put("response_format", JSONObject().put("type", "json_object"))

            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.put(JSONObject().put("role", "user").put("content", cuesJson.toString()))
            requestJson.put("messages", messages)

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(GROQ_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "MakimonoPlayer/1.9")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string().orEmpty()
                    AppLog.w(TAG, "Groq API error $code: $err")
                    return@withContext Result.failure(IOException("Groq HTTP $code: $err"))
                }

                val respBody = response.body?.string().orEmpty()
                val parsedMap = parseJsonResponse(respBody)
                Result.success(parsedMap)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Groq translation failed", e)
            Result.failure(e)
        }
    }

    private fun parseJsonResponse(raw: String): Map<Int, String> {
        val root = JSONObject(raw)
        val choices = root.getJSONArray("choices")
        val firstContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
        val contentObj = JSONObject(firstContent)
        val arr = contentObj.optJSONArray("translations") ?: return emptyMap()

        val map = mutableMapOf<Int, String>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val id = item.getInt("id")
            val text = item.getString("text")
            map[id] = text
        }
        return map
    }
}

@Singleton
class GeminiSubtitleTranslator @Inject constructor(
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) : SubtitleTranslator {

    companion object {
        private const val TAG = "GeminiSubtitleTranslator"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini 2.5 Flash"

    var apiKey: String = ""

    override suspend fun translateBatch(
        cues: List<Pair<Int, String>>,
        sourceLang: String,
        targetLang: String
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured"))
        }
        if (cues.isEmpty()) return@withContext Result.success(emptyMap())

        try {
            val prompt = """
                You are a professional anime subtitle translator into natural Brazilian Portuguese (PT-BR).
                Translate the following dialogue lines. Keep character voice and context.
                IMPORTANT: Preserve tokens like [[TAG_0]], [[TAG_1]] intact.
                Format response as strict JSON:
                {"translations": [{"id": 1, "text": "..."}]}
                
                Lines to translate:
            """.trimIndent()

            val cuesJson = JSONArray()
            for ((id, text) in cues) {
                val item = JSONObject()
                item.put("id", id)
                item.put("text", text)
                cuesJson.put(item)
            }

            val requestJson = JSONObject()
            val contents = JSONArray()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", "$prompt\n$cuesJson"))
            contents.put(JSONObject().put("parts", parts))
            requestJson.put("contents", contents)

            val genConfig = JSONObject()
            genConfig.put("response_mime_type", "application/json")
            genConfig.put("temperature", 0.3)
            requestJson.put("generationConfig", genConfig)

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .header("User-Agent", "MakimonoPlayer/1.9")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string().orEmpty()
                    AppLog.w(TAG, "Gemini API error $code: $err")
                    return@withContext Result.failure(IOException("Gemini HTTP $code: $err"))
                }

                val respStr = response.body?.string().orEmpty()
                val root = JSONObject(respStr)
                val candidates = root.getJSONArray("candidates")
                val textContent = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val contentObj = JSONObject(textContent)
                val arr = contentObj.optJSONArray("translations") ?: return@withContext Result.success(emptyMap())
                val map = mutableMapOf<Int, String>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.getInt("id")
                    val text = item.getString("text")
                    map[id] = text
                }
                Result.success(map)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Gemini translation failed", e)
            Result.failure(e)
        }
    }
}

@Singleton
class DeepLSubtitleTranslator @Inject constructor(
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) : SubtitleTranslator {

    companion object {
        private const val TAG = "DeepLSubtitleTranslator"
        private const val DEEPL_URL = "https://api-free.deepl.com/v2/translate"
    }

    override val providerId: String = "deepl"
    override val displayName: String = "DeepL API Free"

    var apiKey: String = ""

    override suspend fun translateBatch(
        cues: List<Pair<Int, String>>,
        sourceLang: String,
        targetLang: String
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("DeepL API key is not configured"))
        }
        if (cues.isEmpty()) return@withContext Result.success(emptyMap())

        try {
            val jsonBody = JSONObject()
            val textArray = JSONArray()
            for ((_, text) in cues) {
                textArray.put(text)
            }
            jsonBody.put("text", textArray)
            jsonBody.put("target_lang", "PT-BR")
            jsonBody.put("tag_handling", "xml")

            val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(DEEPL_URL)
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .header("User-Agent", "MakimonoPlayer/1.9")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string().orEmpty()
                    AppLog.w(TAG, "DeepL API error $code: $err")
                    return@withContext Result.failure(IOException("DeepL HTTP $code: $err"))
                }

                val respStr = response.body?.string().orEmpty()
                val root = JSONObject(respStr)
                val translations = root.getJSONArray("translations")
                val map = mutableMapOf<Int, String>()
                for (i in 0 until translations.length()) {
                    if (i < cues.size) {
                        val id = cues[i].first
                        val trans = translations.getJSONObject(i).getString("text")
                        map[id] = trans
                    }
                }
                Result.success(map)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "DeepL translation failed", e)
            Result.failure(e)
        }
    }
}

@Singleton
class PublicFallbackTranslator @Inject constructor(
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) : SubtitleTranslator {

    companion object {
        private const val TAG = "PublicFallbackTranslator"
        private const val GTX_URL = "https://translate.googleapis.com/translate_a/single"
    }

    override val providerId: String = "fallback"
    override val displayName: String = "Automático (Sem Chave)"

    override suspend fun translateBatch(
        cues: List<Pair<Int, String>>,
        sourceLang: String,
        targetLang: String
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        if (cues.isEmpty()) return@withContext Result.success(emptyMap())

        try {
            // Process in parallel with up to 4 concurrent coroutines for fast throughput
            val results = cues.chunked(10).map { chunk ->
                async {
                    val chunkMap = mutableMapOf<Int, String>()
                    for ((id, text) in chunk) {
                        val translated = translateSingle(text, targetLang)
                        chunkMap[id] = translated
                    }
                    chunkMap
                }
            }.awaitAll()

            val combined = mutableMapOf<Int, String>()
            for (res in results) {
                combined.putAll(res)
            }
            Result.success(combined)
        } catch (e: Exception) {
            AppLog.e(TAG, "Public fallback translation error", e)
            Result.failure(e)
        }
    }

    private fun translateSingle(text: String, targetLang: String): String {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val target = if (targetLang.startsWith("pt", ignoreCase = true)) "pt" else targetLang
            val url = "$GTX_URL?client=gtx&sl=auto&tl=$target&dt=t&q=$encoded"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return text
                val raw = resp.body?.string().orEmpty()
                val root = JSONArray(raw)
                val sentences = root.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val s = sentences.getJSONArray(i).getString(0)
                    sb.append(s)
                }
                sb.toString().ifBlank { text }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed single translation: ${e.message}")
            text
        }
    }
}

@Singleton
class MultiProviderSubtitleTranslator @Inject constructor(
    val groqTranslator: GroqSubtitleTranslator,
    val geminiTranslator: GeminiSubtitleTranslator,
    val deeplTranslator: DeepLSubtitleTranslator,
    val publicTranslator: PublicFallbackTranslator
) : SubtitleTranslator {

    companion object {
        private const val TAG = "MultiProviderSubtitleTranslator"
    }

    override val providerId: String = "auto"
    override val displayName: String = "Multi-Provedor Inteligente"

    var selectedProvider: String = "auto"

    override suspend fun translateBatch(
        cues: List<Pair<Int, String>>,
        sourceLang: String,
        targetLang: String
    ): Result<Map<Int, String>> {
        val chosen = when (selectedProvider.lowercase()) {
            "groq" -> groqTranslator
            "gemini" -> geminiTranslator
            "deepl" -> deeplTranslator
            else -> null
        }

        if (chosen != null) {
            val primaryResult = chosen.translateBatch(cues, sourceLang, targetLang)
            if (primaryResult.isSuccess) {
                return primaryResult
            }
            AppLog.w(TAG, "Chosen provider ${chosen.providerId} failed, trying fallback: ${primaryResult.exceptionOrNull()?.message}")
        }

        // Automatic fallback chain:
        // Try Groq if key exists -> Try Gemini if key exists -> Try DeepL if key exists -> Public fallback
        if (groqTranslator.apiKey.isNotBlank()) {
            val res = groqTranslator.translateBatch(cues, sourceLang, targetLang)
            if (res.isSuccess) return res
        }
        if (geminiTranslator.apiKey.isNotBlank()) {
            val res = geminiTranslator.translateBatch(cues, sourceLang, targetLang)
            if (res.isSuccess) return res
        }
        if (deeplTranslator.apiKey.isNotBlank()) {
            val res = deeplTranslator.translateBatch(cues, sourceLang, targetLang)
            if (res.isSuccess) return res
        }

        return publicTranslator.translateBatch(cues, sourceLang, targetLang)
    }
}
