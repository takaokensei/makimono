package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.state.ResourceErrorKind
import zechs.drive.stream.ui.files.FilesStatePolicy

class FilesStatePolicyTest {
    @Test
    fun sessionAndAuthorizationErrorsOfferDriveReconnect() {
        assertTrue(FilesStatePolicy.isAuthenticationError("Sessão do Google Drive ausente ou expirada"))
        assertTrue(FilesStatePolicy.isAuthenticationError("Sessão expirada ou não autorizada (401)"))
        assertTrue(FilesStatePolicy.isAuthenticationError("Conecte sua conta do Google Drive para continuar."))
        assertFalse(FilesStatePolicy.isAuthenticationError("Falha de conexão com a rede"))
        assertFalse(FilesStatePolicy.isAuthenticationError("Recurso não encontrado no Google Drive (404)"))
    }

    @Test
    fun typedDriveErrorsKeepRecoveryActionsDistinct() {
        assertEquals(
            FilesStatePolicy.ErrorPresentation.AUTHENTICATION,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.AUTHENTICATION, "HTTP 401")
        )
        assertEquals(
            FilesStatePolicy.ErrorPresentation.ACCESS_DENIED,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.ACCESS_DENIED, "HTTP 403")
        )
        assertEquals(
            FilesStatePolicy.ErrorPresentation.RATE_LIMITED,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.RATE_LIMITED, "HTTP 403")
        )
        assertEquals(
            FilesStatePolicy.ErrorPresentation.NOT_FOUND,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.NOT_FOUND, "HTTP 404")
        )
        assertEquals(
            FilesStatePolicy.ErrorPresentation.NETWORK,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.NETWORK, "Socket timeout")
        )
        assertEquals(
            FilesStatePolicy.ErrorPresentation.SERVER,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.SERVER, "HTTP 503")
        )
    }

    @Test
    fun legacyErrorMessagesStillMapToSpecificStates() {
        assertEquals(
            FilesStatePolicy.ErrorPresentation.ACCESS_DENIED,
            FilesStatePolicy.errorPresentation(null, "Acesso negado (403)")
        )
        assertEquals(
            FilesStatePolicy.ErrorPresentation.RATE_LIMITED,
            FilesStatePolicy.errorPresentation(ResourceErrorKind.UNKNOWN, "HTTP 429 rate limit")
        )
    }

    @Test
    fun emptyListDistinguishesEmptyFolderFromUnmatchedSearch() {
        assertEquals(
            FilesStatePolicy.EmptyFilesMessage.NO_FILES,
            FilesStatePolicy.emptyMessage(hasUnfilteredItems = false, query = "")
        )
        assertEquals(
            FilesStatePolicy.EmptyFilesMessage.NO_SEARCH_RESULTS,
            FilesStatePolicy.emptyMessage(hasUnfilteredItems = true, query = "xyz")
        )
        assertEquals(
            FilesStatePolicy.EmptyFilesMessage.NO_FILES,
            FilesStatePolicy.emptyMessage(hasUnfilteredItems = true, query = " ")
        )
    }
}
