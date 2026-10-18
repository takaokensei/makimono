package zechs.drive.stream.ui.series

enum class Coverage { COMPLETE, PARTIAL, MIXED }

data class MediaFact(val value: String?, val coverage: Coverage) {
    fun formatQuality(): String = when (coverage) {
        Coverage.COMPLETE -> value ?: "HD"
        Coverage.PARTIAL -> if (value != null) "$value (parcial)" else "HD (parcial)"
        Coverage.MIXED -> "Variada"
    }

    fun formatAudio(): String = when (coverage) {
        Coverage.COMPLETE -> value ?: "Dual Áudio"
        Coverage.PARTIAL -> if (value != null) "$value (parcial)" else "Parcial"
        Coverage.MIXED -> "Variado"
    }

    fun formatSubtitles(): String = when (coverage) {
        Coverage.COMPLETE -> value ?: "Legendas"
        Coverage.PARTIAL -> "Legendas (parcial)"
        Coverage.MIXED -> "Legendas (parcial)"
    }
}

/** Aggregates filename-derived hints without claiming coverage the Drive listing did not provide. */
internal object SeriesMediaMetadataPolicy {
    private val qualityTags = listOf(
        "4K" to Regex("(?i)(?<![a-z0-9])(?:2160p|4k)(?![a-z0-9])"),
        "1080p" to Regex("(?i)(?<![a-z0-9])1080p(?![a-z0-9])"),
        "720p" to Regex("(?i)(?<![a-z0-9])720p(?![a-z0-9])"),
        "480p" to Regex("(?i)(?<![a-z0-9])480p(?![a-z0-9])")
    )
    private val dualAudioTag = Regex("(?iu)(?<![a-z0-9])dual[ ._-]?(?:audio|áudio)(?![a-z0-9])")
    private val ptBrAudioTag = Regex("(?i)(?<![a-z0-9])pt[ ._-]?br(?![a-z0-9])")
    private val dubladoTag = Regex("(?iu)(?<![a-z0-9])(?:dublado|dub)(?![a-z0-9])")
    private val subtitleTag = Regex("(?i)(?<![a-z0-9])(?:multi[ ._-]?subs?|subtitles?|legendado|legendas)(?![a-z0-9])")

    fun quality(fileNames: List<String>, listingComplete: Boolean): MediaFact? {
        val labels = fileNames.map(::qualityLabel)
        val observed = labels.filterNotNull().distinct()
        return when {
            observed.isEmpty() -> null
            observed.size > 1 -> MediaFact(value = null, coverage = Coverage.MIXED)
            listingComplete && labels.all { it != null } -> MediaFact(value = observed.single(), coverage = Coverage.COMPLETE)
            else -> MediaFact(value = observed.firstOrNull(), coverage = Coverage.PARTIAL)
        }
    }

    fun audio(fileNames: List<String>, listingComplete: Boolean): MediaFact? {
        val labels = fileNames.map(::audioLabelForFile)
        val observed = labels.filterNotNull().distinct()
        return when {
            observed.isEmpty() -> null
            observed.size > 1 -> MediaFact(value = null, coverage = Coverage.MIXED)
            listingComplete && labels.all { it != null } -> MediaFact(value = observed.single(), coverage = Coverage.COMPLETE)
            else -> MediaFact(value = observed.firstOrNull(), coverage = Coverage.PARTIAL)
        }
    }

    fun subtitles(fileNames: List<String>, listingComplete: Boolean): MediaFact? {
        val taggedCount = fileNames.count { subtitleTag.containsMatchIn(it) }
        return when {
            taggedCount == 0 -> null
            listingComplete && taggedCount == fileNames.size -> MediaFact(value = "Legendas", coverage = Coverage.COMPLETE)
            else -> MediaFact(value = "Legendas", coverage = Coverage.PARTIAL)
        }
    }

    fun audioLabelForFile(fileName: String): String? = when {
        dualAudioTag.containsMatchIn(fileName) -> "Dual Áudio"
        ptBrAudioTag.containsMatchIn(fileName) -> "PT-BR"
        dubladoTag.containsMatchIn(fileName) -> "Dublado"
        else -> null
    }

    private fun qualityLabel(fileName: String): String? = qualityTags.firstNotNullOfOrNull { (label, pattern) ->
        label.takeIf { pattern.containsMatchIn(fileName) }
    }

    /**
     * Formats hero synopsis into a concise, non-truncated overview (1 to 2 complete sentences)
     * without mid-sentence truncation or trailing broken ellipsis.
     */
    fun formatHeroSynopsis(synopsis: String?, maxSentences: Int = 2, maxChars: Int = 220): String? {
        if (synopsis.isNullOrBlank()) return null
        val clean = synopsis
            .replace(Regex("<br\\s*/?>"), " ")
            .replace(Regex("<.*?>"), "")
            .replace(Regex("\\[Written by.*?\\]"), "")
            .replace(Regex("\\(Source:.*?\\)"), "")
            .trim()
        if (clean.isBlank()) return null

        val sentenceEndRegex = Regex("(?<=[.!?])\\s+")
        val sentences = clean.split(sentenceEndRegex).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.isEmpty()) return null

        val sb = StringBuilder()
        var count = 0
        for (sentence in sentences) {
            if (count >= maxSentences) break
            if (sb.isNotEmpty() && (sb.length + sentence.length + 1 > maxChars)) {
                break
            }
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(sentence)
            count++
        }

        val result = sb.toString().trim()
        return if (result.isNotBlank()) {
            result
        } else {
            val first = sentences.first()
            if (first.length <= maxChars) {
                first
            } else {
                first.take(maxChars).substringBeforeLast(" ").trimEnd('.', ',', ';', ':') + "."
            }
        }
    }
}

