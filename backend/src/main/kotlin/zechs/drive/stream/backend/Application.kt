package zechs.drive.stream.backend

import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.CancellationException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.net.URLDecoder
import io.ktor.utils.io.readAvailable
import com.zaxxer.hikari.HikariDataSource

private val pairingJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Local development host and Cloud Run entry point. Pairing OAuth is not production-ready yet. */
fun main() {
    val environment = System.getenv()
    val config = PairingServerConfiguration.from(environment)
    val dataSource = configuredPairingDataSource(environment, config.databasePoolSize)
    val store = if (dataSource == null) {
        InMemoryPairingChallengeStore()
    } else {
        migratePairingDatabase(dataSource)
        PostgresPairingChallengeStore(dataSource)
    }
    val protocol = PairingProtocol(verificationBaseUrl = config.publicBaseUrl, store = store)
    val server = embeddedServer(Netty, host = config.host, port = config.port) {
        pairingModule(protocol)
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(gracePeriodMillis = 1_000, timeoutMillis = 3_000)
        dataSource?.close()
    })
    server.start(wait = true)
}

private fun configuredPairingDataSource(
    environment: Map<String, String>,
    poolSize: Int
): HikariDataSource? {
    val url = environment["MAKIMONO_DATABASE_URL"]
    val user = environment["MAKIMONO_DATABASE_USER"]
    val password = environment["MAKIMONO_DATABASE_PASSWORD"]
    if (url.isNullOrBlank() && user.isNullOrBlank() && password.isNullOrBlank()) return null
    require(!url.isNullOrBlank() && !user.isNullOrBlank() && !password.isNullOrBlank()) {
        "Set MAKIMONO_DATABASE_URL, MAKIMONO_DATABASE_USER, and MAKIMONO_DATABASE_PASSWORD together"
    }
    return createPairingDataSource(url, user, password, poolSize)
}

