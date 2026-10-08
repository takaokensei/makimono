package zechs.drive.stream.ui.series

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
    private val subtitleTag = Regex("(?i)(?<![a-z0-9])(?:multi[ ._-]?subs?|subtitles?|legendado|legendas)(?![a-z0-9])")

    fun quality(fileNames: List<String>, listingComplete: Boolean): String? {
        val labels = fileNames.map(::qualityLabel)
        val observed = labels.filterNotNull().distinct()
        return when {
            observed.isEmpty() -> null
            observed.size > 1 -> "Variada · nome"
            listingComplete && labels.all { it != null } -> "${observed.single()} · nome"
            else -> "Parcial · nome"
        }
    }

    fun audio(fileNames: List<String>, listingComplete: Boolean): String? {
        val labels = fileNames.map(::audioLabelForFile)
        val observed = labels.filterNotNull().distinct()
        return when {
            observed.isEmpty() -> null
            observed.size > 1 -> "Variado · nome"
            listingComplete && labels.all { it != null } -> observed.single()
            else -> "Parcial · nome"
        }
    }

    fun subtitles(fileNames: List<String>, listingComplete: Boolean): String? {
        val taggedCount = fileNames.count { subtitleTag.containsMatchIn(it) }
        return when {
            taggedCount == 0 -> null
            listingComplete && taggedCount == fileNames.size -> "Legendas · nome"
            else -> "Parcial · nome"
        }
    }

    fun audioLabelForFile(fileName: String): String? = when {
        dualAudioTag.containsMatchIn(fileName) -> "Dual · nome"
        ptBrAudioTag.containsMatchIn(fileName) -> "PT-BR · nome"
        else -> null
    }

    private fun qualityLabel(fileName: String): String? = qualityTags.firstNotNullOfOrNull { (label, pattern) ->
        label.takeIf { pattern.containsMatchIn(fileName) }
    }
}
