package zechs.drive.stream.backend

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class PairingProtocolTest {

    @Test
    fun `challenge contains high entropy locator and one time poll secret`() {
        val protocol = protocol()
        val created = protocol.create(validRequest())

        assertEquals(22, created.challengeId.length) // 128 random bits, base64url without padding
        assertEquals(8, created.userCode.length)
        assertTrue(created.userCode.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
        assertEquals(43, created.pollSecret.length) // 256 random bits, base64url without padding
        assertTrue(created.verificationUrl.endsWith("/pair/${created.challengeId}"))
        assertTrue(!created.verificationUrl.contains(created.pollSecret))
        assertEquals("pending", protocol.status(created.challengeId, created.pollSecret).state.name.lowercase())
    }

    @Test
    fun `invalid device metadata is rejected before allocating a challenge`() {
        val protocol = protocol()
        val error = assertFailsWith<PairingProtocolException> {
            protocol.create(validRequest().copy(deviceName = " "))
        }

        assertEquals(PairingProtocolException.Kind.INVALID_INPUT, error.kind)
    }

    @Test
    fun `cancellation is irreversible and safe to retry`() {
        val protocol = protocol()
        val created = protocol.create(validRequest())

        protocol.cancel(created.challengeId, created.pollSecret)
        protocol.cancel(created.challengeId, created.pollSecret)
        val error = assertFailsWith<PairingProtocolException> {
            protocol.status(created.challengeId, created.pollSecret)
        }

        assertEquals(PairingProtocolException.Kind.EXPIRED, error.kind)
    }

    @Test
    fun `expired challenge cannot be polled or revived`() {
        val clock = MutableClock(Instant.parse("2026-10-05T12:00:00Z"))
        val protocol = protocol(clock)
        val created = protocol.create(validRequest())
        clock.advance(Duration.ofMinutes(5))

        val error = assertFailsWith<PairingProtocolException> {
            protocol.status(created.challengeId, created.pollSecret)
        }
        assertEquals(PairingProtocolException.Kind.EXPIRED, error.kind)
        assertFailsWith<PairingProtocolException> { protocol.cancel(created.challengeId, created.pollSecret) }
    }

    @Test
    fun `locator is not sufficient to read status or cancel`() {
        val protocol = protocol()
        val created = protocol.create(validRequest())

        val error = assertFailsWith<PairingProtocolException> {
            protocol.status(created.challengeId, created.challengeId)
        }
        assertEquals(PairingProtocolException.Kind.INVALID_SECRET, error.kind)
        assertFailsWith<PairingProtocolException> { protocol.cancel(created.challengeId, "") }
        assertNotEquals(created.challengeId, created.pollSecret)
    }

    @Test
    fun `parallel challenge creation reserves distinct codes and locators`() = runBlocking {
        val protocol = protocol()
        val results = (1..100).map {
            async { protocol.create(validRequest().copy(deviceName = "TV $it")) }
        }.awaitAll()

        assertEquals(100, results.map { it.challengeId }.toSet().size)
        assertEquals(100, results.map { it.userCode }.toSet().size)
    }

    @Test
    fun `non https verification base url is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PairingProtocol(verificationBaseUrl = "http://localhost/pair")
        }
    }

    private fun protocol(clock: Clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)) =
        PairingProtocol("https://pairing.example.test", clock = clock)

    private fun validRequest() = PairingRequest(
        deviceName = "Sala TV",
        clientVersion = "1.6.4.1",
        protocolVersion = 1,
        capabilities = listOf("drive-read", "media3")
    )

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) { now = now.plus(duration) }
    }
}
