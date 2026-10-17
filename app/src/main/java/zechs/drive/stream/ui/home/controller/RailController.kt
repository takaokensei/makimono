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

        if (DeviceUi.isTenFootExperience(binding.root.context)) {
            val focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
                if (!binding.root.isAttachedToWindow) return@OnGlobalFocusChangeListener
                val wasInRail = isFocusInRail(oldFocus)
                val isInRail = isFocusInRail(newFocus)
                if (!wasInRail && isInRail) {
                    expandTvRail()
                } else if (wasInRail && !isInRail) {
                    collapseTvRail()
                }
            }
            binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
            binding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
                    }
                    v.removeOnAttachStateChangeListener(this)
                }
            })
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

            btnNavBusca?.setOnClickListener {
                if (currentTab != "Buscar") {
                    previousTabBeforeSearch = currentTab
                }
                selectTab("Buscar")
                viewModel.filterMovies(false)
                viewModel.filterStarred(false)
                if (viewModel.animeLibrary.value.isEmpty()) {
                    viewModel.loadAnimeLibrary(forceRefresh = true)
                }
                contentScrollView.scrollTo(0, 0)
                if (hasOverlay) collapseSidebar()
                if (DeviceUi.isTenFootExperience(binding.root.context)) {
                    collapseTvRail()
                }
                etSearchAnime.requestFocus()
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

            userProfilePill?.contentDescription = tvUserName?.text?.takeIf { it.isNotBlank() }
                ?: root.context.getString(R.string.nav_switch_profile_subtitle)
            btnNavBusca?.contentDescription = tvNavBusca?.text ?: root.context.getString(R.string.search_button)
            btnNavInicio.contentDescription = tvNavInicio.text
            btnNavFavoritos.contentDescription = tvNavFavoritos.text
            btnNavAnimes.contentDescription = tvNavAnimes.text
            btnNavFilmes?.contentDescription = tvNavFilmes?.text
            btnNavPastas.contentDescription = tvNavPastas.text
            btnNavConfig.contentDescription = tvNavConfig.text

            val navItems = listOfNotNull(
                btnBrandLogo.takeIf { it.visibility != View.GONE },
                userProfilePill,
                btnNavBusca,
                btnNavInicio,
                btnNavFavoritos,
                btnNavAnimes,
                btnNavFilmes,
                btnNavPastas,
                btnNavConfig
            )

            navItems.forEachIndexed { index, item ->
                item.nextFocusUpId = navItems.getOrNull(index - 1)?.id ?: item.id
                item.nextFocusDownId = navItems.getOrNull(index + 1)?.id ?: item.id
                item.nextFocusLeftId = item.id
                SingleTapActivation.install(item)
                item.setOnFocusChangeListener { v, hasFocus ->
                    if (!DeviceUi.isTenFootExperience(v.context)) {
                        animateFocus(v, hasFocus, 1.04f, 6f)
                    } else if (hasFocus) {
                        expandTvRail()
                    }
                    updateRailItemColors()
                }

                item.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (hasOverlay) collapseSidebar()
                        if (DeviceUi.isTenFootExperience(binding.root.context)) {
                            collapseTvRail()
                        }
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

        val isTenFoot = DeviceUi.isTenFootExperience(binding.root.context)
        val showHeaderBar = if (isTenFoot) {
            tab == "Buscar" || tab == "Animes" || tab == "Filmes" || tab == "Favoritos"
        } else {
            tab == "Início" || tab == "Buscar" || tab == "Animes" || tab == "Filmes" || tab == "Favoritos"
        }
        binding.root.findViewById<View>(R.id.topHeaderBar)?.visibility =
            if (showHeaderBar) View.VISIBLE else View.GONE

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
                    val isLibraryEmpty = viewModel.animeLibrary.value.isEmpty() && !viewModel.isLoadingAnime.value
                    val isHomeEmpty = !hasRecent && !hasFeatured && !hasQueue && isLibraryEmpty
                    layoutHomeEmpty?.visibility = if (isHomeEmpty) View.VISIBLE else View.GONE
                }
                "Buscar", "Animes" -> {
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

            updateRailItemColors()

            val context = root.context
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

    private fun updateRailItemColors() {
        binding.apply {
            val normalBg = R.drawable.rail_item_focus_bg
            val activeBg = R.drawable.nav_item_active_bg
            val context = root.context
            val normalTextColor = context.resolveThemeColor(R.attr.colorTextPrimary)
            val activeTextColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
            val normalIconColor = context.resolveThemeColor(R.attr.colorTextSecondary)
            val activeIconColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
            val focusedDarkColor = context.resolveThemeColor(R.attr.colorOnPrimary)
            val focusedDarkSubtitleColor = Color.parseColor("#1E293B")
            val tab = currentTab

            val profileFocused = userProfilePill?.hasFocus() == true
            tvUserName?.setTextColor(if (profileFocused) focusedDarkColor else Color.WHITE)
            tvSwitchProfileSubtitle?.setTextColor(
                if (profileFocused) focusedDarkSubtitleColor else context.resolveThemeColor(R.attr.colorTextSecondary)
            )

            fun styleNavItem(
                container: View?,
                label: android.widget.TextView?,
                icon: android.widget.ImageView?,
                isActive: Boolean
            ) {
                if (container == null) return
                val isFocused = container.hasFocus()
                val bg = when {
                    isFocused -> normalBg
                    isActive -> activeBg
                    else -> normalBg
                }
                container.setBackgroundResource(bg)
                container.isSelected = isActive
                val textColor = when {
                    isFocused -> focusedDarkColor
                    isActive -> activeTextColor
                    else -> normalTextColor
                }
                val iconColor = when {
                    isFocused -> focusedDarkColor
                    isActive -> activeIconColor
                    else -> normalIconColor
                }
                label?.setTextColor(textColor)
                icon?.imageTintList = ColorStateList.valueOf(iconColor)
            }

            styleNavItem(btnNavBusca, tvNavBusca, ivNavBuscaIcon, tab == "Buscar")
            styleNavItem(btnNavInicio, tvNavInicio, ivNavInicioIcon, tab == "Início")
            styleNavItem(btnNavFavoritos, tvNavFavoritos, ivNavFavoritosIcon, tab == "Favoritos")
            styleNavItem(btnNavAnimes, tvNavAnimes, ivNavAnimesIcon, tab == "Animes")
            styleNavItem(btnNavFilmes, tvNavFilmes, ivNavFilmesIcon, tab == "Filmes")
            styleNavItem(btnNavPastas, tvNavPastas, ivNavPastasIcon, tab == "Pastas")
            styleNavItem(btnNavConfig, tvNavConfig, ivNavConfigIcon, tab == "Configurações")
        }
    }

    fun focusCurrentNavItem() {
        if (DeviceUi.isTenFootExperience(binding.root.context)) {
            expandTvRail()
        }
        val target = when (currentTab) {
            "Buscar" -> binding.btnNavBusca ?: binding.btnNavInicio
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
            binding.btnBrandLogo.takeIf { it.visibility != View.GONE },
            binding.userProfilePill,
            binding.btnNavBusca,
            binding.btnNavInicio,
            binding.btnNavFavoritos,
            binding.btnNavAnimes,
            binding.btnNavFilmes,
            binding.btnNavPastas,
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

    var isTvRailExpanded: Boolean = false
        private set

    private var tvRailAnimator: android.animation.ValueAnimator? = null

    fun expandTvRail() {
        val context = binding.root.context
        if (!DeviceUi.isTenFootExperience(context) || isTvRailExpanded) return
        isTvRailExpanded = true

        binding.navRail.setBackgroundResource(R.drawable.bg_sidebar_panel_expanded)
        binding.sidebarDimOverlay?.apply {
            animate().cancel()
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val collapsedPx = binding.root.resources.getDimensionPixelSize(R.dimen.tv_nav_rail_width)
        val expandedPx = binding.root.resources.getDimensionPixelSize(R.dimen.tv_nav_rail_expanded_width)

        tvRailAnimator?.cancel()
        val currentWidth = binding.navRail.layoutParams.width.takeIf { it > 0 } ?: collapsedPx
        tvRailAnimator = android.animation.ValueAnimator.ofInt(currentWidth, expandedPx).apply {
            duration = 220L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { anim ->
                binding.navRail.layoutParams.width = anim.animatedValue as Int
                binding.navRail.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    setTvLabelsVisible(true)
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.navRail.layoutParams.width = expandedPx
                    binding.navRail.requestLayout()
                }
            })
            start()
        }
    }

    fun collapseTvRail() {
        val context = binding.root.context
        if (!DeviceUi.isTenFootExperience(context) || !isTvRailExpanded) return
        isTvRailExpanded = false

        binding.sidebarDimOverlay?.apply {
            animate().cancel()
            animate()
                .alpha(0f)
                .setDuration(200L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { visibility = View.GONE }
                .start()
        }

        val collapsedPx = binding.root.resources.getDimensionPixelSize(R.dimen.tv_nav_rail_width)
        val expandedPx = binding.root.resources.getDimensionPixelSize(R.dimen.tv_nav_rail_expanded_width)

        tvRailAnimator?.cancel()
        val currentWidth = binding.navRail.layoutParams.width.takeIf { it > 0 } ?: expandedPx
        tvRailAnimator = android.animation.ValueAnimator.ofInt(currentWidth, collapsedPx).apply {
            duration = 200L
            interpolator = android.view.animation.AccelerateInterpolator()
            addUpdateListener { anim ->
                binding.navRail.layoutParams.width = anim.animatedValue as Int
                binding.navRail.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    setTvLabelsVisible(false)
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.navRail.layoutParams.width = collapsedPx
                    binding.navRail.requestLayout()
                    if (!isTvRailExpanded) {
                        binding.navRail.setBackgroundResource(R.drawable.bg_sidebar_panel)
                    }
                }
            })
            start()
        }
    }

    private fun setTvLabelsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        binding.apply {
            tvBrandLogoText?.visibility = visibility
            tvNavBusca?.visibility = visibility
            tvNavInicio.visibility = visibility
            tvNavAnimes.visibility = visibility
            tvNavFilmes?.visibility = visibility
            tvNavPastas.visibility = visibility
            tvNavFavoritos.visibility = visibility
            tvNavConfig.visibility = visibility
            tvUserName?.visibility = visibility
            tvSwitchProfileSubtitle?.visibility = visibility
        }
        updateRailItemColors()
    }
}

