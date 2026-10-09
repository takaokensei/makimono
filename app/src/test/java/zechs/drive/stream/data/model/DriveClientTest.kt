package zechs.drive.stream.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveClientTest {

    @Test
    fun authUrlString_buildsValidOAuthUrl() {
        val client = DriveClient(
            clientId = "test-client-id.apps.googleusercontent.com",
            clientSecret = "test-client-secret",
            redirectUri = "com.googleusercontent.apps.test:/oauth2redirect",
            scopes = listOf("https://www.googleapis.com/auth/drive.readonly", "https://www.googleapis.com/auth/drive.file")
        )

        val urlString = client.authUrlString()

        assertTrue(urlString.startsWith("https://accounts.google.com/o/oauth2/auth?"))
        assertTrue(urlString.contains("response_type=code"))
        assertTrue(urlString.contains("access_type=offline"))
        assertTrue(urlString.contains("client_id=test-client-id.apps.googleusercontent.com"))
        assertTrue(urlString.contains("redirect_uri=com.googleusercontent.apps.test:/oauth2redirect"))
        assertTrue(urlString.contains("scope=https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file"))
    }
}
