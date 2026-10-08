package zechs.drive.stream.ui.home

/** Keeps unknown durations and completed items from being presented as remaining time. */
internal object ContinueWatchingProgressPolicy {
    sealed interface Remaining {
        data class Minutes(val count: Int) : Remaining
        data object LessThanMinute : Remaining
        data object Unknown : Remaining
        data object Complete : Remaining
    }

    fun remaining(watchedDurationMs: Long, totalDurationMs: Long): Remaining {
        if (totalDurationMs <= 0L || watchedDurationMs < 0L) return Remaining.Unknown
        if (watchedDurationMs >= totalDurationMs) return Remaining.Complete

        val remainingMs = totalDurationMs - watchedDurationMs
        if (remainingMs < 60_000L) return Remaining.LessThanMinute

        val roundedMinutes = 1L + (remainingMs - 1L) / 60_000L
        return Remaining.Minutes(roundedMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
