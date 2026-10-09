package zechs.drive.stream.ui.files

import zechs.drive.stream.utils.AppLog
import android.widget.AbsListView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel

/**
 * Gerenciador de paginação (ScrollListener e D-pad na borda inferior) do FilesFragment.
 * Conforme Seção 4.10 do Plano v2.
 */
class FilesPaginationHandler(
    private val recyclerView: RecyclerView,
    private val filesAdapter: FilesAdapter,
    private val viewModel: FilesViewModel,
    private val folderQuery: String?,
    private val onPageRequested: (String?) -> Unit
) {

    var isLoading: Boolean = false
    var isScrolling: Boolean = false
        private set

    val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val visibleItemCount = layoutManager.childCount
            val totalItemCount = layoutManager.itemCount

            val isAtLastItem = firstVisibleItemPosition + visibleItemCount >= totalItemCount
            val isLastPage = viewModel.isLastPage

            if (isAtLastItem && !isLoading && !isLastPage && !viewModel.hasFailed) {
                AppLog.d("FilesPagination", "Paginating on scroll...")
                onPageRequested(folderQuery)
                isScrolling = false
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                isScrolling = true
            }
        }
    }

    fun handleDpadFocusNearEnd(position: Int) {
        if (position >= filesAdapter.itemCount - 2 &&
            !isLoading &&
            !viewModel.isLastPage &&
            !viewModel.hasFailed &&
            filesAdapter.currentList.getOrNull(position) is FilesDataModel.File
        ) {
            AppLog.d("FilesPagination", "Paginating via D-pad focus near end (pos=$position)")
            onPageRequested(folderQuery)
        }
    }
}
