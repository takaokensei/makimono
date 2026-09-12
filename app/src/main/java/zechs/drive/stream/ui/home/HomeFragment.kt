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
    private var currentTab = "Início"
    private var isSidebarExpanded = false
    private var lastFocusedAnimeView: View? = null

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
                // Quick 1-click (1 toque rápido): iniciar/retomar anime no último episódio!
                handleQuickPlay(file)
            },
            onStarClickListener = { file, star ->
                // Star toggled
            },
            onLongClickListener = { file ->
                // Long press (segurar o botão): abrir pasta para navegar episódios!
                handleOpenFolder(file)
            },
            onPlayClickListener = { file ->
                // Botão play central: iniciar imediatamente!
                handleQuickPlay(file)
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
        setupVoiceSearch()
        setupViewToggle()
        setupSidebarNavigation()
        setupBrandLogo()
        setupBackPressedHandling()

        binding.rvContinueWatchingShelf.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvContinueWatchingShelf.adapter = continueWatchingAdapter

        // "VER HISTÓRICO" shows all watched items — for now scrolls the shelf to end
        binding.tvShelfViewHistory?.setOnClickListener {
            val count = continueWatchingAdapter.itemCount
            if (count > 0) binding.rvContinueWatchingShelf.smoothScrollToPosition(count - 1)
        }

        binding.btnExploreAnimes?.setOnClickListener {
            selectTab("Animes")
        }

        observeAnimeLibrary()
        observeRecentWatches()
        observeLogOutState()
        observeMpv()

        // Set initial tab state to Início
        selectTab("Início")

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
            if (query.isNotBlank() && currentTab == "Início") {
                selectTab("Animes")
            }
            viewModel.filterAnimes(query)
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchAnime.setText("")
        }

        binding.etSearchAnime.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                if (binding.etSearchAnime.selectionStart == 0 && binding.sidebarDimOverlay != null) {
                    expandSidebar()
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
            binding.btnVoiceSearch.imageTintList = android.content.res.ColorStateList.valueOf(
                if (hasFocus) android.graphics.Color.parseColor("#38BDF8") else android.graphics.Color.parseColor("#94A3B8")
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
                "Pesquisa por voz não suportada neste dispositivo",
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
        val hasOverlay = binding.sidebarDimOverlay != null

        if (hasOverlay) {
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
        }

        binding.apply {
            btnNavAnimes.setOnClickListener {
                selectTab("Animes")
                viewModel.filterStarred(false)
                if (viewModel.animeLibrary.value.isEmpty()) {
                    viewModel.loadAnimeLibrary(forceRefresh = true)
                }
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
            }

            btnNavFavoritos.setOnClickListener {
                selectTab("Favoritos")
                viewModel.filterStarred(true)
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
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
                if (hasOverlay) collapseSidebar()
            }

            btnNavConfig.setOnClickListener {
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

                if (hasOverlay) {
                    item.setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            collapseSidebar()
                            lastFocusedAnimeView?.requestFocus() ?: binding.rvAnimeLibrary.requestFocus()
                            true
                        } else false
                    }
                }
            }
        }

        // Setup D-pad Left on anime adapter to expand sidebar when on leftmost column
        animeAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
        }

        animeAdapter.onDpadLeftListener = { itemView ->
            if (hasOverlay) {
                val pos = binding.rvAnimeLibrary.getChildAdapterPosition(itemView)
                val spanCount = (binding.rvAnimeLibrary.layoutManager as? GridLayoutManager)?.spanCount ?: 1
                if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION && pos % spanCount == 0) {
                    expandSidebar()
                    true
                } else false
            } else false
        }

        continueWatchingAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
        }

        continueWatchingAdapter.onDpadLeftListener = {
            if (hasOverlay) {
                expandSidebar()
                true
            } else false
        }
    }

    private fun expandSidebar() {
        if (isSidebarExpanded || binding.sidebarDimOverlay == null) return
        isSidebarExpanded = true

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

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    private fun selectTab(tab: String) {
        currentTab = tab
        val hasRecent = viewModel.recentWatches.value.isNotEmpty()

        binding.apply {
            when (tab) {
                "Início" -> {
                    // Home: Shows ONLY Continuar Assistindo shelf (or clean empty state if none)
                    // The main anime library grid is isolated to the "Animes" tab!
                    shelfHeaderRow?.visibility = if (hasRecent) View.VISIBLE else View.GONE
                    rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
                    layoutHomeEmpty?.visibility = if (hasRecent) View.GONE else View.VISIBLE
                    rvAnimeLibrary.visibility = View.GONE
                    layoutEmpty.visibility = View.GONE
                    containerViewToggle?.visibility = View.GONE
                    containerItemCount?.visibility = if (hasRecent) View.VISIBLE else View.GONE
                    tvItemCount.text = "${viewModel.recentWatches.value.size} em andamento"
                }
                "Animes" -> {
                    // Animes: Full library grid isolated here
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                    rvAnimeLibrary.visibility = View.VISIBLE
                    containerViewToggle?.visibility = View.VISIBLE
                    containerItemCount?.visibility = View.VISIBLE
                    val animesCount = viewModel.filteredAnimes.value.size
                    tvItemCount.text = "$animesCount animes"
                    val isEmpty = animesCount == 0 && !viewModel.isLoadingAnime.value
                    layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                }
                "Favoritos" -> {
                    // Favoritos: Filtered library grid for starred animes
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                    rvAnimeLibrary.visibility = View.VISIBLE
                    containerViewToggle?.visibility = View.VISIBLE
                    containerItemCount?.visibility = View.VISIBLE
                    val favCount = viewModel.filteredAnimes.value.size
                    tvItemCount.text = "$favCount favoritos"
                    val isEmpty = favCount == 0 && !viewModel.isLoadingAnime.value
                    layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                }
                else -> {
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
                    layoutHomeEmpty?.visibility = View.GONE
                }
            }

            val normalBg = R.drawable.rail_item_focus_bg
            val activeBg = R.drawable.nav_item_active_bg
            val normalTextColor = android.graphics.Color.parseColor("#94A3B8")
            val activeTextColor = android.graphics.Color.WHITE
            val normalIconColor = android.graphics.Color.parseColor("#7FA3D6")
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
        }
    }

    private fun setupBrandLogo() {
        binding.btnBrandLogo.setOnClickListener {
            if (binding.sidebarDimOverlay != null) {
                toggleSidebar()
            }
        }
        binding.btnBrandLogo.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                if (binding.sidebarDimOverlay != null) {
                    expandSidebar()
                    true
                } else false
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
                        }
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
                    continueWatchingAdapter.submitList(items)
                    if (currentTab == "Início") {
                        val hasRecent = items.isNotEmpty()
                        binding.shelfHeaderRow?.visibility = if (hasRecent) View.VISIBLE else View.GONE
                        binding.rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
                        binding.layoutHomeEmpty?.visibility = if (hasRecent) View.GONE else View.VISIBLE
                        binding.containerItemCount?.visibility = if (hasRecent) View.VISIBLE else View.GONE
                        binding.tvItemCount.text = "${items.size} em andamento"
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

    private fun launchVideoPlayer(file: DriveFile, startPosition: Long = -1L) {
        val fileId = file.id
        val thumb = file.thumbnailLarge ?: file.posterUrl ?: file.thumbnailLink
        when (mainViewModel.currentPlayerIndex) {
            zechs.drive.stream.utils.VideoPlayer.EXO_PLAYER -> {
                val intent = android.content.Intent(requireContext(), zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
                    putExtra("fileId", fileId)
                    putExtra("title", file.name)
                    putExtra("thumbnailLink", thumb)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    if (startPosition > 0L) {
                        putExtra("startPosition", startPosition)
                    }
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            zechs.drive.stream.utils.VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), "Iniciando MPV Player...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(fileId, file.name, thumb)
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
                    if (watchItem.watchedDuration > 0L) {
                        putExtra("startPosition", watchItem.watchedDuration)
                    }
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