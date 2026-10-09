package zechs.drive.stream.playback.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter
import zechs.drive.stream.utils.PlaybackProgressPolicy

/**
 * ADR-002: PlaybackSession
 * Unifies playback state, chapter skip handling, playlist progression,
 * and database watch progress persistence across player engines (ExoPlayer and MPV).
 */
class PlaybackSession(
    private val progressRecorder: ProgressRecorder,
    val coordinator: PlaybackCoordinator = PlaybackCoordinator(),
    val skipController: SkipController = SkipController()
) {

    data class SessionState(
        val currentItem: PlaylistItem? = null,
        val nextEpisode: PlaylistItem? = null,
        val prevEpisode: PlaylistItem? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val isWatched: Boolean = false,
        val watchProgressPercent: Int = 0,
        val autoplayUiState: PlaybackCoordinator.AutoplayUiState = PlaybackCoordinator.AutoplayUiState.Hidden,
        val skipState: SkipController.SkipState = SkipController.SkipState(),
        val activeChapter: ParsedChapter? = null
    )

    data class TickResult(
        val autoplayDecision: PlaybackProgressPolicy.AutoplayDecision,
        val skipAction: SkipController.SkipAction
    )

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Updates active playlist and identifies current item.
     */
    fun setPlaylist(items: List<PlaylistItem>, currentFileId: String, currentTitle: String? = null) {
        coordinator.setPlaylist(items, currentFileId, currentTitle)
        val coordState = coordinator.state.value
        _sessionState.value = _sessionState.value.copy(
            currentItem = coordState.currentItem,
            nextEpisode = coordState.nextEpisode,
            prevEpisode = coordState.prevEpisode,
            autoplayUiState = coordState.autoplayUiState
        )
    }

    /**
     * Sets MKV/native chapters.
     */
    fun setChapters(chapters: List<ParsedChapter>) {
        skipController.setChapters(chapters)
        _sessionState.value = _sessionState.value.copy(
            skipState = SkipController.SkipState(allChapters = chapters)
        )
    }

    /**
     * Merges AniSkip chapters into loaded chapters.
     */
    fun mergeAniSkipChapters(chapters: List<ParsedChapter>) {
        skipController.mergeAniSkipChapters(chapters)
        _sessionState.value = _sessionState.value.copy(
            skipState = _sessionState.value.skipState.copy(allChapters = skipController.getChapters())
        )
    }

    /**
     * Periodic playback tick evaluation.
     */
    fun onPlaybackTick(
        positionMs: Long,
        durationMs: Long,
        autoSkipEnabled: Boolean = false,
        controllerVisible: Boolean = false,
        controlsLocked: Boolean = false
    ): TickResult {
        val autoplayDecision = coordinator.onPlaybackTick(
            positionMs = positionMs,
            durationMs = durationMs,
            controlsLocked = controlsLocked
        )

        val (skipState, skipAction) = skipController.onPlaybackTick(
            positionMs = positionMs,
            autoSkipEnabled = autoSkipEnabled,
            controllerVisible = controllerVisible,
            controlsLocked = controlsLocked
        )

        val coordState = coordinator.state.value
        _sessionState.value = _sessionState.value.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            isWatched = coordState.isWatched,
            watchProgressPercent = coordState.watchProgressPercent,
            autoplayUiState = coordState.autoplayUiState,
            skipState = skipState,
            activeChapter = skipState.activeChapter
        )

        return TickResult(
            autoplayDecision = autoplayDecision,
            skipAction = skipAction
        )
    }

    /**
     * Executes manual skip action. Returns target timestamp in milliseconds.
     */
    fun performSkip(currentPositionMs: Long): Long? {
        return skipController.performSkip(currentPositionMs)
    }

    /**
     * Cancels autoplay countdown.
     */
    fun cancelAutoplay() {
        coordinator.cancelAutoplay()
        _sessionState.value = _sessionState.value.copy(
            autoplayUiState = coordinator.state.value.autoplayUiState
        )
    }

    /**
     * Resets autoplay cancellation flag.
     */
    fun resetAutoplayCancellation() {
        coordinator.resetAutoplayCancellation()
    }

    /**
     * Advances to next episode.
     */
    fun advanceToNext(): PlaylistItem? {
        val next = coordinator.advanceToNext()
        val coordState = coordinator.state.value
        _sessionState.value = _sessionState.value.copy(
            currentItem = coordState.currentItem,
            nextEpisode = coordState.nextEpisode,
            prevEpisode = coordState.prevEpisode,
            autoplayUiState = coordState.autoplayUiState
        )
        return next
    }

    /**
     * Advances to previous episode.
     */
    fun advanceToPrevious(): PlaylistItem? {
        val prev = coordinator.advanceToPrevious()
        val coordState = coordinator.state.value
        _sessionState.value = _sessionState.value.copy(
            currentItem = coordState.currentItem,
            nextEpisode = coordState.nextEpisode,
            prevEpisode = coordState.prevEpisode,
            autoplayUiState = coordState.autoplayUiState
        )
        return prev
    }

    /**
     * Persists playback progress to database via [ProgressRecorder].
     */
    suspend fun recordProgress(
        fileId: String,
        title: String,
        watchedDuration: Long,
        totalDuration: Long,
        thumbnailLink: String? = null,
        audioDelayMs: Long? = null,
        subtitleDelayMs: Long? = null
    ): Boolean {
        return progressRecorder.recordProgress(
            fileId = fileId,
            title = title,
            watchedDuration = watchedDuration,
            totalDuration = totalDuration,
            thumbnailLink = thumbnailLink,
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs
        )
    }

    suspend fun getResumePosition(fileId: String): Long {
        return progressRecorder.getResumePosition(fileId)
    }

    suspend fun getDelays(fileId: String): Pair<Long, Long> {
        return progressRecorder.getDelays(fileId)
    }

    suspend fun updateDelays(fileId: String, audioDelayMs: Long, subtitleDelayMs: Long) {
        progressRecorder.updateDelays(fileId, audioDelayMs, subtitleDelayMs)
    }
}
