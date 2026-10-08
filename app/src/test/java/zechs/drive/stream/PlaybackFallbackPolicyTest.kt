package zechs.drive.stream

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.ui.player.PlaybackFallbackPolicy

class PlaybackFallbackPolicyTest {
    @Test
    fun unsupportedOrMalformedContainerOffersManualMpvFallback() {
        assertTrue(PlaybackFallbackPolicy.shouldOfferMpv(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertTrue(PlaybackFallbackPolicy.shouldOfferMpv(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }

    @Test
    fun arbitrarySourceAndNetworkCodesDoNotOfferMpvFallback() {
        assertFalse(PlaybackFallbackPolicy.shouldOfferMpv(-1))
        assertFalse(PlaybackFallbackPolicy.shouldOfferMpv(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
    }
}
