package zechs.drive.stream.backend

import java.time.Clock
import java.time.Duration

/** Small per-process guard for the loopback pairing prototype; production needs a shared limiter. */
internal class PairingRateLimiter(
    private val clock: Clock = Clock.systemUTC(),
    private val window: Duration = Duration.ofMinutes(1),
    limits: Map<Operation, Int> = emptyMap(),
    private val maxBuckets: Int = 4_096
) {
    enum class Operation { CREATE, STATUS, CANCEL, LOOKUP, PREVIEW }

    data class Decision(val allowed: Boolean, val retryAfterSeconds: Long = 0)

    private data class Bucket(var windowStartMillis: Long, var requestCount: Int)

    private val limits = DEFAULT_LIMITS + limits
    private val buckets = HashMap<Pair<String, Operation>, Bucket>()
    private val windowMillis = window.toMillis()

    init {
        require(windowMillis > 0)
        require(maxBuckets > 0)
        require(Operation.entries.all { (this.limits[it] ?: 0) > 0 })
    }

    @Synchronized
    fun acquire(source: String, operation: Operation): Decision {
        val now = clock.millis()
        val windowStart = Math.floorDiv(now, windowMillis) * windowMillis
        val key = source.take(MAX_SOURCE_LENGTH) to operation
        var bucket = buckets[key]

        if (bucket == null) {
            prune(windowStart)
            if (buckets.size >= maxBuckets) {
                return Decision(allowed = false, retryAfterSeconds = secondsUntilNextWindow(now, windowStart))
            }
            bucket = Bucket(windowStart, 0)
            buckets[key] = bucket
        } else if (bucket.windowStartMillis != windowStart) {
            bucket.windowStartMillis = windowStart
            bucket.requestCount = 0
        }

        if (bucket.requestCount >= this.limits.getValue(operation)) {
            return Decision(allowed = false, retryAfterSeconds = secondsUntilNextWindow(now, windowStart))
        }
        bucket.requestCount += 1
        return Decision(allowed = true)
    }

    private fun prune(currentWindowStart: Long) {
        buckets.entries.removeAll { it.value.windowStartMillis < currentWindowStart }
    }

    private fun secondsUntilNextWindow(now: Long, windowStart: Long) =
        ((windowStart + windowMillis - now).coerceAtLeast(1) + 999) / 1_000

    private companion object {
        const val MAX_SOURCE_LENGTH = 64
        val DEFAULT_LIMITS = mapOf(
            Operation.CREATE to 5,
            Operation.STATUS to 20,
            Operation.CANCEL to 10,
            Operation.LOOKUP to 5,
            Operation.PREVIEW to 20
        )
    }
}
