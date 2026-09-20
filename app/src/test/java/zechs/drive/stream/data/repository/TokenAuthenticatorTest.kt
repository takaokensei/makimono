package zechs.drive.stream.data.repository

import io.mockk.every
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenAuthenticatorTest {

    private val tokenProvider = mockk<TokenProvider>()

    private fun newRequest(token: String?): Request =
        Request.Builder().url("https://www.googleapis.com/drive/v3/files")
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
            .build()

    private fun newResponse(request: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .apply { if (prior != null) priorResponse(prior) }
            .build()

    @Test
    fun `gives up after max consecutive retries`() {
        val request = newRequest("old-token")
        val first = newResponse(request)
        val second = newResponse(request, prior = first)
        assertNull(TokenAuthenticator(tokenProvider).authenticate(null, second))
    }

    @Test
    fun `reuses token already refreshed by another thread`() {
        val request = newRequest("old-token")
        every { tokenProvider.getCachedToken() } returns "new-token"
        val result = TokenAuthenticator(tokenProvider).authenticate(null, newResponse(request))
        assertEquals("Bearer new-token", result?.header("Authorization"))
    }
}
