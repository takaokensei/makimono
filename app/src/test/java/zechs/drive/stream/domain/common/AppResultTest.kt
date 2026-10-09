package zechs.drive.stream.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun successReturnsDataAndExecutesOnSuccess() {
        val result = AppResult.Success("test_data")

        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertEquals("test_data", result.getOrNull())

        var executed = false
        result.onSuccess {
            assertEquals("test_data", it)
            executed = true
        }
        assertTrue(executed)

        val mapped = result.map { it.length }
        assertTrue(mapped is AppResult.Success)
        assertEquals(9, (mapped as AppResult.Success).data)
    }

    @Test
    fun errorReturnsErrorAndExecutesOnError() {
        val error = AppError.Network("Erro de rede")
        val result: AppResult<String> = AppResult.Error(error)

        assertFalse(result.isSuccess)
        assertTrue(result.isError)
        assertNull(result.getOrNull())

        var executed = false
        result.onError {
            assertEquals("Erro de rede", it.userMessage)
            executed = true
        }
        assertTrue(executed)

        val mapped = result.map { it.length }
        assertTrue(mapped is AppResult.Error)
    }

    @Test
    fun appErrorTypesPreserveMessagesAndCauses() {
        val cause = RuntimeException("Root cause")
        val network = AppError.Network("Network fail", cause)
        val auth = AppError.Authentication("Auth fail", cause)
        val notFound = AppError.NotFound("Not found", cause)
        val codec = AppError.UnsupportedCodec("Codec fail", cause)
        val unknown = AppError.Unknown("Unknown fail", cause)

        assertEquals("Network fail", network.userMessage)
        assertEquals(cause, network.technicalCause)
        assertEquals("Auth fail", auth.userMessage)
        assertEquals("Not found", notFound.userMessage)
        assertEquals("Codec fail", codec.userMessage)
        assertEquals("Unknown fail", unknown.userMessage)
    }
}
