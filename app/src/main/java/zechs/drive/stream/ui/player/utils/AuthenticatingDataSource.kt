package zechs.drive.stream.ui.player.utils

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.common.util.Assertions
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.ui.player.PlayerActivity.Companion.TAG
import java.io.IOException

@OptIn(UnstableApi::class)
class AuthenticatingDataSource(
    private val wrappedDataSource: DefaultHttpDataSource,
    private val tokenProvider: TokenProvider
) : DataSource {

    class Factory(
        private val wrappedFactory: DefaultHttpDataSource.Factory,
        private val tokenProvider: TokenProvider
    ) : DataSource.Factory {
        override fun createDataSource(): AuthenticatingDataSource {
            return AuthenticatingDataSource(
                wrappedFactory.createDataSource(),
                tokenProvider
            )
        }
    }

    private var upstreamOpened = false

    override fun addTransferListener(transferListener: TransferListener) {
        Assertions.checkNotNull(transferListener)
        wrappedDataSource.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        upstreamOpened = true

        // Apply pre-warmed token non-blockingly before opening connection
        val cachedToken = tokenProvider.getCachedToken()
        if (!cachedToken.isNullOrEmpty()) {
            wrappedDataSource.setRequestProperty("Authorization", "Bearer $cachedToken")
        }

        return try {
            wrappedDataSource.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            when (e.responseCode) {
                401 -> {
                    // Token expired or invalid; invalidate cached token asynchronously without blocking
                    tokenProvider.invalidateToken()
                    AppLog.w(TAG, "HTTP 401 in AuthenticatingDataSource.open: token invalidated asynchronously")
                }
                403 -> {
                    AppLog.w(TAG, "HTTP 403 Forbidden in AuthenticatingDataSource.open: request denied")
                }
            }
            throw e
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        return wrappedDataSource.read(buffer, offset, readLength)
    }

    override fun getUri(): Uri? {
        return wrappedDataSource.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return wrappedDataSource.responseHeaders
    }

    @Throws(IOException::class)
    override fun close() {
        if (upstreamOpened) {
            upstreamOpened = false
            wrappedDataSource.close()
        }
    }
}