package zechs.drive.stream.data.translation

import zechs.drive.stream.utils.SubtitleConverter

/**
 * High-fidelity subtitle codec that isolates human dialogue for neural translation while
 * preserving ASS/SSA override tags (styles, positions, fades, colors) and SubRip HTML tags.
 */
object AssTagPreservingCodec {

    private val ASS_TAG_REGEX = Regex("""\{[^}]*\}""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val TOKEN_REGEX = Regex("""\[\[TAG_(\d+)\]\]""")
    private val LINE_BREAK_REGEX = Regex("""\r\n|\r|\n|\\N|\\n""")

    data class CueItem(
        val id: Int,
        val startMs: Long,
        val endMs: Long,
        val cleanText: String,
        val prefix: String, // Everything before Text in Dialogue line (or empty for SRT)
        val tagsMap: Map<String, String>, // token -> original tag (e.g. "[[TAG_0]]" -> "{\an8\pos(960,120)}")
        val isAss: Boolean = true
    )

    /**
     * Extracts translatable dialogue lines from an ASS/SSA file, replacing all `{...}` tags with
     * immutable tokens (`[[TAG_0]]`, `[[TAG_1]]`) so neural LLMs and MT engines don't corrupt them.
     */
    fun extractFromAss(lines: List<String>): List<CueItem> {
        val result = mutableListOf<CueItem>()
        var inEvents = false
        var startIndex = 1
        var endIndex = 2
        var textIndex = 9
        var cueCounter = 0

        for (line in lines) {
            val cleanLine = line.removePrefix("\uFEFF").trim()
            if (cleanLine.equals("[Events]", ignoreCase = true) || cleanLine.endsWith("[Events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (!inEvents) continue

            if (cleanLine.startsWith("Format:", ignoreCase = true)) {
                val fields = cleanLine.substringAfter(":").split(",").map { it.trim().lowercase() }
                val s = fields.indexOf("start")
                if (s != -1) startIndex = s
                val e = fields.indexOf("end")
                if (e != -1) endIndex = e
                val t = fields.indexOf("text")
                if (t != -1) textIndex = t
                continue
            }

            if (cleanLine.startsWith("Dialogue:", ignoreCase = true)) {
                val payload = cleanLine.substringAfter("Dialogue:").trim()
                val maxCol = maxOf(textIndex, startIndex, endIndex)
                val parts = SubtitleConverter.parseAssDialogueLine(payload, maxCol)
                if (parts.size > maxCol) {
                    val startRaw = parts[startIndex].trim()
                    val endRaw = parts[endIndex].trim()
                    val rawText = parts.drop(textIndex).joinToString(",")

                    val startMs = SubtitleConverter.parseAssTimeToMs(startRaw)
                    val endMs = SubtitleConverter.parseAssTimeToMs(endRaw)

                    val prefix = "Dialogue: " + parts.take(textIndex).joinToString(",") + ","

                    val (tokenizedText, tagsMap) = tokenizeTags(rawText, isAss = true)
                    val normalizedText = normalizeLineBreaks(tokenizedText)

                    if (normalizedText.isNotBlank() && endMs > startMs) {
                        cueCounter++
                        result.add(
                            CueItem(
                                id = cueCounter,
                                startMs = startMs,
                                endMs = endMs,
                                cleanText = normalizedText,
                                prefix = prefix,
                                tagsMap = tagsMap,
                                isAss = true
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * Reconstructs a full ASS file with translated dialogues while keeping all metadata,
     * styles, headers, and original tags intact.
     */
    fun reconstructAss(
        originalLines: List<String>,
        translatedMap: Map<Int, String>
    ): String {
        val output = StringBuilder()
        var inEvents = false
        var cueCounter = 0
        var startIndex = 1
        var endIndex = 2
        var textIndex = 9

        for (line in originalLines) {
            val cleanLine = line.removePrefix("\uFEFF").trim()
            if (cleanLine.equals("[Events]", ignoreCase = true) || cleanLine.endsWith("[Events]", ignoreCase = true)) {
                inEvents = true
                output.append(line).append("\n")
                continue
            }
            if (!inEvents) {
                output.append(line).append("\n")
                continue
            }

            if (cleanLine.startsWith("Format:", ignoreCase = true)) {
                val fields = cleanLine.substringAfter(":").split(",").map { it.trim().lowercase() }
                val s = fields.indexOf("start")
                if (s != -1) startIndex = s
                val e = fields.indexOf("end")
                if (e != -1) endIndex = e
                val t = fields.indexOf("text")
                if (t != -1) textIndex = t
                output.append(line).append("\n")
                continue
            }

            if (cleanLine.startsWith("Dialogue:", ignoreCase = true)) {
                val payload = cleanLine.substringAfter("Dialogue:").trim()
                val maxCol = maxOf(textIndex, startIndex, endIndex)
                val parts = SubtitleConverter.parseAssDialogueLine(payload, maxCol)
                if (parts.size > maxCol) {
                    val rawText = parts.drop(textIndex).joinToString(",")
                    val (tokenizedText, tagsMap) = tokenizeTags(rawText, isAss = true)
                    val normalized = normalizeLineBreaks(tokenizedText)
                    if (normalized.isNotBlank()) {
                        cueCounter++
                        val translatedText = translatedMap[cueCounter]
                        if (translatedText != null) {
                            val restoredText = detokenizeTags(translatedText, tagsMap, isAss = true)
                            val prefix = "Dialogue: " + parts.take(textIndex).joinToString(",") + ","
                            output.append(prefix).append(restoredText).append("\n")
                            continue
                        }
                    }
                }
            }
            output.append(line).append("\n")
        }
        return output.toString()
    }

    /**
     * Extracts cues from SRT text format with tag protection.
     */
    fun extractFromSrt(srtContent: String): List<CueItem> {
        val result = mutableListOf<CueItem>()
        val blocks = srtContent.trim().split(Regex("""\r?\n\r?\n"""))
        val srtTimePattern = Regex("""(\d{2}:\d{2}:\d{2}[,\.]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,\.]\d{3})""")

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue

            val id = lines[0].toIntOrNull() ?: continue
            val timeMatch = srtTimePattern.find(lines[1]) ?: continue
            val startMs = parseSrtTimeToMs(timeMatch.groupValues[1])
            val endMs = parseSrtTimeToMs(timeMatch.groupValues[2])

            val rawText = lines.drop(2).joinToString("\n")
            val (tokenizedText, tagsMap) = tokenizeTags(rawText, isAss = false)

            result.add(
                CueItem(
                    id = id,
                    startMs = startMs,
                    endMs = endMs,
                    cleanText = tokenizedText,
                    prefix = "${lines[0]}\n${lines[1]}\n",
                    tagsMap = tagsMap,
                    isAss = false
                )
            )
        }
        return result
    }

    /**
     * Reconstructs an SRT document from translated cues.
     */
    fun reconstructSrt(cues: List<CueItem>, translatedMap: Map<Int, String>): String {
        val sb = StringBuilder()
        for (cue in cues) {
            val text = translatedMap[cue.id] ?: cue.cleanText
            val restored = detokenizeTags(text, cue.tagsMap, isAss = false)
            sb.append(cue.prefix).append(restored).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    internal fun tokenizeTags(rawText: String, isAss: Boolean): Pair<String, Map<String, String>> {
        val tagsMap = mutableMapOf<String, String>()
        var tokenCounter = 0
        val regex = if (isAss) ASS_TAG_REGEX else HTML_TAG_REGEX

        val replaced = regex.replace(rawText) { matchResult ->
            val token = "[[TAG_${tokenCounter++}]]"
            tagsMap[token] = matchResult.value
            token
        }
        return replaced to tagsMap
    }

    internal fun detokenizeTags(
        translatedText: String,
        tagsMap: Map<String, String>,
        isAss: Boolean
    ): String {
        var result = translatedText

        // Restore tags matching [[TAG_X]]
        result = TOKEN_REGEX.replace(result) { matchResult ->
            val token = matchResult.value
            tagsMap[token] ?: ""
        }

        // Restore any missing tags that the translator might have dropped
        // Prepend leading style/override tags if they disappeared from the translated text
        val leadingTags = tagsMap.entries
            .filter { (token, _) -> !translatedText.contains(token) }
            .map { it.value }

        if (leadingTags.isNotEmpty() && isAss) {
            val missingPrefix = leadingTags.filter { it.startsWith("{\\") }.joinToString("")
            if (missingPrefix.isNotEmpty() && !result.startsWith(missingPrefix)) {
                result = missingPrefix + result
            }
        }

        return if (isAss) {
            // In ASS, newlines in dialogue must be \N
            result.replace("\r\n", "\\N").replace("\n", "\\N")
        } else {
            result.replace("\\N", "\n").replace("\\n", "\n")
        }
    }

    private fun normalizeLineBreaks(text: String): String {
        return text.replace(LINE_BREAK_REGEX, "\n").trim()
    }

    private fun parseSrtTimeToMs(time: String): Long {
        val parts = time.replace('.', ',').split(":")
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secParts = parts[2].split(",")
        val seconds = secParts[0].toLongOrNull() ?: 0L
        val millis = if (secParts.size > 1) secParts[1].take(3).padEnd(3, '0').toLongOrNull() ?: 0L else 0L
        return hours * 3600_000L + minutes * 60_000L + seconds * 1000L + millis
    }
}
