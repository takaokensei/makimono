package zechs.drive.stream.ui.files

import android.os.Bundle
import zechs.drive.stream.utils.AppLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.databinding.FragmentFilesBinding
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.FocusMemory
import zechs.drive.stream.utils.SeasonEpisodeGrouper
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind

@AndroidEntryPoint
class FilesFragment : BaseFragment() {

    companion object {
        const val TAG = "FilesFragment"
    }

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel by activityViewModels<MainViewModel>()
    private val viewModel by lazy {
        ViewModelProvider(this)[FilesViewModel::class.java]
    }

    private val args by navArgs<FilesFragmentArgs>()

    @javax.inject.Inject
    lateinit var tenraiService: dagger.Lazy<zechs.drive.stream.data.remote.TenraiAnimeService>

    @javax.inject.Inject
    lateinit var profileManager: zechs.drive.stream.utils.ProfileManager

    @javax.inject.Inject
    lateinit var profileArtCatalog: zechs.drive.stream.data.remote.ProfileArtCatalog

    private lateinit var stateHandler: FilesStateHandler
    private lateinit var paginationHandler: FilesPaginationHandler
    private lateinit var searchFilterHandler: FilesSearchFilterHandler

    private val filesAdapter by lazy {
        FilesAdapter(
            onClickListener = { handleFileOnClick(it) },
            onLongClickListener = { handleFileOnLongPress(it) },
            onStarClickListener = { file, isStarred ->
                viewModel.starFile(file, isStarred)
            },
            onPlayClickListener = { file ->
                handleFileOnPlayClick(file)
            },
            onPageErrorAction = { errorKind ->
                when (errorKind) {
                    ResourceErrorKind.AUTHENTICATION ->
                        findNavController().navigate(R.id.action_filesFragment_to_signInFragment)
                    ResourceErrorKind.ACCESS_DENIED,
                    ResourceErrorKind.NOT_FOUND -> findNavController().popBackStack()
                    else -> viewModel.queryFiles(args.query)
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(
            inflater, container, /* attachToParent */false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFilesBinding.bind(view)

        TvAccessibilityLayout.adaptFilesChrome(
            requireContext(),
            view.findViewById(R.id.filesNavRail),
            listOf(
                view.findViewById(R.id.userProfilePill),
                view.findViewById(R.id.filesBusca),
                view.findViewById(R.id.filesHome),
                view.findViewById(R.id.filesFavorites),
                view.findViewById(R.id.filesHistory),
                view.findViewById(R.id.filesDownloads),
                view.findViewById(R.id.filesCatalog),
                view.findViewById(R.id.filesFilmes),
                view.findViewById(R.id.filesFolders),
                view.findViewById(R.id.filesSettings)
            ),
            view.findViewById(R.id.searchContainer),
            binding.etSearch,
            listOf(binding.btnClearSearch, binding.btnToggleGrid)
        )

        val colorBackground = MaterialColors.getColor(view, android.R.attr.colorBackground)
        view.setBackgroundColor(colorBackground)

        binding.toolbar.apply {
            title = args.name
            setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }

        AppLog.d(TAG, "FilesFragment(name=${args.name}, query=${args.query})")

        val folderMatch = Regex("'([^']+)' in parents").find(args.query ?: "")
        if (folderMatch != null) {
            val folderId = folderMatch.groupValues[1]
            if (folderId != "root") {
                viewModel.recordFolderOpened(folderId, args.name)
            }
        }

        initHandlers()
        setupNavRail()
        setupRecyclerView()
        searchFilterHandler.setup()
        setupFilesFocusNavigation()
        setupFilesObserver()
        mpvObserver()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fileUpdate.collect { status ->
                    showSnackBar(status)
                }
            }
        }
    }

    private fun initHandlers() {
        paginationHandler = FilesPaginationHandler(
            recyclerView = binding.rvList,
            filesAdapter = filesAdapter,
            viewModel = viewModel,
            folderQuery = args.query,
            onPageRequested = { query -> viewModel.queryFiles(query) }
        )

        searchFilterHandler = FilesSearchFilterHandler(
            context = requireContext(),
            binding = binding,
            filesAdapter = filesAdapter,
            folderName = args.name,
            tenraiService = tenraiService,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            isFragmentAdded = { isAdded },
            isLoadingProvider = { paginationHandler.isLoading },
            onListSubmitted = { focusFilesContent(forceToList = false) }
        )

        stateHandler = FilesStateHandler(
            binding = binding,
            viewModel = viewModel,
            onContentReady = { files ->
                paginationHandler.isLoading = false
                searchFilterHandler.onFilesLoaded(files)
            },
            onReauthenticate = {
                findNavController().navigateSafe(R.id.action_filesFragment_to_signInFragment)
            }
        )
    }

    private var isFilesRailExpanded = false
    private var filesRailAnimator: android.animation.ValueAnimator? = null
    private var filesRailFocusListener: android.view.ViewTreeObserver.OnGlobalFocusChangeListener? = null
    private var hasRestoredInitialFocus = false

    private fun focusFilesContent(forceToList: Boolean) {
        if (_binding == null || !isAdded) return
        val folderKey = args.folderPath?.lastOrNull() ?: args.name
        val restored = FocusMemory.restore(binding.rvList, "files.folder.$folderKey") { pos ->
            (filesAdapter.currentList.getOrNull(pos) as? FilesDataModel.File)?.driveFile?.id
        }
        if (restored) {
            hasRestoredInitialFocus = true
            return
        }
        val canAutoFocusList = !hasRestoredInitialFocus &&
            (binding.etSearch.hasFocus() || binding.root.findFocus() == null) &&
            binding.etSearch.text.isNullOrEmpty()
        if (filesAdapter.currentList.isNotEmpty() && (forceToList || canAutoFocusList)) {
            hasRestoredInitialFocus = true
            val targetPos = (viewModel.lastFocusedPosition ?: 0).coerceIn(0, filesAdapter.currentList.size - 1)
            binding.rvList.scrollToPosition(targetPos)
            binding.rvList.post {
                if (_binding == null) return@post
                (binding.rvList.findViewHolderForAdapterPosition(targetPos)?.itemView
                    ?: binding.rvList.getChildAt(0))?.requestFocus()
            }
        } else if (forceToList) {
            binding.etSearch.requestFocus()
        }
    }

    private fun isFocusInRail(currentFocus: View?): Boolean {
        if (currentFocus == null) return false
        val root = _binding?.root ?: return false
        val rail = root.findViewById<View>(R.id.filesNavRail) ?: return false
        var view: View? = currentFocus
        while (view != null) {
            if (view === rail) return true
            view = view.parent as? View
        }
        return false
    }

    private fun setupNavRail() {
        val rail = binding.root.findViewById<View>(R.id.filesNavRail) ?: return
        val dimOverlay = binding.root.findViewById<View>(R.id.filesSidebarDimOverlay)
        val isTenFoot = zechs.drive.stream.utils.DeviceUi.isTenFootExperience(requireContext())

        fun returnHome(tab: String) {
            val nav = findNavController()
            nav.getBackStackEntry(R.id.homeFragment).savedStateHandle["homeTab"] = tab
            nav.popBackStack(R.id.homeFragment, false)
        }

        val collapsedPx = resources.getDimensionPixelSize(R.dimen.tv_nav_rail_width)
        val expandedPx = resources.getDimensionPixelSize(R.dimen.tv_nav_rail_expanded_width)

        val normalTextColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.obsidian_text_secondary)
        val activeTextColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.obsidian_text_primary)
        val typedValue = android.util.TypedValue()
        val activeAccentColor = if (requireContext().theme.resolveAttribute(R.attr.colorAccentPrimary, typedValue, true)) {
            typedValue.data
        } else {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.crunchyroll_accent_primary)
        }
        val focusedDarkColor = if (requireContext().theme.resolveAttribute(R.attr.colorOnPrimary, typedValue, true)) {
            typedValue.data
        } else {
            android.graphics.Color.parseColor("#07080B")
        }
        val focusedDarkSubtitleColor = android.graphics.Color.parseColor("#1E293B")

