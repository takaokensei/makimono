package zechs.drive.stream

import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.remote.GithubApi
import zechs.drive.stream.data.repository.GithubRepository
import zechs.drive.stream.utils.state.Resource

class GithubRepositoryTest {

    private val githubApi: GithubApi = mockk()
    private val lazyApi: Lazy<GithubApi> = Lazy { githubApi }
    private val repository = GithubRepository(lazyApi)

    private val fakeRelease = LatestRelease(
        name = "v1.4.25",
        tagName = "v1.4.25",
        htmlUrl = "https://github.com/takaokensei/makimono/releases/tag/v1.4.25",
        assets = emptyList()
    )

    @Test
    fun getLatestRelease_success_returnsSuccessResource() = runTest {
        coEvery { githubApi.getLatestRelease(any()) } returns fakeRelease

        val result = repository.getLatestRelease()

        assertTrue(result is Resource.Success)
        assertEquals(fakeRelease, (result as Resource.Success).data)
        coVerify(exactly = 1) { githubApi.getLatestRelease(any()) }
    }

    @Test
    fun getLatestRelease_apiThrowsException_returnsErrorResource() = runTest {
        coEvery { githubApi.getLatestRelease(any()) } throws RuntimeException("Network error")

        val result = repository.getLatestRelease()

        assertTrue(result is Resource.Error)
        assertEquals("Network error", (result as Resource.Error).message)
    }
}