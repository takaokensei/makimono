package zechs.drive.stream.playback.session

import zechs.drive.stream.utils.MatroskaChapterParser
import zechs.drive.stream.utils.MatroskaChapterParser.ChapterType
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter

/**
 * ADR-002: SkipController
 * Centralizes chapter detection, intelligent AniSkip/MKV chapter merging,
 * automatic opening/ending skips, and 90-second fallback intro skipping.
 */
class SkipController(
    private val autoSkipGracePeriodMs: Long = 3_500L,
    private val fallbackIntroWindowMs: Long = 140_000L,
    private val fallbackIntroSkipDeltaMs: Long = 90_000L
) {

    sealed interface SkipAction {
        object None : SkipAction
        data class AutoSkip(val targetPositionMs: Long, val message: String) : SkipAction
    }

    sealed interface SkipButtonUi {
        object Hidden : SkipButtonUi
        data class Visible(
            val label: String,
            val targetPositionMs: Long,
            val isOverlay: Boolean,
            val chapter: ParsedChapter? = null
        ) : SkipButtonUi
    }

    data class SkipState(
        val activeChapter: ParsedChapter? = null,
        val buttonUi: SkipButtonUi = SkipButtonUi.Hidden,
        val allChapters: List<ParsedChapter> = emptyList()
    )

    private var parsedChapters: List<ParsedChapter> = emptyList()
    private var activeSkipChapter: ParsedChapter? = null
    private var acknowledgedChapter: ParsedChapter? = null
    private var acknowledgedFallbackIntro: Boolean = false
    private var hasAutoSkippedCurrentInterval: Boolean = false
    private var currentIntervalChapterId: Int? = null

    /**
     * Replaces current chapters with a new list, sorted by start time.
     */
    fun setChapters(chapters: List<ParsedChapter>) {
        parsedChapters = chapters.sortedBy { it.startTimeMs }
        activeSkipChapter = null
        acknowledgedChapter = null
        acknowledgedFallbackIntro = false
        hasAutoSkippedCurrentInterval = false
        currentIntervalChapterId = null
    }

    /**
     * Merges AniSkip chapters into existing MKV chapters without overwriting
     * native MKV opening/ending markers if they already exist.
     */
    fun mergeAniSkipChapters(aniSkipChapters: List<ParsedChapter>) {
        if (aniSkipChapters.isEmpty()) return
        val existing = parsedChapters.toMutableList()
        val hasMkvOp = existing.any { it.type == ChapterType.OPENING }
        val hasMkvEd = existing.any { it.type == ChapterType.ENDING }

        for (aniCh in aniSkipChapters) {
            if (aniCh.type == ChapterType.OPENING && !hasMkvOp) {
                existing.add(aniCh)
            } else if (aniCh.type == ChapterType.ENDING && !hasMkvEd) {
                existing.add(aniCh)
            } else if (aniCh.type == ChapterType.RECAP && existing.none { it.type == ChapterType.RECAP }) {
                existing.add(aniCh)
            }
        }

        parsedChapters = existing.sortedBy { it.startTimeMs }
    }

    /**
     * Evaluates skip state and auto-skip action on each playback tick.
     *
     * @param positionMs Current playback position in milliseconds.
     * @param autoSkipEnabled Whether automatic skipping is turned on in settings.
     * @param controllerVisible Whether OSD player controls are currently visible on screen.
     * @param controlsLocked Whether player touch/remote controls are currently locked.
     */
    fun onPlaybackTick(
        positionMs: Long,
        autoSkipEnabled: Boolean = false,
        controllerVisible: Boolean = false,
        controlsLocked: Boolean = false
    ): Pair<SkipState, SkipAction> {
        val ack = acknowledgedChapter
        if (ack != null && positionMs !in ack.startTimeMs until ack.endTimeMs) {
            acknowledgedChapter = null
        }
        if (acknowledgedFallbackIntro && positionMs !in 0L..fallbackIntroWindowMs) {
            acknowledgedFallbackIntro = false
        }

        val specialChapter = if (parsedChapters.isNotEmpty()) {
            parsedChapters.firstOrNull { chapter ->
                chapter.type != ChapterType.OTHER &&
                        chapter != acknowledgedChapter &&
                        positionMs in chapter.startTimeMs until chapter.endTimeMs
            }
        } else null

        activeSkipChapter = specialChapter

        // Reset auto-skip flag when leaving chapter interval
        if (specialChapter == null || specialChapter.index != currentIntervalChapterId) {
            hasAutoSkippedCurrentInterval = false
            currentIntervalChapterId = specialChapter?.index
        }

        var action: SkipAction = SkipAction.None

        if (specialChapter != null) {
            val isOpOrRecap = specialChapter.type == ChapterType.OPENING || specialChapter.type == ChapterType.RECAP
            val isEnding = specialChapter.type == ChapterType.ENDING

            // Evaluate automatic skip
            if (autoSkipEnabled && (isOpOrRecap || isEnding) && !hasAutoSkippedCurrentInterval) {
                if (positionMs in specialChapter.startTimeMs..(specialChapter.startTimeMs + autoSkipGracePeriodMs)) {
                    hasAutoSkippedCurrentInterval = true
                    acknowledgedChapter = specialChapter
                    activeSkipChapter = null
                    val notifyText = when (specialChapter.type) {
                        ChapterType.ENDING -> "⏩ Encerramento pulado automaticamente (AniSkip)"
                        ChapterType.RECAP -> "⏩ Recap pulado automaticamente (AniSkip)"
                        else -> "⏩ Abertura pulada automaticamente (AniSkip)"
                    }
                    action = SkipAction.AutoSkip(
                        targetPositionMs = specialChapter.endTimeMs,
                        message = notifyText
                    )
                }
            }

            val label = when (specialChapter.type) {
                ChapterType.RECAP -> "Pular Recap"
                ChapterType.OPENING -> "Pular Abertura"
                ChapterType.ENDING -> "Pular Encerramento"
                ChapterType.OTHER -> "Pular Capítulo"
            }

            // Visible as overlay if controls are hidden and not locked, or inside controls
            val buttonUi = if (!controlsLocked && action !is SkipAction.AutoSkip) {
                SkipButtonUi.Visible(
                    label = label,
                    targetPositionMs = specialChapter.endTimeMs,
                    isOverlay = !controllerVisible,
                    chapter = specialChapter
                )
            } else {
                SkipButtonUi.Hidden
            }

            val state = SkipState(
                activeChapter = specialChapter,
                buttonUi = buttonUi,
                allChapters = parsedChapters
            )
            return Pair(state, action)
        }

        // Fallback: If no recognized chapters in the file, allow 90s intro skip in the first 140s
        val hasRecognizedChapters = parsedChapters.any { it.type != ChapterType.OTHER }
        val isFallbackWindow = !hasRecognizedChapters && !acknowledgedFallbackIntro &&
            positionMs in 0L..fallbackIntroWindowMs && !controlsLocked && controllerVisible
        val buttonUi = if (isFallbackWindow) {
            SkipButtonUi.Visible(
                label = "Pular Abertura (+90s)",
                targetPositionMs = positionMs + fallbackIntroSkipDeltaMs,
                isOverlay = false,
                chapter = null
            )
        } else {
            SkipButtonUi.Hidden
        }

        val state = SkipState(
            activeChapter = null,
            buttonUi = buttonUi,
            allChapters = parsedChapters
        )
        return Pair(state, action)
    }

    /**
     * Executes manual skip request for the active chapter or 90s fallback.
     * Returns the target position in milliseconds, or null if skip is not applicable.
     */
    fun performSkip(currentPositionMs: Long): Long? {
        val chapter = activeSkipChapter
        if (chapter != null) {
            acknowledgedChapter = chapter
            activeSkipChapter = null
            return chapter.endTimeMs
        }
        val hasRecognizedChapters = parsedChapters.any { it.type != ChapterType.OTHER }
        if (!hasRecognizedChapters && currentPositionMs in 0L..fallbackIntroWindowMs) {
            acknowledgedFallbackIntro = true
            return currentPositionMs + fallbackIntroSkipDeltaMs
        }
        return null
    }

    /**
     * Returns the active special chapter if currently inside one (or just skipped).
     */
    fun getActiveChapter(): ParsedChapter? = activeSkipChapter ?: acknowledgedChapter

    /**
     * Returns all currently loaded chapters.
     */
    fun getChapters(): List<ParsedChapter> = parsedChapters
}
