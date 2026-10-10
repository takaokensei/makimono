package zechs.drive.stream.ui.home.controller

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.navigation.NavController
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.home.HomeFragmentDirections
import zechs.drive.stream.ui.home.HomeViewModel
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.SingleTapActivation
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.ext.resolveThemeColor

/**
 * Controlador da barra de navegação lateral (Navigation Rail / Drawer).
 * Conforme Seção 4.10 e 7.2 do Plano v2:
 * - Largura de 80dp na TV, ícones de 24dp centralizados.
 * - Indicador do item ativo: barra e fundo de acento.
 * - Suporte a D-pad Right levando ao conteúdo e D-pad Left abrindo rail se recolhido.
 */
class RailController(
    private val binding: FragmentHomeBinding,
    private val viewModel: HomeViewModel,
    private val navController: NavController,
    private val animateFocus: (View, Boolean, Float, Float) -> Unit,
    private val onContentFocusRequested: () -> Unit,
    private val onCancelFeaturedPreview: () -> Unit,
    private val onRefreshShelvesRequested: () -> Unit
) {

    var currentTab: String = "Início"
        private set

    var isSidebarExpanded: Boolean = false
        private set

    var previousTabBeforeSearch: String? = null

    private fun Float.dpToPx(): Float =
        this * binding.root.resources.displayMetrics.density

    fun isCollapsibleRail(): Boolean {
        return RailNavigationPolicy.isOverlaySidebarActive(
            isTenFootExperience = DeviceUi.isTenFootExperience(binding.root.context),
            hasDimOverlay = binding.sidebarDimOverlay != null,
            isCollapsibleRailConfig = binding.root.resources.getBoolean(R.bool.is_collapsible_rail)
        )
    }

    fun usesOverlaySidebar(): Boolean =
        isCollapsibleRail() && binding.sidebarDimOverlay != null

    fun setup() {
        val hasOverlay = usesOverlaySidebar()

        if (hasOverlay) {
            binding.navRail.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            binding.navRail.visibility = View.INVISIBLE
            setNavItemsFocusable(false)

            binding.navRail.post {
                val railWidth = binding.navRail.width.toFloat().coerceAtLeast(240f.dpToPx())
                binding.navRail.translationX = -railWidth
            }
            binding.sidebarDimOverlay?.apply {
                visibility = View.GONE
                alpha = 0f
                setOnClickListener { collapseSidebar() }
            }
        } else {
            binding.navRail.visibility = View.VISIBLE
            binding.navRail.translationX = 0f
            binding.navRail.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setNavItemsFocusable(true)
        }

        binding.apply {
            btnHamburgerMenu?.setOnClickListener { toggleSidebar() }

            btnBrandLogo.setOnClickListener {
                if (hasOverlay) toggleSidebar() else focusCurrentNavItem()
            }
            btnBrandLogo.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (hasOverlay) expandSidebar() else focusCurrentNavItem()
                    true
                } else false
            }

            btnNavAnimes.setOnClickListener {
                previousTabBeforeSearch = null
                selectTab("Animes")
                viewModel.filterMovies(false)
                if (viewModel.animeLibrary.value.isEmpty()) {
                    viewModel.loadAnimeLibrary(forceRefresh = true)
                }
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
            }

            btnNavFilmes?.setOnClickListener {
                previousTabBeforeSearch = null
                selectTab("Filmes")
                viewModel.filterMovies(true)
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
                    navController.navigateSafe(action)
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
                navController.navigateSafe(R.id.action_homeFragment_to_settingsFragment)
            }

            val navItems = listOfNotNull(
                btnNavInicio,
                btnNavAnimes,
                btnNavFilmes,
                btnNavPastas,
                btnNavFavoritos,
                btnNavConfig
            )

            navItems.forEach { item ->
                SingleTapActivation.install(item)
                item.setOnFocusChangeListener { v, hasFocus ->
                    animateFocus(v, hasFocus, 1.04f, 6f)
                }

                item.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (hasOverlay) collapseSidebar()
                        onContentFocusRequested()
                        true
                    } else false
                }
            }

            // Wire up mobile bottom navigation bar
            bottomNavInicio?.setOnClickListener { btnNavInicio.performClick() }
            bottomNavAnimes?.setOnClickListener { btnNavAnimes.performClick() }
            bottomNavFilmes?.setOnClickListener { btnNavFilmes?.performClick() }
            bottomNavPastas?.setOnClickListener { btnNavPastas.performClick() }
            bottomNavFavoritos?.setOnClickListener { btnNavFavoritos.performClick() }
            bottomNavConfig?.setOnClickListener { btnNavConfig.performClick() }
        }
    }

    fun selectTab(tab: String) {
        if (tab != "Início") onCancelFeaturedPreview()
        currentTab = tab

        if (tab == "Início" || tab == "Animes" || tab == "Filmes" || tab == "Favoritos") {
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
                    onRefreshShelvesRequested()
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
                    tvItemCount.text = if (animesCount == 1) "1 anime" else "$animesCount animes"
                    val isEmpty = animesCount == 0 && !viewModel.isLoadingAnime.value
                    layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                }
                "Filmes" -> {
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
                    val moviesCount = viewModel.filteredAnimes.value.size
                    tvItemCount.text = if (moviesCount == 1) "1 filme" else "$moviesCount filmes"
                    val isEmpty = moviesCount == 0 && !viewModel.isLoadingAnime.value
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
            val context = root.context
            val normalTextColor = context.resolveThemeColor(R.attr.colorTextSecondary)
            val activeTextColor = Color.WHITE
            val normalIconColor = context.resolveThemeColor(R.attr.colorTextSecondary)
            val activeIconColor = Color.WHITE

            btnNavInicio.setBackgroundResource(if (tab == "Início") activeBg else normalBg)
            tvNavInicio.setTextColor(if (tab == "Início") activeTextColor else normalTextColor)
            ivNavInicioIcon?.imageTintList = ColorStateList.valueOf(if (tab == "Início") activeIconColor else normalIconColor)

            btnNavAnimes.setBackgroundResource(if (tab == "Animes") activeBg else normalBg)
            tvNavAnimes.setTextColor(if (tab == "Animes") activeTextColor else normalTextColor)
            ivNavAnimesIcon.imageTintList = ColorStateList.valueOf(if (tab == "Animes") activeIconColor else normalIconColor)

            btnNavFilmes?.setBackgroundResource(if (tab == "Filmes") activeBg else normalBg)
            tvNavFilmes?.setTextColor(if (tab == "Filmes") activeTextColor else normalTextColor)
            ivNavFilmesIcon?.imageTintList = ColorStateList.valueOf(if (tab == "Filmes") activeIconColor else normalIconColor)

            btnNavPastas.setBackgroundResource(if (tab == "Pastas") activeBg else normalBg)
            tvNavPastas.setTextColor(if (tab == "Pastas") activeTextColor else normalTextColor)

            btnNavFavoritos.setBackgroundResource(if (tab == "Favoritos") activeBg else normalBg)
            tvNavFavoritos.setTextColor(if (tab == "Favoritos") activeTextColor else normalTextColor)
            ivNavFavoritosIcon.imageTintList = ColorStateList.valueOf(if (tab == "Favoritos") activeIconColor else normalIconColor)

            val bottomActiveColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
            val bottomInactiveColor = context.resolveThemeColor(R.attr.colorTextSecondary)

            ivBottomNavInicio?.imageTintList = ColorStateList.valueOf(if (tab == "Início") bottomActiveColor else bottomInactiveColor)
            tvBottomNavInicio?.setTextColor(if (tab == "Início") bottomActiveColor else bottomInactiveColor)

            ivBottomNavAnimes?.imageTintList = ColorStateList.valueOf(if (tab == "Animes") bottomActiveColor else bottomInactiveColor)
            tvBottomNavAnimes?.setTextColor(if (tab == "Animes") bottomActiveColor else bottomInactiveColor)

            ivBottomNavFilmes?.imageTintList = ColorStateList.valueOf(if (tab == "Filmes") bottomActiveColor else bottomInactiveColor)
            tvBottomNavFilmes?.setTextColor(if (tab == "Filmes") bottomActiveColor else bottomInactiveColor)

            ivBottomNavPastas?.imageTintList = ColorStateList.valueOf(if (tab == "Pastas") bottomActiveColor else bottomInactiveColor)
            tvBottomNavPastas?.setTextColor(if (tab == "Pastas") bottomActiveColor else bottomInactiveColor)

            ivBottomNavFavoritos?.imageTintList = ColorStateList.valueOf(if (tab == "Favoritos") bottomActiveColor else bottomInactiveColor)
            tvBottomNavFavoritos?.setTextColor(if (tab == "Favoritos") bottomActiveColor else bottomInactiveColor)
        }
    }

    fun focusCurrentNavItem() {
        val target = when (currentTab) {
            "Animes" -> binding.btnNavAnimes
            "Filmes" -> binding.btnNavFilmes
            "Pastas" -> binding.btnNavPastas
            "Favoritos" -> binding.btnNavFavoritos
            "Configurações" -> binding.btnNavConfig
            else -> binding.btnNavInicio
        }
        target?.requestFocus()
    }

    fun setNavItemsFocusable(enabled: Boolean) {
        val navItems = listOfNotNull(
            binding.btnNavInicio,
            binding.btnNavAnimes,
            binding.btnNavFilmes,
            binding.btnNavPastas,
            binding.btnNavFavoritos,
            binding.btnNavConfig
        )
        navItems.forEach { item ->
            item.isFocusable = enabled
            item.isFocusableInTouchMode = enabled
        }
    }

    fun expandSidebar() {
        if (isSidebarExpanded || binding.sidebarDimOverlay == null) return
        isSidebarExpanded = true

        binding.navRail.visibility = View.VISIBLE
        binding.navRail.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        setNavItemsFocusable(true)

        binding.sidebarDimOverlay?.apply {
            animate().cancel()
            visibility = View.VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(binding.root.resources.getInteger(R.integer.motion_osd_show).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.navRail.apply {
            bringToFront()
            animate().cancel()
            animate()
                .translationX(0f)
                .setDuration(binding.root.resources.getInteger(R.integer.motion_card).toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    focusCurrentNavItem()
                }
                .start()
        }
    }

    fun collapseSidebar() {
        if (!isSidebarExpanded || binding.sidebarDimOverlay == null) return
        isSidebarExpanded = false

        binding.navRail.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        setNavItemsFocusable(false)

        binding.sidebarDimOverlay?.apply {
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(binding.root.resources.getInteger(R.integer.motion_osd_hide).toLong())
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { visibility = View.GONE }
                .start()
        }

        val railWidth = binding.navRail.width.toFloat().coerceAtLeast(240f.dpToPx())
        binding.navRail.animate().cancel()
        binding.navRail.animate()
            .translationX(-railWidth)
            .setDuration(binding.root.resources.getInteger(R.integer.motion_card).toLong())
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!isSidebarExpanded) {
                    binding.navRail.visibility = View.INVISIBLE
                }
            }
            .start()
    }

    fun toggleSidebar() {
        if (isSidebarExpanded) collapseSidebar() else expandSidebar()
    }

    fun isFocusInRail(currentFocus: View?): Boolean {
        if (currentFocus == null) return false
        var view: View? = currentFocus
        while (view != null) {
            if (view === binding.navRail) return true
            view = view.parent as? View
        }
        return false
    }

    fun handleBackPressed(): Boolean {
        if (RailNavigationPolicy.shouldConsumeBackPress(isSidebarExpanded)) {
            collapseSidebar()
            return true
        }
        return false
    }
}
