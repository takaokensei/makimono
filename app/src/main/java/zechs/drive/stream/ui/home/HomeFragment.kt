package zechs.drive.stream.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.utils.ext.navigateSafe

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    companion object {
        const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel by activityViewModels<HomeViewModel>()
    private val mainViewModel by activityViewModels<zechs.drive.stream.ui.main.MainViewModel>()

    private var isGridMode = true
    private var currentTab = "Animes"

    private val animeAdapter by lazy {
        FilesAdapter(
            onClickListener = { file ->
                handleFileOnClick(file)
            },
            onStarClickListener = { file, star ->
                // Star toggled
            },
            onLongClickListener = { file ->
                handleFileOnClick(file)
            },
            onPlayClickListener = { file ->
                handleFileOnPlayClick(file)
            }
        )
    }

    private val continueWatchingAdapter = ContinueWatchingAdapter { watchItem ->
        playWatchItem(watchItem)
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
        setupViewToggle()
        setupSidebarNavigation()
        setupBrandLogo()

        binding.rvContinueWatchingShelf.adapter = continueWatchingAdapter

        observeAnimeLibrary()
        observeRecentWatches()
        observeLogOutState()
        observeMpv()

        // Load anime library immediately on opening
        viewModel.loadAnimeLibrary()
        viewModel.getRecentWatches()
    }

    private fun getResponsiveSpanCount(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) 5 else 2
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
            viewModel.filterAnimes(query)
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchAnime.setText("")
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
                listBtn.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#64748B"))
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
                gridBtn.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#64748B"))
                setupAnimeGrid()
            }
        }
    }

    private fun setupSidebarNavigation() {
        binding.apply {
            btnNavAnimes.setOnClickListener {
                selectTab("Animes")
                viewModel.filterStarred(false)
                if (viewModel.animeLibrary.value.isEmpty()) {
                    viewModel.loadAnimeLibrary(forceRefresh = true)
                }
                contentScrollView.scrollTo(0, 0)
            }

            btnNavFavoritos.setOnClickListener {
                selectTab("Favoritos")
                viewModel.filterStarred(true)
                contentScrollView.scrollTo(0, 0)
            }

            btnNavPastas.setOnClickListener {
                selectTab("Pastas")
                val action = HomeFragmentDirections.actionHomeFragmentToFilesFragment(
                    name = getString(R.string.my_drive),
                    query = "'root' in parents and trashed = false"
                )
                findNavController().navigateSafe(action)
            }

            btnNavInicio.setOnClickListener {
                selectTab("Início")
                viewModel.filterStarred(false)
                contentScrollView.scrollTo(0, 0)
            }

            btnNavConfig.setOnClickListener {
                findNavController().navigateSafe(R.id.action_homeFragment_to_settingsFragment)
            }

            // TV Focus setup
            listOfNotNull(
                btnNavInicio,
                btnNavAnimes,
                btnNavPastas,
                btnNavFavoritos,
                btnNavConfig
            ).forEach { item ->
                item.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(120L).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                    }
                }
            }
        }
    }

    private fun selectTab(tab: String) {
        currentTab = tab
        binding.apply {
            val normalBg = R.drawable.rail_item_focus_bg
            val activeBg = R.drawable.nav_item_active_bg
            val normalTextColor = android.graphics.Color.parseColor("#94A3B8")
            val activeTextColor = android.graphics.Color.WHITE
            val normalIconColor = android.graphics.Color.parseColor("#7FA3D6")
            val activeIconColor = android.graphics.Color.WHITE

            // Início
            btnNavInicio.setBackgroundResource(if (tab == "Início") activeBg else normalBg)
            tvNavInicio.setTextColor(if (tab == "Início") activeTextColor else normalTextColor)

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
        }
    }

    private fun setupBrandLogo() {
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
                        binding.tvItemCount.text = "${animes.size} animes"
                        val isEmpty = animes.isEmpty() && !viewModel.isLoadingAnime.value
                        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isLoadingAnime.collect { loading ->
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun observeRecentWatches() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentWatches.collect { items ->
                    val hasItems = items.isNotEmpty()
                    binding.tvShelfLabel.visibility = if (hasItems) View.VISIBLE else View.GONE
                    binding.rvContinueWatchingShelf.visibility = if (hasItems) View.VISIBLE else View.GONE
                    continueWatchingAdapter.submitList(items)
                }
            }
        }
    }

    private fun handleFileOnPlayClick(file: DriveFile) {
        if (file.isVideoFile || file.isShortcutVideo) {
            val target = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                file.copy(id = file.shortcutDetails.targetId)
            } else file
            launchVideoPlayer(target)
        } else if (file.isFolder || file.isShortcutFolder) {
            val folderId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                file.shortcutDetails.targetId
            } else file.id
            android.widget.Toast.makeText(context, "Buscando episódio...", android.widget.Toast.LENGTH_SHORT).show()
            viewModel.getFirstEpisodeInFolder(folderId) { video ->
                if (video != null) {
                    launchVideoPlayer(video)
                } else {
                    handleFileOnClick(file)
                }
            }
        } else {
            handleFileOnClick(file)
        }
    }

    private fun handleFileOnClick(file: DriveFile) {
        val isFolder = file.isFolder || file.isShortcutFolder
        if (isFolder) {
            val folderId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
            viewModel.recordFolderOpened(folderId, file.name)
            val action = HomeFragmentDirections.actionHomeFragmentToFilesFragment(
                name = file.name,
                query = "'$folderId' in parents and trashed = false"
            )
            findNavController().navigateSafe(action)
        } else if (file.isVideoFile || file.isShortcutVideo) {
            launchVideoPlayer(file)
        } else {
            android.widget.Toast.makeText(requireContext(), file.name, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchVideoPlayer(file: DriveFile) {
        val fileId = file.id
        when (mainViewModel.currentPlayerIndex) {
            zechs.drive.stream.utils.VideoPlayer.EXO_PLAYER -> {
                val intent = android.content.Intent(requireContext(), zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
                    putExtra("fileId", fileId)
                    putExtra("title", file.name)
                    putExtra("thumbnailLink", file.thumbnailLarge ?: file.posterUrl)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            zechs.drive.stream.utils.VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), "Iniciando MPV Player...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(fileId, file.name, file.thumbnailLarge ?: file.posterUrl)
            }
        }
    }

    private fun playWatchItem(watchItem: WatchList) {
        when (mainViewModel.currentPlayerIndex) {
            zechs.drive.stream.utils.VideoPlayer.EXO_PLAYER -> {
                val intent = android.content.Intent(requireContext(), zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
                    putExtra("fileId", watchItem.videoId)
                    putExtra("title", watchItem.name)
                    putExtra("thumbnailLink", watchItem.thumbnailLink)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            zechs.drive.stream.utils.VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), "Iniciando MPV Player...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(watchItem.videoId, watchItem.name, watchItem.thumbnailLink)
            }
        }
    }

    private fun observeMpv() {
        viewModel.mpvFile.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { resource ->
                when (resource) {
                    is zechs.drive.stream.utils.state.Resource.Success -> {
                        val file = resource.data!!
                        val intent = android.content.Intent(
                            requireContext(),
                            zechs.drive.stream.ui.player2.MPVActivity::class.java
                        ).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("fileId", file.fileId)
                            putExtra("title", file.fileName)
                            putExtra("accessToken", file.accessToken)
                        }
                        startActivity(intent)
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