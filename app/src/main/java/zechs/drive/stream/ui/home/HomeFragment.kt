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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.utils.GlideApp
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.ext.navigateSafe
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
        setupHeroAndProfile()

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
        observeFeaturedAnime()
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
        val hasOverlay = isCollapsibleRail()

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

                item.setOnKeyListener { _, keyCode, event ->
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (hasOverlay) collapseSidebar()
                        focusContent()
                        true
                    } else false
                }
            }
        }

        // Setup D-pad Left on anime adapter to expand sidebar when on leftmost column
        animeAdapter.onFocusItemListener = { v ->
            lastFocusedAnimeView = v
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

    private fun isCollapsibleRail(): Boolean =
        binding.sidebarDimOverlay?.visibility == View.VISIBLE

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
        target?.requestFocus()
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
        val hasOverlay = isCollapsibleRail()

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
                    binding.ivUserAvatar?.setImageResource(avatarRes)
                }
            }
        }
    }

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    private fun selectTab(tab: String) {
        currentTab = tab
        val hasRecent = viewModel.recentWatches.value.isNotEmpty()

        binding.apply {
            when (tab) {
                "Início" -> {
                    // TV Home: spotlight + one resume shelf + the actual library.
                    val hasFeatured = viewModel.featuredAnime.value != null
                    val hasLibrary = viewModel.animeLibrary.value.isNotEmpty()
                    featuredHeroContainer?.visibility = if (hasFeatured) View.VISIBLE else View.GONE
                    shelfHeaderRow?.visibility = if (hasRecent) View.VISIBLE else View.GONE
                    rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
                    layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasLibrary) View.VISIBLE else View.GONE
                    rvAnimeLibrary.visibility = View.VISIBLE
                    layoutEmpty.visibility = View.GONE
                    containerViewToggle?.visibility = View.GONE
                    containerItemCount.visibility = if (hasLibrary) View.VISIBLE else View.GONE
                    tvItemCount.text = "${viewModel.animeLibrary.value.size} títulos"
                }
                "Animes" -> {
                    featuredHeroContainer?.visibility = View.GONE
                    shelfHeaderRow?.visibility = View.GONE
                    rvContinueWatchingShelf.visibility = View.GONE
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
                            binding.tvItemCount.text = "${animes.size} títulos"
                            binding.containerItemCount.visibility = if (animes.isNotEmpty()) View.VISIBLE else View.GONE
                            val hasRecent = viewModel.recentWatches.value.isNotEmpty()
                            val hasFeatured = viewModel.featuredAnime.value != null
                            binding.layoutHomeEmpty?.visibility = if (animes.isEmpty() && !hasRecent && !hasFeatured && !viewModel.isLoadingAnime.value) View.VISIBLE else View.GONE
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
                        val hasFeatured = viewModel.featuredAnime.value != null
                        val hasLibrary = viewModel.animeLibrary.value.isNotEmpty()
                        binding.shelfHeaderRow?.visibility = if (hasRecent) View.VISIBLE else View.GONE
                        binding.rvContinueWatchingShelf.visibility = if (hasRecent) View.VISIBLE else View.GONE
                        binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasFeatured && !hasLibrary) View.VISIBLE else View.GONE
                        binding.containerItemCount.visibility = if (hasLibrary) View.VISIBLE else View.GONE
                        binding.tvItemCount.text = "${viewModel.animeLibrary.value.size} títulos"
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

                        if (!featured.synopsis.isNullOrBlank()) {
                            binding.tvFeaturedSynopsis?.text = featured.synopsis
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
                                setTextColor(android.graphics.Color.parseColor("#94A3B8"))
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
                                GlideApp.with(iv)
                                    .load(imgToLoad)
                                    .centerCrop()
                                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                                    .into(iv)
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