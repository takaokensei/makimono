package zechs.drive.stream.ui.signin

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PairingCreateResponse
import zechs.drive.stream.data.model.PairingStatusResponse
import zechs.drive.stream.data.remote.PairingApi
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.utils.SessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pairingApi = mockk<PairingApi>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val tokenProvider = mockk<TokenProvider>(relaxed = true)
    private lateinit var viewModel: PairingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = PairingViewModel(
            pairingApi = pairingApi,
            sessionManager = sessionManager,
            tokenProvider = tokenProvider,
            pairingApiBaseUrl = "https://pairing.example/"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun approvedResponsePersistsTheOneTimeSessionAndNavigatesHome() = runTest(dispatcher) {
        stubPairing()
        coEvery {
            pairingApi.getPairingStatus("pairing-id-123456789012", any())
        } returns PairingStatusResponse(state = "approved", sessionToken = "one-time-session-token")

        viewModel.effects.test {
            viewModel.startPairingIfNeeded()
            advanceUntilIdle()

            assertEquals(PairingEffect.OpenHome, awaitItem())
            coVerify(exactly = 1) { sessionManager.savePairingSessionToken("one-time-session-token") }
            coVerify(exactly = 1) { pairingApi.getPairingStatus("pairing-id-123456789012", any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun consumedResponseShowsRecoverableLostResponseMessage() = runTest(dispatcher) {
        stubPairing()
        coEvery {
            pairingApi.getPairingStatus("pairing-id-123456789012", any())
        } returns PairingStatusResponse(state = "consumed")

        viewModel.startPairingIfNeeded()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PairingUiState.Error)
        assertEquals(R.string.pairing_response_lost, (state as PairingUiState.Error).messageRes)
        coVerify(exactly = 0) { sessionManager.savePairingSessionToken(any()) }
        coVerify(exactly = 1) { pairingApi.getPairingStatus("pairing-id-123456789012", any()) }
    }

    @Test
    fun pairingResponseFromAnotherHostIsRejected() = runTest(dispatcher) {
        coEvery { pairingApi.createPairing(any()) } returns createResponse(qrUrl = "https://other.example/p/pairing-id")

        viewModel.startPairingIfNeeded()
        advanceUntilIdle()

        assertEquals(PairingUiState.Error(R.string.pairing_create_failed), viewModel.uiState.value)
        coVerify(exactly = 0) { pairingApi.getPairingStatus(any(), any()) }
    }

    @Test
    fun pairingResponseWithoutHttpsHostIsRejected() = runTest(dispatcher) {
        coEvery { pairingApi.createPairing(any()) } returns createResponse(qrUrl = "https:///p/pairing-id")

        viewModel.startPairingIfNeeded()
        advanceUntilIdle()

        assertEquals(PairingUiState.Error(R.string.pairing_create_failed), viewModel.uiState.value)
        coVerify(exactly = 0) { pairingApi.getPairingStatus(any(), any()) }
    }

    private fun stubPairing() {
        coEvery { pairingApi.createPairing(any()) } returns createResponse()
    }

    private fun createResponse(qrUrl: String = "https://pairing.example/p/pairing-id") = PairingCreateResponse(
        pairingId = "pairing-id-123456789012",
        deviceSecret = "d".repeat(43),
        confirmCode = "234567",
        qrUrl = qrUrl,
        expiresIn = 300,
        pollIntervalSeconds = 3
    )
}