        fun updateFilesRailStyles() {
            val root = _binding?.root ?: return
            val userProfilePill = root.findViewById<View>(R.id.userProfilePill)
            val profileFocused = userProfilePill?.hasFocus() == true
            root.findViewById<TextView>(R.id.tvUserName)?.setTextColor(
                if (profileFocused) focusedDarkColor else activeTextColor
            )
            root.findViewById<TextView>(R.id.tvSwitchProfileSubtitle)?.setTextColor(
                if (profileFocused) focusedDarkSubtitleColor else normalTextColor
            )
            userProfilePill?.setBackgroundResource(
                if (profileFocused) R.drawable.rail_item_focus_bg else android.R.color.transparent
            )

            fun styleItem(containerId: Int, labelId: Int, iconId: Int, isActive: Boolean) {
                val container = root.findViewById<View>(containerId) ?: return
                val label = root.findViewById<TextView>(labelId)
                val icon = root.findViewById<android.widget.ImageView>(iconId)
                val isFocused = container.hasFocus()

                container.isSelected = isActive
                container.setBackgroundResource(
                    when {
                        isFocused -> R.drawable.rail_item_focus_bg
                        isActive -> R.drawable.nav_item_active_bg
                        else -> R.drawable.rail_item_focus_bg
                    }
                )

                val textColor = when {
                    isFocused -> focusedDarkColor
                    isActive -> activeTextColor
                    else -> normalTextColor
                }
                val iconColor = when {
                    isFocused -> focusedDarkColor
                    isActive -> activeAccentColor
                    else -> normalTextColor
                }
                label?.setTextColor(textColor)
                icon?.imageTintList = android.content.res.ColorStateList.valueOf(iconColor)
            }

            styleItem(R.id.filesBusca, R.id.tvFilesBusca, R.id.ivFilesBuscaIcon, false)
            styleItem(R.id.filesHome, R.id.tvFilesHome, R.id.ivFilesHomeIcon, false)
            styleItem(R.id.filesFavorites, R.id.tvFilesFavorites, R.id.ivFilesFavoritesIcon, false)
            styleItem(R.id.filesHistory, R.id.tvFilesHistory, R.id.ivFilesHistoryIcon, false)
            styleItem(R.id.filesDownloads, R.id.tvFilesDownloads, R.id.ivFilesDownloadsIcon, false)
            styleItem(R.id.filesCatalog, R.id.tvFilesCatalog, R.id.ivFilesCatalogIcon, false)
            styleItem(R.id.filesFilmes, R.id.tvFilesFilmes, R.id.ivFilesFilmesIcon, false)
            styleItem(R.id.filesFolders, R.id.tvFilesFolders, R.id.ivFilesFoldersIcon, true)
            styleItem(R.id.filesSettings, R.id.tvFilesSettings, R.id.ivFilesSettingsIcon, false)
        }

