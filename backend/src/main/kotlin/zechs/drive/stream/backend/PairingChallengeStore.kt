package zechs.drive.stream.backend

import java.security.MessageDigest
import java.time.Instant

internal data class PairingChallengeRecord(
    val challengeId: String,
    val userCode: String,
    val pollSecretHash: ByteArray,
    val deviceName: String,
    val clientVersion: String,
    val protocolVersion: Int,
    val capabilities: List<String>,
    val expiresAt: Instant,
    val state: PairingChallengeStatus.State = PairingChallengeStatus.State.PENDING
)

/** Deliberately minimal preview shown after a user enters a short pairing code. */
internal data class PairingChallengePreview(
    val challengeId: String,
    val deviceName: String,
    val expiresAt: Instant
)

/** Atomic persistence boundary for a challenge. Implementations must serialize state transitions. */
internal interface PairingChallengeStore {
    /** Returns false when the locator or pending user code collides. */
    fun insertPending(record: PairingChallengeRecord, now: Instant): Boolean

    /** Returns only an active challenge's display-safe fields; never returns pairing credentials. */
    fun lookupByUserCode(userCode: String, now: Instant): PairingChallengePreview?

    /** QR locator lookup has the same minimal preview and pending-only visibility as code lookup. */
    fun lookupByChallengeId(challengeId: String, now: Instant): PairingChallengePreview?

    fun status(
        challengeId: String,
        suppliedPollSecretHash: ByteArray,
        now: Instant,
        pollIntervalSeconds: Long
    ): PairingChallengeStatus

    fun cancel(challengeId: String, suppliedPollSecretHash: ByteArray, now: Instant)
}

internal class PairingStoreCapacityException : RuntimeException("Pairing challenge storage is full")

internal abstract class AuthenticatedPairingChallengeStore : PairingChallengeStore {
    protected fun authenticate(record: PairingChallengeRecord?, suppliedHash: ByteArray): PairingChallengeRecord {
        val challenge = record ?: throw PairingProtocolException(
            PairingProtocolException.Kind.NOT_FOUND,
            "Unknown pairing challenge"
        )
        if (!MessageDigest.isEqual(challenge.pollSecretHash, suppliedHash)) {
            throw PairingProtocolException(
                PairingProtocolException.Kind.INVALID_SECRET,
                "Invalid pairing credential"
            )
        }
        return challenge
    }

    protected fun expireIfNeeded(record: PairingChallengeRecord, now: Instant): PairingChallengeRecord =
        if (record.state == PairingChallengeStatus.State.PENDING && !now.isBefore(record.expiresAt)) {
            record.copy(state = PairingChallengeStatus.State.EXPIRED)
        } else {
            record
        }

    protected fun activeStatus(record: PairingChallengeRecord, pollIntervalSeconds: Long): PairingChallengeStatus {
        if (record.state == PairingChallengeStatus.State.EXPIRED ||
            record.state == PairingChallengeStatus.State.CANCELLED ||
            record.state == PairingChallengeStatus.State.DENIED
        ) {
            throw PairingProtocolException(
                PairingProtocolException.Kind.EXPIRED,
                "Pairing challenge is no longer active"
            )
        }
        return PairingChallengeStatus(record.state, record.expiresAt, pollIntervalSeconds)
    }

    protected fun cancelState(record: PairingChallengeRecord): PairingChallengeRecord = when (record.state) {
        PairingChallengeStatus.State.PENDING -> record.copy(state = PairingChallengeStatus.State.CANCELLED)
        PairingChallengeStatus.State.CANCELLED -> record
        PairingChallengeStatus.State.EXPIRED,
        PairingChallengeStatus.State.DENIED,
        PairingChallengeStatus.State.APPROVED,
        PairingChallengeStatus.State.CLAIMED -> throw PairingProtocolException(
            PairingProtocolException.Kind.EXPIRED,
            "Pairing challenge cannot be cancelled in its current state"
        )
    }
}

/** Deterministic, process-local implementation used by unit tests and the loopback-only dev host. */
internal class InMemoryPairingChallengeStore : AuthenticatedPairingChallengeStore() {
    private val lock = Any()
    private val records = linkedMapOf<String, PairingChallengeRecord>()

    override fun insertPending(record: PairingChallengeRecord, now: Instant): Boolean = synchronized(lock) {
        records.replaceAll { _, current -> expireIfNeeded(current, now) }
        if (records.size >= MAX_ACTIVE_AND_RECENT_CHALLENGES) {
            records.entries.removeIf { it.value.state != PairingChallengeStatus.State.PENDING }
        }
        if (records.size >= MAX_ACTIVE_AND_RECENT_CHALLENGES) throw PairingStoreCapacityException()
        if (records.containsKey(record.challengeId) || records.values.any {
                it.userCode == record.userCode && it.state == PairingChallengeStatus.State.PENDING
            }
        ) return@synchronized false
        records[record.challengeId] = record
        true
    }

    override fun lookupByUserCode(userCode: String, now: Instant): PairingChallengePreview? = synchronized(lock) {
        records.replaceAll { _, current -> expireIfNeeded(current, now) }
        records.values.firstOrNull {
            it.userCode == userCode && it.state == PairingChallengeStatus.State.PENDING
        }?.let { PairingChallengePreview(it.challengeId, it.deviceName, it.expiresAt) }
    }

    override fun lookupByChallengeId(challengeId: String, now: Instant): PairingChallengePreview? = synchronized(lock) {
        val current = records[challengeId] ?: return@synchronized null
        val active = expireIfNeeded(current, now)
        records[challengeId] = active
        active.takeIf { it.state == PairingChallengeStatus.State.PENDING }
            ?.let { PairingChallengePreview(it.challengeId, it.deviceName, it.expiresAt) }
    }

    override fun status(
        challengeId: String,
        suppliedPollSecretHash: ByteArray,
        now: Instant,
        pollIntervalSeconds: Long
    ): PairingChallengeStatus = synchronized(lock) {
        val authenticated = authenticate(records[challengeId], suppliedPollSecretHash)
        val updated = expireIfNeeded(authenticated, now)
        records[challengeId] = updated
        activeStatus(updated, pollIntervalSeconds)
    }

    override fun cancel(challengeId: String, suppliedPollSecretHash: ByteArray, now: Instant) = synchronized(lock) {
        val authenticated = authenticate(records[challengeId], suppliedPollSecretHash)
        val expired = expireIfNeeded(authenticated, now)
        records[challengeId] = expired
        val updated = cancelState(expired)
        records[challengeId] = updated
    }

    private companion object {
        const val MAX_ACTIVE_AND_RECENT_CHALLENGES = 10_000
    }
}
