package zechs.drive.stream.backend

import java.security.SecureRandom
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

internal data class PairingRequest(
    val deviceName: String,
    val clientVersion: String,
    val protocolVersion: Int,
    val capabilities: List<String>
)

internal data class PairingChallengeCreated(
    val challengeId: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresAt: Instant,
    val pollIntervalSeconds: Long,
    val pollSecret: String
)

internal data class PairingChallengeStatus(
    val state: State,
    val expiresAt: Instant,
    val pollIntervalSeconds: Long
) {
    enum class State { PENDING, APPROVED, DENIED, CANCELLED, EXPIRED, CLAIMED }
}

internal class PairingProtocolException(
    val kind: Kind,
    message: String
) : RuntimeException(message) {
    enum class Kind { INVALID_INPUT, NOT_FOUND, INVALID_SECRET, EXPIRED, UNAVAILABLE }
}

/**
 * In-memory development implementation of the pairing challenge contract.
 * It is deliberately single-process and ephemeral: production must use an
 * atomic persistent repository before this API is deployed.
 */
internal class PairingProtocol(
    private val verificationBaseUrl: String,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val ttl: Duration = Duration.ofMinutes(5),
    private val pollIntervalSeconds: Long = 3,
    private val store: PairingChallengeStore = InMemoryPairingChallengeStore()
) {
    private val codeAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    init {
        require(verificationBaseUrl.startsWith("https://")) {
            "Pairing verification URL must use HTTPS"
        }
        require(ttl > Duration.ZERO)
        require(pollIntervalSeconds > 0)
    }

    fun create(request: PairingRequest): PairingChallengeCreated {
        validate(request)
        repeat(MAX_CODE_GENERATION_ATTEMPTS) {
            val id = uniqueToken(16)
            val code = randomUserCode()
            val secret = uniqueToken(32)
            val expiresAt = clock.instant().plus(ttl)
            val record = PairingChallengeRecord(
                challengeId = id,
                userCode = code,
                pollSecretHash = sha256(secret),
                deviceName = request.deviceName.trim(),
                clientVersion = request.clientVersion.trim(),
                protocolVersion = request.protocolVersion,
                capabilities = request.capabilities,
                expiresAt = expiresAt,
            )
            val inserted = try {
                store.insertPending(record, clock.instant())
            } catch (_: PairingStoreCapacityException) {
                throw PairingProtocolException(
                    PairingProtocolException.Kind.UNAVAILABLE,
                    "Pairing challenge storage is temporarily unavailable"
                )
            }
            if (!inserted) return@repeat
            val base = verificationBaseUrl.trimEnd('/')
            return PairingChallengeCreated(
                challengeId = id,
                userCode = code,
                verificationUrl = "$base/pair/$id",
                expiresAt = expiresAt,
                pollIntervalSeconds = pollIntervalSeconds,
                pollSecret = secret
            )
        }
        throw PairingProtocolException(
            PairingProtocolException.Kind.UNAVAILABLE,
            "Unable to allocate a pairing challenge"
        )
    }

    fun status(challengeId: String, pollSecret: String): PairingChallengeStatus = store.status(
        challengeId = challengeId,
        suppliedPollSecretHash = sha256(pollSecret),
        now = clock.instant(),
        pollIntervalSeconds = pollIntervalSeconds
    )

    /** Resolve a manually entered code to a non-authorizing preview of the pending TV. */
    fun lookupByUserCode(rawCode: String): PairingChallengePreview {
        val normalized = rawCode.filterNot { it == '-' || it.isWhitespace() }.uppercase()
        if (normalized.length != USER_CODE_LENGTH || normalized.any { it !in codeAlphabet }) {
            throw PairingProtocolException(PairingProtocolException.Kind.INVALID_INPUT, "Invalid pairing code")
        }
        return store.lookupByUserCode(normalized, clock.instant())
            ?: throw PairingProtocolException(
                PairingProtocolException.Kind.NOT_FOUND,
                "No active pairing request was found"
            )
    }

    /** QR locator lookup intentionally returns no poll secret and cannot change challenge state. */
    fun previewByChallengeId(challengeId: String): PairingChallengePreview? {
        if (challengeId.length != CHALLENGE_ID_LENGTH || challengeId.any { it !in TOKEN_ALPHABET }) return null
        return store.lookupByChallengeId(challengeId, clock.instant())
    }

    fun cancel(challengeId: String, pollSecret: String) = store.cancel(
        challengeId = challengeId,
        suppliedPollSecretHash = sha256(pollSecret),
        now = clock.instant()
    )

    private fun randomUserCode(): String = buildString(USER_CODE_LENGTH) {
        repeat(USER_CODE_LENGTH) {
            append(codeAlphabet[random.nextInt(codeAlphabet.length)])
        }
    }

    private fun uniqueToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun validate(request: PairingRequest) {
        if (request.deviceName.trim().length !in 1..48 ||
            request.clientVersion.trim().length !in 1..32 ||
            request.protocolVersion <= 0 ||
            request.capabilities.size > MAX_CAPABILITIES ||
            request.capabilities.any { it.length !in 1..MAX_CAPABILITY_LENGTH }
        ) {
            throw PairingProtocolException(PairingProtocolException.Kind.INVALID_INPUT, "Invalid pairing request")
        }
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))

    private companion object {
        const val USER_CODE_LENGTH = 8
        const val CHALLENGE_ID_LENGTH = 22
        const val MAX_CAPABILITIES = 32
        const val MAX_CAPABILITY_LENGTH = 32
        const val MAX_CODE_GENERATION_ATTEMPTS = 64
        const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
    }
}
