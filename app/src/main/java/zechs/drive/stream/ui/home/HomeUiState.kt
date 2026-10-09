package zechs.drive.stream.ui.home

import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.domain.common.AppError

sealed interface HomeUiState {
    data class Loading(
        val isFirstLoad: Boolean = true
    ) : HomeUiState

    data class Content(
        val featuredAnime: HomeViewModel.FeaturedAnime? = null,
        val continueWatching: List<WatchList> = emptyList(),
        val watchHistory: List<WatchList> = emptyList(),
        val starredFiles: List<DriveFile> = emptyList(),
        val libraryFiles: List<DriveFile> = emptyList(),
        val filteredAnimes: List<DriveFile> = emptyList(),
        val watchQueue: List<WatchQueueItem> = emptyList(),
        val lastFocusedShelfIndex: Int? = null,
        val lastFocusedItemIndex: Int? = null
    ) : HomeUiState

    data class Empty(
        val message: String = "Nenhum anime ou pasta encontrado na sua biblioteca."
    ) : HomeUiState

    data class Error(
        val error: AppError,
        val message: String = error.displayMessage
    ) : HomeUiState
}

sealed interface HomeEvent {
    data class SaveFocus(val shelfIndex: Int, val itemIndex: Int) : HomeEvent
    data class Search(val query: String) : HomeEvent
    object Refresh : HomeEvent
    data class RemoveWatchItem(val item: WatchList) : HomeEvent
    data class RestoreWatchItem(val item: WatchList) : HomeEvent
    data class MarkWatchItemFinished(val item: WatchList) : HomeEvent
    data class FocusFeatured(val folderId: String, val title: String) : HomeEvent
}
