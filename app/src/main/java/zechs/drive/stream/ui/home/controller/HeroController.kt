package zechs.drive.stream.ui.home.controller

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.bumptech.glide.Glide
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
            animateFocus(v, hasFocus, 1.05f, 8f)
        }

        binding.btnFeaturedPlay?.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                onRailFocusRequested()
                true
            } else false
        }

        binding.btnFeaturedInfo?.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus, 1.05f, 8f)
        }

        binding.userProfilePill?.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus, 1.05f, 6f)
        }

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
            if (insideRail) binding.btnFeaturedPlay?.requestFocus()
        }
    }

    fun observeFeaturedAnime(
        currentTabProvider: () -> String,
        reduceMotionEnabled: Boolean,
        isCollapsible: Boolean
    ) {
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
