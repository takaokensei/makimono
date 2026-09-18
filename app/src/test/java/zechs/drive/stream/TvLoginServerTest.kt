package zechs.drive.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.utils.TvLoginServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

class TvLoginServerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var server: TvLoginServer
    private var receivedToken: String? = null
    private var activatedDefault: Boolean = false

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + Job())
        receivedToken = null
        activatedDefault = false

        // Pick port 0 or an available port
        server = TvLoginServer(
            port = 18880,
            clientId = "test-client-id",
            onAuthCodeReceived = {},
            onSessionReceived = { token, _, _, _ -> receivedToken = token },
            onActivateDefault = { activatedDefault = true },
            isAlreadyAuthenticated = { false }
        )
        val started = server.start(scope)
        assertTrue("Server should start", started)
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun sendHttpRequest(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String = ""
    ): Pair<Int, String> {
        Socket("127.0.0.1", server.boundPort).use { socket ->
            socket.soTimeout = 3000
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))

            val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
            writer.print("$method $path HTTP/1.1\r\n")
            writer.print("Host: 127.0.0.1\r\n")
            writer.print("Content-Length: ${bodyBytes.size}\r\n")
            headers.forEach { (k, v) -> writer.print("$k: $v\r\n") }
            writer.print("Connection: close\r\n\r\n")
            if (body.isNotEmpty()) {
                writer.print(body)
            }
            writer.flush()

            val statusLine = reader.readLine() ?: ""
            val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 0

            val responseBody = StringBuilder()
            var isBody = false
            var line: String? = reader.readLine()
            while (line != null) {
                if (isBody) {
                    responseBody.append(line).append("\n")
                } else if (line.isEmpty()) {
                    isBody = true
                }
                line = reader.readLine()
            }
            return Pair(statusCode, responseBody.toString().trim())
        }
    }

    @Test
    fun postWithoutNonce_returns401() {
        val (code, body) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"refreshToken\":\"secret_refresh_token_123\"}"
        )
        assertEquals(401, code)
        assertTrue(body.contains("Nonce de pareamento ausente"))
        assertEquals(null, receivedToken)
    }

    @Test
    fun postWithInvalidNonce_returns401() {
        val (code, body) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Pairing-Nonce" to "invalid_fake_nonce"
            ),
            body = "{\"refreshToken\":\"secret_refresh_token_123\"}"
        )
        assertEquals(401, code)
        assertTrue(body.contains("Nonce de pareamento inválido"))
        assertEquals(null, receivedToken)
    }

    @Test
    fun postWithExpiredNonce_returns410() {
        // Manually set an expired session
        val expiredSession = TvLoginServer.PairingSession(
            nonce = "expired_nonce_123",
            expiresAt = System.currentTimeMillis() - 1000L
        )
        server.setPairingSessionForTesting(expiredSession)

        val (code, body) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Pairing-Nonce" to "expired_nonce_123"
            ),
            body = "{\"refreshToken\":\"secret_refresh_token_123\"}"
        )
        assertEquals(410, code)
        assertTrue(body.contains("Sessão de pareamento expirada"))
        assertEquals(null, receivedToken)
    }

    @Test
    fun postWithReusedNonce_returns409() {
        val validNonce = server.getPairingNonce()

        // 1st request with valid nonce succeeds
        val (code1, _) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"refreshToken\":\"token_1\",\"nonce\":\"$validNonce\"}"
        )
        assertEquals(200, code1)
        assertEquals("token_1", receivedToken)

        // 2nd request with same nonce is rejected
        val (code2, body2) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"refreshToken\":\"token_2\",\"nonce\":\"$validNonce\"}"
        )
        assertEquals(409, code2)
        assertTrue(body2.contains("já utilizada"))
        assertEquals("token_1", receivedToken) // Not overwritten
    }

    @Test
    fun postPayloadTooLarge_returns413() {
        val largeBody = "{\"data\":\"" + "A".repeat(70_000) + "\"}"
        val (code, _) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf("Content-Type" to "application/json"),
            body = largeBody
        )
        assertEquals(413, code)
    }

    @Test
    fun response_neverEchoesSensitiveTokens() {
        val validNonce = server.getPairingNonce()
        val sensitiveRefreshToken = "very_secret_refresh_token_999"

        val (code, responseBody) = sendHttpRequest(
            method = "POST",
            path = "/api/session",
            headers = mapOf("Content-Type" to "application/json"),
            body = "{\"refreshToken\":\"$sensitiveRefreshToken\",\"nonce\":\"$validNonce\"}"
        )
        assertEquals(200, code)
        assertFalse("Response must not echo refresh token", responseBody.contains(sensitiveRefreshToken))
    }
}
