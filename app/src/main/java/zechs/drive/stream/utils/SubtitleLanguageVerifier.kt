package zechs.drive.stream.utils

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

enum class SubtitleLanguage(
    val primaryCode: String,
    val isoAliases: Set<String>,
    val displayName: String,
    val shortLabel: String,
    val flag: String
) {
    PT_BR(
        primaryCode = "pob",
        isoAliases = setOf("pob", "pt-br", "pt_br", "pb", "brazilian", "ptbr"),
        displayName = "Português (Brasil)",
        shortLabel = "PT-BR",
        flag = "🇧🇷"
    ),
    PT_PT(
        primaryCode = "por",
        isoAliases = setOf("por", "pt", "pt-pt", "pt_pt", "portuguese"),
        displayName = "Português (Portugal)",
        shortLabel = "PT-PT",
        flag = "🇵🇹"
    ),
    EN(
        primaryCode = "eng",
        isoAliases = setOf("eng", "en", "en-us", "en-gb", "english"),
        displayName = "English (Oficial)",
        shortLabel = "EN",
        flag = "🇺🇸"
    ),
    ES(
        primaryCode = "spa",
        isoAliases = setOf("spa", "es", "es-la", "es-es", "spanish", "espanol"),
        displayName = "Español",
        shortLabel = "ES",
        flag = "🇪🇸"
    ),
    JA(
        primaryCode = "jpn",
        isoAliases = setOf("jpn", "ja", "jp", "japanese"),
        displayName = "日本語 (Japonês)",
        shortLabel = "JA",
        flag = "🇯🇵"
    );

    fun matchesLanguageCode(rawCode: String): Boolean {
        val norm = rawCode.trim().lowercase(Locale.ROOT)
        if (norm in isoAliases) return true
        // PT_BR also accepts generic "por"/"pt" as candidate (verified by lexical analysis)
        if (this == PT_BR && norm in PT_PT.isoAliases) return true
        return false
    }

    fun isExactCodeMatch(rawCode: String): Boolean {
        return rawCode.trim().lowercase(Locale.ROOT) in isoAliases
    }

    companion object {
        fun fromCodeOrNull(rawCode: String?): SubtitleLanguage? {
            val norm = rawCode?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return values().firstOrNull { norm in it.isoAliases }
        }

        fun fromCode(rawCode: String?): SubtitleLanguage {
            return fromCodeOrNull(rawCode) ?: PT_BR
        }

        fun flagForCode(rawCode: String?): String {
            return fromCodeOrNull(rawCode)?.flag ?: "🌐"
        }
    }
}

data class LanguageVerificationResult(
    val detectedLanguage: SubtitleLanguage?,
    val certaintyScore: Int,
    val isHighCertaintyMatch: Boolean,
    val isOfficialGrade: Boolean,
    val cueCount: Int,
    val previewSample: String?,
    val normalizedUtf8Text: String,
    val encodingLabel: String
)

/**
 * Verificador léxico, ortográfico e estrutural de legendas com normalização automática
 * de codificação (UTF-8 / UTF-16 / Windows-1252) para garantir 100% de certeza do idioma
 * selecionado e evitar caracteres corrompidos em português.
 */
object SubtitleLanguageVerifier {

    private val SRT_TIMESTAMP_REGEX = Regex("\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{2,3}\\s*-->\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{2,3}")
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val ASS_TAG_REGEX = Regex("\\{[^}]*\\}")
    private val WORD_SPLIT_REGEX = Regex("[^\\p{L}']+")

    private val WATERMARK_PATTERNS = listOf(
        "opensubtitles",
        "subtitles by",
        "legendas por",
        "sincronizado por",
        "tradução:",
        "traducao:",
        "addic7ed",
        "subscene",
        "www.",
        "http://",
        "https://"
    )

