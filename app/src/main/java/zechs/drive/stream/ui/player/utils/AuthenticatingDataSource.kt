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

        // Apply pre-warmed token; if cold (e.g. launching .ts or cached catalog item directly),
        // resolve from SessionManager/TokenProvider on ExoPlayer's background Loader thread.
        val token = tokenProvider.getCachedToken()?.takeIf { it.isNotEmpty() }
            ?: try {
                kotlinx.coroutines.runBlocking { tokenProvider.validToken() }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to resolve token in AuthenticatingDataSource.open", e)
                null
            }

        if (!token.isNullOrEmpty()) {
            wrappedDataSource.setRequestProperty("Authorization", "Bearer $token")
        }

        return try {
            wrappedDataSource.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            when (e.responseCode) {
                401 -> {
                    tokenProvider.invalidateToken()
                    AppLog.w(TAG, "HTTP 401 in AuthenticatingDataSource.open: refreshing token and retrying once")
                    val refreshed = try {
                        kotlinx.coroutines.runBlocking { tokenProvider.refresh() }
                    } catch (_: Exception) {
                        null
                    }
                    if (!refreshed.isNullOrEmpty()) {
                        wrappedDataSource.setRequestProperty("Authorization", "Bearer $refreshed")
                        return wrappedDataSource.open(dataSpec)
                    }
                }
                403 -> {
                    AppLog.w(TAG, "HTTP 403 Forbidden in AuthenticatingDataSource.open: retrying with refreshed token and acknowledgeAbuse=True")
                    val refreshed = try {
                        kotlinx.coroutines.runBlocking { tokenProvider.validToken() ?: tokenProvider.refresh() }
                    } catch (_: Exception) {
                        null
                    }
                    if (!refreshed.isNullOrEmpty()) {
                        wrappedDataSource.setRequestProperty("Authorization", "Bearer $refreshed")
                    }
                    val uriStr = dataSpec.uri.toString()
                    if (uriStr.contains("googleapis.com/drive") && !uriStr.contains("acknowledgeAbuse")) {
                        val fallbackUri = Uri.parse("$uriStr&acknowledgeAbuse=True")
                        val fallbackSpec = dataSpec.buildUpon().setUri(fallbackUri).build()
                        return wrappedDataSource.open(fallbackSpec)
                    }
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