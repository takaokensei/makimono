package zechs.drive.stream.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.FocusedItemScroll
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.ui.home.adapter.StarredShelfAdapter
import zechs.drive.stream.ui.home.adapter.WatchQueueShelfAdapter
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.ext.resolveThemeColor
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
    lateinit var profileManager: zechs.drive.stream.utils.ProfileManager

    @Inject
    lateinit var profileArtCatalog: ProfileArtCatalog

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var followedFolderRepository: FollowedFolderRepository

    private var isGridMode = true
    private var currentTab = "Início"
    private var previousTabBeforeSearch: String? = null
    private var isSidebarExpanded = false
    private var lastFocusedAnimeView: View? = null
    private var heroAutoFocused = false
    private var lastFocusedItemId: String? = null
    private var lastProfileConfiguration: String? = null
    private var reduceMotionEnabled = false
    private var featuredPreviewScheduler: FeaturedPreviewScheduler? = null
    private var featuredHeroBinder: FeaturedHeroBinder? = null

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
            onClickListener = { file ->
                handleOpenFolder(file)
            },
            onStarClickListener = { file, star ->
                viewModel.starFile(file, star)
            },
            onLongClickListener = { file ->
                showAnimeContextMenu(file)
            },
            onPlayClickListener = { file ->
                // Botão play central: iniciar imediatamente!
                handleQuickPlay(file)
            },
            compactCatalog = true
        )
    }

    private var pendingMpvStartPosition: Long? = null

    private val continueWatchingAdapter = ContinueWatchingAdapter { watchItem ->
        playWatchItem(watchItem)
    }.apply {
        onLongClickListener = { watchItem, focusAnchor ->
            showContinueWatchingContextMenu(watchItem, focusAnchor)
        }
    }

    private val watchQueueAdapter = WatchQueueShelfAdapter { item ->
        playQueueItem(item)
    }.apply {
        onLongClickListener = { item ->
            showQueueItemContextMenu(item)
        }
    }

    private val starredShelfAdapter = StarredShelfAdapter(
        onClick = ::handleOpenFolder
    )

    private val recentlyAddedAdapter = StarredShelfAdapter(
        onClick = ::handleOpenFolder,
        folderBadgeTextResId = R.string.home_recently_added_type_series,
        folderSubtitleTextResId = R.string.home_recently_added_subtitle
    )

    private val followedEpisodesAdapter = StarredShelfAdapter(
        onClick = ::handleOpenFolder,
        folderBadgeTextResId = R.string.home_followed_type,
        folderSubtitleTextResId = R.string.home_followed_subtitle
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        featuredHeroBinder = FeaturedHeroBinder(binding, requireContext())
        featuredPreviewScheduler = FeaturedPreviewScheduler(viewLifecycleOwner.lifecycleScope) {
            viewModel.previewFeatured(it)
        }

        refreshMotionPreference()
        configureHomePosterShelfMeasurements()

        setupAnimeGrid()
        setupHeaderSearch()
        setupVoiceSearch()
        setupViewToggle()
        setupSidebarNavigation()
        setupBrandLogo()
        setupBackPressedHandling()
        setupHeroAndProfile()
        TvAccessibilityLayout.adaptHomeHero(
            requireContext(),
            binding.featuredHeroContainer,
            binding.btnFeaturedPlay,
            binding.btnFeaturedInfo
        )

        binding.rvContinueWatchingShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvContinueWatchingShelf.adapter = continueWatchingAdapter
        continueWatchingAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusAdjacentHomeShelf(binding.rvContinueWatchingShelf, focused, keyCode)
        }

        binding.rvWatchQueueShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvWatchQueueShelf.adapter = watchQueueAdapter
        watchQueueAdapter.onDpadLeftListener = {
            if (usesOverlaySidebar()) expandSidebar() else focusCurrentNavItem()
            true
        }
        watchQueueAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusAdjacentHomeShelf(binding.rvWatchQueueShelf, focused, keyCode)
        }
        watchQueueAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvQueueShelfLabel, v)
            val position = binding.rvWatchQueueShelf.getChildAdapterPosition(v)
            watchQueueAdapter.currentList.getOrNull(position)?.let { item ->
                findLibraryFolderForTitle(item.name)?.let { folder ->
                    scheduleFeaturedPreview(folder, v)
                }
            }
        }

        binding.rvStarredShelf?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvStarredShelf?.adapter = starredShelfAdapter
        starredShelfAdapter.onDpadLeftListener = {
            if (usesOverlaySidebar()) expandSidebar() else focusCurrentNavItem()
            true
        }
        starredShelfAdapter.onDpadVerticalListener = { focused, keyCode ->
            binding.rvStarredShelf?.let { focusAdjacentHomeShelf(it, focused, keyCode) } ?: false
        }
        starredShelfAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            binding.rvStarredShelf?.let {
                HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvMyListShelfLabel, v)
            }
            val position = binding.rvStarredShelf?.getChildAdapterPosition(v)
                ?: androidx.recyclerview.widget.RecyclerView.NO_POSITION
            starredShelfAdapter.currentList.getOrNull(position)?.let { folder ->
                scheduleFeaturedPreview(folder, v)
            }
        }

        binding.rvRecentlyAddedShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvRecentlyAddedShelf.adapter = recentlyAddedAdapter
        recentlyAddedAdapter.onDpadLeftListener = {
            if (usesOverlaySidebar()) expandSidebar() else focusCurrentNavItem()
            true
        }
        recentlyAddedAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusAdjacentHomeShelf(binding.rvRecentlyAddedShelf, focused, keyCode)
        }
        recentlyAddedAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvRecentlyAddedShelfLabel, v)
            val position = binding.rvRecentlyAddedShelf.getChildAdapterPosition(v)
            recentlyAddedAdapter.currentList.getOrNull(position)?.let { folder ->
                scheduleFeaturedPreview(folder, v)
            }
        }

        binding.rvNewEpisodesShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvNewEpisodesShelf.adapter = followedEpisodesAdapter
        followedEpisodesAdapter.onDpadLeftListener = {
            if (usesOverlaySidebar()) expandSidebar() else focusCurrentNavItem()
            true
        }
        followedEpisodesAdapter.onDpadVerticalListener = { focused, keyCode ->
            focusAdjacentHomeShelf(binding.rvNewEpisodesShelf, focused, keyCode)
        }
        followedEpisodesAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvNewEpisodesShelfLabel, v)
            val position = binding.rvNewEpisodesShelf.getChildAdapterPosition(v)
            followedEpisodesAdapter.currentList.getOrNull(position)?.let { folder ->
                scheduleFeaturedPreview(folder, v)
            }
        }

        setupTenFootFocusChain()
        observeFollowedEpisodesShelf()

        binding.tvShelfViewHistory?.setOnClickListener {
            showWatchHistoryDialog()
        }

        binding.tvShelfViewQueue?.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToQueueFragment()
            findNavController().navigateSafe(action)
        }

        binding.btnExploreAnimes?.setOnClickListener {
            selectTab("Animes")
        }

        observeAnimeLibrary()
        observeRecentWatches()
        observeWatchQueue()
        observeStarredShelf()
        observeFeaturedAnime()
        observeProfileConfiguration()
        observeLogOutState()
        observeMpv()

        // Set initial tab state to Início
        selectTab("Início")

        // Load anime library immediately on opening
        viewModel.loadAnimeLibrary()
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
            if (reduceMotionEnabled) {
                binding.root.findFocus()?.let { focused ->
                    focused.animate().cancel()
                    focused.scaleX = 1f
                    focused.scaleY = 1f
                    focused.translationZ = 0f
                }
            }
        }
    }

    private fun observeProfileConfiguration() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collect { profile ->
                    val signature = listOf(
                        profile.id,
                        profile.libraryRootId.orEmpty(),
                        profile.libraryRootName.orEmpty()
                    ).joinToString("|")
                    val previous = lastProfileConfiguration
                    lastProfileConfiguration = signature
                    if (previous != null && previous != signature) {
                        viewModel.loadAnimeLibrary(forceRefresh = true)
                    }
                }
            }
        }
    }

    private fun getResponsiveSpanCount(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) return 2

        val railWidthDp = (binding.navRail.layoutParams.width / resources.displayMetrics.density).toInt()
        val contentWidth = resources.configuration.screenWidthDp - railWidthDp - 44
        return (contentWidth / 132).coerceIn(3, 7)
    }

    private fun setupTenFootFocusChain() {
        // Setup focus chain for TV and tablet landscape, but not phone landscape
        val isTabletOrTV = zechs.drive.stream.utils.DeviceUi.isTenFootExperience(requireContext()) || 
                          zechs.drive.stream.utils.DeviceUi.isTablet(requireContext())
        
        if (!isTabletOrTV) return
        
        val search = binding.etSearchAnime
        val play = binding.btnFeaturedPlay
        val info = binding.btnFeaturedInfo
        val continueShelf = binding.rvContinueWatchingShelf
        val queueShelf = binding.rvWatchQueueShelf
        val starredShelf = binding.rvStarredShelf
        val followedShelf = binding.rvNewEpisodesShelf
        val recentlyAddedShelf = binding.rvRecentlyAddedShelf
        val catalog = binding.rvAnimeLibrary

        listOf(
            binding.btnBrandLogo,
            binding.btnNavInicio,
            binding.btnNavAnimes,
            binding.btnNavPastas,
            binding.btnNavFavoritos,
            binding.userProfilePill,
            binding.btnNavConfig
        ).forEach { railItem ->
            railItem?.nextFocusRightId = search.id
        }

        search.nextFocusLeftId = binding.btnNavInicio.id
        play?.nextFocusUpId = search.id
        info?.nextFocusUpId = search.id
        play?.nextFocusLeftId = binding.btnNavInicio.id
        play?.nextFocusRightId = info?.id ?: View.NO_ID
        info?.nextFocusLeftId = play?.id ?: View.NO_ID
        catalog.nextFocusLeftId = binding.btnNavInicio.id
        // Shelves arrive asynchronously and may be absent for a new profile.
        // Rebuild vertical links after layout so no explicit link points at a hidden row.
        val root = binding.root
        val focusLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val rows = listOfNotNull(continueShelf, queueShelf, followedShelf, starredShelf, recentlyAddedShelf, catalog).filter { it.isShown }
            val heroTarget = play?.takeIf { it.isShown } ?: search
            play?.nextFocusDownId = rows.firstOrNull()?.id ?: View.NO_ID
            info?.nextFocusDownId = rows.firstOrNull()?.id ?: View.NO_ID
            search.nextFocusDownId = play?.takeIf { it.isShown }?.id ?: rows.firstOrNull()?.id ?: View.NO_ID
            rows.forEachIndexed { index, row ->
                row.nextFocusUpId = rows.getOrNull(index - 1)?.id ?: heroTarget.id
                row.nextFocusDownId = rows.getOrNull(index + 1)?.id ?: View.NO_ID
            }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(focusLayoutListener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnGlobalLayoutListener(focusLayoutListener)
                v.removeOnAttachStateChangeListener(this)
            }
        })

        FocusedItemScroll.install(viewport = binding.contentScrollView, rows = listOfNotNull(continueShelf, queueShelf, followedShelf, starredShelf, recentlyAddedShelf, catalog))
    }

    /** Keeps D-pad movement within the corresponding column as it crosses Home shelves. */
    private fun focusAdjacentHomeShelf(
        source: androidx.recyclerview.widget.RecyclerView,
        focusedChild: View,
        keyCode: Int
    ): Boolean {
        val rows = listOfNotNull(
            binding.rvContinueWatchingShelf,
            binding.rvWatchQueueShelf,
            binding.rvNewEpisodesShelf,
            binding.rvStarredShelf,
            binding.rvRecentlyAddedShelf,
            binding.rvAnimeLibrary
        ).filter { it.isShown && (it.adapter?.itemCount ?: 0) > 0 }
        val sourceIndex = rows.indexOf(source)
        if (sourceIndex < 0) return false

        val targetIndex = when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> sourceIndex - 1
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> sourceIndex + 1
            else -> return false
        }
        if (targetIndex < 0) {
            (binding.btnFeaturedPlay?.takeIf { it.isShown } ?: binding.etSearchAnime).requestFocus()
            return true
        }
        val target = rows.getOrNull(targetIndex) ?: return false
        val sourcePosition = source.getChildAdapterPosition(focusedChild)
            .takeIf { it != androidx.recyclerview.widget.RecyclerView.NO_POSITION } ?: 0
        val targetPosition = if (target.layoutManager is GridLayoutManager) {
            sourcePosition % (target.layoutManager as GridLayoutManager).spanCount
        } else {
            sourcePosition
        }.coerceIn(0, (target.adapter?.itemCount ?: 1) - 1)

        target.scrollToPosition(targetPosition)
        target.postDelayed({
            if (target.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus() != true) {
                target.postDelayed({
                    target.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
                }, 64)
            }
        }, 64)
        return true
    }

    private fun setupAnimeGrid() {
        val spanCount = if (isGridMode) getResponsiveSpanCount() else 1
        animeAdapter.isGridMode = isGridMode
        binding.rvAnimeLibrary.layoutManager = GridLayoutManager(context, spanCount)
        binding.rvAnimeLibrary.adapter = animeAdapter
    }

    /** Poster cards are taller than landscape video cards; keep their rails at their measured content height. */
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

    private var catalogSort = 0

    private fun submitCatalog() {
        val source = viewModel.filteredAnimes.value
        val titles = compareBy<DriveFile> { zechs.drive.stream.data.remote.AnimePosterResolver.cleanAnimeTitle(it.name).lowercase(java.util.Locale.ROOT) }
        val sorted = when (catalogSort) {
            1 -> source.sortedWith(titles)
            2 -> source.sortedWith(titles.reversed())
            else -> source
        }
        val focusedItems = (0 until animeAdapter.itemCount).mapNotNull { position ->
            (animeAdapter.currentList.getOrNull(position) as? FilesDataModel.File)?.driveFile
        }
        val ordered = if (catalogSort == 0 && binding.rvAnimeLibrary.hasFocus()) {
            preserveFocusedShelfOrder(focusedItems, sorted)
        } else {
            sorted
        }
        animeAdapter.submitList(ordered.map { FilesDataModel.File(it) })
    }

    private fun setupHeaderSearch() {
        val prefs = requireContext().getSharedPreferences("catalog_presentation", android.content.Context.MODE_PRIVATE)
        catalogSort = prefs.getInt("sort", 0).coerceIn(0, 2)
        binding.root.findViewById<View>(R.id.btnCatalogSort)?.setOnClickListener { button ->
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Ordenar catálogo")
                .setSingleChoiceItems(arrayOf("Ordem da biblioteca", "Nome: A–Z", "Nome: Z–A"), catalogSort) { dialog, choice ->
                    catalogSort = choice
                    prefs.edit().putInt("sort", choice).apply()
                    submitCatalog()
                    binding.rvAnimeLibrary.scrollToPosition(0)
                    dialog.dismiss()
                }
                .setOnDismissListener { button.requestFocus() }
                .show()
        }

        binding.etSearchAnime.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            binding.btnClearSearch.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
            if (query.isNotBlank()) {
                if (previousTabBeforeSearch == null && currentTab != "Animes") {
                    previousTabBeforeSearch = currentTab
                }
                if (currentTab != "Animes") {
                    selectTab("Animes")
                }
            } else {
                previousTabBeforeSearch?.let { prevTab ->
                    previousTabBeforeSearch = null
                    selectTab(prevTab)
                }
            }
            viewModel.filterAnimes(query)
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchAnime.setText("")
        }

        binding.etSearchAnime.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                if (binding.etSearchAnime.selectionStart == 0) {
                    if (isCollapsibleRail()) {
                        expandSidebar()
                    } else {
                        focusCurrentNavItem()
                    }
                    true
                } else false
            } else false
        }
    }

    private fun setupVoiceSearch() {
        binding.btnVoiceSearch.setOnClickListener {
            launchVoiceSearch()
        }

        binding.btnVoiceSearch.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus, scale = 1.15f)
            val context = requireContext()
            val activeColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
            val inactiveColor = context.resolveThemeColor(R.attr.colorTextSecondary)
            binding.btnVoiceSearch.imageTintList = android.content.res.ColorStateList.valueOf(
                if (hasFocus) activeColor else inactiveColor
            )
        }
    }

    private fun animateFocus(view: View, hasFocus: Boolean, scale: Float, elevation: Float = 0f) {
        if (reduceMotionEnabled) {
            view.animate().cancel()
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationZ = 0f
            return
        }
        val targetScale = if (hasFocus) scale else 1f
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationZ(if (hasFocus) elevation else 0f)
            .setDuration(120L)
            .start()
    }

    private fun launchVoiceSearch() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Fale o nome do anime...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                context,
                getString(R.string.voice_search_not_supported),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupViewToggle() {
        val gridBtn = binding.btnToggleGrid
        val listBtn = binding.btnToggleList
        if (gridBtn == null || listBtn == null) return

        gridBtn.setOnClickListener {
            if (!isGridMode) {
                isGridMode = true
                animeAdapter.isGridMode = true
                gridBtn.setBackgroundResource(R.drawable.bg_segmented_active)
                gridBtn.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                listBtn.background = null
                listBtn.imageTintList = android.content.res.ColorStateList.valueOf(requireContext().resolveThemeColor(R.attr.colorTextSecondary))
                setupAnimeGrid()
            }
        }

        listBtn.setOnClickListener {
            if (isGridMode) {
                isGridMode = false
                animeAdapter.isGridMode = false
                listBtn.setBackgroundResource(R.drawable.bg_segmented_active)
                listBtn.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                gridBtn.background = null
                gridBtn.imageTintList = android.content.res.ColorStateList.valueOf(requireContext().resolveThemeColor(R.attr.colorTextSecondary))
                setupAnimeGrid()
            }
        }
    }

    private fun setupSidebarNavigation() {
        val hasOverlay = usesOverlaySidebar()

        if (hasOverlay) {
            binding.navRail.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            binding.navRail.visibility = View.INVISIBLE
            setNavItemsFocusable(false)

            // Retract sidebar off-screen by default
            binding.navRail.post {
                val railWidth = binding.navRail.width.toFloat().coerceAtLeast(240f.dpToPx())
                binding.navRail.translationX = -railWidth
            }
            binding.sidebarDimOverlay?.apply {
                visibility = View.GONE
                alpha = 0f
                setOnClickListener {
                    collapseSidebar()
                }
            }
        } else {
            // On TV the rail stays visible. This gives the D-pad a stable
            // left boundary instead of making navigation depend on an overlay animation.
            binding.navRail.visibility = View.VISIBLE
            binding.navRail.translationX = 0f
            binding.navRail.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setNavItemsFocusable(true)
        }

        binding.apply {
            btnHamburgerMenu?.setOnClickListener {
                toggleSidebar()
            }
            btnBrandLogo.setOnClickListener {
                if (hasOverlay) toggleSidebar() else focusCurrentNavItem()
            }
            btnBrandLogo.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (hasOverlay) expandSidebar() else focusCurrentNavItem()
                    true
                } else false
            }

            btnNavAnimes.setOnClickListener {
                previousTabBeforeSearch = null
                selectTab("Animes")
                viewModel.filterStarred(false)
                if (viewModel.animeLibrary.value.isEmpty()) {
                    viewModel.loadAnimeLibrary(forceRefresh = true)
                }
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
            }

            btnNavFavoritos.setOnClickListener {
                previousTabBeforeSearch = null
                selectTab("Favoritos")
                viewModel.filterStarred(true)
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
            }

            btnNavPastas.setOnClickListener {
                previousTabBeforeSearch = null
                selectTab("Pastas")
                if (hasOverlay) collapseSidebar()
                viewModel.getLibraryRootFolder { folderId, folderName ->
                    val resolvedId = folderId ?: "root"
                    val action = HomeFragmentDirections.actionHomeFragmentToFilesFragment(
                        name = folderName,
                        query = "'$resolvedId' in parents and trashed = false",
                        folderPath = arrayOf(resolvedId)
                    )
                    findNavController().navigateSafe(action)
                }
            }

            btnNavInicio.setOnClickListener {
                previousTabBeforeSearch = null
                selectTab("Início")
                viewModel.filterStarred(false)
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
            }

            btnNavConfig.setOnClickListener {
                if (hasOverlay) collapseSidebar()
                findNavController().navigateSafe(R.id.action_homeFragment_to_settingsFragment)
            }

            // TV Focus setup & D-pad Right navigation back to content
            val navItems = listOfNotNull(
                btnNavInicio,
                btnNavAnimes,
                btnNavPastas,
                btnNavFavoritos,
                btnNavConfig
            )

            navItems.forEach { item ->
                // On a touchscreen attached to a TV-style launcher, the first tap can otherwise
                // only move focus. Consume the gesture and route one click on ACTION_UP.
                zechs.drive.stream.utils.SingleTapActivation.install(item)
                item.setOnFocusChangeListener { v, hasFocus ->
                    animateFocus(v, hasFocus, scale = 1.04f, elevation = 6f)
                }

                item.setOnKeyListener { _, keyCode, event ->
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (hasOverlay) collapseSidebar()
                        focusContent()
                        true
                    } else false
                }
            }
        }

        // Wire up mobile bottom navigation bar
        binding.bottomNavInicio?.setOnClickListener { binding.btnNavInicio.performClick() }
        binding.bottomNavAnimes?.setOnClickListener { binding.btnNavAnimes.performClick() }
        binding.bottomNavPastas?.setOnClickListener { binding.btnNavPastas.performClick() }
        binding.bottomNavFavoritos?.setOnClickListener { binding.btnNavFavoritos.performClick() }
        binding.bottomNavConfig?.setOnClickListener { binding.btnNavConfig.performClick() }

        // Setup D-pad Left on anime adapter to expand sidebar when on leftmost column
        animeAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvHomeCatalogTitle, v)
            val pos = binding.rvAnimeLibrary.getChildAdapterPosition(v)
            if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION && pos < animeAdapter.currentList.size) {
                val item = animeAdapter.currentList[pos]
                if (item is FilesDataModel.File) {
                    lastFocusedItemId = item.driveFile.id
                    if (item.driveFile.isFolder || item.driveFile.isShortcutFolder) {
                        scheduleFeaturedPreview(item.driveFile, v)
                    }
                }
            }
        }

        animeAdapter.onDpadLeftListener = { itemView ->
            val pos = binding.rvAnimeLibrary.getChildAdapterPosition(itemView)
            val spanCount = (binding.rvAnimeLibrary.layoutManager as? GridLayoutManager)?.spanCount ?: 1
            if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION && pos % spanCount == 0) {
                if (hasOverlay) expandSidebar() else focusCurrentNavItem()
                true
            } else false
        }

        continueWatchingAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
            HomeShelfScroll.alignHeading(binding.contentScrollView, binding.tvShelfLabel, v)
            val position = binding.rvContinueWatchingShelf.getChildAdapterPosition(v)
            continueWatchingAdapter.currentList.getOrNull(position)?.let { item ->
                findLibraryFolderForTitle(item.name)?.let { folder ->
                    scheduleFeaturedPreview(folder, v)
                }
            }
        }

        continueWatchingAdapter.onDpadLeftListener = {
            if (hasOverlay) expandSidebar() else focusCurrentNavItem()
            true
        }
    }

    private fun isCollapsibleRail(): Boolean {
        if (binding.sidebarDimOverlay == null) return false
        if (zechs.drive.stream.utils.DeviceUi.isTenFootExperience(requireContext())) {
            return false
        }
        return resources.getBoolean(R.bool.is_collapsible_rail)
    }

    /** Drawer overlay exists only on compact Home variants with a dim scrim, not chip rows. */
    private fun usesOverlaySidebar(): Boolean =
        isCollapsibleRail() && binding.sidebarDimOverlay != null

    private fun refreshInicioShelfUi() {
        if (currentTab != "Início") return
        val hasRecent = viewModel.recentWatches.value.isNotEmpty()
        val hasQueue = viewModel.watchQueue.value.isNotEmpty()
        val hasMyList = starredShelfAdapter.itemCount > 0
        val hasFollowed = followedEpisodesAdapter.itemCount > 0
        val hasRecentlyAdded = recentlyAddedAdapter.itemCount > 0
        binding.rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.shelfHeaderRow?.visibility = if (hasRecent || hasQueue) View.VISIBLE else View.GONE
        binding.tvShelfLabel?.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.tvShelfViewHistory?.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.tvShelfViewQueue?.visibility = if (hasQueue) View.VISIBLE else View.GONE
        binding.tvQueueShelfLabel?.visibility = if (hasQueue) View.VISIBLE else View.GONE
        binding.rvWatchQueueShelf?.visibility = if (hasQueue) View.VISIBLE else View.GONE
        binding.tvNewEpisodesShelfLabel.visibility = if (hasFollowed) View.VISIBLE else View.GONE
        binding.rvNewEpisodesShelf.visibility = if (hasFollowed) View.VISIBLE else View.GONE
        binding.tvMyListShelfLabel?.visibility = if (hasMyList) View.VISIBLE else View.GONE
        binding.rvStarredShelf?.visibility = if (hasMyList) View.VISIBLE else View.GONE
        binding.tvRecentlyAddedShelfLabel.visibility = if (hasRecentlyAdded) View.VISIBLE else View.GONE
        binding.rvRecentlyAddedShelf.visibility = if (hasRecentlyAdded) View.VISIBLE else View.GONE
        binding.tvShelfLabel?.setText(R.string.home_continue_watching)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refreshMotionPreference()
        findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("homeTab")?.let { tab ->
            selectTab(tab)
            viewModel.filterStarred(tab == "Favoritos")
        }
        viewModel.getRecentWatches()
        viewModel.getWatchHistory()
        viewModel.getLastWatched()
        viewModel.getStarredFiles()
        if (currentTab == "Favoritos") {
            viewModel.filterStarred(true)
        }
        if (!isCollapsibleRail()) {
            // Restore stable focus on TV resume
            val focusedView = lastFocusedAnimeView
            if (focusedView != null && focusedView.isAttachedToWindow) {
                focusedView.requestFocus()
            } else {
                focusContent()
            }
        }
    }

    private fun focusCurrentNavItem() {
        val target = when (currentTab) {
            "Animes" -> binding.btnNavAnimes
            "Pastas" -> binding.btnNavPastas
            "Favoritos" -> binding.btnNavFavoritos
            "Configurações" -> binding.btnNavConfig
            else -> binding.btnNavInicio
        }
        target.requestFocus()
    }

    private fun focusContent() {
        val target = when {
            binding.featuredHeroContainer?.visibility == View.VISIBLE -> binding.btnFeaturedPlay
            binding.rvContinueWatchingShelf.visibility == View.VISIBLE && continueWatchingAdapter.itemCount > 0 -> binding.rvContinueWatchingShelf
            binding.rvWatchQueueShelf?.visibility == View.VISIBLE && watchQueueAdapter.itemCount > 0 -> binding.rvWatchQueueShelf
            binding.rvNewEpisodesShelf.visibility == View.VISIBLE && followedEpisodesAdapter.itemCount > 0 -> binding.rvNewEpisodesShelf
            binding.rvStarredShelf?.visibility == View.VISIBLE && starredShelfAdapter.itemCount > 0 -> binding.rvStarredShelf
            binding.rvRecentlyAddedShelf.visibility == View.VISIBLE && recentlyAddedAdapter.itemCount > 0 -> binding.rvRecentlyAddedShelf
            binding.rvAnimeLibrary.visibility == View.VISIBLE -> binding.rvAnimeLibrary
            else -> binding.contentScrollView
        }
        if (target === binding.rvAnimeLibrary) {
            binding.rvAnimeLibrary.post {
                binding.rvAnimeLibrary.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    ?: binding.rvAnimeLibrary.requestFocus()
            }
        } else {
            target?.requestFocus()
        }
    }

    private fun setNavItemsFocusable(enabled: Boolean) {
        val navItems = listOfNotNull(
            binding.btnNavInicio,
            binding.btnNavAnimes,
            binding.btnNavPastas,
            binding.btnNavFavoritos,
            binding.btnNavConfig
        )
        navItems.forEach { item ->
            item.isFocusable = enabled
            item.isFocusableInTouchMode = enabled
        }
    }

    private fun expandSidebar() {
        if (isSidebarExpanded || binding.sidebarDimOverlay == null) return
        isSidebarExpanded = true

        binding.navRail.visibility = View.VISIBLE
        binding.navRail.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        setNavItemsFocusable(true)

        binding.sidebarDimOverlay?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        binding.navRail.apply {
            bringToFront()
            animate()
                .translationX(0f)
                .setDuration(240L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    val targetBtn = when (currentTab) {
                        "Início" -> binding.btnNavInicio
                        "Pastas" -> binding.btnNavPastas
                        "Favoritos" -> binding.btnNavFavoritos
                        else -> binding.btnNavAnimes
                    }
                    targetBtn.requestFocus()
                }
                .start()
        }
    }

    private fun collapseSidebar() {
        if (!isSidebarExpanded || binding.sidebarDimOverlay == null) return
        isSidebarExpanded = false

        binding.navRail.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setNavItemsFocusable(false)

        binding.sidebarDimOverlay?.apply {
            animate()
                .alpha(0f)
                .setDuration(200L)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction { visibility = View.GONE }
                .start()
        }

        val railWidth = binding.navRail.width.toFloat().coerceAtLeast(240f.dpToPx())
        binding.navRail.animate()
            .translationX(-railWidth)
            .setDuration(220L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                if (!isSidebarExpanded) {
                    binding.navRail.visibility = View.INVISIBLE
                }
            }
            .start()
    }

    private fun toggleSidebar() {
        if (isSidebarExpanded) collapseSidebar() else expandSidebar()
    }

    private fun setupBackPressedHandling() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isSidebarExpanded) {
                        collapseSidebar()
                        lastFocusedAnimeView?.requestFocus() ?: binding.rvAnimeLibrary.requestFocus()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupHeroAndProfile() {
        val hasOverlay = usesOverlaySidebar()

        binding.btnFeaturedPlay?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                lastFocusedAnimeView = v
                animateFocus(v, true, scale = 1.05f, elevation = 8f)
            } else {
                animateFocus(v, false, scale = 1.05f, elevation = 8f)
            }
        }

        binding.btnFeaturedPlay?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                if (hasOverlay) {
                    expandSidebar()
                } else {
                    focusCurrentNavItem()
                }
                true
            } else false
        }

        binding.btnFeaturedInfo?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                lastFocusedAnimeView = v
                animateFocus(v, true, scale = 1.05f, elevation = 8f)
            } else {
                animateFocus(v, false, scale = 1.05f, elevation = 8f)
            }
        }

        binding.userProfilePill?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                animateFocus(v, true, scale = 1.05f, elevation = 6f)
            } else {
                animateFocus(v, false, scale = 1.05f, elevation = 6f)
            }
        }

        binding.userProfilePill?.setOnClickListener {
            findNavController().navigateSafe(R.id.action_homeFragment_to_profileSelectionFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collect { profile ->
                    binding.tvUserName?.text = profile.name
                    val avatarRes = profileManager.getAvatarDrawableRes(profile.avatarResName)
                    val avatarSource: Any? = profile.avatarUrl
                        ?: if (profile.avatarResName == "avatar_caua") avatarRes
                        else profileArtCatalog.defaultAvatarForProfile(profile.id)?.url
                    binding.ivUserAvatar?.let { avatar ->
                        avatar.imageTintList = null
                        avatar.clearColorFilter()
                        com.bumptech.glide.Glide.with(this@HomeFragment).load(avatarSource)
                            .placeholder(R.drawable.avatar_caua).error(R.drawable.avatar_caua)
                            .circleCrop().into(avatar)
                    }
                }
            }
        }
    }

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    private fun selectTab(tab: String) {
        if (tab != "Início") featuredPreviewScheduler?.cancel()
        currentTab = tab
        if (tab == "Início" || tab == "Animes" || tab == "Favoritos") {
            binding.root.findViewById<View>(R.id.topHeaderBar)?.visibility = View.VISIBLE
        }
        binding.root.findViewById<View>(R.id.tvHomeCatalogTitle)?.visibility =
            if (tab == "Início") View.VISIBLE else View.GONE
        val hasRecent = viewModel.recentWatches.value.isNotEmpty()

        binding.apply {
            when (tab) {
                "Início" -> {
                    val hasFeatured = viewModel.featuredAnime.value != null
                    featuredHeroContainer?.visibility = if (hasFeatured) View.VISIBLE else View.GONE
                    refreshInicioShelfUi()
                    rvAnimeLibrary.visibility = View.VISIBLE
                    layoutEmpty.visibility = View.GONE
                    containerViewToggle?.visibility = View.GONE
                    containerItemCount.visibility = View.GONE
                    val hasQueue = viewModel.watchQueue.value.isNotEmpty()
                    layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasQueue && viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value) View.VISIBLE else View.GONE
                }
                "Animes" -> {
                    featuredHeroContainer?.visibility = View.GONE
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
                    rvWatchQueueShelf?.visibility = View.GONE
                    tvNewEpisodesShelfLabel.visibility = View.GONE
                    rvNewEpisodesShelf.visibility = View.GONE
                    tvMyListShelfLabel?.visibility = View.GONE
                    rvStarredShelf?.visibility = View.GONE
                    tvRecentlyAddedShelfLabel.visibility = View.GONE
                    rvRecentlyAddedShelf.visibility = View.GONE
                    tvQueueShelfLabel?.visibility = View.GONE
                    tvShelfViewQueue?.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                    rvAnimeLibrary.visibility = View.VISIBLE
                    containerViewToggle?.visibility = View.VISIBLE
                    containerItemCount.visibility = View.VISIBLE
                    val animesCount = viewModel.filteredAnimes.value.size
                    tvItemCount.text = "$animesCount animes"
                    val isEmpty = animesCount == 0 && !viewModel.isLoadingAnime.value
                    layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                }
                "Favoritos" -> {
                    featuredHeroContainer?.visibility = View.GONE
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
                    rvWatchQueueShelf?.visibility = View.GONE
                    tvNewEpisodesShelfLabel.visibility = View.GONE
                    rvNewEpisodesShelf.visibility = View.GONE
                    tvMyListShelfLabel?.visibility = View.GONE
                    rvStarredShelf?.visibility = View.GONE
                    tvRecentlyAddedShelfLabel.visibility = View.GONE
                    rvRecentlyAddedShelf.visibility = View.GONE
                    tvQueueShelfLabel?.visibility = View.GONE
                    tvShelfViewQueue?.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                    rvAnimeLibrary.visibility = View.VISIBLE
                    containerViewToggle?.visibility = View.VISIBLE
                    containerItemCount.visibility = View.VISIBLE
                    val favCount = viewModel.filteredAnimes.value.size
                    tvItemCount.text = "$favCount favoritos"
                    val isEmpty = favCount == 0 && !viewModel.isLoadingAnime.value
                    layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                }
                else -> {
                    featuredHeroContainer?.visibility = View.GONE
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
                    rvWatchQueueShelf?.visibility = View.GONE
                    tvNewEpisodesShelfLabel.visibility = View.GONE
                    rvNewEpisodesShelf.visibility = View.GONE
                    tvMyListShelfLabel?.visibility = View.GONE
                    rvStarredShelf?.visibility = View.GONE
                    tvRecentlyAddedShelfLabel.visibility = View.GONE
                    rvRecentlyAddedShelf.visibility = View.GONE
                    tvQueueShelfLabel?.visibility = View.GONE
                    tvShelfViewQueue?.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                }
            }

            val normalBg = R.drawable.rail_item_focus_bg
            val activeBg = R.drawable.nav_item_active_bg
            val normalTextColor = requireContext().resolveThemeColor(R.attr.colorTextSecondary)
            val activeTextColor = android.graphics.Color.WHITE
            val normalIconColor = requireContext().resolveThemeColor(R.attr.colorTextSecondary)
            val activeIconColor = android.graphics.Color.WHITE

            // Início
            btnNavInicio.setBackgroundResource(if (tab == "Início") activeBg else normalBg)
            tvNavInicio.setTextColor(if (tab == "Início") activeTextColor else normalTextColor)
            ivNavInicioIcon?.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Início") activeIconColor else normalIconColor)

            // Animes
            btnNavAnimes.setBackgroundResource(if (tab == "Animes") activeBg else normalBg)
            tvNavAnimes.setTextColor(if (tab == "Animes") activeTextColor else normalTextColor)
            ivNavAnimesIcon.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Animes") activeIconColor else normalIconColor)

            // Pastas
            btnNavPastas.setBackgroundResource(if (tab == "Pastas") activeBg else normalBg)
            tvNavPastas.setTextColor(if (tab == "Pastas") activeTextColor else normalTextColor)

            // Favoritos
            btnNavFavoritos.setBackgroundResource(if (tab == "Favoritos") activeBg else normalBg)
            tvNavFavoritos.setTextColor(if (tab == "Favoritos") activeTextColor else normalTextColor)
            ivNavFavoritosIcon.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Favoritos") activeIconColor else normalIconColor)

            // Mobile Bottom Navigation Bar state
            val bottomActiveColor = requireContext().resolveThemeColor(R.attr.colorAccentPrimary)
            val bottomInactiveColor = requireContext().resolveThemeColor(R.attr.colorTextSecondary)

            ivBottomNavInicio?.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Início") bottomActiveColor else bottomInactiveColor)
            tvBottomNavInicio?.setTextColor(if (tab == "Início") bottomActiveColor else bottomInactiveColor)

            ivBottomNavAnimes?.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Animes") bottomActiveColor else bottomInactiveColor)
            tvBottomNavAnimes?.setTextColor(if (tab == "Animes") bottomActiveColor else bottomInactiveColor)

            ivBottomNavPastas?.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Pastas") bottomActiveColor else bottomInactiveColor)
            tvBottomNavPastas?.setTextColor(if (tab == "Pastas") bottomActiveColor else bottomInactiveColor)

            ivBottomNavFavoritos?.imageTintList = android.content.res.ColorStateList.valueOf(if (tab == "Favoritos") bottomActiveColor else bottomInactiveColor)
            tvBottomNavFavoritos?.setTextColor(if (tab == "Favoritos") bottomActiveColor else bottomInactiveColor)
        }
    }

    private fun setupBrandLogo() {
        binding.btnBrandLogo.setOnClickListener {
            if (isCollapsibleRail()) toggleSidebar() else focusCurrentNavItem()
        }
        binding.btnBrandLogo.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isCollapsibleRail()) expandSidebar() else focusCurrentNavItem()
                true
            } else false
        }
        binding.btnBrandLogo.setOnLongClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.log_out_dialog_title))
                .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                    dialog.dismiss()
                    viewModel.logOut()
                }
                .show()
            true
        }
    }

    private fun observeAnimeLibrary() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredAnimes.collect { animes ->
                        submitCatalog()
                        if (currentTab == "Animes") {
                            binding.tvItemCount.text = "${animes.size} animes"
                            val isEmpty = animes.isEmpty() && !viewModel.isLoadingAnime.value
                            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        } else if (currentTab == "Favoritos") {
                            binding.tvItemCount.text = "${animes.size} favoritos"
                            val isEmpty = animes.isEmpty() && !viewModel.isLoadingAnime.value
                            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        } else if (currentTab == "Início") {
                            binding.rvAnimeLibrary.visibility = View.VISIBLE
                            binding.containerItemCount.visibility = View.GONE
                            val hasRecent = viewModel.recentWatches.value.isNotEmpty()
                            val hasFeatured = viewModel.featuredAnime.value != null
                            val hasQueue = viewModel.watchQueue.value.isNotEmpty()
                            val hasMyList = starredShelfAdapter.itemCount > 0
                            val hasRecentlyAdded = recentlyAddedAdapter.itemCount > 0
                            binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasQueue && !hasMyList && !hasRecentlyAdded && viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.animeLibrary.collect { files ->
                        val sortedRecent = recentlyModifiedLibraryFolders(files)
                        val recentToShow = if (binding.rvRecentlyAddedShelf.hasFocus()) {
                            preserveFocusedShelfOrder(recentlyAddedAdapter.currentList, sortedRecent)
                        } else {
                            sortedRecent
                        }
                        recentlyAddedAdapter.submitList(recentToShow)
                        if (currentTab == "Início") refreshInicioShelfUi()
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
                            val isFullScreenErrorActive = viewModel.homeState.value is HomeViewModel.HomeState.Error
                            if (!isFullScreenErrorActive) {
                                com.google.android.material.snackbar.Snackbar
                                    .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                    .setAction("Tentar de novo") {
                                        viewModel.loadAnimeLibrary(forceRefresh = true)
                                    }
                                    .show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.searchNotice.collect { message ->
                        if (!message.isNullOrBlank() && viewModel.searchQuery.value.isNotBlank()) {
                            com.google.android.material.snackbar.Snackbar
                                .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .show()
                        }
                    }
                }

                launch {
                    viewModel.homeState.collect { state ->
                        when (state) {
                            is HomeViewModel.HomeState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.layoutHomeEmpty?.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.GONE
                            }
                            is HomeViewModel.HomeState.Content -> {
                                binding.progressBar.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.GONE
                                binding.layoutEmpty.visibility = View.GONE
                            }
                            is HomeViewModel.HomeState.Empty -> {
                                binding.progressBar.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.VISIBLE
                                binding.layoutEmpty.visibility = View.GONE
                                binding.layoutHomeEmpty?.findViewById<TextView>(R.id.tvEmptyMessage)?.text = state.message
                                binding.layoutHomeEmpty?.findViewById<TextView>(R.id.tvEmptySubMessage)?.text = getString(R.string.home_empty_submessage)
                                binding.layoutHomeEmpty?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExploreAnimes)?.visibility = View.VISIBLE
                                binding.layoutHomeEmpty?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEmptyRetry)?.visibility = View.GONE
                            }
                            is HomeViewModel.HomeState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.layoutHomeEmpty?.visibility = View.VISIBLE
                                binding.layoutEmpty.visibility = View.GONE
                                val emptyMessage = when {
                                    state.isOffline -> getString(R.string.home_error_offline)
                                    state.isAuthError -> getString(R.string.home_error_auth)
                                    else -> getString(R.string.home_error_library)
                                }
                                val emptySubMessage = when {
                                    state.isOffline -> getString(R.string.home_error_offline_submessage)
                                    state.isAuthError -> getString(R.string.home_error_auth_submessage)
                                    else -> zechs.drive.stream.ui.common.StateView.sanitizeErrorMessage(state.message)
                                }
                                binding.layoutHomeEmpty?.findViewById<TextView>(R.id.tvEmptyMessage)?.text = emptyMessage
                                binding.layoutHomeEmpty?.findViewById<TextView>(R.id.tvEmptySubMessage)?.text = emptySubMessage
                                binding.layoutHomeEmpty?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExploreAnimes)?.visibility = View.GONE
                                binding.layoutHomeEmpty?.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEmptyRetry)?.apply {
                                    visibility = View.VISIBLE
                                    setOnClickListener {
                                        viewModel.loadAnimeLibrary(forceRefresh = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeRecentWatches() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentWatches.collect { items ->
                    continueWatchingAdapter.submitList(items)
                    if (currentTab == "Início") {
                        val hasRecent = items.isNotEmpty()
                        val hasFeatured = viewModel.featuredAnime.value != null
                        val hasQueue = viewModel.watchQueue.value.isNotEmpty()
                        refreshInicioShelfUi()
                        binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasQueue && viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value) View.VISIBLE else View.GONE
                        binding.containerItemCount.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun observeWatchQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchQueue.collect { items ->
                    watchQueueAdapter.submitList(items)
                    if (currentTab == "Início") {
                        refreshInicioShelfUi()
                    }
                }
            }
        }
    }

    private fun observeStarredShelf() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.starredFiles, viewModel.animeLibrary) { starred, library ->
                    filterStarredLibraryFolders(starred, library)
                }.collect { files ->
                    starredShelfAdapter.submitList(files)
                    if (currentTab == "Início") refreshInicioShelfUi()
                }
            }
        }
    }

    private fun observeFollowedEpisodesShelf() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collectLatest { profile ->
                    combine(
                        viewModel.animeLibrary,
                        followedFolderRepository.observeFollowed(profile.id)
                    ) { library, followed ->
                        filterFollowedLibraryFolders(followed, library)
                    }.collect { files ->
                        followedEpisodesAdapter.submitList(files)
                        if (currentTab == "Início") refreshInicioShelfUi()
                    }
                }
            }
        }
    }

    /**
     * Streaming-style entry point: on TV / landscape shells the cursor starts on the
     * hero's "Assistir agora" button instead of the sidebar's "Início" item. Runs once,
     * and only if focus is still on the rail (or nowhere), so it never steals focus
     * from something the user already navigated to.
     */
    private fun focusHeroOnFirstShow() {
        if (heroAutoFocused || isCollapsibleRail()) return
        heroAutoFocused = true
        binding.featuredHeroContainer?.post {
            if (view == null) return@post
            val focused = binding.root.findFocus()
            val rail = binding.root.findViewById<View>(R.id.navRail)
            var insideRail = focused == null
            var currentParent: android.view.ViewParent? = focused?.parent
            while (!insideRail && currentParent != null) {
                if (currentParent === rail) insideRail = true
                currentParent = currentParent.parent
            }
            if (insideRail) binding.btnFeaturedPlay?.requestFocus()
        }
    }

    private fun observeFeaturedAnime() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.featuredAnime.collect { featured ->
                    if (featured != null) {
                        if (currentTab == "Início") {
                            binding.featuredHeroContainer?.visibility = View.VISIBLE
                            binding.layoutHomeEmpty?.visibility = View.GONE
                            focusHeroOnFirstShow()
                        }
                        featuredHeroBinder?.bind(featured, reduceMotionEnabled)

                        binding.btnFeaturedPlay?.setOnClickListener {
                            val file = viewModel.animeLibrary.value.firstOrNull {
                                it.id == featured.folderId || it.shortcutDetails.targetId == featured.folderId
                            }
                            if (featured.isFocusedPreview) {
                                val action = HomeFragmentDirections.actionHomeFragmentToSeriesDetailFragment(
                                    name = featured.title,
                                    folderId = featured.folderId,
                                    posterUrl = featured.posterUrl ?: featured.backdropUrl
                                )
                                findNavController().navigateSafe(action)
                            } else if (file != null) {
                                handleQuickPlay(file)
                            } else {
                                val folderFile = DriveFile(
                                    id = featured.folderId,
                                    name = featured.title,
                                    size = null,
                                    mimeType = "application/vnd.google-apps.folder",
                                    iconLink = null,
                                    thumbnailLink = featured.posterUrl,
                                    shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
                                    starred = zechs.drive.stream.data.model.Starred.UNSTARRED
                                )
                                handleQuickPlay(folderFile)
                            }
                        }

                        binding.btnFeaturedInfo?.setOnClickListener {
                            val action = HomeFragmentDirections.actionHomeFragmentToSeriesDetailFragment(
                                name = featured.title,
                                folderId = featured.folderId,
                                posterUrl = featured.posterUrl ?: featured.backdropUrl
                            )
                            findNavController().navigateSafe(action)
                        }
                    } else {
                        if (currentTab == "Início") {
                            binding.featuredHeroContainer?.visibility = View.GONE
                            val hasRecent = viewModel.recentWatches.value.isNotEmpty()
                            val hasLibrary = viewModel.animeLibrary.value.isNotEmpty()
                            binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasLibrary) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun scheduleFeaturedPreview(folder: DriveFile, focusAnchor: View) {
        if (currentTab != "Início" || !(folder.isFolder || folder.isShortcutFolder)) return
        featuredPreviewScheduler?.schedule(folder) {
            _binding != null && currentTab == "Início" &&
                focusAnchor.isAttachedToWindow && focusAnchor.hasFocus()
        }
    }

    private fun findLibraryFolderForTitle(fileName: String): DriveFile? {
        val parsed = zechs.drive.stream.utils.EpisodeParser.parse(fileName)
        val title = parsed.showTitle.ifBlank { parsed.cleanTitle }
        val normalized = zechs.drive.stream.data.remote.AnimePosterResolver
            .cleanAnimeTitle(title)
            .trim()
            .lowercase(java.util.Locale.ROOT)
        if (normalized.isBlank()) return null
        return viewModel.animeLibrary.value.firstOrNull { file ->
            (file.isFolder || file.isShortcutFolder) &&
                zechs.drive.stream.data.remote.AnimePosterResolver.cleanAnimeTitle(file.name)
                    .trim().lowercase(java.util.Locale.ROOT) == normalized
        }
    }

    private fun handleQuickPlay(file: DriveFile) {
        if (file.isVideoFile || file.isShortcutVideo) {
            val target = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                file.copy(id = file.shortcutDetails.targetId)
            } else file
            launchVideoPlayer(target)
        } else if (file.isFolder || file.isShortcutFolder) {
            android.widget.Toast.makeText(context, "Iniciando ${file.name}...", android.widget.Toast.LENGTH_SHORT).show()
            viewModel.getResumeOrFirstEpisode(file) { video, startPos ->
                if (video != null) {
                    launchVideoPlayer(video, startPos)
                } else {
                    handleOpenFolder(file)
                }
            }
        } else {
            handleOpenFolder(file)
        }
    }

    private fun handleOpenFolder(file: DriveFile) {
        val isFolder = file.isFolder || file.isShortcutFolder
        if (isFolder) {
            val folderId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
            viewModel.recordFolderOpened(folderId, file.name)
            val action = HomeFragmentDirections.actionHomeFragmentToSeriesDetailFragment(
                name = file.name,
                folderId = folderId,
                posterUrl = file.posterUrl ?: file.thumbnailLarge ?: file.thumbnailLink
            )
            findNavController().navigateSafe(action)
        } else if (file.isVideoFile || file.isShortcutVideo) {
            launchVideoPlayer(file)
        } else {
            android.widget.Toast.makeText(requireContext(), file.name, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAnimeContextMenu(file: DriveFile) {
        val isStarred = file.starred == zechs.drive.stream.data.model.Starred.STARRED
        val options = mutableListOf("Abrir detalhes")
        val actions = mutableListOf<() -> Unit>({ handleOpenFolder(file) })

        options += if (isStarred) "Remover dos favoritos" else "Adicionar aos favoritos"
        actions += { viewModel.starFile(file, !isStarred) }

        if (file.isVideoFile || file.isShortcutVideo) {
            options += "Adicionar à fila"
            actions += { viewModel.addToQueue(file) }
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }

    private fun showWatchHistoryDialog() {
        val items = viewModel.watchHistory.value
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "O histórico está vazio", Toast.LENGTH_SHORT).show()
            return
        }
        val menuItems = items.mapIndexed { index, watch ->
            zechs.drive.stream.ui.player.GlassMenuItem(
                id = "history_$index",
                title = watch.name,
                subtitle = watch.watchProgress().let { if (it > 0) "$it% assistido" else null },
                tag = watch
            )
        }
        zechs.drive.stream.ui.player.PlayerGlassMenuDialog(
            context = requireContext(),
            title = "Histórico recente",
            items = menuItems
        ) { selected ->
            (selected.tag as? zechs.drive.stream.data.model.WatchList)?.let { playWatchItem(it) }
        }.show()
    }

    private fun showWatchQueueDialog() {
        // Navigate to the new QueueFragment instead of showing dialog
        val action = HomeFragmentDirections.actionHomeFragmentToQueueFragment()
        findNavController().navigateSafe(action)
    }

    private fun playQueueItem(item: WatchQueueItem) {
        playWatchItem(
            WatchList(
                name = item.name,
                videoId = item.fileId,
                watchedDuration = 0L,
                totalDuration = 0L,
                thumbnailLink = item.posterUrl
            )
        )
    }

    private fun showQueueItemContextMenu(item: WatchQueueItem) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(item.name)
            .setItems(arrayOf("Reproduzir", "Remover da fila")) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    playWatchItem(
                        WatchList(
                            name = item.name,
                            videoId = item.fileId,
                            watchedDuration = 0L,
                            totalDuration = 0L,
                            thumbnailLink = item.posterUrl
                        )
                    )
                } else {
                    viewModel.removeFromQueue(item.fileId)
                }
            }
            .show()
    }

    private fun launchVideoPlayer(file: DriveFile, startPosition: Long = -1L) {
        val fileId = file.id
        val thumb = file.thumbnailLarge ?: file.posterUrl ?: file.thumbnailLink
        val catalogPlaylist: ArrayList<PlaylistItem>? = run {
            val videoFiles = animeAdapter.currentList.mapNotNull { (it as? FilesDataModel.File)?.driveFile }.filter { it.isVideoFile }
            if (videoFiles.size > 1 && videoFiles.any { it.id == fileId }) {
                ArrayList(videoFiles.map { PlaylistItem(it.id, it.name, it.thumbnailLarge ?: it.thumbnailLink) })
            } else null
        }
        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                PlayerLauncher.launch(
                    context = requireContext(),
                    playerType = VideoPlayer.EXO_PLAYER,
                    fileId = fileId,
                    title = file.name,
                    thumbnailLink = thumb,
                    themeIndex = mainViewModel.currentThemeIndex,
                    playlist = catalogPlaylist,
                    startPosition = startPosition
                )
            }
            VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), getString(R.string.starting_mpv), android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(fileId, file.name, thumb)
            }
        }
    }

    private fun showContinueWatchingContextMenu(watchItem: WatchList, focusAnchor: View) {
        zechs.drive.stream.ui.home.ContinueWatchingContextMenu.show(
            context = requireContext(),
            watchItem = watchItem,
            focusAnchor = focusAnchor
        ) { action ->
            when (action) {
                zechs.drive.stream.ui.home.ContinueWatchingContextMenu.Action.RESUME ->
                    playWatchItem(watchItem, startFromBeginning = false)
                zechs.drive.stream.ui.home.ContinueWatchingContextMenu.Action.RESTART ->
                    playWatchItem(watchItem, startFromBeginning = true)
                zechs.drive.stream.ui.home.ContinueWatchingContextMenu.Action.OPEN_SERIES -> {
                    val series = findLibraryFolderForTitle(watchItem.name)
                    if (series != null) handleOpenFolder(series)
                    else Toast.makeText(requireContext(), R.string.home_series_not_found, Toast.LENGTH_SHORT).show()
                }
                zechs.drive.stream.ui.home.ContinueWatchingContextMenu.Action.MARK_FINISHED -> {
                    viewModel.markWatchItemFinished(watchItem)
                    com.google.android.material.snackbar.Snackbar.make(binding.root, R.string.home_marked_finished, 1500).show()
                }
                zechs.drive.stream.ui.home.ContinueWatchingContextMenu.Action.REMOVE -> {
                    viewModel.removeWatchItem(watchItem)
                    com.google.android.material.snackbar.Snackbar.make(binding.root, R.string.home_continue_removed, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { viewModel.restoreWatchItem(watchItem) }
                        .show()
                }
            }
        }
    }

    private fun playWatchItem(watchItem: WatchList, startFromBeginning: Boolean = false) {
        val startPos = if (startFromBeginning || watchItem.hasFinished()) {
            0L
        } else if (watchItem.watchedDuration > 0L) {
            watchItem.watchedDuration
        } else {
            -1L
        }
        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                PlayerLauncher.launch(
                    context = requireContext(),
                    playerType = VideoPlayer.EXO_PLAYER,
                    fileId = watchItem.videoId,
                    title = watchItem.name,
                    thumbnailLink = watchItem.thumbnailLink,
                    themeIndex = mainViewModel.currentThemeIndex,
                    startPosition = startPos
                )
            }
            VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), getString(R.string.starting_mpv), android.widget.Toast.LENGTH_SHORT).show()
                pendingMpvStartPosition = startPos
                viewModel.fetchToken(watchItem.videoId, watchItem.name, watchItem.thumbnailLink)
            }
        }
    }

    private fun observeMpv() {
        viewModel.mpvFile.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { resource ->
                when (resource) {
                    is zechs.drive.stream.utils.state.Resource.Success -> {
                        val file = resource.data
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
                    is zechs.drive.stream.utils.state.Resource.Error -> {
                        android.widget.Toast.makeText(
                            requireContext(),
                            resource.message ?: "Erro ao iniciar MPV",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeLogOutState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasLoggedOut.collect {
                    if (it) {
                        requireActivity().finish()
                        delay(250L)
                        requireActivity().startActivity(requireActivity().intent)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        featuredPreviewScheduler?.cancel()
        featuredPreviewScheduler = null
        featuredHeroBinder = null
        super.onDestroyView()
        _binding = null
    }
}