internal fun Application.pairingModule(
    protocol: PairingProtocol,
    rateLimiter: PairingRateLimiter = PairingRateLimiter()
) {
    install(ContentNegotiation) {
        json(pairingJson)
    }

    routing {
        get("/healthz") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/pair/{challengeId}") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            if (!call.allow(rateLimiter, PairingRateLimiter.Operation.PREVIEW)) return@get
            val challengeId = call.parameters["challengeId"].orEmpty()
            call.respondPairingPage(PairingPage.render(protocol.previewByChallengeId(challengeId)))
        }

        post("/pair/lookup") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            if (!call.allow(rateLimiter, PairingRateLimiter.Operation.LOOKUP)) return@post
            val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_LOOKUP_BODY_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, PairingApiError("request_too_large", "Request body is too large"))
                return@post
            }
            val userCode = try {
                val contentType = call.request.headers[HttpHeaders.ContentType]
                    ?.substringBefore(';')
                    ?.trim()
                require(contentType.equals(ContentType.Application.FormUrlEncoded.toString(), ignoreCase = true))
                val bytes = call.receiveChannel().readBounded(MAX_LOOKUP_BODY_BYTES)
                val encoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
                decodePairingForm(encoded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RequestBodyTooLargeException) {
                call.respond(HttpStatusCode.PayloadTooLarge, PairingApiError("request_too_large", "Request body is too large"))
                return@post
            } catch (_: Exception) {
                null
            }
            val preview = userCode?.let { code ->
                try {
                    protocol.lookupByUserCode(code)
                } catch (_: PairingProtocolException) {
                    null
                }
            }
            call.respondPairingPage(PairingPage.render(preview))
        }

        post("/v1/pairing/lookup") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            if (!call.allow(rateLimiter, PairingRateLimiter.Operation.LOOKUP)) return@post
            val body = try {
                val bytes = call.receiveChannel().readBounded(MAX_LOOKUP_BODY_BYTES)
                val text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
                pairingJson.decodeFromString<PairingCodeLookupRequest>(text)
            } catch (_: RequestBodyTooLargeException) {
                call.respond(HttpStatusCode.PayloadTooLarge, PairingApiError("request_too_large", "Request body is too large"))
                return@post
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, PairingApiError("invalid_request", "Invalid request body"))
                return@post
            }
            val preview = try {
                protocol.lookupByUserCode(body.userCode)
            } catch (error: PairingProtocolException) {
                call.respond(error.httpStatus(), PairingApiError(error.publicCode(), error.message.orEmpty()))
                return@post
            }
            call.respond(
                PairingCodeLookupResponse(
                    challengeId = preview.challengeId,
                    deviceName = preview.deviceName,
                    expiresAt = preview.expiresAt.toString()
                )
            )
        }

        route("/v1/pairing/challenges") {
            post {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                if (!call.allow(rateLimiter, PairingRateLimiter.Operation.CREATE)) return@post
                val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declaredLength != null && declaredLength > MAX_CREATE_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge, PairingApiError("request_too_large", "Request body is too large"))
                    return@post
                }
                val body = try {
                    val bytes = call.receiveChannel().readBounded(MAX_CREATE_BODY_BYTES)
                    val text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                    pairingJson.decodeFromString<CreatePairingRequest>(text)
                } catch (_: RequestBodyTooLargeException) {
                    call.respond(HttpStatusCode.PayloadTooLarge, PairingApiError("request_too_large", "Request body is too large"))
                    return@post
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SerializationException) {
                    call.respond(HttpStatusCode.BadRequest, PairingApiError("invalid_request", "Invalid request body"))
                    return@post
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, PairingApiError("invalid_request", "Invalid request body"))
                    return@post
                }
                val created = try {
                    protocol.create(
                        PairingRequest(
                            deviceName = body.deviceName,
                            clientVersion = body.clientVersion,
                            protocolVersion = body.protocolVersion,
                            capabilities = body.capabilities
                        )
                    )
                } catch (error: PairingProtocolException) {
                    call.respond(error.httpStatus(), PairingApiError(error.publicCode(), error.message.orEmpty()))
                    return@post
                }
                call.respond(
                    HttpStatusCode.Created,
                    CreatePairingResponse(
                        challengeId = created.challengeId,
                        userCode = created.userCode,
                        verificationUrl = created.verificationUrl,
                        expiresAt = created.expiresAt.toString(),
                        pollIntervalSeconds = created.pollIntervalSeconds,
                        pollSecret = created.pollSecret
                    )
                )
            }

            get("/{challengeId}/status") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                if (!call.allow(rateLimiter, PairingRateLimiter.Operation.STATUS)) return@get
                val challengeId = call.parameters["challengeId"].orEmpty()
                val secret = call.request.headers[HttpHeaders.Authorization]
                    ?.takeIf { it.startsWith("Pairing ") }
                    ?.removePrefix("Pairing ")
                    .orEmpty()
                val status = try {
                    protocol.status(challengeId, secret)
                } catch (error: PairingProtocolException) {
                    call.respond(error.httpStatus(), PairingApiError(error.publicCode(), error.message.orEmpty()))
                    return@get
                }
                call.respond(
                    PairingStatusResponse(
                        state = status.state.name.lowercase(),
                        expiresAt = status.expiresAt.toString(),
                        pollIntervalSeconds = status.pollIntervalSeconds
                    )
                )
            }

            delete("/{challengeId}") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                if (!call.allow(rateLimiter, PairingRateLimiter.Operation.CANCEL)) return@delete
                val challengeId = call.parameters["challengeId"].orEmpty()
                val secret = call.request.headers[HttpHeaders.Authorization]
                    ?.takeIf { it.startsWith("Pairing ") }
                    ?.removePrefix("Pairing ")
                    .orEmpty()
                try {
                    protocol.cancel(challengeId, secret)
                } catch (error: PairingProtocolException) {
                    call.respond(error.httpStatus(), PairingApiError(error.publicCode(), error.message.orEmpty()))
                    return@delete
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.allow(
    rateLimiter: PairingRateLimiter,
    operation: PairingRateLimiter.Operation
): Boolean {
    val decision = rateLimiter.acquire(request.local.remoteHost, operation)
    if (decision.allowed) return true
    response.header(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
    respond(
        HttpStatusCode.TooManyRequests,
        PairingApiError("rate_limited", "Too many requests; retry after the indicated delay")
    )
    return false
}

private suspend fun io.ktor.server.application.ApplicationCall.respondPairingPage(html: String) {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'; img-src 'none'")
    response.header("Referrer-Policy", "no-referrer")
    response.header("X-Content-Type-Options", "nosniff")
    response.header("X-Frame-Options", "DENY")
    respondText(html, ContentType.Text.Html, HttpStatusCode.OK)
}

private fun decodePairingForm(encoded: String): String {
    require(encoded.isNotEmpty())
    val fields = encoded.split('&')
    require(fields.size == 1) { "Unexpected form fields" }
    val separator = fields.single().indexOf('=')
    require(separator > 0) { "Malformed form field" }
    val name = URLDecoder.decode(fields.single().substring(0, separator), StandardCharsets.UTF_8)
    val value = URLDecoder.decode(fields.single().substring(separator + 1), StandardCharsets.UTF_8)
    require(name == "userCode") { "Unexpected form field" }
    return value
}

@Serializable
internal data class CreatePairingRequest(
    val deviceName: String,
    val clientVersion: String,
    val protocolVersion: Int,
    val capabilities: List<String> = emptyList()
)

@Serializable
internal data class CreatePairingResponse(
    val challengeId: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresAt: String,
    val pollIntervalSeconds: Long,
    val pollSecret: String
)

@Serializable
internal data class PairingCodeLookupRequest(val userCode: String)

@Serializable
internal data class PairingCodeLookupResponse(
    val challengeId: String,
    val deviceName: String,
    val expiresAt: String
)

@Serializable
internal data class PairingStatusResponse(
    val state: String,
    val expiresAt: String,
    val pollIntervalSeconds: Long
)

@Serializable
internal data class PairingApiError(val code: String, val message: String)

private const val MAX_CREATE_BODY_BYTES = 8 * 1024
private const val MAX_LOOKUP_BODY_BYTES = 1 * 1024

private class RequestBodyTooLargeException : RuntimeException()

private suspend fun io.ktor.utils.io.ByteReadChannel.readBounded(maxBytes: Int): ByteArray {
    val buffer = ByteArray(maxBytes + 1)
    var length = 0
    while (length < buffer.size) {
        val read = readAvailable(buffer, length, buffer.size - length)
        if (read == -1) break
        if (read == 0) continue
        length += read
    }
    if (length > maxBytes) throw RequestBodyTooLargeException()
    return buffer.copyOf(length)
}

private fun PairingProtocolException.httpStatus() = when (kind) {
    PairingProtocolException.Kind.INVALID_INPUT -> HttpStatusCode.BadRequest
    PairingProtocolException.Kind.NOT_FOUND -> HttpStatusCode.NotFound
    PairingProtocolException.Kind.INVALID_SECRET -> HttpStatusCode.Unauthorized
    PairingProtocolException.Kind.EXPIRED -> HttpStatusCode.Gone
    PairingProtocolException.Kind.UNAVAILABLE -> HttpStatusCode.ServiceUnavailable
}

private fun PairingProtocolException.publicCode() = when (kind) {
    PairingProtocolException.Kind.INVALID_INPUT -> "invalid_request"
    PairingProtocolException.Kind.NOT_FOUND -> "challenge_not_found"
    PairingProtocolException.Kind.INVALID_SECRET -> "invalid_pairing_credential"
    PairingProtocolException.Kind.EXPIRED -> "challenge_unavailable"
    PairingProtocolException.Kind.UNAVAILABLE -> "service_unavailable"
}
