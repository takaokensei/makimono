package zechs.drive.stream.ui.player.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.utils.PlaybackProgressPolicy

/**
 * ARCH-01: Playback coordinator contract.
 * Unifies playlist management, next/previous episode calculation,
 * progress evaluation, and autoplay transitions across player engines.
 */
class PlaybackCoordinator(
    private val progressPolicy: PlaybackProgressPolicy = PlaybackProgressPolicy
) {

    data class CoordinatorState(
        val currentItem: PlaylistItem? = null,
        val nextEpisode: PlaylistItem? = null,
        val prevEpisode: PlaylistItem? = null,
        val autoplayUiState: AutoplayUiState = AutoplayUiState.Hidden,
        val isWatched: Boolean = false,
        val watchProgressPercent: Int = 0
    )

    sealed interface AutoplayUiState {
        object Hidden : AutoplayUiState
        data class Countdown(val secondsRemaining: Int, val nextItem: PlaylistItem) : AutoplayUiState
        data class Advance(val nextItem: PlaylistItem) : AutoplayUiState
    }

    private val _state = MutableStateFlow(CoordinatorState())
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()

    private var playlist: List<PlaylistItem> = emptyList()
    private var currentIndex: Int = -1
    private var isAutoplayCanceled: Boolean = false

    /**
     * Updates the active playlist and sets the current episode matching [currentFileId].
     */
    fun setPlaylist(items: List<PlaylistItem>, currentFileId: String) {
        playlist = items
        currentIndex = items.indexOfFirst { it.fileId == currentFileId }

        val current = if (currentIndex in playlist.indices) playlist[currentIndex] else null
        val next = if (currentIndex in playlist.indices && currentIndex + 1 < playlist.size) {
            playlist[currentIndex + 1]
        } else null
        val prev = if (currentIndex in playlist.indices && currentIndex - 1 >= 0) {
            playlist[currentIndex - 1]
        } else null

        isAutoplayCanceled = false
        _state.value = _state.value.copy(
            currentItem = current,
            nextEpisode = next,
            prevEpisode = prev,
            autoplayUiState = AutoplayUiState.Hidden
        )
    }

    /**
     * Evaluates playback progress and autoplay decision on every playback tick.
     */
    fun onPlaybackTick(
        positionMs: Long,
        durationMs: Long,
        controlsLocked: Boolean = false
    ): PlaybackProgressPolicy.AutoplayDecision {
        val next = _state.value.nextEpisode
        val hasNext = next != null
        val isWatched = progressPolicy.isFinished(positionMs, durationMs)
        val progressPercent = progressPolicy.calculateProgress(positionMs, durationMs)

        val decision = progressPolicy.evaluateAutoplay(
            positionMs = positionMs,
            durationMs = durationMs,
            hasNextEpisode = hasNext,
            isCanceled = isAutoplayCanceled,
            controlsLocked = controlsLocked
        )

        val uiState = when (decision) {
            is PlaybackProgressPolicy.AutoplayDecision.ShowCountdown -> {
                if (next != null) AutoplayUiState.Countdown(decision.countdownSeconds, next)
                else AutoplayUiState.Hidden
            }
            is PlaybackProgressPolicy.AutoplayDecision.PlayNext -> {
                if (next != null) AutoplayUiState.Advance(next)
                else AutoplayUiState.Hidden
            }
            else -> AutoplayUiState.Hidden
        }

        _state.value = _state.value.copy(
            isWatched = isWatched,
            watchProgressPercent = progressPercent,
            autoplayUiState = uiState
        )

        return decision
    }

    /**
     * Cancels pending autoplay countdown for the current episode.
     */
    fun cancelAutoplay() {
        isAutoplayCanceled = true
        _state.value = _state.value.copy(autoplayUiState = AutoplayUiState.Hidden)
    }

    /**
     * Resets cancellation state (e.g. when seeking backwards).
     */
    fun resetAutoplayCancellation() {
        isAutoplayCanceled = false
    }

    /**
     * Returns the next episode in playlist without advancing.
     */
    fun getNextEpisode(): PlaylistItem? = _state.value.nextEpisode

    /**
     * Returns the previous episode in playlist without advancing.
     */
    fun getPreviousEpisode(): PlaylistItem? = _state.value.prevEpisode

    /**
     * Advances index to next episode and returns it, or null if none.
     */
    fun advanceToNext(): PlaylistItem? {
        val next = _state.value.nextEpisode ?: return null
        setPlaylist(playlist, next.fileId)
        return next
    }

    /**
     * Advances index to previous episode and returns it, or null if none.
     */
    fun advanceToPrevious(): PlaylistItem? {
        val prev = _state.value.prevEpisode ?: return null
        setPlaylist(playlist, prev.fileId)
        return prev
    }

    /**
     * Checks if current playback should be saved to database.
     */
    fun shouldSaveProgress(positionMs: Long, durationMs: Long): Boolean {
        return progressPolicy.shouldSave(positionMs, durationMs)
    }
}
