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
                view.findViewById(R.id.filesHome),
                view.findViewById(R.id.filesCatalog),
                view.findViewById(R.id.filesFolders),
                view.findViewById(R.id.filesFavorites),
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
            isLoadingProvider = { paginationHandler.isLoading }
        )

        stateHandler = FilesStateHandler(
            binding = binding,
            viewModel = viewModel,
            onContentReady = { files ->
                paginationHandler.isLoading = false
                searchFilterHandler.onFilesLoaded(files)
            }
        )
    }

    private fun setupNavRail() {
        fun returnHome(tab: String) {
            val nav = findNavController()
            nav.getBackStackEntry(R.id.homeFragment).savedStateHandle["homeTab"] = tab
            nav.popBackStack(R.id.homeFragment, false)
        }
        binding.root.findViewById<View>(R.id.filesHome)?.setOnClickListener { returnHome("Início") }
        binding.root.findViewById<View>(R.id.filesBrand)?.setOnClickListener { returnHome("Início") }
        binding.root.findViewById<View>(R.id.filesCatalog)?.setOnClickListener { returnHome("Animes") }
        binding.root.findViewById<View>(R.id.filesFilmes)?.setOnClickListener { returnHome("Filmes") }
        binding.root.findViewById<View>(R.id.filesFavorites)?.setOnClickListener { returnHome("Favoritos") }
        binding.root.findViewById<View>(R.id.filesFolders)?.setOnClickListener { binding.etSearch.requestFocus() }
        binding.root.findViewById<View>(R.id.userProfilePill)?.setOnClickListener {
            findNavController().navigate(R.id.profileSelectionFragment)
        }
        binding.root.findViewById<View>(R.id.filesSettings)?.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        val navItems = listOfNotNull(
            binding.root.findViewById<View>(R.id.filesBrand),
            binding.root.findViewById<View>(R.id.filesHome),
            binding.root.findViewById<View>(R.id.filesCatalog),
            binding.root.findViewById<View>(R.id.filesFilmes),
            binding.root.findViewById<View>(R.id.filesFolders),
            binding.root.findViewById<View>(R.id.filesFavorites),
            binding.root.findViewById<View>(R.id.userProfilePill),
            binding.root.findViewById<View>(R.id.filesSettings)
        )
        navItems.forEach { item ->
            item.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    binding.etSearch.requestFocus()
                    true
                } else false
            }
        }
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
        binding.rvList.isFocusable = true
        binding.etSearch.nextFocusDownId = binding.rvList.id
        binding.btnClearSearch.nextFocusDownId = binding.rvList.id
        binding.btnToggleGrid.nextFocusDownId = binding.rvList.id
        binding.rvList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val position = binding.rvList.getChildAdapterPosition(view)
                val firstRowSize = (binding.rvList.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)
                    ?.spanCount ?: 1
                if (position in 0 until firstRowSize) {
                    view.nextFocusUpId = binding.etSearch.id
                }
                view.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        val pos = binding.rvList.getChildAdapterPosition(view)
                        if (pos != RecyclerView.NO_POSITION) {
                            viewModel.onEvent(FilesEvent.SaveFocus(pos))
                        }
                    }
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
                .setTitle("Legenda Externa")
                .setMessage("Deseja reproduzir o vídeo correspondente '${matchingVideo.name}' com esta legenda?")
                .setPositiveButton("Reproduzir") { _, _ ->
                    launchVideoPlayer(matchingVideo)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Legenda Externa")
                .setMessage("Arquivo de legenda (${subFile.name}). Coloque um arquivo de vídeo na mesma pasta para vinculação automática.")
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
                (filesAdapter.currentList.getOrNull(position) as? FilesDataModel.File)?.let { fileModel ->
                    FocusMemory.remember("files.folder.${args.name}", fileModel.driveFile.id)
                }
                paginationHandler.handleDpadFocusNearEnd(position)
            }
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
            FocusMemory.restore(binding.rvList, "files.folder.${args.name}") { pos ->
                (filesAdapter.currentList.getOrNull(pos) as? FilesDataModel.File)?.driveFile?.id
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stateHandler.destroy()
        binding.rvList.adapter = null
        _binding = null
    }
}
