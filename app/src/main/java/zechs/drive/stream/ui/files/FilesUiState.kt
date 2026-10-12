package zechs.drive.stream.ui.files

import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.utils.SeasonGroup

/**
 * Estado unidirecional selado da tela de arquivos e pastas (Pastas/Arquivos).
 */
sealed interface FilesUiState {
    data object Loading : FilesUiState

    data class Content(
        val files: List<FilesDataModel>,
        val fileCount: Int,
        val seasonGroups: List<SeasonGroup> = emptyList(),
        val selectedSeason: SeasonGroup? = null,
        val isFirstLoad: Boolean = false
    ) : FilesUiState

    data class Empty(
        val reason: String = "Esta pasta está vazia."
    ) : FilesUiState

    data class Error(
        val error: AppError,
        val partialFiles: List<FilesDataModel> = emptyList(),
        val uiError: zechs.drive.stream.ui.common.UiError = zechs.drive.stream.ui.common.UiError.fromAppError(error)
    ) : FilesUiState
}

/**
 * Eventos da tela de arquivos e pastas enviados ao ViewModel.
 */
sealed interface FilesEvent {
    data class QueryFiles(val query: String?) : FilesEvent
    data class SelectSeason(val season: SeasonGroup) : FilesEvent
    data class SaveFocus(val position: Int) : FilesEvent
    data object Retry : FilesEvent
}
