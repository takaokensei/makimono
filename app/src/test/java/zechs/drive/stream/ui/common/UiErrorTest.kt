package zechs.drive.stream.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.R
import zechs.drive.stream.domain.common.AppError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UiErrorTest {

    @Test
    fun fromThrowable_withUnknownHostException_returnsNetwork() {
        val error = UiError.fromThrowable(UnknownHostException("Unable to resolve host"))
        assertTrue(error is UiError.Network)
        assertEquals(R.string.state_error_network_message, error.messageRes)
        assertEquals(UiErrorAction.RETRY, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withSocketTimeoutException_returnsNetwork() {
        val error = UiError.fromThrowable(SocketTimeoutException("Read timed out"))
        assertTrue(error is UiError.Network)
        assertEquals(UiErrorAction.RETRY, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withConnectException_returnsNetwork() {
        val error = UiError.fromThrowable(ConnectException("Connection refused"))
        assertTrue(error is UiError.Network)
        assertEquals(UiErrorAction.RETRY, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withAuthMessage_returnsAuth() {
        val error = UiError.fromThrowable(IllegalStateException("OAuth token expired (401)"))
        assertTrue(error is UiError.Auth)
        assertEquals(R.string.state_error_auth_message, error.messageRes)
        assertEquals(UiErrorAction.REAUTHENTICATE, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withNotFoundMessage_returnsNotFound() {
        val error = UiError.fromThrowable(IOException("Resource not found 404"))
        assertTrue(error is UiError.NotFound)
        assertEquals(R.string.state_error_not_found_message, error.messageRes)
        assertEquals(UiErrorAction.GO_BACK, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withPlaybackCodecMessage_returnsPlayback() {
        val error = UiError.fromThrowable(IllegalStateException("Unsupported codec in player"))
        assertTrue(error is UiError.Playback)
        assertEquals(R.string.state_error_playback_message, error.messageRes)
        assertEquals(UiErrorAction.SWITCH_ENGINE, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withGenericMessage_returnsUnknown() {
        val error = UiError.fromThrowable(RuntimeException("Unexpected internal fault"))
        assertTrue(error is UiError.Unknown)
        assertEquals(R.string.state_error_generic_message, error.messageRes)
        assertEquals(UiErrorAction.RETRY, error.suggestedAction)
    }

    @Test
    fun fromThrowable_withNull_returnsUnknown() {
        val error = UiError.fromThrowable(null)
        assertTrue(error is UiError.Unknown)
        assertEquals(UiErrorAction.RETRY, error.suggestedAction)
    }

    @Test
    fun fromAppError_mapsAppErrorsToCorrespondingUiErrors() {
        assertTrue(UiError.fromAppError(AppError.Network()) is UiError.Network)
        assertTrue(UiError.fromAppError(AppError.Authentication()) is UiError.Auth)
        assertTrue(UiError.fromAppError(AppError.NotFound()) is UiError.NotFound)
        assertTrue(UiError.fromAppError(AppError.UnsupportedCodec()) is UiError.Playback)
        assertTrue(UiError.fromAppError(AppError.AccessDenied()) is UiError.NotFound)
        assertTrue(UiError.fromAppError(AppError.RateLimited()) is UiError.Network)
        assertTrue(UiError.fromAppError(AppError.Server()) is UiError.Unknown)
        assertTrue(UiError.fromAppError(AppError.Unknown()) is UiError.Unknown)
    }
}
