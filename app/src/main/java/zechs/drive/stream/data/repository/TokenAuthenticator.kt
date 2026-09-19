package zechs.drive.stream.data.repository

import android.util.Log
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val tokenProvider: TokenProvider
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"

        // OkHttp calls authenticate() again if the request we return still
        // comes back as a challenge. ARCH-02 caps this to a single retry.
        private const val MAX_RETRIES = 1
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
            Log.w(TAG, "Giving up refreshing token after $MAX_RETRIES attempt")
            return null
        }

        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val currentCached = tokenProvider.getCachedToken()

        // If another thread already refreshed the token concurrently, reuse it without another network roundtrip
        if (!currentCached.isNullOrEmpty() && currentCached != requestToken) {
            Log.d(TAG, "Token already refreshed by concurrent thread, using new cached token")
            return response.request.newBuilder()
                .removeHeader("Authorization")
                .addHeader("Authorization", "Bearer $currentCached")
                .url(response.request.url.toString())
                .build()
        }

        // Authenticator callbacks are synchronous. Never block an OkHttp
        // dispatcher thread on a coroutine/network refresh here. DriveRepository
        // performs a bounded refresh-and-retry before making the request, while
        // streaming callers invalidate the provider and retry on the next open.
        Log.w(TAG, "No refreshed cached token available; refusing a blocking refresh")
        return null
    }

}
