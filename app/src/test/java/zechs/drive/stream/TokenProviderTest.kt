package zechs.drive.stream

import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.DriveClient
import zechs.drive.stream.data.model.TokenResponse
import zechs.drive.stream.data.repository.DefaultTokenProvider
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.TokenAuthenticator
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.ui.player.utils.AuthenticatingDataSource
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource

class TokenProviderTest {

    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val driveRepository: DriveRepository = mockk(relaxed = true)
    private lateinit var tokenProvider: DefaultTokenProvider

    private val testClient = DriveClient("id", "secret", "http://localhost", listOf("drive.readonly"))
    private val token1 = TokenResponse("token-abc-1", 3600, "Bearer", "drive.readonly")
    private val token2 = TokenResponse("token-xyz-2", 3600, "Bearer", "drive.readonly")

    @Before
    fun setUp() {
        coEvery { sessionManager.fetchClient() } returns testClient
        coEvery { driveRepository.fetchAccessToken(testClient, forceRefresh = false) } returns Resource.Success(token1)
        coEvery { driveRepository.fetchAccessToken(testClient, forceRefresh = true) } returns Resource.Success(token2)
        tokenProvider = DefaultTokenProvider(sessionManager, dagger.Lazy { driveRepository })
    }

    @Test
    fun validToken_cachesAndReusesToken() = runTest {
        val first = tokenProvider.validToken()
        assertEquals("token-abc-1", first)
        assertEquals("token-abc-1", tokenProvider.getCachedToken())

        // Second call should return cached token without calling fetchAccessToken again
        val second = tokenProvider.validToken()
        assertEquals("token-abc-1", second)
        coVerify(exactly = 1) { driveRepository.fetchAccessToken(testClient, forceRefresh = false) }
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
        coVerify(exactly = 1) { driveRepository.fetchAccessToken(testClient, forceRefresh = false) }
    }

    @Test
    fun refresh_forcesNewTokenUnderLock() = runTest {
        val initial = tokenProvider.validToken()
        assertEquals("token-abc-1", initial)

        val refreshed = tokenProvider.refresh()
        assertEquals("token-xyz-2", refreshed)
        assertEquals("token-xyz-2", tokenProvider.getCachedToken())
        coVerify(exactly = 1) { driveRepository.fetchAccessToken(testClient, forceRefresh = true) }
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
        val dataSpec = DataSpec(android.net.Uri.parse("https://drive.google.com/test"))

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
        val dataSpec = DataSpec(android.net.Uri.parse("https://drive.google.com/test"))
        every { wrapped.open(any()) } throws HttpDataSource.InvalidResponseCodeException(
            401, "Unauthorized", emptyMap(), dataSpec
        )

        val dataSource = AuthenticatingDataSource(wrapped, provider)

        try {
            dataSource.open(dataSpec)
            fail("Expected InvalidResponseCodeException")
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            assertEquals(401, e.responseCode)
            verify(exactly = 1) { provider.invalidateToken() }
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