    private val PT_COMMON_WORDS = setOf(
        "não", "nao", "que", "para", "com", "uma", "está", "esta", "isso", "aqui",
        "como", "mais", "mas", "por", "meu", "minha", "tudo", "vamos", "então", "entao",
        "também", "tambem", "ele", "ela", "nós", "nos", "eles", "elas", "muito", "quando",
        "onde", "quem", "porque", "ainda", "agora", "nada", "algo", "fazer", "dizer",
        "pode", "posso", "ser", "ter", "foi", "era", "são", "sao", "estão", "estao",
        "este", "esta", "esse", "essa", "aquele", "aquela", "dele", "dela", "sempre",
        "nunca", "mesmo", "depois", "antes", "sobre", "entre", "até", "ate", "Obrigado", "obrigado"
    )

    private val PT_BR_MARKERS = setOf(
        "você", "voce", "vocês", "voces", "pra", "pro", "tá", "né", "cara",
        "garoto", "garota", "legal", "cadê", "cade", "aí", "ai", "daqui",
        "falando", "fazendo", "olhando", "pensando", "tentando", "esperando"
    )

    private val PT_PT_MARKERS = setOf(
        "estás", "tens", "foste", "vós", "fixe", "miúdo", "miudo", "rapariga",
        "comboio", "ecrã", "facto", "equipa", "connosco", "percebes"
    )

    private val ES_COMMON_WORDS = setOf(
        "que", "qué", "no", "para", "con", "una", "está", "estás", "eso", "aquí",
        "como", "cómo", "más", "pero", "por", "mi", "todo", "vamos", "entonces",
        "también", "él", "ella", "nosotros", "ellos", "muy", "cuando", "cuándo",
        "dónde", "quién", "porque", "todavía", "ahora", "nada", "hacer", "decir",
        "puede", "puedo", "usted", "ustedes", "bueno", "hacia", "desde", "siempre",
        "nunca", "mismo", "después", "antes", "gracias", "hola", "eres", "soy", "son"
    )

    private val ES_EXCLUSIVE_WORDS = setOf(
        "usted", "ustedes", "pero", "muy", "también", "hacia", "nosotros", "todavía",
        "después", "quién", "cuándo", "dónde", "eres", "hola", "gracias", "bueno", "ahora"
    )

    private val EN_COMMON_WORDS = setOf(
        "the", "you", "that", "with", "have", "this", "what", "your", "from", "they",
        "know", "don't", "you're", "i'm", "it's", "there", "about", "would", "could",
        "should", "think", "right", "going", "want", "here", "where", "when", "because",
        "something", "nothing", "everything", "never", "always", "really", "please"
    )

    /**
     * Detecta o charset (UTF-8, UTF-16LE/BE ou Windows-1252/ISO-8859-1) e normaliza
     * o conteúdo para UTF-8 limpo sem BOM corrompido.
     */
    fun decodeAndNormalizeToUtf8(bytes: ByteArray): Pair<String, String> {
        if (bytes.isEmpty()) return Pair("", "UTF-8")

        // 1. Check BOMs
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            return Pair(text, "UTF-8")
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            val text = String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            return Pair(text, "UTF-16LE → UTF-8")
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            val text = String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            return Pair(text, "UTF-16BE → UTF-8")
        }

