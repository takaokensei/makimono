package zechs.drive.stream.ui.series

/** Builds a concise spoken label for a selectable episode card from known metadata only. */
object EpisodeCardAccessibilityLabel {
    fun build(
        actionAndTitle: String,
        duration: String?,
        audio: String?,
        progress: String?
    ): String = listOf(actionAndTitle, duration, audio, progress)
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        .joinToString(separator = ", ")

    /**
     * Builds the spoken TalkBack description for a Home "Continue Watching" card,
     * announcing the series title, episode identifier, and remaining duration/status.
     */
    fun buildContinueWatchingLabel(
        title: String,
        episodeBadge: String?,
        remainingLabel: String?
    ): String = listOf(title, episodeBadge, remainingLabel)
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        .joinToString(separator = ", ")
}
