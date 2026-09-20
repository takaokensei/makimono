package zechs.drive.stream.data.repository

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

interface TokenProvider {
    suspend fun validToken(): String?
    suspend fun refresh(): String?
    fun getCachedToken(): String?
    fun invalidateToken()
}

@Singleton
class DefaultTokenProvider @Inject constructor(
    private val sessionManager: SessionManager,
    private val driveRepository: dagger.Lazy<DriveRepository>
) : TokenProvider {

    companion object {
        private const val TAG = "DefaultTokenProvider"
        // 60-second safety window before actual expiration
        private const val EXPIRY_SAFETY_WINDOW_SECONDS = 60L
    }

    private data class CachedToken(val token: String, val expiryEpochSeconds: Long)

    private val cachedTokenRef = AtomicReference<CachedToken?>(null)
    private val refreshMutex = Mutex()

    private fun isExpired(cached: CachedToken): Boolean {
        val now = System.currentTimeMillis() / 1000
        return now >= (cached.expiryEpochSeconds - EXPIRY_SAFETY_WINDOW_SECONDS)
    }

    override fun getCachedToken(): String? {
        val cached = cachedTokenRef.get() ?: return null
        return if (!isExpired(cached)) cached.token else null
    }

    override fun invalidateToken() {
        cachedTokenRef.set(null)
    }

    override suspend fun validToken(): String? {
        val current = cachedTokenRef.get()
        if (current != null && !isExpired(current) && current.token.isNotEmpty()) {
            return current.token
        }

        return refreshMutex.withLock {
            val existing = cachedTokenRef.get()
            if (existing != null && !isExpired(existing) && existing.token.isNotEmpty()) {
                return@withLock existing.token
            }

            val client = sessionManager.fetchClient()
            if (client == null) {
                Log.w(TAG, "No DriveClient configured, cannot retrieve token")
                return@withLock null
            }

            val response = driveRepository.get().fetchAccessToken(client, forceRefresh = false)
            if (response is Resource.Success && response.data != null) {
                val token = response.data.accessToken
                val expiry = response.data.expiresIn
                cachedTokenRef.set(CachedToken(token, expiry))
                token
            } else {
                Log.w(TAG, "Failed to retrieve valid token: ${response.message}")
                null
            }
        }
    }

    override suspend fun refresh(): String? {
        return refreshMutex.withLock {
            val client = sessionManager.fetchClient()
            if (client == null) {
                Log.w(TAG, "No DriveClient configured, cannot refresh token")
                return@withLock null
            }

            Log.d(TAG, "Refreshing access token under lock")
            val response = driveRepository.get().fetchAccessToken(client, forceRefresh = true)
            if (response is Resource.Success && response.data != null) {
                val token = response.data.accessToken
                val expiry = response.data.expiresIn
                cachedTokenRef.set(CachedToken(token, expiry))
                token
            } else {
                Log.w(TAG, "Failed to force refresh token: ${response.message}")
                null
            }
        }
    }
}