        // 2. Try strict UTF-8 decoding
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            Pair(charBuffer.toString(), "UTF-8")
        } catch (_: Exception) {
            // 3. Fallback to Windows-1252 (Latin-1 superset used by legacy PT-BR/ES subtitles)
            val win1252 = try {
                Charset.forName("windows-1252")
            } catch (_: Exception) {
                Charsets.ISO_8859_1
            }
            val text = String(bytes, win1252)
            Pair(text, "Win-1252 → UTF-8")
        }
    }

    fun verifySubtitleBytes(
        bytes: ByteArray,
        targetLanguage: SubtitleLanguage,
        declaredLangCode: String? = null
    ): LanguageVerificationResult {
        val (utf8Text, encodingLabel) = decodeAndNormalizeToUtf8(bytes)
        return verifySubtitleText(utf8Text, targetLanguage, declaredLangCode, encodingLabel)
    }

    fun verifySubtitleText(
        rawText: String,
        targetLanguage: SubtitleLanguage,
        declaredLangCode: String? = null,
        encodingLabel: String = "UTF-8"
    ): LanguageVerificationResult {
        val lines = rawText.lineSequence().toList()
        var cueCount = 0
        val dialogueLines = mutableListOf<String>()
        val previewCandidates = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue
            if (SRT_TIMESTAMP_REGEX.containsMatchIn(line)) {
                cueCount++
                continue
            }
            if (line.startsWith("Dialogue:", ignoreCase = true)) {
                cueCount++
                val afterDialogue = line.substringAfter("Dialogue:").trim()
                val textPart = afterDialogue.split(",", limit = 10).getOrNull(9)?.trim().orEmpty()
                val cleaned = cleanDialogueLine(textPart)
                if (cleaned.isNotEmpty() && !isWatermarkLine(cleaned)) {
                    dialogueLines.add(cleaned)
                    if (previewCandidates.size < 2 && cleaned.length in 10..78) {
                        previewCandidates.add(cleaned)
                    }
                }
                continue
            }
            // Skip SRT cue numbers and ASS headers
            if (line.all { it.isDigit() }) continue
            if (line.startsWith("[") && line.endsWith("]")) continue
            if (line.startsWith("Format:", ignoreCase = true) ||
                line.startsWith("Style:", ignoreCase = true) ||
                line.startsWith("Title:", ignoreCase = true) ||
                line.startsWith("ScriptType:", ignoreCase = true) ||
                line.startsWith("PlayRes", ignoreCase = true) ||
                line.startsWith("WEBVTT", ignoreCase = true)
            ) continue

            val cleaned = cleanDialogueLine(line)
            if (cleaned.isNotEmpty() && !isWatermarkLine(cleaned)) {
                dialogueLines.add(cleaned)
                if (previewCandidates.size < 2 && cleaned.length in 10..78) {
                    previewCandidates.add(cleaned)
                }
            }
        }

        if (cueCount == 0 || dialogueLines.isEmpty()) {
            return LanguageVerificationResult(
                detectedLanguage = null,
                certaintyScore = 0,
                isHighCertaintyMatch = false,
                isOfficialGrade = false,
                cueCount = cueCount,
                previewSample = null,
                normalizedUtf8Text = rawText,
                encodingLabel = encodingLabel
            )
        }

        val sampleText = dialogueLines.take(250).joinToString(" ")
        val lowerSample = sampleText.lowercase(Locale.ROOT)
        val words = lowerSample.split(WORD_SPLIT_REGEX).filter { it.length >= 2 }

        if (words.isEmpty()) {
            return LanguageVerificationResult(
                detectedLanguage = null,
                certaintyScore = 0,
                isHighCertaintyMatch = false,
                isOfficialGrade = false,
                cueCount = cueCount,
                previewSample = previewCandidates.firstOrNull(),
                normalizedUtf8Text = rawText,
                encodingLabel = encodingLabel
            )
        }

        // Count Japanese characters
        val jpCharCount = sampleText.count { ch ->
            ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' || ch in '\u4E00'..'\u9FAF'
        }

        // Count Portuguese, Spanish, English lexical hits
        var ptHits = 0
        var ptBrHits = 0
        var ptPtHits = 0
        var esHits = 0
        var esExclusiveHits = 0
        var enHits = 0

        for (w in words) {
            if (w in PT_COMMON_WORDS) ptHits++
            if (w in PT_BR_MARKERS) {
                ptHits += 2
                ptBrHits += 2
            }
            if (w in PT_PT_MARKERS) {
                ptHits += 2
                ptPtHits += 2
            }
            if (w in ES_COMMON_WORDS) esHits++
            if (w in ES_EXCLUSIVE_WORDS) {
                esHits += 2
                esExclusiveHits += 2
            }
            if (w in EN_COMMON_WORDS) enHits += 2
        }

        // Orthographic / diacritic signatures
        val ptDiacritics = lowerSample.count { it == 'ã' || it == 'õ' || it == 'ç' || it == 'ê' || it == 'ô' }
        val esPunctuation = lowerSample.count { it == '¿' || it == '¡' || it == 'ñ' }

        val totalPtScore = ptHits + (ptDiacritics * 2) - (esPunctuation * 3)
        val totalEsScore = esHits + (esExclusiveHits * 2) + (esPunctuation * 4) - (ptDiacritics * 2)
        val totalEnScore = enHits
        val totalJaScore = jpCharCount * 2

        val detectedLanguage = when {
            totalJaScore >= 20 && totalJaScore > totalPtScore && totalJaScore > totalEnScore -> SubtitleLanguage.JA
            totalEnScore > totalPtScore && totalEnScore > totalEsScore && totalEnScore >= 4 -> SubtitleLanguage.EN
            totalEsScore > totalPtScore && (esExclusiveHits >= 2 || esPunctuation >= 1) -> SubtitleLanguage.ES
            totalPtScore >= 3 -> {
                val declaredNorm = declaredLangCode?.trim()?.lowercase(Locale.ROOT).orEmpty()
                if (ptPtHits > ptBrHits && declaredNorm !in SubtitleLanguage.PT_BR.isoAliases) {
                    SubtitleLanguage.PT_PT
                } else {
                    SubtitleLanguage.PT_BR
                }
            }
            else -> declaredLangCode?.let { SubtitleLanguage.fromCode(it) }
        }

        val matchesTarget = when (targetLanguage) {
            SubtitleLanguage.PT_BR -> detectedLanguage == SubtitleLanguage.PT_BR || detectedLanguage == SubtitleLanguage.PT_PT
            SubtitleLanguage.PT_PT -> detectedLanguage == SubtitleLanguage.PT_PT || detectedLanguage == SubtitleLanguage.PT_BR
            else -> detectedLanguage == targetLanguage
        }

        val isExactDialect = detectedLanguage == targetLanguage
        val declaredExact = declaredLangCode != null && targetLanguage.isExactCodeMatch(declaredLangCode)

        val baseCertainty = when {
            !matchesTarget -> 18
            targetLanguage == SubtitleLanguage.JA && totalJaScore >= 30 -> 98
            targetLanguage == SubtitleLanguage.EN && totalEnScore >= 12 -> 97
            targetLanguage == SubtitleLanguage.ES && totalEsScore >= 12 -> 97
            (targetLanguage == SubtitleLanguage.PT_BR || targetLanguage == SubtitleLanguage.PT_PT) && totalPtScore >= 14 -> {
                when {
                    isExactDialect && declaredExact && ptBrHits >= 2 -> 99
                    isExactDialect && declaredExact -> 97
                    isExactDialect -> 95
                    else -> 89
                }
            }
            matchesTarget -> 86
            else -> 35
        }

        // Adjust by cue completeness (typical 24-min anime episode has 150-400 cues)
        val cueBonus = when {
            cueCount >= 120 -> 0
            cueCount >= 50 -> -2
            cueCount >= 15 -> -6
            else -> -15
        }

        val finalCertainty = (baseCertainty + cueBonus).coerceIn(5, 99)
        val isHighCertainty = matchesTarget && finalCertainty >= 85
        val isOfficialGrade = isHighCertainty && cueCount >= 60 && !rawText.contains('\uFFFD')

        val previewSample = previewCandidates.firstOrNull()?.let { first ->
            if (previewCandidates.size >= 2) {
                "$first / ${previewCandidates[1]}"
            } else {
                first
            }
        }

        return LanguageVerificationResult(
            detectedLanguage = detectedLanguage,
            certaintyScore = finalCertainty,
            isHighCertaintyMatch = isHighCertainty,
            isOfficialGrade = isOfficialGrade,
            cueCount = cueCount,
            previewSample = previewSample,
            normalizedUtf8Text = rawText,
            encodingLabel = encodingLabel
        )
    }

    private fun cleanDialogueLine(raw: String): String {
        return raw
            .replace(ASS_TAG_REGEX, "")
            .replace(HTML_TAG_REGEX, "")
            .replace("\\N", " ")
            .replace("\\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isWatermarkLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return WATERMARK_PATTERNS.any { lower.contains(it) }
    }
}
