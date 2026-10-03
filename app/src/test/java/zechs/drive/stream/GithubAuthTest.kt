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

    @Test
    fun bearerForConfiguredToken_returnsBearerHeader() {
        assertEquals("Bearer ghp_example", GithubAuth.bearerFor(" ghp_example "))
    }

    @Test
    fun authorizationForGithubReleaseUrl_returnsBearerHeader() {
        assertEquals(
            "Bearer ghp_example",
            GithubAuth.authorizationFor(
                "https://github.com/takaokensei/makimono/releases/download/v1.6.4.1/app.apk",
                "ghp_example"
            )
        )
    }

    @Test
    fun authorizationForUntrustedHost_doesNotSendToken() {
        assertNull(
            GithubAuth.authorizationFor("https://attacker.example/file.apk", "ghp_example")
        )
    }

    @Test
    fun authorizationForDifferentGithubRepository_doesNotSendToken() {
        assertNull(
            GithubAuth.authorizationFor(
                "https://github.com/attacker/repo/releases/download/v1/app.apk",
                "ghp_example"
            )
        )
    }
}