        fun setLabelsVisible(visible: Boolean) {
            val root = _binding?.root ?: return
            val v = if (visible) View.VISIBLE else View.GONE
            listOf(
                R.id.tvBrandLogoText,
                R.id.tvUserName,
                R.id.tvSwitchProfileSubtitle,
                R.id.tvFilesBusca,
                R.id.tvFilesHome,
                R.id.tvFilesFavorites,
                R.id.tvFilesHistory,
                R.id.tvFilesDownloads,
                R.id.tvFilesCatalog,
                R.id.tvFilesFilmes,
                R.id.tvFilesFolders,
                R.id.tvFilesSettings
            ).forEach { id ->
                root.findViewById<View>(id)?.visibility = v
            }
            updateFilesRailStyles()
        }

        fun expandRail() {
            if (!isTenFoot || isFilesRailExpanded) return
            isFilesRailExpanded = true
            rail.setBackgroundResource(R.drawable.bg_sidebar_panel_expanded)
            dimOverlay?.apply {
                animate().cancel()
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .setDuration(220L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            filesRailAnimator?.cancel()
            val currentWidth = rail.layoutParams.width.takeIf { it > 0 } ?: collapsedPx
            filesRailAnimator = android.animation.ValueAnimator.ofInt(currentWidth, expandedPx).apply {
                duration = 220L
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { anim ->
                    rail.layoutParams.width = anim.animatedValue as Int
                    rail.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        setLabelsVisible(true)
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        rail.layoutParams.width = expandedPx
                        rail.requestLayout()
                    }
                })
                start()
            }
        }

