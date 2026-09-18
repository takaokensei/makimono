package zechs.drive.stream

import dagger.Lazy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.data.model.TokenResponse
import zechs.drive.stream.data.remote.DriveApi
import zechs.drive.stream.data.remote.TokenApi
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.util.Constants

class DriveRepositoryTest {

    private lateinit var driveApi: DriveApi
    private lateinit var tokenApiLazy: Lazy<TokenApi>
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: DriveRepository

    @Before
    fun setUp() {
        driveApi = mockk()
        tokenApiLazy = Lazy { mockk() }
        val testToken = TokenResponse(
            accessToken = "valid_test_access_token",
            expiresIn = (System.currentTimeMillis() / 1000) + 3600L,
            tokenType = "Bearer",
            scope = Constants.DEFAULT_DRIVE_SCOPE
        )
        sessionManager = mockk {
            coEvery { fetchClient() } returns Constants.DEFAULT_CLIENT
            coEvery { fetchAccessToken() } returns testToken
            coEvery { fetchRefreshToken() } returns "valid_test_refresh_token"
        }
        repository = DriveRepository(driveApi, tokenApiLazy, sessionManager)
    }

    private fun createFile(id: String, name: String) = File(
        id = id,
        name = name,
        size = 1000L,
        mimeType = "video/mp4",
        iconLink = "https://example.com/icon.png",
        thumbnailLink = null
    )

    @Test
    fun getAllFiles_singlePage_returnsAllFiles() = runBlocking {
        val pageFiles = listOf(createFile("f1", "Movie 1.mp4"), createFile("f2", "Movie 2.mp4"))
        coEvery {
            driveApi.getFiles(
                accessToken = "Bearer valid_test_access_token",
                q = any(),
                pageSize = any(),
                pageToken = null,
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = pageFiles, nextPageToken = null)

        val result = repository.getAllFiles(query = "trashed=false")
        assertTrue(result is Resource.Success)
        val files = (result as Resource.Success).data
        assertEquals(2, files.size)
        assertEquals("f1", files[0].id)
        assertEquals("f2", files[1].id)
    }

    @Test
    fun getAllFiles_multiplePages_accumulatesAcrossPages() = runBlocking {
        val page1 = listOf(createFile("f1", "Ep 1.mp4"))
        val page2 = listOf(createFile("f2", "Ep 2.mp4"))

        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = null,
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = page1, nextPageToken = "token_page_2")

        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = "token_page_2",
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = page2, nextPageToken = null)

        val result = repository.getAllFiles(query = "trashed=false")
        assertTrue(result is Resource.Success)
        val files = (result as Resource.Success).data
        assertEquals(2, files.size)
        assertEquals("f1", files[0].id)
        assertEquals("f2", files[1].id)
    }

    @Test
    fun getAllFiles_apiError_returnsResourceError() = runBlocking {
        val response = Response.error<FilesResponse>(
            401,
            "{\"error\":\"Unauthorized\"}".toResponseBody()
        )
        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = any(),
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } throws HttpException(response)

        val result = repository.getAllFiles(query = "trashed=false")
        assertTrue(result is Resource.Error)
        val message = (result as Resource.Error).message
        assertTrue(message.contains("Sessão expirada"))
    }

    @Test
    fun getFiles_nullAccessToken_returnsError() = runBlocking {
        val nullTokenSessionManager = mockk<SessionManager> {
            coEvery { fetchAccessToken() } returns null
            coEvery { fetchClient() } returns null
        }
        val repo = DriveRepository(driveApi, tokenApiLazy, nullTokenSessionManager)

        val result = repo.getFiles(query = "trashed=false", pageToken = null, pageSize = 25)
        assertTrue(result is Resource.Error)
        assertEquals("Access token can not be null", result.message)
    }

    @Test
    fun doOnError_http403_returnsPermissionError() = runBlocking {
        val response = Response.error<FilesResponse>(
            403,
            "{\"error\":\"Forbidden\"}".toResponseBody()
        )
        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = any(),
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } throws HttpException(response)

        val result = repository.getFiles(query = "trashed=false", pageToken = null, pageSize = 25)
        assertTrue(result is Resource.Error)
        val message = result.message ?: ""
        assertTrue(message.contains("Acesso negado") || message.contains("403"))
    }

    @Test
    fun doOnError_http429_returnsRateLimitError() = runBlocking {
        val response = Response.error<FilesResponse>(
            429,
            "{\"error\":\"Too Many Requests\"}".toResponseBody()
        )
        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = any(),
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } throws HttpException(response)

        val result = repository.getFiles(query = "trashed=false", pageToken = null, pageSize = 25)
        assertTrue(result is Resource.Error)
        val message = result.message ?: ""
        assertTrue(message.contains("Muitas requisições") || message.contains("429"))
    }
}
