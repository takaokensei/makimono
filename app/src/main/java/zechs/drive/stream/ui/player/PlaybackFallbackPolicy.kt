package zechs.drive.stream.ui.player

import androidx.media3.common.PlaybackException

/** Offers the alternate engine only for a source format mismatch, not transient I/O/auth failures. */
object PlaybackFallbackPolicy {
    fun shouldOfferMpv(errorCode: Int): Boolean = errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
}
