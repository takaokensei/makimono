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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.ui.home.adapter.WatchQueueShelfAdapter
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.GlideApp
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.VideoPlayer
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

    private var isGridMode = true
    private var currentTab = "Início"
    private var previousTabBeforeSearch: String? = null
    private var isSidebarExpanded = false
    private var lastFocusedAnimeView: View? = null
    private var lastFocusedItemId: String? = null
    private var lastProfileConfiguration: String? = null

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
            }
        )
    }

    private var pendingMpvStartPosition: Long? = null

    private val continueWatchingAdapter = ContinueWatchingAdapter { watchItem ->
        playWatchItem(watchItem)
    }.apply {
        onLongClickListener = { watchItem ->
            showContinueWatchingContextMenu(watchItem)
        }
    }

    private val watchQueueAdapter = WatchQueueShelfAdapter { item ->
        playQueueItem(item)
    }.apply {
        onLongClickListener = { item ->
            showQueueItemContextMenu(item)
        }
    }

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

        setupAnimeGrid()
        setupHeaderSearch()
        setupVoiceSearch()
        setupViewToggle()
        setupSidebarNavigation()
        setupBrandLogo()
        setupBackPressedHandling()
        setupHeroAndProfile()

        binding.rvContinueWatchingShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvContinueWatchingShelf.adapter = continueWatchingAdapter

        binding.rvWatchQueueShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvWatchQueueShelf.adapter = watchQueueAdapter
        watchQueueAdapter.onDpadLeftListener = {
            if (usesOverlaySidebar()) expandSidebar() else focusCurrentNavItem()
            true
        }
        watchQueueAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
        }

        setupTenFootFocusChain()

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
        observeFeaturedAnime()
        observeProfileConfiguration()
        observeLogOutState()
        observeMpv()

        // Set initial tab state to Início
        selectTab("Início")

        // Load anime library immediately on opening
        viewModel.loadAnimeLibrary()
        viewModel.getRecentWatches()
        viewModel.getWatchHistory()
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

        val contentWidth = resources.configuration.screenWidthDp - 128 - 44
        return (contentWidth / 108).coerceIn(3, 8)
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
        play?.nextFocusLeftId = binding.btnNavInicio.id
        play?.nextFocusRightId = info?.id ?: View.NO_ID
        play?.nextFocusDownId = continueShelf.id
        info?.nextFocusLeftId = play?.id ?: View.NO_ID
        info?.nextFocusDownId = continueShelf.id
        continueShelf.nextFocusUpId = play?.id ?: search.id
        continueShelf.nextFocusDownId = queueShelf?.id ?: catalog.id
        queueShelf?.nextFocusUpId = continueShelf.id
        queueShelf?.nextFocusDownId = catalog.id
        catalog.nextFocusUpId = queueShelf?.id ?: continueShelf.id
        catalog.nextFocusLeftId = binding.btnNavInicio.id
    }

    private fun setupAnimeGrid() {
        val spanCount = if (isGridMode) getResponsiveSpanCount() else 1
        animeAdapter.isGridMode = isGridMode
        binding.rvAnimeLibrary.layoutManager = GridLayoutManager(context, spanCount)
        binding.rvAnimeLibrary.adapter = animeAdapter
    }

    private fun setupHeaderSearch() {
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
            v.animate().scaleX(if (hasFocus) 1.15f else 1.0f).scaleY(if (hasFocus) 1.15f else 1.0f).setDuration(120L).start()
            val context = requireContext()
            val activeColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
            val inactiveColor = context.resolveThemeColor(R.attr.colorTextSecondary)
            binding.btnVoiceSearch.imageTintList = android.content.res.ColorStateList.valueOf(
                if (hasFocus) activeColor else inactiveColor
            )
        }
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
                        query = "'$resolvedId' in parents and trashed = false"
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
                item.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(120L).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                    }
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
            val pos = binding.rvAnimeLibrary.getChildAdapterPosition(v)
            if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION && pos < animeAdapter.currentList.size) {
                val item = animeAdapter.currentList[pos]
                if (item is FilesDataModel.File) {
                    lastFocusedItemId = item.driveFile.id
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
        binding.rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.shelfHeaderRow?.visibility = if (hasRecent || hasQueue) View.VISIBLE else View.GONE
        binding.tvShelfLabel?.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.tvShelfViewHistory?.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.tvShelfViewQueue?.visibility = if (hasQueue) View.VISIBLE else View.GONE
        binding.tvQueueShelfLabel?.visibility = if (hasQueue) View.VISIBLE else View.GONE
        binding.rvWatchQueueShelf?.visibility = if (hasQueue) View.VISIBLE else View.GONE
        binding.tvShelfLabel?.text = "Continuar assistindo"
    }

    override fun onResume() {
        super.onResume()
        findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("homeTab")?.let { tab ->
            selectTab(tab)
            viewModel.filterStarred(tab == "Favoritos")
        }
        viewModel.getRecentWatches()
        viewModel.getWatchHistory()
        viewModel.getLastWatched()
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
                v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(8f).setDuration(120L).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
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
                v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(8f).setDuration(120L).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
            }
        }

        binding.userProfilePill?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(6f).setDuration(120L).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
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
                    binding.ivUserAvatar?.let { avatar ->
                        com.bumptech.glide.Glide.with(this@HomeFragment).load(profile.avatarUrl)
                            .placeholder(avatarRes).error(avatarRes).into(avatar)
                    }
                }
            }
        }
    }

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    private fun selectTab(tab: String) {
        currentTab = tab
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
                    tvQueueShelfLabel?.visibility = View.GONE
                    tvShelfViewQueue?.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                }
            }

            val normalBg = R.drawable.rail_item_focus_bg
            val activeBg = R.drawable.nav_item_active_bg
            val normalTextColor = requireContext().resolveThemeColor(R.attr.colorTextSecondary)
            val activeTextColor = android.graphics.Color.WHITE
            val normalIconColor = requireContext().resolveThemeColor(R.attr.colorAccentPrimary)
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
                        val dataModels = animes.map { FilesDataModel.File(it) }
                        animeAdapter.submitList(dataModels)
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
                            binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasQueue && viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value) View.VISIBLE else View.GONE
                        }
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
                                    else -> state.message
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

    private fun observeFeaturedAnime() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.featuredAnime.collect { featured ->
                    if (featured != null) {
                        if (currentTab == "Início") {
                            binding.featuredHeroContainer?.visibility = View.VISIBLE
                            binding.layoutHomeEmpty?.visibility = View.GONE
                        }
                        binding.tvFeaturedTitle?.text = featured.title

                        if (!featured.titleJapanese.isNullOrBlank()) {
                            binding.tvFeaturedJapaneseTitle?.text = featured.titleJapanese
                            binding.tvFeaturedJapaneseTitle?.visibility = View.VISIBLE
                        } else {
                            binding.tvFeaturedJapaneseTitle?.visibility = View.GONE
                        }

                        val cleanSynopsis = featured.synopsis
                            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                            ?.replace(Regex("<[^>]+>"), "")
                            ?.replace(Regex("\\s+"), " ")
                            ?.trim()
                        if (!cleanSynopsis.isNullOrBlank()) {
                            binding.tvFeaturedSynopsis?.text = cleanSynopsis
                            binding.tvFeaturedSynopsis?.visibility = View.VISIBLE
                        } else {
                            binding.tvFeaturedSynopsis?.visibility = View.GONE
                        }

                        // Dynamic genre pills
                        binding.layoutFeaturedGenres?.removeAllViews()
                        featured.genres.take(4).forEach { genreName ->
                            val pill = android.widget.TextView(requireContext()).apply {
                                text = genreName
                                textSize = 10f
                                setTextColor(requireContext().resolveThemeColor(R.attr.colorTextSecondary))
                                setBackgroundResource(R.drawable.tag_genre_pill_bg)
                                setPadding(18, 6, 18, 6)
                                val params = android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    marginEnd = 12
                                }
                                layoutParams = params
                            }
                            binding.layoutFeaturedGenres?.addView(pill)
                        }

                        val imgToLoad = featured.backdropUrl ?: featured.posterUrl
                        if (!imgToLoad.isNullOrBlank()) {
                            binding.ivFeaturedBackdrop?.let { iv ->
                                MediaImageLoader.backdrop(iv, imgToLoad)
                            }
                        }

                        binding.btnFeaturedPlay?.setOnClickListener {
                            val file = viewModel.animeLibrary.value.firstOrNull {
                                it.id == featured.folderId || it.shortcutDetails.targetId == featured.folderId
                            }
                            if (file != null) {
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
        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                PlayerLauncher.launch(
                    context = requireContext(),
                    playerType = VideoPlayer.EXO_PLAYER,
                    fileId = fileId,
                    title = file.name,
                    thumbnailLink = thumb,
                    themeIndex = mainViewModel.currentThemeIndex,
                    startPosition = startPosition
                )
            }
            VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), getString(R.string.starting_mpv), android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(fileId, file.name, thumb)
            }
        }
    }

    private fun showContinueWatchingContextMenu(watchItem: WatchList) {
        val parsed = zechs.drive.stream.utils.EpisodeParser.parse(watchItem.name)
        val title = parsed.showTitle.ifBlank { parsed.cleanTitle }

        val options = arrayOf(
            "Continuar assistindo (${watchItem.watchProgress()}%)",
            "Assistir do início (0:00)",
            "Marcar como concluído",
            "Remover de Continuar Assistindo"
        )

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(title)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> playWatchItem(watchItem, startFromBeginning = false)
                    1 -> playWatchItem(watchItem, startFromBeginning = true)
                    2 -> {
                        viewModel.markWatchItemFinished(watchItem)
                        com.google.android.material.snackbar.Snackbar.make(binding.root, "Episódio marcado como concluído", 1500).show()
                    }
                    3 -> {
                        viewModel.removeWatchItem(watchItem)
                        com.google.android.material.snackbar.Snackbar.make(binding.root, "Item removido de Continuar Assistindo", 1500).show()
                    }
                }
            }
            .show()
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
        super.onDestroyView()
        _binding = null
    }
}
