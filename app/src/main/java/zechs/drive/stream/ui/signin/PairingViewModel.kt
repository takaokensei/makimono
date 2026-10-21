package zechs.drive.stream.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PairingCreateRequest
import zechs.drive.stream.data.model.PairingCreateResponse
import zechs.drive.stream.data.remote.PairingApi
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.SessionManager
import javax.inject.Inject
import javax.inject.Named

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Loading : PairingUiState
    data class Ready(
        val qrUrl: String,
        val confirmCode: String,
        val remainingSeconds: Long,
        val statusMessageRes: Int
    ) : PairingUiState
    data class Error(val messageRes: Int) : PairingUiState
}

sealed interface PairingEffect {
    data object OpenHome : PairingEffect
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingApi: PairingApi,
    private val sessionManager: SessionManager,
    private val tokenProvider: TokenProvider,
    @Named("PairingApiBaseUrl") private val pairingApiBaseUrl: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PairingEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var pairing: PairingCreateResponse? = null
    private var pairingExpiresAtMs: Long = 0
    private var pollingJob: Job? = null
    private var screenActive = false
    private var currentDeviceName: String = "Makimono TV"

    fun startPairingIfNeeded(deviceName: String = "Makimono TV") {
        screenActive = true
        currentDeviceName = deviceName
        when (_uiState.value) {
            PairingUiState.Idle -> createPairing(deviceName)
            is PairingUiState.Ready -> startPolling()
            else -> Unit
        }
    }

    fun retryWithNewQr() {
        val previous = pairing
        pollingJob?.cancel()
        pollingJob = null
        pairing = null
        _uiState.value = PairingUiState.Idle
        if (previous != null) {
            viewModelScope.launch {
                runCatching { pairingApi.cancelPairing(previous.pairingId, previous.deviceSecret) }
            }
        }
        createPairing(currentDeviceName)
    }

    fun stopPolling() {
        screenActive = false
        pollingJob?.cancel()
        pollingJob = null
    }

    fun onQrRenderFailed() {
        _uiState.value = PairingUiState.Error(R.string.pairing_create_failed)
    }

    private fun createPairing(deviceName: String = currentDeviceName) {
        if (_uiState.value == PairingUiState.Loading) return
        _uiState.value = PairingUiState.Loading
        viewModelScope.launch {
            try {
                tokenProvider.flushPendingSessionActions()
                if (!pairingApiBaseUrl.startsWith("https://", ignoreCase = true)) {
                    _uiState.value = PairingUiState.Error(R.string.pairing_service_not_configured)
                    return@launch
                }
                val response = pairingApi.createPairing(
                    PairingCreateRequest(
                        deviceName = deviceName,
                        clientVersion = BuildConfig.VERSION_NAME
                    )
                )
                val apiUri = runCatching { URI(pairingApiBaseUrl) }.getOrNull()
                val qrUri = runCatching { URI(response.qrUrl) }.getOrNull()
                val apiHost = apiUri?.host
                val qrHost = qrUri?.host
                if (response.pairingId.isBlank() || response.deviceSecret.isBlank() ||
                    apiUri == null || qrUri == null ||
                    !apiUri.scheme.equals("https", ignoreCase = true) || apiHost.isNullOrBlank() ||
                    !qrUri.scheme.equals("https", ignoreCase = true) || qrHost.isNullOrBlank() ||
                    apiUri.userInfo != null || apiUri.rawQuery != null || apiUri.rawFragment != null || apiUri.path !in listOf("", "/") ||
                    qrUri.userInfo != null || qrUri.rawQuery != null || qrUri.rawFragment != null || !qrUri.path.matches(Regex("/p/[A-Za-z0-9_-]+")) ||
                    !qrHost.equals(apiHost, ignoreCase = true) || effectivePort(qrUri) != effectivePort(apiUri) ||
                    !response.confirmCode.matches(Regex("[2-9]{6}")) || response.expiresIn <= 0
                ) {
                    _uiState.value = PairingUiState.Error(R.string.pairing_create_failed)
                    return@launch
                }
                pairing = response
                pairingExpiresAtMs = System.currentTimeMillis() + response.expiresIn * 1000
                publishReady(R.string.pairing_waiting_for_phone)
                if (screenActive) startPolling()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AppLog.w(TAG, "Pairing request failed (${error.javaClass.simpleName})")
                _uiState.value = PairingUiState.Error(
                    if (error is IOException || error is HttpException) R.string.pairing_connection_failed
                    else R.string.pairing_create_failed
                )
            }
        }
    }

    private fun publishReady(statusMessageRes: Int) {
        val current = pairing ?: return
        val remaining = ((pairingExpiresAtMs - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
        _uiState.value = PairingUiState.Ready(current.qrUrl, current.confirmCode, remaining, statusMessageRes)
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        val current = pairing ?: return
        pollingJob = viewModelScope.launch {
            var nextPollAt = System.currentTimeMillis()
            while (screenActive && System.currentTimeMillis() < pairingExpiresAtMs) {
                val now = System.currentTimeMillis()
                publishReady(if (now >= nextPollAt) R.string.pairing_waiting_for_phone else R.string.pairing_waiting_for_approval)
                if (now >= nextPollAt) {
                    try {
                        val status = pairingApi.getPairingStatus(current.pairingId, current.deviceSecret)
                        status.expiresIn?.takeIf { it > 0 }?.let { expiresIn ->
                            pairingExpiresAtMs = System.currentTimeMillis() + expiresIn * 1000
                        }
                        when (status.state.lowercase()) {
                            "pending" -> publishReady(R.string.pairing_waiting_for_phone)
                            "confirmed" -> publishReady(R.string.pairing_waiting_for_approval)
                            "approved" -> {
                                val sessionToken = status.sessionToken
                                if (sessionToken.isNullOrBlank()) {
                                    _uiState.value = PairingUiState.Error(R.string.pairing_response_lost)
                                    return@launch
                                }
                                sessionManager.savePairingSessionToken(sessionToken)
                                pairing = null
                                _effects.emit(PairingEffect.OpenHome)
                                return@launch
                            }
                            "consumed" -> {
                                _uiState.value = PairingUiState.Error(R.string.pairing_response_lost)
                                return@launch
                            }
                            "expired" -> {
                                _uiState.value = PairingUiState.Error(R.string.pairing_expired)
                                return@launch
                            }
                            "denied", "cancelled", "failed" -> {
                                _uiState.value = PairingUiState.Error(R.string.pairing_not_approved)
                                return@launch
                            }
                        }
                        nextPollAt = System.currentTimeMillis() + current.pollIntervalSeconds.coerceAtLeast(1) * 1000
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (error is HttpException && error.code() == 401) {
                            _uiState.value = PairingUiState.Error(R.string.pairing_device_rejected)
                            return@launch
                        }
                        AppLog.w(TAG, "Pairing status check failed (${error.javaClass.simpleName})")
                        publishReady(R.string.pairing_retrying_connection)
                        nextPollAt = System.currentTimeMillis() + current.pollIntervalSeconds.coerceAtLeast(1) * 1000
                    }
                }
                delay(1_000)
            }
            _uiState.value = PairingUiState.Error(R.string.pairing_expired)
        }
    }

    companion object {
        private const val TAG = "PairingViewModel"

        private fun effectivePort(uri: URI): Int = when {
            uri.port >= 0 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            uri.scheme.equals("http", ignoreCase = true) -> 80
            else -> -1
        }
    }
}
