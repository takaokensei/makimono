package zechs.drive.stream.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import zechs.drive.stream.data.remote.PairingApi
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.SessionManager
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

interface TokenProvider {
    suspend fun validToken(): String?
    suspend fun refresh(): String?
    fun getCachedToken(): String?
    fun invalidateToken()
    suspend fun logoutCurrentDevice()
    suspend fun disconnectAllDevices()
    suspend fun flushPendingSessionActions()
}

@Singleton
class DefaultTokenProvider @Inject constructor(
    private val sessionManager: SessionManager,
    private val pairingApi: PairingApi
) : TokenProvider {

    companion object {
        private const val TAG = "DefaultTokenProvider"
        private const val EXPIRY_SAFETY_WINDOW_SECONDS = 120L
    }

    private data class CachedToken(val token: String, val expiryEpochSeconds: Long)

    private val cachedTokenRef = AtomicReference<CachedToken?>(null)
    private val refreshMutex = Mutex()
    private val pendingActionsMutex = Mutex()

    private fun isExpired(cached: CachedToken): Boolean =
        System.currentTimeMillis() / 1000 >= cached.expiryEpochSeconds - EXPIRY_SAFETY_WINDOW_SECONDS

    override fun getCachedToken(): String? {
        val cached = cachedTokenRef.get() ?: return null
        return cached.token.takeIf { !isExpired(cached) }
    }

    override fun invalidateToken() {
        cachedTokenRef.set(null)
    }

    override suspend fun validToken(): String? {
        getCachedToken()?.let { return it }
        return refreshMutex.withLock {
            getCachedToken()?.let { return@withLock it }
            fetchShortLivedDriveToken()
        }
    }

    override suspend fun refresh(): String? = refreshMutex.withLock {
        cachedTokenRef.set(null)
        fetchShortLivedDriveToken()
    }

    private suspend fun fetchShortLivedDriveToken(): String? {
        flushPendingSessionActions()
        val sessionToken = sessionManager.fetchPairingSessionToken() ?: return null
        return try {
            val response = pairingApi.getDriveToken("Bearer $sessionToken")
            if (response.accessToken.isBlank() || response.expiresIn <= 0) return null
            val expiry = System.currentTimeMillis() / 1000 + response.expiresIn
            cachedTokenRef.set(CachedToken(response.accessToken, expiry))
            response.accessToken
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is HttpException && error.code() in listOf(401, 409)) {
                sessionManager.resetDataStore()
                cachedTokenRef.set(null)
            }
            AppLog.w(TAG, "Could not obtain the short-lived Drive token (${error.javaClass.simpleName})")
            null
        }
    }

    override suspend fun logoutCurrentDevice() {
        val sessionToken = sessionManager.fetchPairingSessionToken()
        invalidateToken()
        if (!sessionToken.isNullOrBlank()) {
            performOrQueue("logout", sessionToken)
        }
        sessionManager.resetDataStore()
    }

    override suspend fun disconnectAllDevices() {
        val sessionToken = sessionManager.fetchPairingSessionToken()
        invalidateToken()
        if (!sessionToken.isNullOrBlank()) {
            performOrQueue("disconnect_all", sessionToken)
        }
        sessionManager.resetDataStore()
    }

    private suspend fun performOrQueue(action: String, token: String) {
        try {
            performSessionAction(action, token)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            sessionManager.addPendingSessionAction(action, token)
            AppLog.w(TAG, "Session action queued until the network is available (${error.javaClass.simpleName})")
        }
    }

    private suspend fun performSessionAction(action: String, token: String) {
        val response = when (action) {
            "disconnect_all" -> pairingApi.disconnectAll("Bearer $token")
            else -> pairingApi.logout("Bearer $token")
        }
        if (!response.isSuccessful && response.code() != 401) {
            throw IllegalStateException("Session endpoint returned HTTP ${response.code()}")
        }
    }

    override suspend fun flushPendingSessionActions() = pendingActionsMutex.withLock {
        val actions = sessionManager.getPendingSessionActions()
        for (action in actions) {
            try {
                performSessionAction(action.action, action.token)
                sessionManager.removePendingSessionAction(action)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                AppLog.w(TAG, "A pending session action is still waiting for the network (${error.javaClass.simpleName})")
                break
            }
        }
    }
}
