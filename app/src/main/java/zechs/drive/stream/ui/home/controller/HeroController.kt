package zechs.drive.stream.ui.home.controller

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.home.FeaturedHeroBinder
import zechs.drive.stream.ui.home.FeaturedPreviewScheduler
import zechs.drive.stream.ui.home.HomeFragmentDirections
import zechs.drive.stream.ui.home.HomeViewModel
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.ext.resolveThemeColor

/**
 * Controlador de destaque (Hero Banner) e perfil do usuário na Home.
 * Encapsula pré-visualização de trailers/metadados, avatar do perfil e foco do hero per Seção 4.10 e 7.3.
 */
class HeroController(
    private val binding: FragmentHomeBinding,
    private val viewModel: HomeViewModel,
    private val navController: NavController,
    private val profileManager: ProfileManager,
    private val profileArtCatalog: ProfileArtCatalog,
    private val lifecycleOwner: LifecycleOwner,
    private val animateFocus: (View, Boolean, Float, Float) -> Unit,
    private val onQuickPlay: (DriveFile) -> Unit,
    private val onRailFocusRequested: () -> Unit
) {

    private var heroAutoFocused = false
    private var featuredPreviewScheduler: FeaturedPreviewScheduler? = null
    private var featuredHeroBinder: FeaturedHeroBinder? = null

    private var heroAutoCycleJob: Job? = null
    private var activeTabProvider: (() -> String)? = null

    fun setup(hasOverlay: Boolean) {
        featuredHeroBinder = FeaturedHeroBinder(
            binding = binding,
            context = binding.root.context
        )

        featuredPreviewScheduler = FeaturedPreviewScheduler(
            scope = lifecycleOwner.lifecycleScope
        ) { folder ->
            viewModel.previewFeatured(folder)
        }

        binding.btnFeaturedPlay?.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus, 1.03f, 6f)
            if (hasFocus) {
                viewModel.clearFocusedFeaturedFolder()
                viewModel.clearSavedShelfFocus()
                binding.contentScrollView.smoothScrollTo(0, 0)
                activeTabProvider?.let { startHeroAutoCycle(it) }
            }
        }

        binding.btnFeaturedPlay?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                onRailFocusRequested()
                true
            } else false
        }

        binding.btnFeaturedInfo?.setOnFocusChangeListener { v, hasFocus ->
            applyHeroActionForeground(v, hasFocus)
            animateFocus(v, hasFocus, 1.03f, 6f)
            if (hasFocus) {
                viewModel.clearFocusedFeaturedFolder()
                viewModel.clearSavedShelfFocus()
                binding.contentScrollView.smoothScrollTo(0, 0)
                activeTabProvider?.let { startHeroAutoCycle(it) }
            }
        }
        binding.btnFeaturedInfo?.let { applyHeroActionForeground(it, it.hasFocus()) }

        binding.userProfilePill?.setOnClickListener {
            navController.navigateSafe(R.id.action_homeFragment_to_profileSelectionFragment)
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collect { profile ->
                    binding.tvUserName?.text = profile.name
                    val avatarRes = profileManager.getAvatarDrawableRes(profile.avatarResName)
                    val avatarSource: Any? = profile.avatarUrl
                        ?: if (profile.avatarResName == "avatar_caua") avatarRes
                        else profileArtCatalog.defaultAvatarForProfile(profile.id)?.url
                    binding.ivUserAvatar?.let { avatar ->
                        avatar.imageTintList = null
                        avatar.clearColorFilter()
                        Glide.with(binding.root)
                            .load(avatarSource)
                            .placeholder(R.drawable.avatar_caua)
                            .error(R.drawable.avatar_caua)
                            .circleCrop()
                            .into(avatar)
                    }
                }
            }
        }
    }

    private fun applyHeroActionForeground(container: View, focused: Boolean) {
        val context = container.context
        val color = context.resolveThemeColor(
            if (focused) R.attr.colorOnPrimary else R.attr.colorTextPrimary
        )
        if (container is android.view.ViewGroup) {
            for (index in 0 until container.childCount) {
                when (val child = container.getChildAt(index)) {
                    is android.widget.TextView -> child.setTextColor(color)
                    is android.widget.ImageView -> child.imageTintList = android.content.res.ColorStateList.valueOf(color)
                }
            }
        }
    }

    private var hadSavedFocusOnCreate: Boolean = false

    fun focusHeroOnFirstShow(isCollapsible: Boolean) {
        if (heroAutoFocused || isCollapsible) return
        heroAutoFocused = true
        binding.featuredHeroContainer?.post {
            val focused = binding.root.findFocus()
            val rail = binding.root.findViewById<View>(R.id.navRail)
            var insideRail = focused == null
            var currentParent: android.view.ViewParent? = focused?.parent
            while (!insideRail && currentParent != null) {
                if (currentParent === rail) insideRail = true
                currentParent = currentParent.parent
            }
            if (insideRail || !hadSavedFocusOnCreate) {
                if (!hadSavedFocusOnCreate) {
                    viewModel.onEvent(zechs.drive.stream.ui.home.HomeEvent.SaveFocus(null, null))
                }
                binding.contentScrollView.scrollTo(0, 0)
                binding.btnFeaturedPlay?.requestFocus()
            }
        }
    }

    fun observeFeaturedAnime(
        currentTabProvider: () -> String,
        reduceMotionEnabled: Boolean,
        isCollapsible: Boolean
    ) {
        this.activeTabProvider = currentTabProvider
        val initialState = viewModel.uiState.value
        hadSavedFocusOnCreate = initialState is zechs.drive.stream.ui.home.HomeUiState.Content &&
            initialState.lastFocusedShelfIndex != null
        // Synchronously restore Hero visibility if featuredAnime is already in memory (e.g. when returning from SeriesDetailFragment)
        val initialFeatured = viewModel.featuredAnime.value
        if (initialFeatured != null && currentTabProvider() == "Início") {
            binding.featuredHeroContainer?.visibility = View.VISIBLE
            binding.layoutHomeEmpty?.visibility = View.GONE
            featuredHeroBinder?.bind(initialFeatured, reduceMotionEnabled)
        }
        startHeroAutoCycle(currentTabProvider)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.featuredAnime.collect { featured ->
                    if (featured != null) {
                        if (currentTabProvider() == "Início") {
                            binding.featuredHeroContainer?.visibility = View.VISIBLE
                            binding.layoutHomeEmpty?.visibility = View.GONE
                            focusHeroOnFirstShow(isCollapsible)
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
                                navController.navigateSafe(action)
                            } else if (file != null) {
                                onQuickPlay(file)
                            } else {
                                val folderFile = DriveFile(
                                    id = featured.folderId,
                                    name = featured.title,
                                    size = null,
                                    mimeType = "application/vnd.google-apps.folder",
                                    iconLink = null,
                                    thumbnailLink = featured.posterUrl,
                                    shortcutDetails = ShortcutDetails(),
                                    starred = Starred.UNSTARRED
                                )
                                onQuickPlay(folderFile)
                            }
                        }

                        binding.btnFeaturedInfo?.setOnClickListener {
                            val action = HomeFragmentDirections.actionHomeFragmentToSeriesDetailFragment(
                                name = featured.title,
                                folderId = featured.folderId,
                                posterUrl = featured.posterUrl ?: featured.backdropUrl
                            )
                            navController.navigateSafe(action)
                        }
                    } else {
                        if (currentTabProvider() == "Início") {
                            binding.featuredHeroContainer?.visibility = View.GONE
                            val hasRecent = viewModel.recentWatches.value.isNotEmpty()
                            val hasLibrary = viewModel.animeLibrary.value.isNotEmpty()
                            binding.layoutHomeEmpty?.visibility = if (!hasRecent && !hasLibrary) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    viewModel.featuredCandidates,
                    viewModel.currentFeaturedIndex
                ) { candidates, activeIdx ->
                    candidates to activeIdx
                }.collect { (candidates, activeIdx) ->
                    featuredHeroBinder?.updatePaginationPills(candidates.size, activeIdx) { index ->
                        viewModel.selectFeaturedByIndex(index)
                    }
                }
            }
        }
    }

    fun startHeroAutoCycle(currentTabProvider: () -> String) {
        heroAutoCycleJob?.cancel()
        heroAutoCycleJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(8500L)
                    val focused = binding.root.findFocus()
                    val isHeroFocused = focused === binding.btnFeaturedPlay || focused === binding.btnFeaturedInfo
                    val rail = binding.root.findViewById<View>(R.id.navRail)
                    val isRailFocused = focused != null && rail != null && run {
                        var p: android.view.ViewParent? = focused.parent
                        var inside = false
                        while (p != null) {
                            if (p === rail) {
                                inside = true
                                break
                            }
                            p = p.parent
                        }
                        inside
                    }

                    if (currentTabProvider() == "Início" && !viewModel.isFocusPreviewActive) {
                        viewModel.cycleNextFeaturedSpotlight()
                    }
                }
            }
        }
    }

    fun scheduleFeaturedPreview(
        folder: DriveFile,
        focusAnchor: View,
        currentTabProvider: () -> String
    ) {
        if (currentTabProvider() != "Início" || !(folder.isFolder || folder.isShortcutFolder)) return
        featuredPreviewScheduler?.schedule(folder) {
            currentTabProvider() == "Início" && focusAnchor.isAttachedToWindow && focusAnchor.hasFocus()
        }
    }

    fun findLibraryFolderForTitle(fileName: String, animeLibrary: List<DriveFile>): DriveFile? {
        val parsed = EpisodeParser.parse(fileName)
        val title = parsed.showTitle.ifBlank { parsed.cleanTitle }
        val normalized = AnimePosterResolver
            .cleanAnimeTitle(title)
            .trim()
            .lowercase(java.util.Locale.ROOT)
        if (normalized.length < 2) return null

        val exactMatch = animeLibrary.firstOrNull { folder ->
            val folderNorm = AnimePosterResolver.cleanAnimeTitle(folder.name).trim().lowercase(java.util.Locale.ROOT)
            folderNorm == normalized
        }
        if (exactMatch != null) return exactMatch

        return animeLibrary.firstOrNull { folder ->
            val folderNorm = AnimePosterResolver.cleanAnimeTitle(folder.name).trim().lowercase(java.util.Locale.ROOT)
            folderNorm.contains(normalized) || normalized.contains(folderNorm)
        }
    }
}
