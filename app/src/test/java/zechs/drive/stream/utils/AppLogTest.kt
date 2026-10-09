package zechs.drive.stream.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLogTest {

    private val loggedEntries = mutableListOf<LogEntry>()

    data class LogEntry(
        val priority: Int,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    @Before
    fun setUp() {
        loggedEntries.clear()
        AppLog.logHandler = { priority, tag, message, throwable ->
            loggedEntries.add(LogEntry(priority, tag, message, throwable))
        }
    }

    @After
    fun tearDown() {
        AppLog.logHandler = null
    }

    @Test
    fun redact_bearerToken_isRedacted() {
        val input = "Header: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val output = AppLog.redact(input)
        assertEquals("Header: Bearer [REDACTED]", output)
    }

    @Test
    fun redact_googleAccessToken_isRedacted() {
        // Fixture sintética com padrão falso para evitar falsos-positivos em scanners de segredo
        val input = "Fetching file with token ya29.FAKE_TEST_TOKEN for download"
        val output = AppLog.redact(input)
        assertTrue(output.contains("ya29.[REDACTED]"))
        assertFalse(output.contains("ya29.FAKE_TEST_TOKEN"))
    }

    @Test
    fun redact_sensitiveQueryParams_areRedacted() {
        val input = "https://oauth2.googleapis.com/token?client_secret=secret123&refresh_token=rt456&access_token=at789&code=authcode999&password=p@ssword!"
        val output = AppLog.redact(input)
        assertTrue(output.contains("client_secret=[REDACTED]"))
        assertTrue(output.contains("refresh_token=[REDACTED]"))
        assertTrue(output.contains("access_token=[REDACTED]"))
        assertTrue(output.contains("code=[REDACTED]"))
        assertTrue(output.contains("password=[REDACTED]"))
        assertFalse(output.contains("secret123"))
        assertFalse(output.contains("rt456"))
        assertFalse(output.contains("p@ssword!"))
    }

    @Test
    fun redact_jsonSecrets_areRedacted() {
        val json = """{"client_secret": "mySecretKey", "password": "pass", "fileId": "12345"}"""
        val output = AppLog.redact(json)
        assertTrue(output.contains(""""client_secret": "[REDACTED]""""))
        assertTrue(output.contains(""""password": "[REDACTED]""""))
        assertTrue(output.contains(""""fileId": "12345""""))
    }

    @Test
    fun redact_normalMessage_unaltered() {
        val message = "Playback started for episode S01E05 at position 120000ms"
        assertEquals(message, AppLog.redact(message))
    }

    @Test
    fun loggingMethods_redactSensitiveContentAutomatically() {
        AppLog.d("TestTag", "Request with Bearer abc1234567")
        // Fixture sintética com padrão falso
        AppLog.i("TestTag", "ya29.FAKE_TEST_TOKEN_FOR_LOGGING")
        AppLog.w("TestTag", "Failed refresh with refresh_token=xyz789")
        AppLog.e("TestTag", "Fatal error with password=mySecretPassword")

        assertEquals(4, loggedEntries.size)
        assertEquals(AppLog.DEBUG, loggedEntries[0].priority)
        assertTrue(loggedEntries[0].message.contains("Bearer [REDACTED]"))

        assertEquals(AppLog.INFO, loggedEntries[1].priority)
        assertTrue(loggedEntries[1].message.contains("ya29.[REDACTED]"))

        assertEquals(AppLog.WARN, loggedEntries[2].priority)
        assertTrue(loggedEntries[2].message.contains("refresh_token=[REDACTED]"))

        assertEquals(AppLog.ERROR, loggedEntries[3].priority)
        assertTrue(loggedEntries[3].message.contains("password=[REDACTED]"))
    }

    @Test
    fun logSwallowed_recordsExceptionWithActionDescriptionAndSanitization() {
        val error = RuntimeException("Auth failed: Bearer secret-leaked-token")
        AppLog.logSwallowed("TestTag", "refreshing session cache", error)

        assertEquals(1, loggedEntries.size)
        val entry = loggedEntries.first()
        assertEquals(AppLog.WARN, entry.priority)
        assertEquals("TestTag", entry.tag)
        assertTrue(entry.message.contains("Safely handled exception during refreshing session cache"))
        assertTrue(entry.message.contains("Bearer [REDACTED]"))
        assertFalse(entry.message.contains("secret-leaked-token"))
        assertEquals(error, entry.throwable)
    }
}
