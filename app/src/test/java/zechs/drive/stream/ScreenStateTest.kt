package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.state.ScreenState

class ScreenStateTest {

    @Test
    fun screenState_contentCarriesDataAndRefreshFlag() {
        val content = ScreenState.Content(data = listOf("item1", "item2"), isRefreshing = true)
        assertEquals(2, content.data.size)
        assertTrue(content.isRefreshing)
    }

    @Test
    fun screenState_emptyCarriesMessage() {
        val empty = ScreenState.Empty("Nenhum item encontrado")
        assertEquals("Nenhum item encontrado", empty.message)
    }

    @Test
    fun screenState_offlineCarriesCachedData() {
        val offline = ScreenState.Offline(cachedData = "cached_anime", message = "Sem conexão")
        assertEquals("cached_anime", offline.cachedData)
        assertEquals("Sem conexão", offline.message)
    }

    @Test
    fun screenState_errorIdentifiesAuthAndRateLimit() {
        val authError = ScreenState.Error(
            message = "Sessão expirada",
            statusCode = 401,
            isAuthError = true
        )
        assertTrue(authError.isSessionExpired)
        assertFalse(authError.isRateLimit)

        val rateLimitError = ScreenState.Error(
            message = "Muitas requisições",
            statusCode = 429,
            isRateLimit = true
        )
        assertTrue(rateLimitError.isRateLimit)
        assertFalse(rateLimitError.isSessionExpired)
    }
}
