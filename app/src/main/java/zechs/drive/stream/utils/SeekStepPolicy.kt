package zechs.drive.stream.utils

/**
 * Implements Kodi's additive/progressive seek steps algorithm.
 *
 * Consecutive presses within [TIMEOUT_MS] step through progressively larger jump intervals:
 * 1st press: 10 seconds
 * 2nd press: 30 seconds (cumulative 40s)
 * 3rd press: 60 seconds (cumulative 1m 40s)
 * 4th press: 180 seconds (3 min)
 * 5th+ press: 300 seconds (5 min)
 *
 * If the direction reverses, the accumulator and step index reset immediately.
 */
class SeekStepPolicy {

    companion object {
        const val TIMEOUT_MS = 1200L

        // Progressive jump values in seconds (matching Kodi default steps)
        val STEP_INTERVALS = listOf(10, 30, 60, 180, 300)
    }

    private var lastDirection: Int = 0 // 1 for forward, -1 for backward
    private var stepIndex: Int = 0
    private var lastInputTimeMs: Long = 0L

    var cumulativeSeconds: Int = 0
        private set

    /**
     * Registers a seek press in a given direction (+1 for forward, -1 for backward).
     * Returns the incremental delta seconds to seek for this specific press.
     */
    fun onSeekPress(direction: Int, nowMs: Long = System.currentTimeMillis()): Int {
        val isDirectionChanged = lastDirection != 0 && lastDirection != direction
        val isTimedOut = (nowMs - lastInputTimeMs) > TIMEOUT_MS

        if (isDirectionChanged || isTimedOut) {
            stepIndex = 0
            cumulativeSeconds = 0
        }

        val stepValue = STEP_INTERVALS.getOrElse(stepIndex) { STEP_INTERVALS.last() }
        val delta = stepValue * direction

        cumulativeSeconds += delta
        lastDirection = direction
        lastInputTimeMs = nowMs

        if (stepIndex < STEP_INTERVALS.size - 1) {
            stepIndex++
        }

        return delta
    }

    /**
     * Resets accumulator state when timeout expires or player state changes.
     */
    fun reset() {
        stepIndex = 0
        cumulativeSeconds = 0
        lastDirection = 0
        lastInputTimeMs = 0L
    }

    /**
     * Formats the cumulative seek time cleanly for OSD display (e.g., "+10s", "+1m", "-3m 30s").
     */
    fun formatCumulative(): String {
        val sign = if (cumulativeSeconds > 0) "+" else "-"
        val absSec = kotlin.math.abs(cumulativeSeconds)
        val mins = absSec / 60
        val secs = absSec % 60

        return when {
            mins == 0 -> "$sign${secs}s"
            secs == 0 -> "$sign${mins}m"
            else -> "$sign${mins}m ${secs}s"
        }
    }
}
