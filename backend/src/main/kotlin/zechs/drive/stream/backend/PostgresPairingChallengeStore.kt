package zechs.drive.stream.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** PostgreSQL implementation with row-level locking for authenticated state transitions. */
internal class PostgresPairingChallengeStore(
    private val dataSource: DataSource
) : AuthenticatedPairingChallengeStore() {

    override fun insertPending(record: PairingChallengeRecord, now: Instant): Boolean = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                """UPDATE pairing_challenge SET state = 'EXPIRED'
                   WHERE state = 'PENDING' AND user_code = ? AND expires_at <= ?""".trimIndent()
            ).use { statement ->
                statement.setString(1, record.userCode)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.executeUpdate()
            }
            val inserted = connection.prepareStatement(
                """INSERT INTO pairing_challenge (
                       challenge_id, user_code, poll_secret_hash, device_name, client_version,
                       protocol_version, capabilities, expires_at, state
                   ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                   ON CONFLICT DO NOTHING""".trimIndent()
            ).use { statement ->
                statement.setString(1, record.challengeId)
                statement.setString(2, record.userCode)
                statement.setBytes(3, record.pollSecretHash)
                statement.setString(4, record.deviceName)
                statement.setString(5, record.clientVersion)
                statement.setInt(6, record.protocolVersion)
                statement.setArray(7, connection.createArrayOf("text", record.capabilities.toTypedArray()))
                statement.setTimestamp(8, Timestamp.from(record.expiresAt))
                statement.executeUpdate() == 1
            }
            connection.commit()
            inserted
        } catch (error: SQLException) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    override fun lookupByUserCode(userCode: String, now: Instant): PairingChallengePreview? =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """UPDATE pairing_challenge SET state = 'EXPIRED'
                       WHERE user_code = ? AND state = 'PENDING' AND expires_at <= ?""".trimIndent()
                ).use { statement ->
                    statement.setString(1, userCode)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.executeUpdate()
                }
                val preview = connection.prepareStatement(
                    """SELECT challenge_id, device_name, expires_at
                       FROM pairing_challenge
                       WHERE user_code = ? AND state = 'PENDING' AND expires_at > ?""".trimIndent()
                ).use { statement ->
                    statement.setString(1, userCode)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.executeQuery().use { result ->
                        if (!result.next()) null else PairingChallengePreview(
                            challengeId = result.getString("challenge_id"),
                            deviceName = result.getString("device_name"),
                            expiresAt = result.getTimestamp("expires_at").toInstant()
                        )
                    }
                }
                connection.commit()
                preview
            } catch (error: SQLException) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    override fun lookupByChallengeId(challengeId: String, now: Instant): PairingChallengePreview? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """UPDATE pairing_challenge SET state = 'EXPIRED'
                   WHERE challenge_id = ? AND state = 'PENDING' AND expires_at <= ?""".trimIndent()
            ).use { statement ->
                statement.setString(1, challengeId)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """SELECT challenge_id, device_name, expires_at
                   FROM pairing_challenge
                   WHERE challenge_id = ? AND state = 'PENDING' AND expires_at > ?""".trimIndent()
            ).use { statement ->
                statement.setString(1, challengeId)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.executeQuery().use { result ->
                    if (!result.next()) null else PairingChallengePreview(
                        challengeId = result.getString("challenge_id"),
                        deviceName = result.getString("device_name"),
                        expiresAt = result.getTimestamp("expires_at").toInstant()
                    )
                }
            }
        }

    override fun status(
        challengeId: String,
        suppliedPollSecretHash: ByteArray,
        now: Instant,
        pollIntervalSeconds: Long
    ): PairingChallengeStatus {
        val outcome = inTransaction { connection ->
            runCatching {
                var record = authenticate(selectForUpdate(connection, challengeId), suppliedPollSecretHash)
                val updated = expireIfNeeded(record, now)
                if (updated !== record) {
                    updateState(connection, challengeId, updated.state)
                    record = updated
                }
                activeStatus(record, pollIntervalSeconds)
            }
        }
        return outcome.getOrThrow()
    }

    override fun cancel(challengeId: String, suppliedPollSecretHash: ByteArray, now: Instant) {
        val outcome = inTransaction { connection ->
            runCatching {
                val record = authenticate(selectForUpdate(connection, challengeId), suppliedPollSecretHash)
                val expired = expireIfNeeded(record, now)
                if (expired !== record) updateState(connection, challengeId, expired.state)
                val updated = cancelState(expired)
                if (updated !== expired) updateState(connection, challengeId, updated.state)
            }
        }
        outcome.getOrThrow()
    }

    private fun selectForUpdate(connection: Connection, challengeId: String): PairingChallengeRecord? =
        connection.prepareStatement(
            """SELECT challenge_id, user_code, poll_secret_hash, device_name, client_version,
                      protocol_version, capabilities, expires_at, state
               FROM pairing_challenge WHERE challenge_id = ? FOR UPDATE""".trimIndent()
        ).use { statement ->
            statement.setString(1, challengeId)
            statement.executeQuery().use { result ->
                if (!result.next()) null else {
                    val capabilities = (result.getArray("capabilities")?.array as? Array<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                    PairingChallengeRecord(
                        challengeId = result.getString("challenge_id"),
                        userCode = result.getString("user_code"),
                        pollSecretHash = result.getBytes("poll_secret_hash"),
                        deviceName = result.getString("device_name"),
                        clientVersion = result.getString("client_version"),
                        protocolVersion = result.getInt("protocol_version"),
                        capabilities = capabilities,
                        expiresAt = result.getTimestamp("expires_at").toInstant(),
                        state = PairingChallengeStatus.State.valueOf(result.getString("state"))
                    )
                }
            }
        }

    private fun updateState(connection: Connection, challengeId: String, state: PairingChallengeStatus.State) {
        connection.prepareStatement(
            "UPDATE pairing_challenge SET state = ? WHERE challenge_id = ?"
        ).use { statement ->
            statement.setString(1, state.name)
            statement.setString(2, challengeId)
            check(statement.executeUpdate() == 1) { "Locked pairing challenge disappeared" }
        }
    }

    private fun <T> inTransaction(block: (Connection) -> Result<T>): Result<T> = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val outcome = block(connection)
            connection.commit()
            outcome
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }
}

internal fun migratePairingDatabase(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

internal fun createPairingDataSource(
    jdbcUrl: String,
    username: String,
    password: String,
    poolSize: Int = 8
): HikariDataSource {
    require(jdbcUrl.startsWith("jdbc:postgresql://")) { "Pairing database must be PostgreSQL over JDBC" }
    require(username.isNotBlank() && password.isNotBlank()) { "Pairing database credentials are required" }
    require(poolSize in 2..32)
    val config = HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        this.username = username
        this.password = password
        maximumPoolSize = poolSize
        minimumIdle = 0
        connectionTimeout = 5_000
        validationTimeout = 2_000
        poolName = "makimono-pairing"
        isAutoCommit = true
    }
    return HikariDataSource(config)
}
