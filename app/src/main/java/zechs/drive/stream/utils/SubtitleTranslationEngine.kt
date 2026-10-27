package zechs.drive.stream.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.translation.AssTagPreservingCodec
import zechs.drive.stream.data.translation.MultiProviderSubtitleTranslator
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleTranslationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val translator: MultiProviderSubtitleTranslator
) {

    companion object {
        private const val TAG = "SubtitleTranslationEngine"
        private const val LOOKAHEAD_WINDOW_MS = 90_000L // 90 seconds lookahead
        private const val BATCH_SIZE = 25
    }

    sealed class EngineStatus {
        data object Idle : EngineStatus()
        data class Translating(val currentCue: Int, val totalCues: Int, val isInitialChunkReady: Boolean) : EngineStatus()
        data class Completed(val outputFile: File, val totalCues: Int) : EngineStatus()
        data class Error(val message: String) : EngineStatus()
    }

    private val _engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val memoryCueCache = ConcurrentHashMap<String, String>() // rawText -> translatedText
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Translates an ASS or SRT file into PT-BR using lookahead streaming.
     * The initial chunk (covering the current playback position) is translated first (< 1.2s),
     * allowing instant video playback. Background chunks complete progressively.
     */
    suspend fun translateFileProgressive(
        sourceFile: File,
        cacheKey: String,
        currentPositionMs: Long = 0L,
        onProgressiveFileUpdated: suspend (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val ext = sourceFile.extension.lowercase()
        val isAss = ext == "ass" || ext == "ssa"
        val outExt = if (isAss) "ass" else "srt"

        val subDir = File(context.cacheDir, "subtitles")
        subDir.mkdirs()
        val finalFile = File(subDir, "translated_${cacheKey}_ptbr.$outExt")

        // 1. If completed file exists in cache, return immediately
        if (finalFile.exists() && finalFile.length() > 0) {
            AppLog.d(TAG, "Returning fully cached translated subtitle: ${finalFile.name}")
            _engineStatus.value = EngineStatus.Completed(finalFile, 0)
            return@withContext Result.success(finalFile)
        }

        // Cancel previous job for this cacheKey if running
        activeJobs[cacheKey]?.cancel()

        try {
            val lines = sourceFile.readLines(Charsets.UTF_8)
            val cues = if (isAss) {
                AssTagPreservingCodec.extractFromAss(lines)
            } else {
                AssTagPreservingCodec.extractFromSrt(lines.joinToString("\n"))
            }

            if (cues.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No dialogue cues found in source subtitle"))
            }

            val totalCues = cues.size
            val translatedMap = ConcurrentHashMap<Int, String>()

            // 2. Identify priority chunk (around currentPositionMs)
            val priorityCues = cues.filter {
                it.endMs >= maxOf(0L, currentPositionMs - 10_000L) &&
                        it.startMs <= currentPositionMs + LOOKAHEAD_WINDOW_MS
            }.ifEmpty { cues.take(BATCH_SIZE) }

            _engineStatus.value = EngineStatus.Translating(0, totalCues, isInitialChunkReady = false)

            // 3. Translate Priority Chunk immediately
            val priorityPayload = priorityCues.map { it.id to it.cleanText }
            val priorityResult = translator.translateBatch(priorityPayload)

            if (priorityResult.isSuccess) {
                translatedMap.putAll(priorityResult.getOrDefault(emptyMap()))
            } else {
                AppLog.w(TAG, "Priority chunk translation issue: ${priorityResult.exceptionOrNull()?.message}")
            }

            // Write initial progressive file
            val initialContent = if (isAss) {
                AssTagPreservingCodec.reconstructAss(lines, translatedMap)
            } else {
                AssTagPreservingCodec.reconstructSrt(cues, translatedMap)
            }
            finalFile.writeText(initialContent, Charsets.UTF_8)
            _engineStatus.value = EngineStatus.Translating(priorityCues.size, totalCues, isInitialChunkReady = true)
            onProgressiveFileUpdated(finalFile)

            // 4. Spawn background job for remaining cues
            val backgroundJob = scope.launch {
                val remainingCues = cues.filter { it.id !in translatedMap.keys }
                for (chunk in remainingCues.chunked(BATCH_SIZE)) {
                    if (!isActive) break
                    val payload = chunk.map { it.id to it.cleanText }
                    val res = translator.translateBatch(payload)
                    if (res.isSuccess) {
                        translatedMap.putAll(res.getOrDefault(emptyMap()))
                        // Progressively update file on disk
                        val updatedContent = if (isAss) {
                            AssTagPreservingCodec.reconstructAss(lines, translatedMap)
                        } else {
                            AssTagPreservingCodec.reconstructSrt(cues, translatedMap)
                        }
                        finalFile.writeText(updatedContent, Charsets.UTF_8)
                        _engineStatus.value = EngineStatus.Translating(translatedMap.size, totalCues, isInitialChunkReady = true)
                        onProgressiveFileUpdated(finalFile)
                    }
                }
                _engineStatus.value = EngineStatus.Completed(finalFile, totalCues)
                AppLog.d(TAG, "Background translation finished for ${finalFile.name} ($totalCues cues)")
            }

            activeJobs[cacheKey] = backgroundJob
            Result.success(finalFile)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed progressive subtitle translation", e)
            _engineStatus.value = EngineStatus.Error(e.message ?: "Translation error")
            Result.failure(e)
        }
    }

    /**
     * Translates a single text line on-the-fly with in-memory caching.
     * Used for real-time cues from embedded MKV tracks.
     */
    suspend fun translateSingleCueOnTheFly(rawText: String): String = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext rawText
        val cached = memoryCueCache[rawText]
        if (cached != null) return@withContext cached

        val res = translator.translateBatch(listOf(1 to rawText))
        val translated = res.getOrNull()?.get(1)?.ifBlank { rawText } ?: rawText
        memoryCueCache[rawText] = translated
        translated
    }
}
