package zechs.drive.stream.backend

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingRateLimiterTest {
    @Test
    fun `rate limit is scoped by source and operation`() {
        val limiter = PairingRateLimiter(
            clock = MutableClock(Instant.parse("2026-10-06T12:00:10Z")),
            limits = mapOf(
                PairingRateLimiter.Operation.CREATE to 1,
                PairingRateLimiter.Operation.STATUS to 1,
                PairingRateLimiter.Operation.CANCEL to 1
            )
        )

        assertTrue(limiter.acquire("127.0.0.1", PairingRateLimiter.Operation.CREATE).allowed)
        val denied = limiter.acquire("127.0.0.1", PairingRateLimiter.Operation.CREATE)
        assertFalse(denied.allowed)
        assertEquals(50, denied.retryAfterSeconds)
        assertTrue(limiter.acquire("127.0.0.2", PairingRateLimiter.Operation.CREATE).allowed)
        assertTrue(limiter.acquire("127.0.0.1", PairingRateLimiter.Operation.STATUS).allowed)
    }

    @Test
    fun `new window permits requests and bucket cap fails closed`() {
        val clock = MutableClock(Instant.parse("2026-10-06T12:00:59Z"))
        val limiter = PairingRateLimiter(
            clock = clock,
            window = Duration.ofSeconds(60),
            limits = mapOf(
                PairingRateLimiter.Operation.CREATE to 1,
                PairingRateLimiter.Operation.STATUS to 1,
                PairingRateLimiter.Operation.CANCEL to 1
            ),
            maxBuckets = 1
        )

        assertTrue(limiter.acquire("127.0.0.1", PairingRateLimiter.Operation.CREATE).allowed)
        assertFalse(limiter.acquire("127.0.0.2", PairingRateLimiter.Operation.CREATE).allowed)
        clock.advance(Duration.ofSeconds(1))
        assertTrue(limiter.acquire("127.0.0.2", PairingRateLimiter.Operation.CREATE).allowed)
    }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) { now = now.plus(duration) }
    }
}
