package zechs.drive.stream.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.common.StateView
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.home.controller.CatalogPresentationController
import zechs.drive.stream.ui.home.controller.FocusCoordinator
import zechs.drive.stream.ui.home.controller.HeroController
import zechs.drive.stream.ui.home.controller.HomeActionHandler
import zechs.drive.stream.ui.home.controller.RailController
import zechs.drive.stream.ui.home.controller.ShelvesController
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.FocusMemory
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.state.Resource
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    companion object {
        const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel by activityViewModels<HomeViewModel>()
    private val mainViewModel by activityViewModels<zechs.drive.stream.ui.main.MainViewModel>()

    @Inject
    lateinit var profileManager: ProfileManager

    @Inject
    lateinit var profileArtCatalog: ProfileArtCatalog

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var followedFolderRepository: FollowedFolderRepository

    private var lastFocusedAnimeView: View? = null
    private var lastFocusedItemId: String? = null
    private var lastProfileConfiguration: String? = null
    private var reduceMotionEnabled = false
    private var pendingMpvStartPosition: Long? = null

    private lateinit var focusCoordinator: FocusCoordinator
    private lateinit var heroController: HeroController
    private lateinit var railController: RailController
    private lateinit var actionHandler: HomeActionHandler
    private lateinit var shelvesController: ShelvesController
    private lateinit var catalogController: CatalogPresentationController

    private val voiceSearchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                binding.etSearchAnime.setText(spokenText)
                binding.etSearchAnime.setSelection(spokenText.length)
            }
        }
    }

    private val animeAdapter by lazy {
        FilesAdapter(
            onClickListener = { file -> actionHandler.handleOpenFolder(file) },
            onStarClickListener = { file, star -> viewModel.starFile(file, star) },
            onLongClickListener = { file -> actionHandler.showAnimeContextMenu(file) },
            onPlayClickListener = { file -> actionHandler.handleQuickPlay(file) },
            compactCatalog = true
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        focusCoordinator = FocusCoordinator(binding)
        heroController = HeroController(
            binding = binding,
            viewModel = viewModel,
            navController = findNavController(),
            profileManager = profileManager,
            profileArtCatalog = profileArtCatalog,
            lifecycleOwner = viewLifecycleOwner,
            animateFocus = { v, focus, scale, elev -> catalogController.animateFocus(v, focus, scale, elev) },
            onQuickPlay = { actionHandler.handleQuickPlay(it) },
            onRailFocusRequested = {
                if (railController.usesOverlaySidebar()) railController.expandSidebar()
                else railController.focusCurrentNavItem()
            }
        )
        railController = RailController(
            binding = binding,
            viewModel = viewModel,
            navController = findNavController(),
            animateFocus = { v, focus, scale, elev -> catalogController.animateFocus(v, focus, scale, elev) },
            onContentFocusRequested = {
                if (DeviceUi.isTenFootExperience(requireContext())) {
                    railController.collapseTvRail()
                }
                focusCoordinator.focusContent(
                    continueWatchingCount = shelvesController.continueWatchingAdapter.itemCount,
                    watchQueueCount = shelvesController.watchQueueAdapter.itemCount,
                    followedCount = shelvesController.followedEpisodesAdapter.itemCount,
                    starredCount = shelvesController.starredShelfAdapter.itemCount,
                    recentCount = shelvesController.recentlyAddedAdapter.itemCount
                )
            },
            onCancelFeaturedPreview = { },
            onRefreshShelvesRequested = { refreshInicioShelfUi() }
        )
        actionHandler = HomeActionHandler(
            context = requireContext(),
            rootView = binding.root,
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            navController = findNavController(),
            onFindLibraryFolder = { title -> heroController.findLibraryFolderForTitle(title, viewModel.animeLibrary.value) },
            onMpvStartPending = { pendingMpvStartPosition = it },
            getCatalogVideoFiles = {
                animeAdapter.currentList.mapNotNull { (it as? FilesDataModel.File)?.driveFile }.filter { it.isVideoFile }
            }
        )
        shelvesController = ShelvesController(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            lifecycleOwner = viewLifecycleOwner,
            profileManager = profileManager,
            followedFolderRepository = followedFolderRepository,
            focusCoordinator = focusCoordinator,
            railController = railController,
            heroController = heroController,
            actionHandler = actionHandler,
            onLastFocusedViewChanged = { lastFocusedAnimeView = it }
        )
        catalogController = CatalogPresentationController(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            animeAdapter = animeAdapter,
            railController = railController,
            voiceSearchLauncher = voiceSearchLauncher,
            reduceMotionProvider = { reduceMotionEnabled },
            onGridModeChanged = { setupAnimeGrid() }
        )

        refreshMotionPreference()
        configureHomePosterShelfMeasurements()

        setupAnimeGrid()
        catalogController.setupHeaderSearch()
        catalogController.setupVoiceSearch()
        catalogController.setupViewToggle { setupAnimeGrid() }
        railController.setup()
        setupBackPressedHandling()
        heroController.setup(railController.usesOverlaySidebar())

        TvAccessibilityLayout.adaptHomeHero(
            requireContext(),
            binding.featuredHeroContainer,
            binding.btnFeaturedPlay,
            binding.btnFeaturedInfo
        )

        shelvesController.setup()
        focusCoordinator.setupTenFootFocusChain(requireContext())
        shelvesController.observeShelves { refreshInicioShelfUi() }

        binding.tvShelfViewHistory?.setOnClickListener { actionHandler.showWatchHistoryDialog() }
        binding.tvShelfViewQueue?.setOnClickListener { actionHandler.showWatchQueueDialog() }
        binding.btnExploreAnimes?.setOnClickListener { railController.selectTab("Animes") }

        observeAnimeLibrary()
        heroController.observeFeaturedAnime(
            currentTabProvider = { railController.currentTab },
            reduceMotionEnabled = reduceMotionEnabled,
            isCollapsible = railController.isCollapsibleRail()
        )
        observeProfileConfiguration()
        observeLogOutState()
        observeMpv()

        railController.selectTab("Início")

        if (viewModel.animeLibrary.value.isEmpty()) {
            viewModel.loadAnimeLibrary()
        }
        viewModel.getStarredFiles()
        viewModel.getRecentWatches()
        viewModel.getWatchHistory()
    }

    private fun refreshMotionPreference() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profileId = profileManager.getActiveProfile().id
            reduceMotionEnabled = runCatching {
                MotionPolicy.shouldReduceMotion(
                    appSettings.fetchMotionPreference(profileId),
                    MotionPolicy.areSystemAnimationsEnabled()
                )
            }.getOrDefault(false)
        }
    }

    private fun observeProfileConfiguration() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collectLatest { profile ->
                    val profileConfig = "${profile.id}:${profile.avatarResName}:${profile.avatarUrl}"
                    if (lastProfileConfiguration != null && lastProfileConfiguration != profileConfig) {
                        viewModel.loadAnimeLibrary(forceRefresh = true)
                        viewModel.getStarredFiles()
                        viewModel.getRecentWatches()
                    }
                    lastProfileConfiguration = profileConfig
                }
            }
        }
    }

    private fun getResponsiveSpanCount(): Int {
        val orientation = resources.configuration.orientation
        val screenWidthDp = resources.configuration.screenWidthDp
        return when {
            orientation == Configuration.ORIENTATION_LANDSCAPE -> (screenWidthDp / 180).coerceIn(3, 7)
            screenWidthDp >= 600 -> (screenWidthDp / 160).coerceIn(3, 5)
            else -> 2
        }
    }

    private fun setupAnimeGrid() {
        val spanCount = if (catalogController.isGridMode) getResponsiveSpanCount() else 1
        animeAdapter.isGridMode = catalogController.isGridMode
        binding.rvAnimeLibrary.layoutManager = GridLayoutManager(context, spanCount)
        binding.rvAnimeLibrary.adapter = animeAdapter

        animeAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvHomeCatalogTitle, v)
            val pos = binding.rvAnimeLibrary.getChildAdapterPosition(v)
            if (pos != RecyclerView.NO_POSITION && pos < animeAdapter.currentList.size) {
                viewModel.onEvent(HomeEvent.SaveFocus(5, pos))
                val item = animeAdapter.currentList[pos]
                if (item is FilesDataModel.File) {
                    lastFocusedItemId = item.driveFile.id
                    FocusMemory.remember("home.catalog", pos, item.driveFile.id)
                    if (item.driveFile.isFolder || item.driveFile.isShortcutFolder) {
                        heroController.scheduleFeaturedPreview(item.driveFile, v) { railController.currentTab }
                    }
                }
            }
        }

        animeAdapter.onDpadLeftListener = { itemView ->
            val pos = binding.rvAnimeLibrary.getChildAdapterPosition(itemView)
            val span = (binding.rvAnimeLibrary.layoutManager as? GridLayoutManager)?.spanCount ?: 1
            if (pos != RecyclerView.NO_POSITION && pos % span == 0) {
                if (railController.usesOverlaySidebar()) railController.expandSidebar()
                else railController.focusCurrentNavItem()
                true
            } else false
        }
    }

    private fun configureHomePosterShelfMeasurements() {
        listOf(
            binding.rvNewEpisodesShelf,
            binding.rvStarredShelf,
            binding.rvRecentlyAddedShelf
        ).forEach { shelf ->
            shelf.isNestedScrollingEnabled = false
            shelf.layoutParams = shelf.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
    }

    private fun refreshInicioShelfUi() {
        if (railController.currentTab != "Início") return
        val hasRecent = viewModel.recentWatches.value.isNotEmpty()
        val hasQueue = viewModel.watchQueue.value.isNotEmpty()
        binding.rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.shelfHeaderRow?.visibility = if (hasRecent || hasQueue) View.VISIBLE else View.GONE
        binding.tvShelfLabel?.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.tvShelfViewHistory?.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.tvShelfViewQueue?.visibility = if (hasQueue) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (railController.currentTab == "Início") {
            val rootFocus = binding.root.findFocus()
            val rail = binding.root.findViewById<View>(R.id.navRail)
            val isRailFocused = rootFocus == null || (rail != null && rootFocus.parent === rail)
            if (isRailFocused) {
                val state = viewModel.uiState.value
                if (state is HomeUiState.Content && state.lastFocusedShelfIndex != null && state.lastFocusedItemIndex != null) {
                    focusCoordinator.restoreShelfFocus(state.lastFocusedShelfIndex, state.lastFocusedItemIndex)
                } else if (lastFocusedAnimeView != null) {
                    lastFocusedAnimeView?.requestFocus()
                } else {
                    focusCoordinator.focusContent(
                        continueWatchingCount = shelvesController.continueWatchingAdapter.itemCount,
                        watchQueueCount = shelvesController.watchQueueAdapter.itemCount,
                        followedCount = shelvesController.followedEpisodesAdapter.itemCount,
                        starredCount = shelvesController.starredShelfAdapter.itemCount,
                        recentCount = shelvesController.recentlyAddedAdapter.itemCount
                    )
                }
            }
        }
    }

    private fun setupBackPressedHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    this@HomeFragment.handleBackPressed()
                }
            }
        )
    }

    private var exitDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showExitConfirmationDialog() {
        if (exitDialog?.isShowing == true) return
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.exit_dialog_title)
            .setMessage(R.string.exit_dialog_message)
            .setPositiveButton(R.string.exit_dialog_confirm) { _, _ ->
                activity?.finish()
            }
            .setNegativeButton(R.string.exit_dialog_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                exitDialog = null
                railController.focusCurrentNavItem()
            }

        exitDialog = builder.create().apply {
            show()
            // Gate G1 & Q01.1: Foco inicial em "Cancelar" para evitar encerramento acidental da aplicação
            getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        }
    }

    private fun handleBackPressed() {
        if (exitDialog?.isShowing == true) {
            exitDialog?.dismiss()
            return
        }
        if (railController.handleBackPressed()) return
        if (railController.currentTab != "Início") {
            railController.selectTab("Início")
            return
        }
        if (!railController.isFocusInRail(activity?.currentFocus)) {
            railController.focusCurrentNavItem()
            return
        }
        showExitConfirmationDialog()
    }

    private fun observeAnimeLibrary() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredAnimes.collect { animes ->
                        catalogController.submitCatalog()
                        if (railController.currentTab in listOf("Animes", "Filmes", "Favoritos")) {
                            val count = animes.size
                            val label = when (railController.currentTab) {
                                "Filmes" -> if (count == 1) "1 filme" else "$count filmes"
                                "Favoritos" -> "$count favoritos"
                                else -> if (count == 1) "1 anime" else "$count animes"
                            }
                            binding.tvItemCount.text = label
                            val isEmpty = animes.isEmpty() && !viewModel.isLoadingAnime.value
                            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        } else if (railController.currentTab == "Início") {
                            binding.rvAnimeLibrary.visibility = View.VISIBLE
                            binding.containerItemCount.visibility = View.GONE
                            val hasRecent = viewModel.recentWatches.value.isNotEmpty()
                            val hasFeatured = viewModel.featuredAnime.value != null
                            val hasQueue = viewModel.watchQueue.value.isNotEmpty()
                            val hasMyList = shelvesController.starredShelfAdapter.itemCount > 0
                            val hasRecentlyAdded = shelvesController.recentlyAddedAdapter.itemCount > 0
                            val isLibraryEmpty = viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value
                            val isHomeEmpty = !hasRecent && !hasFeatured && !hasQueue && !hasMyList && !hasRecentlyAdded && isLibraryEmpty
                            binding.layoutHomeEmpty?.visibility = if (isHomeEmpty) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.animeLibrary.collect { files ->
                        val sortedRecent = recentlyModifiedLibraryFolders(files)
                        val recentToShow = if (binding.rvRecentlyAddedShelf.hasFocus()) {
                            preserveFocusedShelfOrder(shelvesController.recentlyAddedAdapter.currentList, sortedRecent)
                        } else {
                            sortedRecent
                        }
                        shelvesController.recentlyAddedAdapter.submitList(recentToShow)
                        if (railController.currentTab == "Início") refreshInicioShelfUi()
                    }
                }

                launch {
                    viewModel.isLoadingAnime.collect { loading ->
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.libraryLoadError.collect { message ->
                        if (!message.isNullOrBlank()) {
                            val isFullScreenErrorActive = viewModel.uiState.value is HomeUiState.Error
                            if (!isFullScreenErrorActive) {
                                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                                    .setAction("Tentar de novo") { viewModel.loadAnimeLibrary(forceRefresh = true) }
                                    .show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.searchNotice.collect { message ->
                        if (!message.isNullOrBlank() && viewModel.searchQuery.value.isNotBlank()) {
                            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is HomeUiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.stateView.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.GONE
                            }
                            is HomeUiState.Content -> {
                                binding.progressBar.visibility = View.GONE
                                binding.stateView.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.GONE
                                if (state.lastFocusedShelfIndex != null && state.lastFocusedItemIndex != null) {
                                    val rootFocus = binding.root.findFocus()
                                    val rail = binding.root.findViewById<View>(R.id.navRail)
                                    val isRailFocused = rootFocus == null || (rail != null && rootFocus.parent === rail)
                                    if (isRailFocused) {
                                        focusCoordinator.restoreShelfFocus(state.lastFocusedShelfIndex, state.lastFocusedItemIndex)
                                    }
                                }
                            }
                            is HomeUiState.Empty -> {
                                binding.progressBar.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.GONE
                                binding.stateView.visibility = View.VISIBLE
                                binding.stateView.setState(StateView.StateType.EMPTY_HOME) {
                                    railController.selectTab("Animes")
                                }
                            }
                            is HomeUiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.GONE
                                binding.stateView.visibility = View.VISIBLE
                                val stateType = when (state.error) {
                                    is AppError.Network -> StateView.StateType.ERROR_NETWORK
                                    is AppError.Authentication -> StateView.StateType.ERROR_AUTH
                                    is AppError.AccessDenied -> StateView.StateType.ERROR_ACCESS_DENIED
                                    is AppError.RateLimited -> StateView.StateType.ERROR_RATE_LIMITED
                                    is AppError.NotFound -> StateView.StateType.ERROR_NOT_FOUND
                                    is AppError.Server -> StateView.StateType.ERROR_SERVER
                                    else -> StateView.StateType.ERROR_GENERIC
                                }
                                binding.stateView.setState(stateType) {
                                    viewModel.onEvent(HomeEvent.Refresh)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeMpv() {
        viewModel.mpvFile.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val file = resource.data
                        if (file != null) {
                            val startPos = pendingMpvStartPosition ?: -1L
                            pendingMpvStartPosition = null
                            PlayerLauncher.launch(
                                context = requireContext(),
                                playerType = VideoPlayer.MPV,
                                fileId = file.fileId,
                                title = file.fileName,
                                accessToken = file.accessToken,
                                thumbnailLink = file.thumbnailLink,
                                themeIndex = mainViewModel.currentThemeIndex,
                                startPosition = startPos
                            )
                        }
                    }
                    is Resource.Error -> {
                        Toast.makeText(context, resource.message ?: "Erro ao iniciar MPV", Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeLogOutState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasLoggedOut.collect { loggedOut ->
                    if (loggedOut) {
                        requireActivity().finish()
                        delay(250L)
                        requireActivity().startActivity(requireActivity().intent)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exitDialog?.dismiss()
        exitDialog = null
        lastFocusedAnimeView = null
        _binding = null
    }
}
