package zechs.drive.stream

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.RedactingLoggingInterceptor

class RedactingLoggingInterceptorTest {

    @Test
    fun redact_bearerToken_isRedacted() {
        val input = "Authorization: Bearer ya29.a0AfH6SMD_very_secret_token_value"
        val output = RedactingLoggingInterceptor.redact(input)
        assertEquals("Authorization: Bearer [REDACTED]", output)
        assertFalse(output.contains("ya29.a0AfH6SMD_very_secret_token_value"))
    }

    @Test
    fun redact_clientSecretInQuery_isRedacted() {
        val input = "POST /token?client_id=12345&client_secret=GOCSPX-Secret98765&grant_type=code"
        val output = RedactingLoggingInterceptor.redact(input)
        assertTrue(output.contains("client_secret=[REDACTED]"))
        assertFalse(output.contains("GOCSPX-Secret98765"))
        assertTrue(output.contains("client_id=12345"))
    }

    @Test
    fun redact_refreshTokenInQuery_isRedacted() {
        val input = "https://oauth2.googleapis.com/token?refresh_token=1//04secret_refresh_token&client_id=123"
        val output = RedactingLoggingInterceptor.redact(input)
        assertTrue(output.contains("refresh_token=[REDACTED]"))
        assertFalse(output.contains("1//04secret_refresh_token"))
    }

    @Test
    fun redact_jsonCredentials_areRedacted() {
        val json = """{"client_secret": "super_secret_123", "refresh_token": "rt_456", "name": "drive"}"""
        val output = RedactingLoggingInterceptor.redact(json)
        assertTrue(output.contains(""""client_secret": "[REDACTED]""""))
        assertTrue(output.contains(""""refresh_token": "[REDACTED]""""))
        assertTrue(output.contains(""""name": "drive""""))
        assertFalse(output.contains("super_secret_123"))
        assertFalse(output.contains("rt_456"))
    }

    @Test
    fun redact_nonSensitiveContent_remainsIntact() {
        val safeLog = "GET https://www.googleapis.com/drive/v3/files?pageSize=50 HTTP/1.1"
        val output = RedactingLoggingInterceptor.redact(safeLog)
        assertEquals(safeLog, output)
    }

    @Test
    fun interceptor_redactsLoggedOutput() {
        val loggedMessages = mutableListOf<String>()
        val interceptor = RedactingLoggingInterceptor.create(
            level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS,
            logger = { loggedMessages.add(it) }
        )
        assertNotNull(interceptor)

        val testHeader = "Authorization: Bearer 1234567890abcdef"
        val redacted = RedactingLoggingInterceptor.redact(testHeader)
        assertEquals("Authorization: Bearer [REDACTED]", redacted)
    }
}
