package zechs.drive.stream.ui.home.controller

import android.content.Context
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.home.HomeEvent
import zechs.drive.stream.ui.home.HomeShelfScroll
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.ui.home.adapter.StarredShelfAdapter
import zechs.drive.stream.ui.home.adapter.WatchQueueShelfAdapter
import zechs.drive.stream.ui.home.filterFollowedLibraryFolders
import zechs.drive.stream.ui.home.filterStarredLibraryFolders
import zechs.drive.stream.ui.home.preserveFocusedShelfOrder
import zechs.drive.stream.ui.home.recentlyModifiedLibraryFolders
import zechs.drive.stream.utils.FocusMemory
import zechs.drive.stream.utils.ProfileManager

/**
 * Controlador das estantes horizontais da Home (Continuar Assistindo, Fila, Favoritos, Novos Episódios, Recentes).
 * Encapsula binding de adaptadores, escutas D-pad e observadores de dados per Seção 4.10 e 7.3.
 */
class ShelvesController(
    private val context: Context,
    private val binding: FragmentHomeBinding,
    private val viewModel: zechs.drive.stream.ui.home.HomeViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val profileManager: ProfileManager,
    private val followedFolderRepository: FollowedFolderRepository,
    private val focusCoordinator: FocusCoordinator,
    private val railController: RailController,
    private val heroController: HeroController,
    private val actionHandler: HomeActionHandler,
    private val onLastFocusedViewChanged: (View) -> Unit
) {

    val continueWatchingAdapter = ContinueWatchingAdapter(
        onClick = { watchItem -> actionHandler.playWatchItem(watchItem) }
    ).apply {
        onLongClickListener = { watchItem, anchor ->
            actionHandler.showContinueWatchingContextMenu(watchItem, anchor)
        }
    }

    val watchQueueAdapter = WatchQueueShelfAdapter { item ->
        actionHandler.playQueueItem(item)
    }.apply {
        onLongClickListener = { item -> actionHandler.showQueueItemContextMenu(item) }
    }

    val starredShelfAdapter = StarredShelfAdapter(
        onClick = { file -> actionHandler.handleOpenFolder(file) }
    )

    val recentlyAddedAdapter = StarredShelfAdapter(
        onClick = { file -> actionHandler.handleOpenFolder(file) },
        folderBadgeTextResId = R.string.home_recently_added_type_series,
        folderSubtitleTextResId = R.string.home_recently_added_subtitle
    )

    val followedEpisodesAdapter = StarredShelfAdapter(
        onClick = { file -> actionHandler.handleOpenFolder(file) },
        folderBadgeTextResId = R.string.home_followed_type,
        folderSubtitleTextResId = R.string.home_followed_subtitle
    )

    fun setup() {
        binding.rvContinueWatchingShelf.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvContinueWatchingShelf.adapter = continueWatchingAdapter
        continueWatchingAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusCoordinator.focusAdjacentHomeShelf(binding.rvContinueWatchingShelf, focused, keyCode)
        }
        continueWatchingAdapter.onDpadLeftListener = {
            if (railController.usesOverlaySidebar()) railController.expandSidebar() else railController.focusCurrentNavItem()
            true
        }
        continueWatchingAdapter.onFocusItemListener = { v ->
            onLastFocusedViewChanged(v)
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvShelfLabel, v)
            val position = binding.rvContinueWatchingShelf.getChildAdapterPosition(v)
            if (position != RecyclerView.NO_POSITION) {
                viewModel.onEvent(HomeEvent.SaveFocus(0, position))
                val list = continueWatchingAdapter.currentList
                zechs.drive.stream.utils.MediaImageLoader.selectNeighborPreloadUrls(
                    urls = list.map { it.thumbnailLink },
                    focusedIndex = position
                ).forEach { url ->
                    zechs.drive.stream.utils.MediaImageLoader.preloadCard(context, url)
                }
                list.getOrNull(position)?.let { item ->
                    FocusMemory.remember("home.continue_watching", position, item.videoId)
                    heroController.findLibraryFolderForTitle(item.name, viewModel.animeLibrary.value)?.let { folder ->
                        heroController.scheduleFeaturedPreview(folder, v) { railController.currentTab }
                    }
                }
            }
        }

        binding.rvWatchQueueShelf.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvWatchQueueShelf.adapter = watchQueueAdapter
        watchQueueAdapter.onDpadLeftListener = {
            if (railController.usesOverlaySidebar()) railController.expandSidebar() else railController.focusCurrentNavItem()
            true
        }
        watchQueueAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusCoordinator.focusAdjacentHomeShelf(binding.rvWatchQueueShelf, focused, keyCode)
        }
        watchQueueAdapter.onFocusItemListener = { v ->
            onLastFocusedViewChanged(v)
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvQueueShelfLabel, v)
            val position = binding.rvWatchQueueShelf.getChildAdapterPosition(v)
            if (position != RecyclerView.NO_POSITION) {
                viewModel.onEvent(HomeEvent.SaveFocus(1, position))
                watchQueueAdapter.currentList.getOrNull(position)?.let { item ->
                    FocusMemory.remember("home.watch_queue", position, item.fileId)
                    heroController.findLibraryFolderForTitle(item.name, viewModel.animeLibrary.value)?.let { folder ->
                        heroController.scheduleFeaturedPreview(folder, v) { railController.currentTab }
                    }
                }
            }
        }

        binding.rvStarredShelf?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvStarredShelf?.adapter = starredShelfAdapter
        starredShelfAdapter.onDpadLeftListener = {
            if (railController.usesOverlaySidebar()) railController.expandSidebar() else railController.focusCurrentNavItem()
            true
        }
        starredShelfAdapter.onDpadVerticalListener = { focused, keyCode ->
            binding.rvStarredShelf?.let { focusCoordinator.focusAdjacentHomeShelf(it, focused, keyCode) } ?: false
        }
        starredShelfAdapter.onFocusItemListener = { v ->
            onLastFocusedViewChanged(v)
            binding.rvStarredShelf?.let {
                HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvMyListShelfLabel, v)
            }
            val position = binding.rvStarredShelf?.getChildAdapterPosition(v) ?: RecyclerView.NO_POSITION
            if (position != RecyclerView.NO_POSITION) {
                viewModel.onEvent(HomeEvent.SaveFocus(3, position))
                starredShelfAdapter.currentList.getOrNull(position)?.let { folder ->
                    FocusMemory.remember("home.starred", position, folder.id)
                    heroController.scheduleFeaturedPreview(folder, v) { railController.currentTab }
                }
            }
        }

        binding.rvRecentlyAddedShelf.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentlyAddedShelf.adapter = recentlyAddedAdapter
        recentlyAddedAdapter.onDpadLeftListener = {
            if (railController.usesOverlaySidebar()) railController.expandSidebar() else railController.focusCurrentNavItem()
            true
        }
        recentlyAddedAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusCoordinator.focusAdjacentHomeShelf(binding.rvRecentlyAddedShelf, focused, keyCode)
        }
        recentlyAddedAdapter.onFocusItemListener = { v ->
            onLastFocusedViewChanged(v)
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvRecentlyAddedShelfLabel, v)
            val position = binding.rvRecentlyAddedShelf.getChildAdapterPosition(v)
            if (position != RecyclerView.NO_POSITION) {
                viewModel.onEvent(HomeEvent.SaveFocus(4, position))
                recentlyAddedAdapter.currentList.getOrNull(position)?.let { folder ->
                    FocusMemory.remember("home.recently_added", position, folder.id)
                    heroController.scheduleFeaturedPreview(folder, v) { railController.currentTab }
                }
            }
        }

        binding.rvNewEpisodesShelf.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvNewEpisodesShelf.adapter = followedEpisodesAdapter
        followedEpisodesAdapter.onDpadLeftListener = {
            if (railController.usesOverlaySidebar()) railController.expandSidebar() else railController.focusCurrentNavItem()
            true
        }
        followedEpisodesAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusCoordinator.focusAdjacentHomeShelf(binding.rvNewEpisodesShelf, focused, keyCode)
        }
        followedEpisodesAdapter.onFocusItemListener = { v ->
            onLastFocusedViewChanged(v)
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvNewEpisodesShelfLabel, v)
            val position = binding.rvNewEpisodesShelf.getChildAdapterPosition(v)
            if (position != RecyclerView.NO_POSITION) {
                viewModel.onEvent(HomeEvent.SaveFocus(2, position))
                followedEpisodesAdapter.currentList.getOrNull(position)?.let { folder ->
                    FocusMemory.remember("home.new_episodes", position, folder.id)
                    heroController.scheduleFeaturedPreview(folder, v) { railController.currentTab }
                }
            }
        }
    }

    fun observeShelves(onRefreshShelvesRequested: () -> Unit) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentWatches.collect { items ->
                        continueWatchingAdapter.submitList(items)
                        if (railController.currentTab == "Início") {
                            onRefreshShelvesRequested()
                            val hasRecent = items.isNotEmpty()
                            val hasFeatured = viewModel.featuredAnime.value != null
                            val hasQueue = viewModel.watchQueue.value.isNotEmpty()
                            binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasQueue && viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value) View.VISIBLE else View.GONE
                            binding.containerItemCount.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.watchQueue.collect { items ->
                        watchQueueAdapter.submitList(items)
                        if (railController.currentTab == "Início") {
                            onRefreshShelvesRequested()
                        }
                    }
                }

                launch {
                    combine(viewModel.starredFiles, viewModel.animeLibrary) { starred, library ->
                        filterStarredLibraryFolders(starred, library)
                    }.collect { files ->
                        starredShelfAdapter.submitList(files)
                        if (railController.currentTab == "Início") onRefreshShelvesRequested()
                    }
                }

                launch {
                    profileManager.activeProfileFlow.collectLatest { profile ->
                        combine(
                            viewModel.animeLibrary,
                            followedFolderRepository.observeFollowed(profile.id)
                        ) { library, followed ->
                            filterFollowedLibraryFolders(followed, library)
                        }.collect { files ->
                            followedEpisodesAdapter.submitList(files)
                            if (railController.currentTab == "Início") onRefreshShelvesRequested()
                        }
                    }
                }
            }
        }
    }
}
