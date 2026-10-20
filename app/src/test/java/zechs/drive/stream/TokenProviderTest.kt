package zechs.drive.stream

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.common.util.Log
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.DriveAccessTokenResponse
import zechs.drive.stream.data.remote.PairingApi
import zechs.drive.stream.data.repository.DefaultTokenProvider
import zechs.drive.stream.data.repository.TokenAuthenticator
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.ui.player.utils.AuthenticatingDataSource
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource

class TokenProviderTest {

    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val pairingApi: PairingApi = mockk(relaxed = true)
    private lateinit var tokenProvider: DefaultTokenProvider

    private val token1 = DriveAccessTokenResponse("token-abc-1", 3600)
    private val token2 = DriveAccessTokenResponse("token-xyz-2", 3600)

    @Before
    fun setUp() {
        coEvery { sessionManager.fetchPairingSessionToken() } returns "makimono-session-token"
        coEvery { pairingApi.getDriveToken("Bearer makimono-session-token") } returns token1 andThen token2
        tokenProvider = DefaultTokenProvider(sessionManager, pairingApi)
    }

    @Test
    fun validToken_cachesAndReusesToken() = runTest {
        val first = tokenProvider.validToken()
        assertEquals("token-abc-1", first)
        assertEquals("token-abc-1", tokenProvider.getCachedToken())

        // Second call should return cached token without calling fetchAccessToken again
        val second = tokenProvider.validToken()
        assertEquals("token-abc-1", second)
        coVerify(exactly = 1) { pairingApi.getDriveToken("Bearer makimono-session-token") }
    }

    @Test
    fun validToken_concurrentCalls_coalesceSingleFetch() = runTest {
        val jobs = (1..10).map {
            async(Dispatchers.Default) {
                tokenProvider.validToken()
            }
        }
        val results = jobs.awaitAll()
        assertTrue(results.all { it == "token-abc-1" })
        coVerify(exactly = 1) { pairingApi.getDriveToken("Bearer makimono-session-token") }
    }

    @Test
    fun refresh_forcesNewTokenUnderLock() = runTest {
        val initial = tokenProvider.validToken()
        assertEquals("token-abc-1", initial)

        val refreshed = tokenProvider.refresh()
        assertEquals("token-xyz-2", refreshed)
        assertEquals("token-xyz-2", tokenProvider.getCachedToken())
        coVerify(exactly = 2) { pairingApi.getDriveToken("Bearer makimono-session-token") }
    }

    @Test
    fun invalidateToken_clearsCache() = runTest {
        tokenProvider.validToken()
        assertEquals("token-abc-1", tokenProvider.getCachedToken())

        tokenProvider.invalidateToken()
        assertNull(tokenProvider.getCachedToken())
    }

    @Test
    fun authenticatingDataSource_open_attachesPrewarmedTokenNonblockingly() {
        val wrapped: DefaultHttpDataSource = mockk(relaxed = true)
        val provider: TokenProvider = mockk(relaxed = true)
        every { provider.getCachedToken() } returns "prewarmed-token-999"
        every { wrapped.open(any()) } returns 12345L

        val dataSource = AuthenticatingDataSource(wrapped, provider)
        // android.net.Uri.parse() is a framework stub in local JVM tests (it returns null).
        // DataSpec only needs a non-null Uri for these mocked data-source interactions.
        val dataSpec = DataSpec(mockk<android.net.Uri>())

        val bytes = dataSource.open(dataSpec)
        assertEquals(12345L, bytes)

        verify(exactly = 1) { wrapped.setRequestProperty("Authorization", "Bearer prewarmed-token-999") }
        verify(exactly = 1) { wrapped.open(dataSpec) }
    }

    @Test
    fun authenticatingDataSource_open_401_invalidatesTokenWithoutBlocking() {
        val wrapped: DefaultHttpDataSource = mockk(relaxed = true)
        val provider: TokenProvider = mockk(relaxed = true)
        every { provider.getCachedToken() } returns "stale-token"
        // Keep the test independent from Android framework Uri implementations.
        val dataSpec = DataSpec(mockk<android.net.Uri>())
        every { wrapped.open(any()) } throws HttpDataSource.InvalidResponseCodeException(
            401, "Unauthorized", null, emptyMap(), dataSpec, ByteArray(0)
        )

        val dataSource = AuthenticatingDataSource(wrapped, provider)

        // Media3's default logger forwards to Android Log, which is a stub in local JVM tests.
        Log.setLogger(mockk(relaxed = true))
        try {
            dataSource.open(dataSpec)
            fail("Expected InvalidResponseCodeException")
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            assertEquals(401, e.responseCode)
            verify(exactly = 1) { provider.invalidateToken() }
        } finally {
            Log.setLogger(Log.Logger.DEFAULT)
        }
    }

    @Test
    fun tokenAuthenticator_concurrent401_reusesCachedToken() {
        val provider: TokenProvider = mockk(relaxed = true)
        every { provider.getCachedToken() } returns "newly-refreshed-token"

        val authenticator = TokenAuthenticator(provider)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer old-stale-token")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val newRequest = authenticator.authenticate(null, response)
        assertNotNull(newRequest)
        assertEquals("Bearer newly-refreshed-token", newRequest?.header("Authorization"))
        coVerify(exactly = 0) { provider.refresh() }
    }

    @Test
    fun tokenAuthenticator_exceedsMaxRetries_givesUp() {
        val provider: TokenProvider = mockk(relaxed = true)
        val authenticator = TokenAuthenticator(provider)

        val initialRequest = Request.Builder().url("https://www.googleapis.com/drive/v3/files").build()
        val prior1 = Response.Builder()
            .request(initialRequest)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        val response = Response.Builder()
            .request(initialRequest)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior1)
            .build()

        val retryRequest = authenticator.authenticate(null, response)
        assertNull(retryRequest)
    }
}
