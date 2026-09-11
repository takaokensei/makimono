package zechs.drive.stream.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val driveRepository: Lazy<DriveRepository>,
    private val sessionManager: Lazy<SessionManager>
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"

        // OkHttp calls authenticate() again if the request we return still
        // comes back as a challenge. Without a cap, a persistently invalid
        // refresh token (or a server that keeps returning 401) makes this
        // recurse/retry forever. Give up after a couple of attempts.
        private const val MAX_RETRIES = 2
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    override fun authenticate(
        route: Route?, response: Response
    ): Request? {

        if (responseCount(response) > MAX_RETRIES) {
            Log.w(TAG, "Giving up refreshing token after $MAX_RETRIES attempts")
            return null
        }

        val client = runBlocking {
            sessionManager.get().fetchClient()
        } ?: return null

        val tokenResponse = runBlocking {
            driveRepository.get().fetchAccessToken(client, forceRefresh = true)
        }

        if (tokenResponse is Resource.Success) {
            tokenResponse.data?.let { token ->
                Log.d(TAG, "Received new access token (len=${token.accessToken.length})")
                return response.request.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer ${token.accessToken}")
                    .url(response.request.url.toString())
                    .build()
            }
        } else {
            Log.d(TAG, tokenResponse.message ?: "Unable to refresh access token")
        }

        return null
    }

}