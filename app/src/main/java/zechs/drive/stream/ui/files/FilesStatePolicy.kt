package zechs.drive.stream.ui.files

import java.util.Locale
import zechs.drive.stream.utils.state.ResourceErrorKind

/** Deterministic mapping from Drive failures and file-list state to recoverable UI states. */
internal object FilesStatePolicy {
    enum class ErrorPresentation { AUTHENTICATION, ACCESS_DENIED, RATE_LIMITED, NOT_FOUND, NETWORK, SERVER, UNKNOWN }

    fun errorPresentation(kind: ResourceErrorKind?, message: String?): ErrorPresentation = when (kind) {
        ResourceErrorKind.AUTHENTICATION -> ErrorPresentation.AUTHENTICATION
        ResourceErrorKind.ACCESS_DENIED -> ErrorPresentation.ACCESS_DENIED
        ResourceErrorKind.RATE_LIMITED -> ErrorPresentation.RATE_LIMITED
        ResourceErrorKind.NOT_FOUND -> ErrorPresentation.NOT_FOUND
        ResourceErrorKind.NETWORK -> ErrorPresentation.NETWORK
        ResourceErrorKind.SERVER -> ErrorPresentation.SERVER
        ResourceErrorKind.UNKNOWN, null -> when {
            isAuthenticationError(message) -> ErrorPresentation.AUTHENTICATION
            message.orEmpty().contains("404") -> ErrorPresentation.NOT_FOUND
            message.orEmpty().contains("429") -> ErrorPresentation.RATE_LIMITED
            message.orEmpty().contains("403") -> ErrorPresentation.ACCESS_DENIED
            message.orEmpty().contains("500") || message.orEmpty().contains("502") ||
                message.orEmpty().contains("503") || message.orEmpty().contains("504") -> ErrorPresentation.SERVER
            message.orEmpty().let { text ->
                val normalized = text.lowercase(Locale.ROOT)
                normalized.contains("conexão") || normalized.contains("network") ||
                    normalized.contains("timeout") || normalized.contains("offline")
            } -> ErrorPresentation.NETWORK
            else -> ErrorPresentation.UNKNOWN
        }
    }

    fun isAuthenticationError(message: String?): Boolean {
        val normalized = message.orEmpty().lowercase(Locale.ROOT)
        return normalized.contains("401") ||
            normalized.contains("unauthorized") ||
            normalized.contains("revogada") ||
            (normalized.contains("conecte sua conta") && normalized.contains("google drive")) ||
            (normalized.contains("sessão") && normalized.contains("google drive")) ||
            (normalized.contains("cliente do google drive") && normalized.contains("configurado"))
    }

    fun emptyMessage(hasUnfilteredItems: Boolean, query: String): EmptyFilesMessage = when {
        hasUnfilteredItems && query.isNotBlank() -> EmptyFilesMessage.NO_SEARCH_RESULTS
        else -> EmptyFilesMessage.NO_FILES
    }

    enum class EmptyFilesMessage { NO_FILES, NO_SEARCH_RESULTS }
}
