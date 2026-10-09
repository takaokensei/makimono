package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.data.remote.GithubAuth

class GithubAuthTest {

    @Test
    fun bearerFor_blankToken_returnsNull() {
        assertNull(GithubAuth.bearerFor("  "))
    }

    // Fixture sintética com padrão falso para evitar alertas em scanners de segredos
    private val testToken = "ghp_FAKE_TEST_TOKEN_0000"

    @Test
    fun bearerForConfiguredToken_returnsBearerHeader() {
        assertEquals("Bearer $testToken", GithubAuth.bearerFor(" $testToken "))
    }

    @Test
    fun authorizationForGithubReleaseUrl_returnsBearerHeader() {
        assertEquals(
            "Bearer $testToken",
            GithubAuth.authorizationFor(
                "https://github.com/takaokensei/makimono/releases/download/v1.6.4.1/app.apk",
                testToken
            )
        )
    }

    @Test
    fun authorizationForUntrustedHost_doesNotSendToken() {
        assertNull(
            GithubAuth.authorizationFor("https://attacker.example/file.apk", testToken)
        )
    }

    @Test
    fun authorizationForDifferentGithubRepository_doesNotSendToken() {
        assertNull(
            GithubAuth.authorizationFor(
                "https://github.com/attacker/repo/releases/download/v1/app.apk",
                testToken
            )
        )
    }
}
