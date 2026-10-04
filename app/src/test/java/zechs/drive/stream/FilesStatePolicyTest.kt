package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
