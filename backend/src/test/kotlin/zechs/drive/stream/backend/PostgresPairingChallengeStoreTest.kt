package zechs.drive.stream.backend

import com.zaxxer.hikari.HikariDataSource
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PostgresPairingChallengeStoreTest {

    @Test
    fun `challenge remains pollable after closing and recreating the database pool`() {
        var issuedChallenge: Pair<String, String>? = null
        val firstDataSource = newDataSource()
        try {
            val created = protocol(firstDataSource).create(validRequest())
            issuedChallenge = created.challengeId to created.pollSecret
            val storedHash = firstDataSource.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT poll_secret_hash FROM pairing_challenge WHERE challenge_id = ?"
                ).use { statement ->
                    statement.setString(1, created.challengeId)
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        result.getBytes(1)
                    }
                }
            }
            assertEquals(32, storedHash.size)
            assertContentEquals(
                MessageDigest.getInstance("SHA-256").digest(created.pollSecret.toByteArray(Charsets.US_ASCII)),
                storedHash
            )
        } finally {
            firstDataSource.close()
        }

        val restartedDataSource = newDataSource()
        try {
            val (challengeId, pollSecret) = requireNotNull(issuedChallenge)
            assertEquals(
                PairingChallengeStatus.State.PENDING,
                protocol(restartedDataSource).status(challengeId, pollSecret).state
            )
            protocol(restartedDataSource).cancel(challengeId, pollSecret)
            protocol(restartedDataSource).cancel(challengeId, pollSecret)
            assertEquals(PairingProtocolException.Kind.EXPIRED, assertFailsWith<PairingProtocolException> {
                protocol(restartedDataSource).status(challengeId, pollSecret)
            }.kind)
        } finally {
            restartedDataSource.close()
        }
    }

    @Test
    fun `wrong poll secret cannot read a persisted challenge`() {
        val created = protocol().create(validRequest())

        val error = assertFailsWith<PairingProtocolException> {
            protocol().status(created.challengeId, "not-the-poll-secret")
        }

        assertEquals(PairingProtocolException.Kind.INVALID_SECRET, error.kind)
    }

    @Test
    fun `manual code lookup reads only active challenge metadata from postgres`() {
        val protocol = protocol()
        val created = protocol.create(validRequest())

        val preview = protocol.lookupByUserCode("${created.userCode.take(4)}-${created.userCode.drop(4).lowercase()}")
        val qrPreview = protocol.previewByChallengeId(created.challengeId)

        assertEquals(created.challengeId, preview.challengeId)
        assertEquals("TV sala", preview.deviceName)
        assertEquals(created.expiresAt, preview.expiresAt)
        assertEquals(preview, qrPreview)
        protocol.cancel(created.challengeId, created.pollSecret)
        assertEquals(null, protocol.previewByChallengeId(created.challengeId))
        assertEquals(PairingProtocolException.Kind.NOT_FOUND, assertFailsWith<PairingProtocolException> {
            protocol.lookupByUserCode(created.userCode)
        }.kind)
    }

    @Test
    fun `expiry transition is persisted and cannot be revived by a new repository`() {
        val clock = MutableClock(Instant.parse("2026-10-05T12:00:00Z"))
        val created = protocol(clock = clock).create(validRequest())
        clock.advance(Duration.ofMinutes(5))

        assertEquals(PairingProtocolException.Kind.EXPIRED, assertFailsWith<PairingProtocolException> {
            protocol(clock = clock).status(created.challengeId, created.pollSecret)
        }.kind)
        assertEquals(PairingProtocolException.Kind.EXPIRED, assertFailsWith<PairingProtocolException> {
            protocol(clock = clock).cancel(created.challengeId, created.pollSecret)
        }.kind)
    }

    @Test
    fun `concurrent cancellation is serialized and idempotent across repository instances`() {
        val created = protocol().create(validRequest())
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (1..32).map {
                pool.submit {
                    protocol().cancel(created.challengeId, created.pollSecret)
                }
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(PairingProtocolException.Kind.EXPIRED, assertFailsWith<PairingProtocolException> {
            protocol().status(created.challengeId, created.pollSecret)
        }.kind)
    }

    private fun protocol(
        dbPool: HikariDataSource = requireNotNull(pool),
        clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    ) = PairingProtocol(
        verificationBaseUrl = "https://pairing.example.test",
        clock = clock,
        ttl = Duration.ofMinutes(5),
        store = PostgresPairingChallengeStore(dbPool)
    )

    private fun newDataSource() = createPairingDataSource(
        postgres.jdbcUrl,
        postgres.username,
        postgres.password,
        poolSize = 2
    )

    private fun validRequest() = PairingRequest(
        deviceName = "TV sala",
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

    companion object {
        private val NOW = Instant.parse("2026-10-05T12:00:00Z")
        private val postgres = PostgreSQLContainer("postgres:16-alpine")
        private var pool: HikariDataSource? = null

        @JvmStatic
        @BeforeClass
        fun startPostgres() {
            try {
                postgres.start()
                pool = createPairingDataSource(
                    postgres.jdbcUrl,
                    postgres.username,
                    postgres.password,
                    poolSize = 8
                )
                migratePairingDatabase(requireNotNull(pool))
            } catch (error: Throwable) {
                pool?.close()
                pool = null
                if (postgres.isRunning) postgres.stop()
                assumeTrue("PostgreSQL integration tests require Docker: ${error.javaClass.simpleName}", false)
            }
        }

        @JvmStatic
        @AfterClass
        fun stopPostgres() {
            pool?.close()
            if (postgres.isRunning) postgres.stop()
        }
    }
}
