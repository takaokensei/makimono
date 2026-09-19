package zechs.drive.stream.utils

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * FEAT-06: Unified playback progress and autoplay policy.
 *
 * Centralizes progress calculation, completion thresholds, saving decisions,
 * and next-episode autoplay advance logic across both ExoPlayer and MPV engines.
 */
object PlaybackProgressPolicy {

    /** Minimum duration to consider progress meaningful (10 seconds). */
    const val MIN_MEANINGFUL_DURATION_MS = 10_000L

    /** Minimum progress percentage to persist a "continue watching" state (10%). */
    const val MIN_SAVE_PROGRESS_PERCENT = 10

    /** Completion threshold percentage (95%). */
    const val FINISHED_PERCENT = 95

    /** Remaining time threshold near the end of a video (<= 15 seconds for long videos). */
    const val FINISHED_REMAINING_WINDOW_MS = 15_000L

    /** Window before episode end where the next-episode card should be displayed (10.5 seconds). */
    const val AUTOPLAY_CARD_TRIGGER_MS = 10_500L

    /** Threshold where automatic transition to the next episode occurs (<= 1 second remaining). */
    const val AUTOPLAY_ADVANCE_MS = 1_000L

    /** Minimum video duration required for autoplay prompt (60 seconds). */
    const val MIN_AUTOPLAY_DURATION_MS = 60_000L

    /** Default fallback duration when real video metadata is unavailable (24 min in ms). */
    const val DEFAULT_FALLBACK_DURATION_MS = 24 * 60 * 1000L

    sealed class AutoplayDecision {
        object None : AutoplayDecision()
        object DismissCard : AutoplayDecision()
        data class ShowCountdown(val remainingMs: Long, val countdownSeconds: Int) : AutoplayDecision()
        object PlayNext : AutoplayDecision()
    }

    /**
     * Calculates the watch progress as an integer percentage in 0..100.
     * Safely returns 0 when durations are non-positive or corrupted.
     */
    fun calculateProgress(watchedDurationMs: Long, totalDurationMs: Long): Int {
        if (totalDurationMs <= 0L || watchedDurationMs <= 0L) return 0
        val percent = ((watchedDurationMs.toDouble() / totalDurationMs.toDouble()) * 100.0).toInt()
        return min(100, max(0, percent))
    }

    /**
     * Determines whether the video has been completed.
     * An episode is finished if:
     * 1. Progress is at least [FINISHED_PERCENT] (95%), OR
     * 2. The video is longer than 5 minutes and remaining time is <= [FINISHED_REMAINING_WINDOW_MS].
     */
    fun isFinished(watchedDurationMs: Long, totalDurationMs: Long): Boolean {
        if (totalDurationMs <= 0L) return false
        val progress = calculateProgress(watchedDurationMs, totalDurationMs)
        if (progress >= FINISHED_PERCENT) return true

        val remainingMs = totalDurationMs - watchedDurationMs
        return totalDurationMs >= 300_000L && remainingMs in 0..FINISHED_REMAINING_WINDOW_MS
    }

    /**
     * Determines if the current playback position should be saved to the database.
     * Prevents saving accidental quick clicks or zero-duration media.
     */
    fun shouldSave(watchedDurationMs: Long, totalDurationMs: Long): Boolean {
        if (watchedDurationMs <= 0L || totalDurationMs <= 0L) return false
        return calculateProgress(watchedDurationMs, totalDurationMs) >= MIN_SAVE_PROGRESS_PERCENT
    }

    /**
     * Evaluates next-episode autoplay behavior for a given playback timestamp.
     * Shared identically between ExoPlayer and MPV.
     */
    fun evaluateAutoplay(
        positionMs: Long,
        durationMs: Long,
        hasNextEpisode: Boolean,
        isCanceled: Boolean,
        controlsLocked: Boolean
    ): AutoplayDecision {
        if (controlsLocked || durationMs <= MIN_AUTOPLAY_DURATION_MS || !hasNextEpisode) {
            return AutoplayDecision.DismissCard
        }

        val remainingMs = durationMs - positionMs

        // Instant advance if episode reaches or exceeds full duration
        if (remainingMs <= AUTOPLAY_ADVANCE_MS || positionMs >= durationMs) {
            return if (!isCanceled) AutoplayDecision.PlayNext else AutoplayDecision.None
        }

        if (isCanceled) return AutoplayDecision.None

        // Within countdown window (e.g. 1.0s to 10.5s before end)
        if (remainingMs in (AUTOPLAY_ADVANCE_MS + 1)..AUTOPLAY_CARD_TRIGGER_MS) {
            val countdownSec = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
            return AutoplayDecision.ShowCountdown(remainingMs, countdownSec)
        }

        return AutoplayDecision.DismissCard
    }

    /**
     * Resolves the real or fallback total duration for marking an episode watched.
     * If a valid [knownTotalDurationMs] is available (> 0), it is prioritized over [DEFAULT_FALLBACK_DURATION_MS].
     */
    fun resolveWatchedDuration(knownTotalDurationMs: Long?): Long {
        return if (knownTotalDurationMs != null && knownTotalDurationMs > 0L) {
            knownTotalDurationMs
        } else {
            DEFAULT_FALLBACK_DURATION_MS
        }
    }
}