        fun collapseRail() {
            if (!isTenFoot || !isFilesRailExpanded) return
            isFilesRailExpanded = false
            dimOverlay?.apply {
                animate().cancel()
                animate()
                    .alpha(0f)
                    .setDuration(200L)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction { visibility = View.GONE }
                    .start()
            }
            filesRailAnimator?.cancel()
            val currentWidth = rail.layoutParams.width.takeIf { it > 0 } ?: expandedPx
            filesRailAnimator = android.animation.ValueAnimator.ofInt(currentWidth, collapsedPx).apply {
                duration = 200L
                interpolator = android.view.animation.AccelerateInterpolator()
                addUpdateListener { anim ->
                    rail.layoutParams.width = anim.animatedValue as Int
                    rail.requestLayout()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        setLabelsVisible(false)
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        rail.layoutParams.width = collapsedPx
                        rail.requestLayout()
                        if (!isFilesRailExpanded) {
                            rail.setBackgroundResource(R.drawable.bg_sidebar_panel)
                        }
                    }
                })
                start()
            }
        }

        dimOverlay?.setOnClickListener { collapseRail() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFilesRailExpanded) {
                    collapseRail()
                    binding.rvList.requestFocus()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.root.findViewById<View>(R.id.filesBusca)?.setOnClickListener { returnHome("Buscar") }
        binding.root.findViewById<View>(R.id.filesHome)?.setOnClickListener { returnHome("Início") }
        binding.root.findViewById<View>(R.id.filesFavorites)?.setOnClickListener { returnHome("Favoritos") }
        binding.root.findViewById<View>(R.id.filesHistory)?.setOnClickListener { returnHome("Histórico") }
        binding.root.findViewById<View>(R.id.filesDownloads)?.setOnClickListener {
            findNavController().navigateSafe(R.id.action_filesFragment_to_downloadsFragment)
        }
        binding.root.findViewById<View>(R.id.filesCatalog)?.setOnClickListener { returnHome("Animes") }
        binding.root.findViewById<View>(R.id.filesFilmes)?.setOnClickListener { returnHome("Filmes") }
        binding.root.findViewById<View>(R.id.filesFolders)?.setOnClickListener {
            collapseRail()
            focusFilesContent(forceToList = true)
        }
        binding.root.findViewById<View>(R.id.userProfilePill)?.setOnClickListener {
            findNavController().navigateSafe(R.id.action_filesFragment_to_profileSelectionFragment)
        }
        binding.root.findViewById<View>(R.id.filesSettings)?.setOnClickListener {
            findNavController().navigateSafe(R.id.action_filesFragment_to_settingsFragment)
        }

        val navItems = listOfNotNull(
            binding.root.findViewById<View>(R.id.userProfilePill),
            binding.root.findViewById<View>(R.id.filesBusca),
            binding.root.findViewById<View>(R.id.filesHome),
            binding.root.findViewById<View>(R.id.filesFavorites),
            binding.root.findViewById<View>(R.id.filesHistory),
            binding.root.findViewById<View>(R.id.filesDownloads),
            binding.root.findViewById<View>(R.id.filesCatalog),
            binding.root.findViewById<View>(R.id.filesFilmes),
            binding.root.findViewById<View>(R.id.filesFolders),
            binding.root.findViewById<View>(R.id.filesSettings)
        )
        navItems.forEachIndexed { index, item ->
            item.nextFocusUpId = navItems.getOrNull(index - 1)?.id ?: item.id
            item.nextFocusDownId = navItems.getOrNull(index + 1)?.id ?: item.id
            item.nextFocusLeftId = item.id
            item.setOnFocusChangeListener { _, _ ->
                updateFilesRailStyles()
            }
            item.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    collapseRail()
                    focusFilesContent(forceToList = true)
                    true
                } else false
            }
        }

        if (isTenFoot) {
            val listener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
                if (_binding == null) return@OnGlobalFocusChangeListener
                val wasInRail = isFocusInRail(oldFocus)
                val isInRail = isFocusInRail(newFocus)
                if (!wasInRail && isInRail) {
                    expandRail()
                } else if (wasInRail && !isInRail) {
                    collapseRail()
                }
                updateFilesRailStyles()
            }
            filesRailFocusListener = listener
            binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        }

        // Observe active profile for avatar & name
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collect { profile ->
                    binding.root.findViewById<TextView>(R.id.tvUserName)?.text = profile.name
                    val avatarRes = profileManager.getAvatarDrawableRes(profile.avatarResName)
                    val avatarSource: Any? = profile.avatarUrl
                        ?: if (profile.avatarResName == "avatar_caua") avatarRes
                        else profileArtCatalog.defaultAvatarForProfile(profile.id)?.url
                    binding.root.findViewById<android.widget.ImageView>(R.id.ivUserAvatar)?.let { avatar ->
                        avatar.imageTintList = null
                        avatar.clearColorFilter()
                        com.bumptech.glide.Glide.with(this@FilesFragment)
                            .load(avatarSource)
                            .placeholder(R.drawable.avatar_caua)
                            .error(R.drawable.avatar_caua)
                            .circleCrop()
                            .into(avatar)
                    }
                }
            }
        }

        updateFilesRailStyles()
    }

    override fun onStart() {
        super.onStart()
        if (!viewModel.hasLoaded || viewModel.shouldResumePageRequest) {
            viewModel.queryFiles(args.query)
        }
    }

    override fun onStop() {
        viewModel.cancelPendingPageRequest()
        super.onStop()
    }

    private fun setupFilesObserver() {
        if (!viewModel.hasLoaded) {
            viewModel.onEvent(FilesEvent.QueryFiles(args.query))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    paginationHandler.isLoading = (state is FilesUiState.Loading)
                    stateHandler.render(state)
                }
            }
        }
    }

    private fun setupFilesFocusNavigation() {
        val filesFolders = binding.root.findViewById<View>(R.id.filesFolders)
        binding.rvList.isFocusable = true
        binding.etSearch.nextFocusDownId = binding.rvList.id
        if (filesFolders != null) {
            binding.etSearch.nextFocusLeftId = filesFolders.id
        }
        binding.btnClearSearch.nextFocusDownId = binding.rvList.id
        binding.btnToggleGrid.nextFocusDownId = binding.rvList.id
        binding.rvList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val position = binding.rvList.getChildAdapterPosition(view)
                val spanCount = (binding.rvList.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)
                    ?.spanCount ?: 1
                if (position in 0 until spanCount) {
                    view.nextFocusUpId = binding.etSearch.id
                }
                if (filesFolders != null && position >= 0 && position % spanCount == 0) {
                    view.nextFocusLeftId = filesFolders.id
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
    }

    private fun handleFileOnPlayClick(file: DriveFile) {
        if (file.isVideoFile || file.isShortcutVideo) {
            val targetVideo = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                file.copy(id = file.shortcutDetails.targetId)
            } else file
            launchVideoPlayer(targetVideo)
        } else if (file.isFolder || file.isShortcutFolder) {
            val folderId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                file.shortcutDetails.targetId
            } else file.id
            Toast.makeText(context, getString(R.string.searching_episode), Toast.LENGTH_SHORT).show()
            viewModel.getFirstEpisodeInFolder(folderId) { firstEp ->
                if (firstEp != null) {
                    launchVideoPlayer(firstEp)
                } else {
                    handleFileOnClick(file)
                }
            }
        } else {
            handleFileOnClick(file)
        }
    }

    private fun handleFileOnLongPress(file: DriveFile) {
        AppLog.d(TAG, "handleFileOnLongPress: $file")
        val targetFile = if (file.isShortcut && file.shortcutDetails.targetId != null) {
            file.copy(id = file.shortcutDetails.targetId)
        } else file
        val isCurrentlyStarred = targetFile.starred == Starred.STARRED
        viewModel.starFile(targetFile, !isCurrentlyStarred)
    }

    private fun handleFileOnClick(file: DriveFile) {
        AppLog.d(TAG, file.toString())
        if (file.isFolder && !file.isShortcut) {
            openFolder(file, file.id)
        } else if (file.isVideoFile) {
            launchVideoPlayer(file)
        } else if (file.isSubtitleFile) {
            handleSubtitleFileClick(file)
        } else if (file.isShortcut) {
            if (file.isShortcutFolder) {
                val targetId = file.shortcutDetails.targetId
                if (targetId == null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.broken_shortcut_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                openFolder(file, targetId)
            } else if (file.isShortcutVideo) {
                val targetId = file.shortcutDetails.targetId
                if (targetId == null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.broken_shortcut_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                val videoShortcutFile = file.copy(id = targetId)
                launchVideoPlayer(videoShortcutFile)
            }
        }
    }

    private fun openFolder(file: DriveFile, targetFolderId: String) {
        val navigation = when (
            val decision = DriveFolderNavigationPolicy.decide(
                folderPath = args.folderPath?.toList(),
                currentQuery = args.query,
                targetFolderId = targetFolderId
            )
        ) {
            DriveFolderNavigationPolicy.Decision.Cycle -> {
                showSnackBar(getString(R.string.files_folder_cycle_error))
                return
            }
            is DriveFolderNavigationPolicy.Decision.Navigate -> decision
        }

        viewModel.recordFolderOpened(targetFolderId, file.name)
        val isDriveRoot = args.name == getString(R.string.my_drive) ||
                args.name == getString(R.string.shared_drives) ||
                args.name == getString(R.string.shared_with_me) ||
                args.name.equals("Root", ignoreCase = true)
        if (isDriveRoot) {
            val action = FilesFragmentDirections.actionFilesFragmentToSeriesDetailFragment(
                name = file.name,
                folderId = targetFolderId,
                posterUrl = file.posterUrl ?: file.thumbnailLarge ?: file.thumbnailLink
            )
            findNavController().navigate(action)
        } else {
            val isLocal = targetFolderId.startsWith(zechs.drive.stream.data.repository.LocalMediaRepository.LOCAL_BUCKET_PREFIX)
            val action = FilesFragmentDirections.actionFilesFragmentSelf(
                name = file.name,
                query = if (isLocal) targetFolderId else "'$targetFolderId' in parents and trashed=false",
                folderPath = navigation.folderPath.toTypedArray()
            )
            findNavController().navigate(action)
        }
    }

    private fun handleSubtitleFileClick(subFile: DriveFile) {
        val models = viewModel.filesList.value?.data ?: emptyList()
        val videoFiles = models.mapNotNull {
            if (it is FilesDataModel.File && it.driveFile.isVideoFile) it.driveFile else null
        }
        val cleanSub = subFile.name.lowercase()
        val matchingVideo = videoFiles.firstOrNull { video ->
            val cleanVideo = video.name.lowercase().substringBeforeLast(".")
            val vEp = EpisodeParser.parse(video.name).episode
            val sEp = EpisodeParser.parse(subFile.name).episode
            cleanSub.startsWith(cleanVideo) || (vEp != null && sEp != null && vEp == sEp)
        } ?: videoFiles.firstOrNull()

        if (matchingVideo != null) {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.external_subtitle_title)
                .setMessage(getString(R.string.external_subtitle_play_match, matchingVideo.name))
                .setPositiveButton("Reproduzir") { _, _ ->
                    launchVideoPlayer(matchingVideo)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.external_subtitle_title)
                .setMessage(getString(R.string.external_subtitle_no_match, subFile.name))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun launchVideoPlayer(file: DriveFile) {
        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> launchExo(file)
            VideoPlayer.MPV -> {
                Toast.makeText(context, getString(R.string.starting_mpv), Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(file)
            }
        }
    }

    private fun mpvObserver() {
        viewModel.mpvFile.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { res ->
                when (res) {
                    is Resource.Success -> launchMpv(res.data)
                    is Resource.Error -> showSnackBar(res.message)
                    else -> {}
                }
            }
        }
    }

    private fun getFolderSubtitles(): ArrayList<SubtitleItem> {
        val models = viewModel.filesList.value?.data ?: return arrayListOf()
        val subItems = models.mapNotNull { item ->
            if (item is FilesDataModel.File) {
                val f = item.driveFile
                if (f.isSubtitleFile) SubtitleItem(
                    id = f.id,
                    name = f.name,
                    source = f.source,
                    uri = f.localUri
                ) else null
            } else null
        }
        return ArrayList(subItems)
    }

    private fun getFolderPlaylist(): ArrayList<PlaylistItem> {
        val models = viewModel.filesList.value?.data ?: return arrayListOf()
        val videoItems = models.mapNotNull { item ->
            if (item is FilesDataModel.File) {
                val f = item.driveFile
                when {
                    f.isVideoFile -> PlaylistItem(f.id, f.name, f.thumbnailLink, f.localUri)
                    f.isShortcut && f.isShortcutVideo && f.shortcutDetails.targetId != null ->
                        PlaylistItem(f.shortcutDetails.targetId, f.name, f.thumbnailLink, f.localUri)
                    else -> null
                }
            } else null
        }
        val sorted = videoItems.sortedWith { a, b ->
            SeasonEpisodeGrouper.compareItems(
                a.title, EpisodeParser.parse(a.title),
                b.title, EpisodeParser.parse(b.title)
            )
        }
        return ArrayList(sorted)
    }

    private fun launchExo(file: DriveFile) {
        val playlist = getFolderPlaylist()
        val subtitles = getFolderSubtitles()
        PlayerLauncher.launch(
            context = requireContext(),
            playerType = VideoPlayer.EXO_PLAYER,
            fileId = file.id,
            title = file.name,
            seriesTitle = args.name,
            thumbnailLink = file.thumbnailLink,
            themeIndex = mainViewModel.currentThemeIndex,
            playlist = playlist,
            subtitles = subtitles,
            localUri = file.localUri
        )
    }

    private fun launchMpv(fileToken: FilesViewModel.FileToken) {
        val playlist = getFolderPlaylist()
        val subtitles = getFolderSubtitles()
        PlayerLauncher.launch(
            context = requireContext(),
            playerType = VideoPlayer.MPV,
            fileId = fileToken.fileId,
            title = fileToken.fileName,
            seriesTitle = args.name,
            accessToken = fileToken.accessToken.takeIf { it.isNotBlank() },
            thumbnailLink = fileToken.thumbnailLink,
            themeIndex = mainViewModel.currentThemeIndex,
            playlist = playlist,
            subtitles = subtitles,
            localUri = fileToken.localUri
        )
    }

    private fun setupRecyclerView() {
        binding.rvList.apply {
            adapter = filesAdapter
            itemAnimator = null
            addOnScrollListener(paginationHandler.scrollListener)
        }
        filesAdapter.onFocusItemListener = { focusedView ->
            val position = binding.rvList.getChildAdapterPosition(focusedView)
            if (position != RecyclerView.NO_POSITION) {
                viewModel.onEvent(FilesEvent.SaveFocus(position))
                (filesAdapter.currentList.getOrNull(position) as? FilesDataModel.File)?.let { fileModel ->
                    val folderKey = args.folderPath?.lastOrNull() ?: args.name
                    FocusMemory.remember("files.folder.$folderKey", position, fileModel.driveFile.id)
                }
                paginationHandler.handleDpadFocusNearEnd(position)
            }
        }
        filesAdapter.onDpadLeftListener = { itemView ->
            val pos = binding.rvList.getChildAdapterPosition(itemView)
            val span = (binding.rvList.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount ?: 1
            val filesFolders = _binding?.root?.findViewById<View>(R.id.filesFolders)
            if (filesFolders != null && pos != RecyclerView.NO_POSITION && pos % span == 0) {
                filesFolders.requestFocus()
                true
            } else false
        }
        searchFilterHandler.updateLayoutMode()
    }

    private fun showSnackBar(message: String?) {
        val snackBar = Snackbar.make(
            binding.root,
            message ?: getString(R.string.something_went_wrong),
            Snackbar.LENGTH_SHORT
        )
        val snackBarView = snackBar.view
        val textView = snackBarView.findViewById<View>(
            com.google.android.material.R.id.snackbar_text
        ) as TextView
        textView.maxLines = 5
        snackBar.show()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            focusFilesContent(forceToList = false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        filesRailFocusListener?.let { listener ->
            if (binding.root.viewTreeObserver.isAlive) {
                binding.root.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
            }
        }
        filesRailFocusListener = null
        filesRailAnimator?.cancel()
        filesRailAnimator = null
        hasRestoredInitialFocus = false
        stateHandler.destroy()
        binding.rvList.adapter = null
        _binding = null
    }
}
