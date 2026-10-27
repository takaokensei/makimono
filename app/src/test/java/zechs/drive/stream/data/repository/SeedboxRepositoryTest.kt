package zechs.drive.stream.data.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.data.model.*
import zechs.drive.stream.data.remote.PairingApi
import zechs.drive.stream.utils.SessionManager

class SeedboxRepositoryTest {

    private val pairingApi = mockk<PairingApi>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private lateinit var repository: SeedboxRepository

    @Before
    fun setup() {
        repository = SeedboxRepository(
            pairingApi = { pairingApi },
            sessionManager = { sessionManager }
        )
    }

    @Test
    fun testGetStatusSuccess() = runBlocking {
        coEvery { sessionManager.fetchPairingSessionToken() } returns "valid_session_token"
        val mockStatus = SeedboxStatusResponse(
            isOnline = true,
            lastHeartbeatAt = 123456789L,
            freeDiskGb = 45.2f,
            activeSpeed = "15 MB/s",
            jobs = listOf(
                SeedboxJobItem(
                    jobId = "job_1",
                    animeQuery = "Bakemonogatari",
                    status = "DOWNLOADING",
                    progressPct = 50
                )
            )
        )
        coEvery { pairingApi.getSeedboxStatus("Bearer valid_session_token") } returns mockStatus

        val result = repository.getStatus()
        assertTrue(result.isSuccess)
        val status = result.getOrNull()
        assertNotNull(status)
        assertTrue(status!!.isOnline)
        assertEquals(1, status.jobs.size)
        assertEquals("Bakemonogatari", status.jobs[0].animeQuery)
    }

    @Test
    fun testEnqueueJobSuccess() = runBlocking {
        coEvery { sessionManager.fetchPairingSessionToken() } returns "valid_session_token"
        val req = SeedboxCreateJobRequest(
            animeQuery = "Frieren",
            episodesFilter = "1-12",
            preferPtbr = true,
            magnetUri = null
        )
        val resp = SeedboxCreateJobResponse(jobId = "job_frieren_123", status = "PENDING")
        coEvery { pairingApi.createSeedboxJob("Bearer valid_session_token", req) } returns resp

        val result = repository.enqueueJob("Frieren", "1-12", true, null)
        assertTrue(result.isSuccess)
        assertEquals("job_frieren_123", result.getOrNull())
    }

    @Test
    fun testUnauthenticatedThrowsFailure() = runBlocking {
        coEvery { sessionManager.fetchPairingSessionToken() } returns null
        val result = repository.getStatus()
        assertTrue(result.isFailure)
    }
}
