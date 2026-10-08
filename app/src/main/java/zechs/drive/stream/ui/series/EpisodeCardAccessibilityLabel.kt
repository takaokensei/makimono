package zechs.drive.stream.ui.series

/** Builds a concise spoken label for a selectable episode card from known metadata only. */
internal object EpisodeCardAccessibilityLabel {
    fun build(
        actionAndTitle: String,
        duration: String?,
        audio: String?,
        progress: String?
    ): String = listOf(actionAndTitle, duration, audio, progress)
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        .joinToString(separator = ", ")
}
