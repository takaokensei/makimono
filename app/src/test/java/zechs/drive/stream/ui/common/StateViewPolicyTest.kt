package zechs.drive.stream.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.R

class StateViewPolicyTest {

    @Test
    fun emptyHomeState_hasExpectedStringsAndAction() {
        val config = StateView.configFor(StateView.StateType.EMPTY_HOME)
        assertNotNull(config)
        assertEquals(R.drawable.ic_folder_24, config!!.iconRes)
        assertEquals(R.string.state_empty_home_title, config.titleRes)
        assertEquals(R.string.state_empty_home_message, config.messageRes)
        assertEquals(R.string.state_empty_home_action, config.actionRes)
    }

    @Test
    fun emptyFolderState_hasNoAction() {
        val config = StateView.configFor(StateView.StateType.EMPTY_FOLDER)
        assertNotNull(config)
        assertEquals(R.drawable.ic_folder_24, config!!.iconRes)
        assertEquals(R.string.state_empty_folder_title, config.titleRes)
        assertEquals(R.string.state_empty_folder_message, config.messageRes)
        assertNull(config.actionRes)
    }

    @Test
    fun errorNetworkState_hasRetryAction() {
        val config = StateView.configFor(StateView.StateType.ERROR_NETWORK)
        assertNotNull(config)
        assertEquals(R.drawable.ic_error_72, config!!.iconRes)
        assertEquals(R.string.state_error_network_title, config.titleRes)
        assertEquals(R.string.state_error_network_message, config.messageRes)
        assertEquals(R.string.state_error_network_action, config.actionRes)
    }

    @Test
    fun errorAuthState_hasConnectAction() {
        val config = StateView.configFor(StateView.StateType.ERROR_AUTH)
        assertNotNull(config)
        assertEquals(R.drawable.ic_person_24, config!!.iconRes)
        assertEquals(R.string.state_error_auth_title, config.titleRes)
        assertEquals(R.string.state_error_auth_message, config.messageRes)
        assertEquals(R.string.state_error_auth_action, config.actionRes)
    }

    @Test
    fun accessDeniedStateOffersBackInsteadOfReconnect() {
        val config = StateView.configFor(StateView.StateType.ERROR_ACCESS_DENIED)
        assertNotNull(config)
        assertEquals(R.string.state_error_access_denied_title, config!!.titleRes)
        assertEquals(R.string.state_error_access_denied_message, config.messageRes)
        assertEquals(R.string.state_error_go_back_action, config.actionRes)
    }

    @Test
    fun rateLimitAndServerStatesExplainTransientFailures() {
        val rateLimit = StateView.configFor(StateView.StateType.ERROR_RATE_LIMITED)
        val server = StateView.configFor(StateView.StateType.ERROR_SERVER)
        assertNotNull(rateLimit)
        assertEquals(R.string.state_error_rate_limited_message, rateLimit!!.messageRes)
        assertNotNull(server)
        assertEquals(R.string.state_error_server_message, server!!.messageRes)
    }

    @Test
    fun playbackErrorState_hasSwitchEngineAction() {
        val config = StateView.configFor(StateView.StateType.ERROR_PLAYBACK)
        assertNotNull(config)
        assertEquals(R.drawable.ic_error_72, config!!.iconRes)
        assertEquals(R.string.state_error_playback_title, config.titleRes)
        assertEquals(R.string.state_error_playback_message, config.messageRes)
        assertEquals(R.string.state_error_playback_action, config.actionRes)
    }

    @Test
    fun sanitizeErrorMessage_removesTechnicalGarbage() {
        val rawException = "java.net.SocketTimeoutException: failed to connect after 10000ms HTTP 500"
        val sanitized = StateView.sanitizeErrorMessage(rawException)
        assertFalse(sanitized.contains("Exception", ignoreCase = true))
        assertFalse(sanitized.contains("HTTP", ignoreCase = true))
        assertFalse(sanitized.contains("500"))
        assertTrue(sanitized.isNotEmpty())
    }

    @Test
    fun sanitizeErrorMessage_doesNotEchoUnknownProviderDetails() {
        val rawError = "Drive provider failed for /users/caua/private/file-123?access_token=secret"

        val sanitized = StateView.sanitizeErrorMessage(rawError)

        assertFalse(sanitized.contains("private", ignoreCase = true))
        assertFalse(sanitized.contains("file-123"))
        assertFalse(sanitized.contains("access_token", ignoreCase = true))
        assertFalse(sanitized.contains("secret", ignoreCase = true))
        assertEquals("Não foi possível carregar os dados. Tente novamente.", sanitized)
    }
}
