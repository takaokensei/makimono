package zechs.drive.stream.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.utils.AppLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CulturalXRayEngine @Inject constructor(
    private val translator: MultiProviderSubtitleTranslator
) {

    companion object {
        private const val TAG = "CulturalXRayEngine"
        private const val CONTEXT_WINDOW_MS = 60_000L // Analyze only the last 60 seconds
    }

    /**
     * Extracts cultural references, kanji puns, and dialogue nuances strictly up to [currentPositionMs].
     * Zero-spoiler guarantee: only dialogue already heard by the user is provided.
     */
    suspend fun explainSceneContext(
        animeTitle: String,
        episodeNumber: Int,
        currentPositionMs: Long,
        cues: List<AssTagPreservingCodec.CueItem>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val windowStart = maxOf(0L, currentPositionMs - CONTEXT_WINDOW_MS)
        val snippetCues = cues.filter { it.startMs in windowStart..currentPositionMs }

        if (snippetCues.isEmpty()) {
            return@withContext Result.success(listOf("Nenhum diálogo com trocadilho ou referência cultural detectado nesta cena."))
        }

        val dialogueText = snippetCues.joinToString("\n") { it.cleanText }

        try {
            val prompt = """
                Obra: $animeTitle - Episódio $episodeNumber
                Diálogo recente (últimos 60 segundos):
                $dialogueText
                
                Explique de forma concisa em 2 a 3 tópicos (bullet points em Português do Brasil):
                1. Trocadilhos em japonês (kanji/dajare) ou nuances de fala/honoríficos.
                2. Referências culturais japonesas presentes nesta cena específica.
                REGRA ABSOLUTA: PROIBIDO revelar qualquer spoiler ou evento futuro.
            """.trimIndent()

            val res = translator.translateBatch(listOf(1 to prompt))
            val explanation = res.getOrNull()?.get(1) ?: return@withContext Result.failure(IllegalStateException("No explanation returned"))

            val bulletPoints = explanation.lines()
                .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
                .filter { it.isNotBlank() }

            Result.success(bulletPoints)
        } catch (e: Exception) {
            AppLog.e(TAG, "Cultural X-Ray analysis failed", e)
            Result.failure(e)
        }
    }
}
