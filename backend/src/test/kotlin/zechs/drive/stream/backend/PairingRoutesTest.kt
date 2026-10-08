package zechs.drive.stream.backend

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingRoutesTest {

    @Test
    fun `qr page safely previews the pending device without enabling authorization`() = testApplication {
        val protocol = PairingProtocol("https://pairing.example.test", clock = Clock.systemUTC())
        application { pairingModule(protocol) }

        val created = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceName":"<script>alert('x')</script> & TV","clientVersion":"1.6.4.1","protocolVersion":1}""")
        }
        val createdBody = created.bodyAsText()
        val challengeId = Regex("\"challengeId\":\"([^\"]+)\"").find(createdBody)!!.groupValues[1]
        val pollSecret = Regex("\"pollSecret\":\"([^\"]+)\"").find(createdBody)!!.groupValues[1]

        val page = client.get("/pair/$challengeId")
        val html = page.bodyAsText()

        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals("no-store", page.headers[HttpHeaders.CacheControl])
        assertEquals("no-referrer", page.headers["Referrer-Policy"])
        assertTrue(page.headers["Content-Security-Policy"].orEmpty().contains("default-src 'none'"))
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; TV"))
        assertFalse(html.contains("<script>alert('x')</script>"))
        assertFalse(html.contains(pollSecret))
        assertTrue(html.contains("name=\"userCode\""))
        assertTrue(html.contains("não está configurada neste ambiente"))

        val status = client.get("/v1/pairing/challenges/$challengeId/status") {
            header(HttpHeaders.Authorization, "Pairing $pollSecret")
        }
        assertTrue(status.bodyAsText().contains("\"state\":\"pending\""))
    }

    @Test
    fun `manual browser form uses post and hides malformed and inactive code differences`() = testApplication {
        val protocol = PairingProtocol("https://pairing.example.test", clock = Clock.systemUTC())
        application { pairingModule(protocol) }
        val create = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceName":"Sala TV","clientVersion":"1.6.4.1","protocolVersion":1}""")
        }
        val body = create.bodyAsText()
        val code = Regex("\"userCode\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val challengeId = Regex("\"challengeId\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        val valid = client.post("/pair/lookup") {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            setBody("userCode=${code.take(4)}-${code.drop(4).lowercase()}")
        }
        val malformed = client.post("/pair/lookup") {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            setBody("userCode=AB-12")
        }
        val unknown = client.post("/pair/lookup") {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            setBody("userCode=23456789")
        }

        assertEquals(HttpStatusCode.OK, valid.status)
        assertTrue(valid.bodyAsText().contains("Sala TV"))
        assertTrue(valid.bodyAsText().contains("Prévia de pareamento"))
        assertFalse(valid.bodyAsText().contains(code))
        assertEquals(HttpStatusCode.OK, malformed.status)
        assertEquals(HttpStatusCode.OK, unknown.status)
        assertEquals(malformed.bodyAsText(), unknown.bodyAsText())
        assertFalse(unknown.bodyAsText().contains("23456789"))

        protocol.cancel(challengeId, Regex("\"pollSecret\":\"([^\"]+)\"").find(body)!!.groupValues[1])
        val expired = client.get("/pair/$challengeId")
        assertTrue(expired.bodyAsText().contains("Solicitação indisponível"))
        assertFalse(expired.bodyAsText().contains("Sala TV"))
    }

    @Test
    fun `browser form rejects oversized and non form bodies`() = testApplication {
        application { pairingModule(PairingProtocol("https://pairing.example.test")) }

        val oversized = client.post("/pair/lookup") {
            header(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
            setBody("userCode=" + "2".repeat(1_100))
        }
        val wrongContentType = client.post("/pair/lookup") {
            contentType(ContentType.Application.Json)
            setBody("""{"userCode":"23456789"}""")
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, oversized.status)
        assertEquals("no-store", oversized.headers[HttpHeaders.CacheControl])
        assertEquals(HttpStatusCode.OK, wrongContentType.status)
        assertTrue(wrongContentType.bodyAsText().contains("Solicitação indisponível"))
    }

    @Test
    fun `manual code lookup returns only an active device preview and never approves`() = testApplication {
        val protocol = PairingProtocol("https://pairing.example.test", clock = Clock.systemUTC())
        application { pairingModule(protocol) }

        val created = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceName":"Sala TV","clientVersion":"1.6.4.1","protocolVersion":1,"capabilities":["media3"]}""")
        }
        val createdBody = created.bodyAsText()
        val code = Regex("\"userCode\":\"([^\"]+)\"").find(createdBody)!!.groupValues[1]
        val challengeId = Regex("\"challengeId\":\"([^\"]+)\"").find(createdBody)!!.groupValues[1]
        val pollSecret = Regex("\"pollSecret\":\"([^\"]+)\"").find(createdBody)!!.groupValues[1]

        val lookup = client.post("/v1/pairing/lookup") {
            contentType(ContentType.Application.Json)
            setBody("""{"userCode":"${code.take(4)}-${code.drop(4).lowercase()}"}""")
        }

        assertEquals(HttpStatusCode.OK, lookup.status)
        assertEquals("no-store", lookup.headers[HttpHeaders.CacheControl])
        val preview = lookup.bodyAsText()
        assertTrue(preview.contains("\"challengeId\":\"$challengeId\""))
        assertTrue(preview.contains("\"deviceName\":\"Sala TV\""))
        assertFalse(preview.contains("pollSecret"))
        assertFalse(preview.contains("capabilities"))
        assertFalse(preview.contains(code))

        val status = client.get("/v1/pairing/challenges/$challengeId/status") {
            header(HttpHeaders.Authorization, "Pairing $pollSecret")
        }
        assertEquals(HttpStatusCode.OK, status.status)
        assertTrue(status.bodyAsText().contains("\"state\":\"pending\""))
    }

    @Test
    fun `manual code lookup rejects malformed and unknown codes without revealing which failed`() = testApplication {
        application { pairingModule(PairingProtocol("https://pairing.example.test")) }

        suspend fun lookup(code: String) = client.post("/v1/pairing/lookup") {
            contentType(ContentType.Application.Json)
            setBody("""{"userCode":"$code"}""")
        }

        val malformed = lookup("AB-12")
        val unknown = lookup("23456789")

        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("no-store", malformed.headers[HttpHeaders.CacheControl])
        assertEquals("no-store", unknown.headers[HttpHeaders.CacheControl])
        assertFalse(malformed.bodyAsText().contains("AB-12"))
        assertFalse(unknown.bodyAsText().contains("23456789"))
    }

    @Test
    fun `manual code lookup has an independent rate limit`() = testApplication {
        val limiter = PairingRateLimiter(
            limits = mapOf(PairingRateLimiter.Operation.LOOKUP to 1)
        )
        application { pairingModule(PairingProtocol("https://pairing.example.test"), limiter) }

        val first = client.post("/v1/pairing/lookup") {
            contentType(ContentType.Application.Json)
            setBody("""{"userCode":"23456789"}""")
        }
        val second = client.post("/v1/pairing/lookup") {
            contentType(ContentType.Application.Json)
            setBody("""{"userCode":"23456789"}""")
        }

        assertEquals(HttpStatusCode.NotFound, first.status)
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertTrue(second.headers[HttpHeaders.RetryAfter] != null)
        assertEquals("no-store", second.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `challenge creation returns retryable rate limit without caching`() = testApplication {
        val protocol = PairingProtocol("https://pairing.example.test", clock = Clock.systemUTC())
        val limiter = PairingRateLimiter(
            clock = Clock.fixed(Instant.parse("2026-10-06T12:00:10Z"), ZoneOffset.UTC),
            limits = mapOf(
                PairingRateLimiter.Operation.CREATE to 1,
                PairingRateLimiter.Operation.STATUS to 20,
                PairingRateLimiter.Operation.CANCEL to 10
            )
        )
        application { pairingModule(protocol, limiter) }

        fun body() = """{"deviceName":"Sala TV","clientVersion":"1.6.4.1","protocolVersion":1}"""
        val first = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody(body())
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val limited = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody(body())
        }
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("50", limited.headers[HttpHeaders.RetryAfter])
        assertEquals("no-store", limited.headers[HttpHeaders.CacheControl])
        assertTrue(limited.bodyAsText().contains("rate_limited"))
    }

    @Test
    fun `create returns uncached challenge and poll secret is required for status`() = testApplication {
        val protocol = PairingProtocol("https://pairing.example.test", clock = Clock.systemUTC())
        application { pairingModule(protocol) }

        val create = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceName":"Sala TV","clientVersion":"1.6.4.1","protocolVersion":1,"capabilities":["media3"]}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("no-store", create.headers[HttpHeaders.CacheControl])
        val body = create.bodyAsText()
        val id = Regex("\"challengeId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val secret = Regex("\"pollSecret\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertFalse(body.contains("pairing.example.test/pair/$id/$secret"))

        val unauthorized = client.get("/v1/pairing/challenges/$id/status") {
            header(HttpHeaders.Authorization, "Pairing wrong-secret")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val status = client.get("/v1/pairing/challenges/$id/status") {
            header(HttpHeaders.Authorization, "Pairing $secret")
        }
        assertEquals(HttpStatusCode.OK, status.status)
        assertEquals("no-store", status.headers[HttpHeaders.CacheControl])
        assertTrue(status.bodyAsText().contains("\"state\":\"pending\""))
        assertFalse(status.bodyAsText().contains("pollSecret"))
    }

    @Test
    fun `cancel endpoint requires poll secret and is retryable`() = testApplication {
        val protocol = PairingProtocol("https://pairing.example.test", clock = Clock.systemUTC())
        application { pairingModule(protocol) }
        val create = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceName":"Sala TV","clientVersion":"1.6.4.1","protocolVersion":1}""")
        }
        val body = create.bodyAsText()
        val id = Regex("\"challengeId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val secret = Regex("\"pollSecret\":\"([^\"]+)\"").find(body)!!.groupValues[1]

        val unauthorized = client.delete("/v1/pairing/challenges/$id")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        repeat(2) {
            val cancelled = client.delete("/v1/pairing/challenges/$id") {
                header(HttpHeaders.Authorization, "Pairing $secret")
            }
            assertEquals(HttpStatusCode.NoContent, cancelled.status)
        }
        val status = client.get("/v1/pairing/challenges/$id/status") {
            header(HttpHeaders.Authorization, "Pairing $secret")
        }
        assertEquals(HttpStatusCode.Gone, status.status)
    }

    @Test
    fun `malformed request body returns typed client error`() = testApplication {
        application { pairingModule(PairingProtocol("https://pairing.example.test")) }

        val response = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("invalid_request"))
    }

    @Test
    fun `oversized challenge request is rejected before JSON parsing`() = testApplication {
        application { pairingModule(PairingProtocol("https://pairing.example.test")) }

        val response = client.post("/v1/pairing/challenges") {
            contentType(ContentType.Application.Json)
            setBody("{" + " ".repeat(9 * 1024) + "}")
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.bodyAsText().contains("request_too_large"))
    }
}
