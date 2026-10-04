package zechs.drive.stream.ui.files

import java.util.Locale

/** Small, deterministic rules for the Files screen's empty and authentication states. */
internal object FilesStatePolicy {
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
