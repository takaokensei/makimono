package zechs.drive.stream.ui.files

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.databinding.FragmentFilesBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.player.PlayerActivity
import zechs.drive.stream.ui.player2.MPVActivity
import zechs.drive.stream.ui.player.GlassMenuItem
import zechs.drive.stream.ui.player.PlayerGlassMenuDialog
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.SeasonEpisodeGrouper
import zechs.drive.stream.utils.SeasonGroup
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.state.Resource


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

    private var isLoading = false
    private var isScrolling = false
    private var isGridMode = false
    private var allFilesList = listOf<FilesDataModel>()
    private var availableSeasons: List<SeasonGroup> = emptyList()
    private var selectedSeason: SeasonGroup? = null

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

        // Workaround for transition animation
        // https://github.com/material-components/material-components-android/issues/1984
        val colorBackground = MaterialColors.getColor(view, android.R.attr.colorBackground)
        view.setBackgroundColor(colorBackground)

        binding.toolbar.apply {
            title = args.name
            setNavigationOnClickListener {
                findNavController().navigateUp()
            }
        }

        Log.d(TAG, "FilesFragment(name=${args.name}, query=${args.query})")

        val folderMatch = Regex("'([^']+)' in parents").find(args.query ?: "")
        if (folderMatch != null) {
            val folderId = folderMatch.groupValues[1]
            if (folderId != "root") {
                viewModel.recordFolderOpened(folderId, args.name)
            }
        }

        val folderName = args.name.trim()
        viewModel.isCurrentFolderOneBlacki = folderName.equals("oneblacki", ignoreCase = true) ||
                (folderName.contains("oneblacki", ignoreCase = true) && !folderName.contains("1oneblacki", ignoreCase = true) && !folderName.startsWith("1"))

        setupRecyclerView()
        setupSearchAndLayoutToggle()
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

    private fun setupFilesObserver() {
        if (!viewModel.hasLoaded) {
            viewModel.queryFiles(args.query)
        }

        viewModel.filesList.observe(viewLifecycleOwner) { response ->
            handleFilesList(response)
        }
    }

    private fun handleFilesList(response: Resource<List<FilesDataModel>>) {
        when (response) {
            is Resource.Success -> response.data?.let { files ->
                onSuccess(files)
            }

            is Resource.Error -> {
                showSnackBar(response.message)
                showError(response.message)
            }

            is Resource.Loading -> {
                isLoading = true
                if (!viewModel.hasLoaded || viewModel.hasFailed) {
                    isLoading(true)
                }
                binding.error.root.apply {
                    if (isVisible) {
                        isGone = true
                    }
                }
            }
        }
    }

    private fun onSuccess(files: List<FilesDataModel>) {
        Log.d(TAG, "onSuccess(files=${files.size})")
        val fileCount = files.count { it is FilesDataModel.File }
        binding.containerItemCount.isVisible = fileCount > 0
        binding.tvItemCount.text = "$fileCount itens"

        if (!viewModel.hasLoaded) {
            doTransition(MaterialFadeThrough())
        }

        isLoading(false)
        isLoading = false
        viewModel.hasLoaded = true

        if (files.isEmpty()) {
            binding.error.apply {
                root.isVisible = true
                errorTxt.text = getString(R.string.no_files_found)
            }
        } else {
            binding.error.root.apply {
                if (isVisible) {
                    isGone = true
                }
            }
        }

        allFilesList = files
        availableSeasons = SeasonEpisodeGrouper.groupFiles(args.name, files)
        if (availableSeasons.size > 1) {
            binding.seasonSelectorRow.isVisible = true
            selectedSeason = availableSeasons.firstOrNull()
            binding.tvSelectedSeason.text = selectedSeason?.name ?: "Temporadas"
            binding.btnSeasonSelector.setOnClickListener {
                showSeasonSelectionDialog()
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val arcs = tenraiService.get().resolveFranchiseArcs(args.name)
                    if (arcs.isNotEmpty() && isAdded) {
                        val updated = availableSeasons.map { group ->
                            when {
                                group.id == "season_1" -> {
                                    val arc = arcs.firstOrNull { it.seasonNumber == 1 }
                                    if (arc != null) group.copy(name = "${arc.title} (Temporada 1)") else group
                                }
                                group.id == "season_2" -> {
                                    val arc = arcs.firstOrNull { it.seasonNumber == 2 }
                                    if (arc != null) group.copy(name = "${arc.title} (Temporada 2)") else group
                                }
                                group.id == "season_prologue" -> {
                                    val arc = arcs.firstOrNull { it.type.contains("Special", ignoreCase = true) || it.type.contains("OVA", ignoreCase = true) }
                                    if (arc != null) group.copy(name = arc.title) else group
                                }
                                else -> group
                            }
                        }
                        availableSeasons = updated
                        if (selectedSeason != null) {
                            selectedSeason = updated.firstOrNull { it.id == selectedSeason?.id } ?: selectedSeason
                            binding.tvSelectedSeason.text = selectedSeason?.name ?: "Temporadas"
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            binding.seasonSelectorRow.isGone = true
            selectedSeason = null
        }

        val currentQuery = binding.etSearch.text?.toString()?.trim() ?: ""
        filterFiles(currentQuery)
    }

    private fun showSeasonSelectionDialog() {
        if (availableSeasons.isEmpty()) return

        val currentSelectedId = selectedSeason?.id ?: "season_all"
        val items = availableSeasons.map { season ->
            GlassMenuItem(
                id = season.id,
                title = season.name,
                subtitle = season.subtitle,
                isSelected = season.id == currentSelectedId,
                tag = season
            )
        }

        PlayerGlassMenuDialog(
            context = requireContext(),
            title = "Temporadas e Arcos",
            items = items
        ) { selected ->
            val season = selected.tag as? SeasonGroup ?: return@PlayerGlassMenuDialog
            selectedSeason = season
            binding.tvSelectedSeason.text = season.name
            filterFiles(binding.etSearch.text?.toString()?.trim() ?: "")
        }.show()
    }

    private fun setupSearchAndLayoutToggle() {
        val prefs = requireContext().getSharedPreferences("FILES_PREFS", android.content.Context.MODE_PRIVATE)
        isGridMode = if (viewModel.isCurrentFolderOneBlacki) true else prefs.getBoolean("IS_GRID_MODE", false)
        updateLayoutMode()

        binding.btnToggleGrid.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit().putBoolean("IS_GRID_MODE", isGridMode).apply()
            updateLayoutMode()
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                filterFiles(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun updateLayoutMode() {
        val hasVideoFiles = allFilesList.any {
            it is FilesDataModel.File && (it.driveFile.isVideoFile || it.driveFile.isShortcutVideo)
        }
        val screenWidthDp = resources.configuration.screenWidthDp
        val spanCount = if (hasVideoFiles) {
            when {
                screenWidthDp >= 1200 -> 4
                screenWidthDp >= 840 -> 3
                screenWidthDp >= 600 -> 3
                screenWidthDp >= 400 -> 2
                else -> 1
            }
        } else {
            when {
                screenWidthDp >= 1200 -> 6
                screenWidthDp >= 900 -> 5
                screenWidthDp >= 650 -> 4
                screenWidthDp >= 420 -> 3
                else -> 2
            }
        }

        if (isGridMode) {
            binding.btnToggleGrid.setImageResource(R.drawable.ic_list_view_24)
            val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), spanCount)
            gridLayoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (filesAdapter.getItemViewType(position) == R.layout.item_loading) spanCount else 1
                }
            }
            binding.rvList.layoutManager = gridLayoutManager
            filesAdapter.isGridMode = true
        } else {
            binding.btnToggleGrid.setImageResource(R.drawable.ic_grid_view_24)
            binding.rvList.layoutManager = LinearLayoutManager(requireContext())
            filesAdapter.isGridMode = false
        }
    }

    private fun filterFiles(query: String) {
        val baseList = if (selectedSeason != null && selectedSeason?.id != "season_all" && selectedSeason?.fileItems?.isNotEmpty() == true) {
            selectedSeason!!.fileItems
        } else {
            allFilesList
        }

        val listToSubmit = if (query.isEmpty()) {
            baseList.toMutableList()
        } else {
            baseList.filter { item ->
                when (item) {
                    is FilesDataModel.File -> item.driveFile.name.contains(query, ignoreCase = true)
                    else -> true
                }
            }.toMutableList()
        }
        val count = listToSubmit.count { it is FilesDataModel.File }
        binding.containerItemCount.isVisible = count > 0
        binding.tvItemCount.text = "$count itens"
        filesAdapter.submitList(listToSubmit)
    }

    private fun doTransition(transition: Transition) {
        TransitionManager.beginDelayedTransition(
            binding.root, transition
        )
    }

    private fun isLoading(hide: Boolean) {
        binding.apply {
            loading.isInvisible = !hide
            rvList.isInvisible = hide
        }
    }

    private fun showError(msg: String?) {
        binding.apply {
            rvList.isInvisible = true
            loading.isInvisible = true
            error.apply {
                root.isVisible = true
                errorTxt.text = msg ?: getString(R.string.something_went_wrong)
                btnRetry.apply {
                    isVisible = true
                    setOnClickListener {
                        viewModel.queryFiles(args.query)
                    }
                }
            }
        }
        isLoading = false
    }

    private val filesAdapter by lazy {
        FilesAdapter(
            onClickListener = { handleFileOnClick(it) },
            onLongClickListener = { handleFileOnLongPress(it) },
            onStarClickListener = { file, isStarred ->
                viewModel.starFile(file, isStarred)
            },
            onPlayClickListener = { file ->
                handleFileOnPlayClick(file)
            }
        )
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
            android.widget.Toast.makeText(context, "Buscando episódio...", android.widget.Toast.LENGTH_SHORT).show()
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
        Log.d(TAG, "handleFileOnLongPress: $file")
        val targetFile = if (file.isShortcut && file.shortcutDetails.targetId != null) {
            file.copy(id = file.shortcutDetails.targetId)
        } else file
        val isCurrentlyStarred = targetFile.starred == Starred.STARRED
        viewModel.starFile(targetFile, !isCurrentlyStarred)
    }

    private fun handleFileOnClick(file: DriveFile) {
        Log.d(TAG, file.toString())
        if (file.isFolder && !file.isShortcut) {
            viewModel.recordFolderOpened(file.id, file.name)
            val action = FilesFragmentDirections.actionFilesFragmentSelf(
                name = file.name,
                query = "'${file.id}' in parents and trashed=false"
            )
            findNavController().navigate(action)
        } else if (file.isVideoFile) {
            launchVideoPlayer(file)
        } else if (file.isSubtitleFile) {
            handleSubtitleFileClick(file)
        } else if (file.isShortcut) {
            if (file.isShortcutFolder) {
                val targetId = file.shortcutDetails.targetId!!
                viewModel.recordFolderOpened(targetId, file.name)
                val action = FilesFragmentDirections.actionFilesFragmentSelf(
                    name = file.name,
                    query = "'$targetId' in parents and trashed=false"
                )
                findNavController().navigate(action)
            } else if (file.isShortcutVideo) {
                val videoShortcutFile = file.copy(id = file.shortcutDetails.targetId!!)
                launchVideoPlayer(videoShortcutFile)
            }
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
            com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_DriveStream_Dialog
            )
                .setTitle("Legenda Externa")
                .setMessage("Deseja reproduzir o vídeo correspondente '${matchingVideo.name}' com esta legenda?")
                .setPositiveButton("Reproduzir") { _, _ ->
                    launchVideoPlayer(matchingVideo)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_DriveStream_Dialog
            )
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
                android.widget.Toast.makeText(context, "Iniciando MPV Player...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(file)
            }
        }
    }

    private fun mpvObserver() {
        viewModel.mpvFile.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { res ->
                when (res) {
                    is Resource.Success -> {
                        launchMpv(res.data!!)
                    }

                    is Resource.Error -> {
                        showSnackBar(res.message!!)
                    }

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
                if (f.isSubtitleFile) {
                    SubtitleItem(f.id, f.name)
                } else null
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
                    f.isVideoFile -> PlaylistItem(f.id, f.name, f.thumbnailLink)
                    f.isShortcut && f.isShortcutVideo && f.shortcutDetails.targetId != null ->
                        PlaylistItem(f.shortcutDetails.targetId, f.name, f.thumbnailLink)
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
        Intent(
            context, PlayerActivity::class.java
        ).apply {
            putExtra("fileId", file.id)
            putExtra("title", file.name)
            putExtra("seriesTitle", args.name)
            putExtra("thumbnailLink", file.thumbnailLink)
            putExtra("theme", mainViewModel.currentThemeIndex)
            putExtra("playlist", playlist)
            putExtra("subtitles", subtitles)
        }.also { startActivity(it) }
    }

    private fun launchMpv(fileToken: FilesViewModel.FileToken) {
        val playlist = getFolderPlaylist()
        val subtitles = getFolderSubtitles()
        Intent(
            context, MPVActivity::class.java
        ).apply {
            putExtra("fileId", fileToken.fileId)
            putExtra("title", fileToken.fileName)
            putExtra("seriesTitle", args.name)
            putExtra("accessToken", fileToken.accessToken)
            putExtra("thumbnailLink", fileToken.thumbnailLink)
            putExtra("theme", mainViewModel.currentThemeIndex)
            putExtra("playlist", playlist)
            putExtra("subtitles", subtitles)
        }.also { startActivity(it) }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val visibleItemCount = layoutManager.childCount
            val totalItemCount = layoutManager.itemCount

            val isAtLastItem = firstVisibleItemPosition + visibleItemCount >= totalItemCount
            val isLastPage = viewModel.isLastPage

            if (isAtLastItem && !isLoading && !isLastPage && isScrolling) {
                Log.d(TAG, "Paginating...")
                viewModel.queryFiles(args.query)
                isScrolling = false
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                isScrolling = true
            }
        }
    }

    private fun setupRecyclerView() {
        binding.rvList.apply {
            adapter = filesAdapter
            addOnScrollListener(this@FilesFragment.scrollListener)
        }
        updateLayoutMode()
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvList.adapter = null
        _binding = null
    }

}