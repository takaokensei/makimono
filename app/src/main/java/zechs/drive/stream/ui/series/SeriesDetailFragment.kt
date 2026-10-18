package zechs.drive.stream.ui.series

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import zechs.drive.stream.utils.AppLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.DeviceUi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.databinding.FragmentSeriesDetailBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.player.PlayerActivity
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.ui.player2.MPVActivity
import zechs.drive.stream.ui.series.adapter.SeriesDetailEpisodeAdapter
import zechs.drive.stream.ui.series.adapter.SeriesDetailSeasonAdapter
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.ui.series.SeriesEpisodeContextMenu
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.FocusMemory
import zechs.drive.stream.utils.ShimmerController
import zechs.drive.stream.ui.common.StateView
import zechs.drive.stream.domain.common.AppError
import java.util.Locale

@AndroidEntryPoint
class SeriesDetailFragment : BaseFragment() {

    private var _binding: FragmentSeriesDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SeriesDetailViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val args: SeriesDetailFragmentArgs by navArgs()

    private lateinit var seasonAdapter: SeriesDetailSeasonAdapter
    private lateinit var episodeAdapter: SeriesDetailEpisodeAdapter
    private lateinit var synopsisDisclosure: SeriesSynopsisDisclosure
    private var hasRestoredInitialFocus = false
    private var shimmerAnimator: android.animation.ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeriesDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TvAccessibilityLayout.adaptSeriesActions(
            requireContext(),
            binding.actionsRow.parent as? android.widget.HorizontalScrollView,
            binding.actionsRow,
            listOf(
                binding.btnPrimaryAction,
                binding.btnFavorite,
                binding.btnMoreActions
            )
        )
        TvAccessibilityLayout.adaptDetailsSynopsis(requireContext(), binding.tvSynopsis)
        setupTopHeader()
        setupAdapters()
        setupNavigationTabs()
        setupFocusAnimations()
        setupFocusChain()
        synopsisDisclosure = SeriesSynopsisDisclosure(
            scrollView = binding.scrollView,
            synopsis = binding.tvSynopsis,
            toggle = binding.tvSynopsisMore,
            fallbackFocus = binding.btnPrimaryAction,
            expandLabel = getString(R.string.series_synopsis_expand),
            collapseLabel = getString(R.string.series_synopsis_collapse),
            onAvailabilityChanged = ::updateSynopsisFocusChain
        )
        binding.btnRetry.setOnClickListener {
            viewModel.loadSeriesDetails(
                folderId = args.folderId,
                seriesTitle = args.name,
                initialPoster = args.posterUrl
            )
        }
        observeUiState()
        hasRestoredInitialFocus = false

        viewModel.loadSeriesDetails(
            folderId = args.folderId,
            seriesTitle = args.name,
            initialPoster = args.posterUrl
        )
    }

    private fun setupNavigationTabs() {
        binding.tabButtonEpisodes.setOnClickListener {
            viewModel.onEvent(SeriesDetailEvent.SelectNavTab(SeriesNavTab.EPISODES))
        }
        binding.tabButtonDetails.setOnClickListener {
            viewModel.onEvent(SeriesDetailEvent.SelectNavTab(SeriesNavTab.DETAILS))
        }
        binding.tabButtonMoreLikeThis.setOnClickListener {
            viewModel.onEvent(SeriesDetailEvent.SelectNavTab(SeriesNavTab.MORE_LIKE_THIS))
        }

        listOf(
            binding.tabButtonEpisodes,
            binding.tabButtonDetails,
            binding.tabButtonMoreLikeThis
        ).forEach {
            FocusEngine.attach(it, FocusEngine.FocusKind.BUTTON)
        }

        binding.tabButtonEpisodes.nextFocusRightId = binding.tabButtonDetails.id
        binding.tabButtonDetails.nextFocusLeftId = binding.tabButtonEpisodes.id
        binding.tabButtonDetails.nextFocusRightId = binding.tabButtonMoreLikeThis.id
        binding.tabButtonMoreLikeThis.nextFocusLeftId = binding.tabButtonDetails.id
    }

    private fun setupTopHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvBreadcrumbCurrent.text = args.name

        binding.btnSearch.setOnClickListener {
            showEpisodeSearchDialog()
        }
        binding.btnMoreActions.setOnClickListener { showMoreActions() }
    }

    private fun showMoreActions() {
        val current = viewModel.uiState.value as? SeriesDetailUiState.Success
        SeriesMoreActionsDialog.show(
            context = requireContext(),
            focusAnchor = binding.btnMoreActions,
            isFollowed = current?.isFollowed == true,
            onTrailer = { binding.btnTrailer.performClick() },
            onFollowToggle = { binding.btnFollow.performClick() },
            onMarkWatched = { binding.btnMarkWatched.performClick() }
        )
    }

    private fun setupAdapters() {
        seasonAdapter = SeriesDetailSeasonAdapter(
            onTabClick = { seasonTab ->
                viewModel.selectSeasonTab(seasonTab)
            },
            onChipClick = { category ->
                viewModel.selectCategory(category)
            }
        )
        binding.rvSeasonTabs.adapter = seasonAdapter

        episodeAdapter = SeriesDetailEpisodeAdapter(
            onEpisodeClick = { episodeItem ->
                val pos = episodeAdapter.currentList.indexOfFirst { it.file.id == episodeItem.file.id }
                if (pos >= 0) viewModel.onEvent(SeriesDetailEvent.SaveFocus(pos))
                FocusMemory.remember("details.episodes.${args.folderId}", pos.coerceAtLeast(0), episodeItem.file.id)
                playEpisode(episodeItem)
            },
            onEpisodeLongClick = { episodeItem, focusAnchor ->
                val pos = episodeAdapter.currentList.indexOfFirst { it.file.id == episodeItem.file.id }
                if (pos >= 0) viewModel.onEvent(SeriesDetailEvent.SaveFocus(pos))
                FocusMemory.remember("details.episodes.${args.folderId}", pos.coerceAtLeast(0), episodeItem.file.id)
                showEpisodeContextMenu(episodeItem, focusAnchor)
            }
        )
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun setupFocusAnimations() {
        val heroButtons = listOf(
            binding.btnPrimaryAction,
            binding.btnFavorite,
            binding.btnMoreActions,
            binding.btnMarkWatched
        )
        val headerButtons = listOf(
            binding.btnBack,
            binding.btnSearch
        )

        for (btn in heroButtons + headerButtons) {
            FocusEngine.attach(btn, FocusEngine.FocusKind.BUTTON)
            btn.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && binding.tvSynopsis.maxLines <= 8 && binding.scrollView.scrollY > 0) {
                    binding.scrollView.smoothScrollTo(0, 0)
                }
            }
        }
    }

    private fun setupFocusChain() {
        if (!DeviceUi.isTenFootExperience(requireContext())) return
        val hasTopHeader = binding.topHeaderBar.visibility == View.VISIBLE

        // Define focus chain for action buttons
        if (hasTopHeader) {
            binding.btnBack.nextFocusRightId = binding.btnSearch.id
            binding.btnSearch.nextFocusRightId = binding.btnPrimaryAction.id
            binding.btnSearch.nextFocusLeftId = binding.btnBack.id
        }
        binding.btnPrimaryAction.nextFocusRightId = binding.btnFavorite.id
        binding.btnFavorite.nextFocusRightId = binding.btnMoreActions.id
        binding.btnMoreActions.nextFocusRightId = binding.btnMarkWatched.id

        // Reverse direction
        binding.btnMarkWatched.nextFocusLeftId = binding.btnMoreActions.id
        binding.btnMoreActions.nextFocusLeftId = binding.btnFavorite.id
        binding.btnFavorite.nextFocusLeftId = binding.btnPrimaryAction.id
        binding.btnPrimaryAction.nextFocusLeftId =
            if (hasTopHeader) binding.btnSearch.id else binding.btnPrimaryAction.id

        updateFocusChain(isMovie = false, hasSeasonTabs = true)
    }

    private fun updateFocusChain(isMovie: Boolean, hasSeasonTabs: Boolean) {
        if (!DeviceUi.isTenFootExperience(requireContext())) return
        val hasTopHeader = binding.topHeaderBar.visibility == View.VISIBLE
        val upTarget = when {
            binding.tvSynopsisMore.visibility == View.VISIBLE -> binding.tvSynopsisMore.id
            hasTopHeader -> binding.btnBack.id
            else -> View.NO_ID
        }
        listOf(
            binding.btnPrimaryAction,
            binding.btnFavorite,
            binding.btnMoreActions,
            binding.btnMarkWatched
        ).forEach {
            it.nextFocusDownId = binding.tabButtonEpisodes.id
            it.nextFocusUpId = upTarget
        }

        listOf(
            binding.tabButtonEpisodes,
            binding.tabButtonDetails,
            binding.tabButtonMoreLikeThis
        ).forEach {
            it.nextFocusUpId = binding.btnPrimaryAction.id
        }

        if (!isMovie) {
            val downTarget = if (hasSeasonTabs) binding.rvSeasonTabs.id else binding.rvEpisodes.id
            binding.tabButtonEpisodes.nextFocusDownId = downTarget
            binding.rvSeasonTabs.nextFocusUpId = binding.tabButtonEpisodes.id
            binding.rvSeasonTabs.nextFocusRightId = binding.rvEpisodes.id
            binding.rvEpisodes.nextFocusLeftId = if (hasSeasonTabs) binding.rvSeasonTabs.id else binding.rvEpisodes.id
            binding.rvEpisodes.nextFocusUpId = binding.tabButtonEpisodes.id
        }
    }

    private fun updateSynopsisFocusChain(hasDisclosure: Boolean) {
        val b = _binding ?: return
        val hasTopHeader = b.topHeaderBar.visibility == View.VISIBLE
        val headerTarget = if (hasTopHeader) b.btnBack.id else View.NO_ID
        val upTarget = if (hasDisclosure) b.tvSynopsisMore.id else headerTarget
        listOf(
            b.btnPrimaryAction,
            b.btnFavorite,
            b.btnMoreActions,
            b.btnMarkWatched
        ).forEach { it.nextFocusUpId = upTarget }
        b.tvSynopsisMore.nextFocusUpId = headerTarget
        b.tvSynopsisMore.nextFocusDownId = b.btnPrimaryAction.id
        b.btnPrimaryAction.post {
            val currentBinding = _binding ?: return@post
            if (currentBinding.root.findFocus() == null) {
                currentBinding.scrollView.scrollTo(0, 0)
                currentBinding.btnPrimaryAction.requestFocus()
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is SeriesDetailUiState.Loading -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.seriesSkeleton.root.visibility = View.VISIBLE
                            shimmerAnimator?.cancel()
                            shimmerAnimator = ShimmerController.start(binding.seriesSkeleton.root)
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.tvPartialListingNotice.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
                            binding.stateView.visibility = View.GONE
                            binding.tvRomajiTitle.text = state.seriesTitle.ifBlank { args.name }
                            binding.tvEnglishSubtitle.visibility = View.GONE
                            state.posterUrl?.let { poster ->
                                MediaImageLoader.poster(binding.ivSeriesPosterCard, poster)
                            }
                            if (!state.bannerUrl.isNullOrBlank()) {
                                MediaImageLoader.backdrop(binding.ivHeroBackdrop, state.bannerUrl)
                            } else {
                                com.bumptech.glide.Glide.with(binding.ivHeroBackdrop).clear(binding.ivHeroBackdrop)
                                binding.ivHeroBackdrop.setImageDrawable(null)
                            }
                        }

                        is SeriesDetailUiState.Error -> {
                            shimmerAnimator?.cancel()
                            shimmerAnimator = null
                            binding.seriesSkeleton.root.visibility = View.GONE
                            binding.pbLoading.visibility = View.GONE
                            binding.tvPartialListingNotice.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
                            binding.stateView.visibility = View.VISIBLE
                            val stateType = when (state.error) {
                                is AppError.Network -> StateView.StateType.ERROR_NETWORK
                                is AppError.Authentication -> StateView.StateType.ERROR_AUTH
                                is AppError.AccessDenied -> StateView.StateType.ERROR_ACCESS_DENIED
                                is AppError.RateLimited -> StateView.StateType.ERROR_RATE_LIMITED
                                is AppError.NotFound -> StateView.StateType.ERROR_NOT_FOUND
                                is AppError.Server -> StateView.StateType.ERROR_SERVER
                                is AppError.Empty -> StateView.StateType.EMPTY_FOLDER
                                else -> StateView.StateType.ERROR_GENERIC
                            }
                            binding.stateView.setState(stateType) {
                                viewModel.loadSeriesDetails(args.folderId, args.name, args.posterUrl)
                            }
                        }

                        is SeriesDetailUiState.Success -> {
                            shimmerAnimator?.cancel()
                            shimmerAnimator = null
                            binding.seriesSkeleton.root.visibility = View.GONE
                            binding.pbLoading.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
                            binding.stateView.visibility = View.GONE
                            binding.tvPartialListingNotice.visibility =
                                if (state.hasIncompleteDriveListing) View.VISIBLE else View.GONE
                            bindSeriesData(state)
                        }
                    }
                }
            }
        }
    }

    private fun bindSeriesData(state: SeriesDetailUiState.Success) {
        // 1. Sharp Poster Card (Featured Carousel - Card Variant)
        val sharpPoster = state.aniListMetadata?.posterUrl
            ?: state.animeEntry?.imageUrl
            ?: state.fallbackPosterUrl
            ?: args.posterUrl
        if (sharpPoster != null) {
            MediaImageLoader.poster(binding.ivSeriesPosterCard, sharpPoster)
        }

        // Ambient Backdrop (use banner first, fallback to poster so TV backdrop is never empty)
        val bannerOrBackdrop = state.aniListMetadata?.bannerUrl
            ?: state.fallbackPosterUrl
            ?: sharpPoster
        if (!bannerOrBackdrop.isNullOrBlank()) {
            MediaImageLoader.backdrop(binding.ivHeroBackdrop, bannerOrBackdrop)
        } else {
            com.bumptech.glide.Glide.with(binding.ivHeroBackdrop).clear(binding.ivHeroBackdrop)
            binding.ivHeroBackdrop.setImageDrawable(null)
        }

        // Dynamic Color Gradient Tint from AniList dominant color
        binding.viewDynamicColorTint.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val dominantHex = state.aniListMetadata?.dominantColor
        if (!dominantHex.isNullOrBlank()) {
            try {
                val parsedColor = android.graphics.Color.parseColor(dominantHex)
                val tintColor = android.graphics.Color.argb(
                    28,
                    android.graphics.Color.red(parsedColor),
                    android.graphics.Color.green(parsedColor),
                    android.graphics.Color.blue(parsedColor)
                )
                val gradient = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(tintColor, android.graphics.Color.TRANSPARENT)
                )
                binding.viewDynamicColorTint.background = gradient
            } catch (e: Exception) {
                AppLog.w("SeriesDetail", "Could not parse dominant color: $dominantHex")
            }
        }

        val isMovie = state.isMovie
        val isTenFoot = DeviceUi.isTenFootExperience(requireContext())
        binding.tvBreadcrumbRoot.text = if (isMovie) {
            getString(R.string.movies)
        } else {
            getString(R.string.series_breadcrumb_library)
        }

        // 2. Titles Block
        val isMovieOrNonAnime = isMovie || state.aniListMetadata?.isAnime == false
        val japaneseTitle = if (isMovieOrNonAnime) {
            null
        } else {
            state.aniListMetadata?.titleNative ?: state.animeEntry?.titleJapanese
        }
        if (!isTenFoot && !japaneseTitle.isNullOrBlank()) {
            binding.tvJapaneseTitle.visibility = View.VISIBLE
            binding.tvJapaneseTitle.text = japaneseTitle
        } else {
            binding.tvJapaneseTitle.visibility = View.GONE
        }

        val canonicalTitle = if (state.isFranchiseHub && state.seriesTitle.isNotBlank()) {
            state.seriesTitle
        } else {
            state.aniListMetadata?.titleRomaji
                ?: state.animeEntry?.title?.takeIf { it.isNotBlank() }
                ?: state.seriesTitle
        }
        binding.tvRomajiTitle.text = canonicalTitle
        binding.tvBreadcrumbCurrent.text = canonicalTitle

        if (state.isFranchiseHub && state.franchiseMembersCount > 1) {
            binding.tvFranchiseCanonicalPill.visibility = View.VISIBLE
            val countStr = resources.getQuantityString(
                R.plurals.franchise_works_count,
                state.franchiseMembersCount,
                state.franchiseMembersCount
            )
            binding.tvFranchiseCanonicalPill.text = getString(R.string.franchise_canonical_badge, countStr)
        } else {
            binding.tvFranchiseCanonicalPill.visibility = View.GONE
        }

        val englishSubtitle = state.aniListMetadata?.titleEnglish
            ?: state.animeEntry?.titleEnglish
            ?: canonicalTitle
        binding.tvEnglishSubtitle.text = englishSubtitle
        val showEnglishSubtitle = !isTenFoot && englishSubtitle.isNotBlank() &&
            !englishSubtitle.equals(canonicalTitle, true) &&
            !englishSubtitle.equals(japaneseTitle, true)
        binding.tvEnglishSubtitle.visibility = if (showEnglishSubtitle) View.VISIBLE else View.GONE

        // 3. Meta Row 1
        val ageRating = formatRating(state.animeEntry?.rating) ?: if (isTenFoot && !isMovie) "A14" else null
        if (ageRating != null) {
            binding.tvAgeRating.visibility = View.VISIBLE
            binding.tvAgeRating.text = ageRating
        } else {
            binding.tvAgeRating.visibility = View.GONE
        }

        val releaseYear = state.activeArcYear ?: state.aniListMetadata?.year ?: state.animeEntry?.year
        if (releaseYear != null) {
            binding.tvReleaseYear.visibility = View.VISIBLE
            binding.tvReleaseYear.text = releaseYear.toString()
        } else {
            binding.tvReleaseYear.visibility = View.GONE
        }

        val airingStatus = SeriesAiringStatusPolicy.classify(state.animeEntry?.status)
        val airingStatusLabel = when (airingStatus) {
            SeriesAiringStatus.COMPLETED -> R.string.anime_status_completed
            SeriesAiringStatus.AIRING -> R.string.anime_status_airing
            SeriesAiringStatus.UPCOMING -> R.string.anime_status_upcoming
            SeriesAiringStatus.HIATUS -> R.string.anime_status_hiatus
            SeriesAiringStatus.CANCELLED -> R.string.anime_status_cancelled
            null -> null
        }
        binding.tvSeriesStatus.visibility = if (airingStatusLabel == null) View.GONE else View.VISIBLE
        airingStatusLabel?.let(binding.tvSeriesStatus::setText)

        val scoreVal = state.activeArcScore ?: state.aniListMetadata?.score ?: state.animeEntry?.score
        binding.layoutRating.visibility = if (scoreVal != null) View.VISIBLE else View.GONE
        val genres = state.aniListMetadata?.genres ?: state.animeEntry?.genres.orEmpty()
        val hasMetaRow1 = ageRating != null || releaseYear != null ||
            airingStatusLabel != null || scoreVal != null || genres.isNotEmpty()
        binding.metaRow1.visibility = if (hasMetaRow1) View.VISIBLE else View.GONE
        if (scoreVal != null) {
            binding.tvMalScore.visibility = View.VISIBLE
            binding.tvMalScore.text = String.format(Locale.US, "%.2f", scoreVal)
            val scoreSource = state.activeArcScoreSource.ifBlank {
                if (state.aniListMetadata?.isAnime == false) "TMDB" else getString(R.string.series_source_mal)
            }
            binding.tvRatingSource?.text = scoreSource
        } else {
            binding.tvMalScore.visibility = View.GONE
        }

        // 4. Meta Row 2 (Tech Specs)
        val quality = state.qualityBadge
        binding.tvQualityBadge.text = quality.orEmpty()
        binding.qualityBadgeContainer.visibility = if (quality.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvQualityBadge.contentDescription = quality?.let {
            getString(R.string.series_inferred_resolution_from_filename, it)
        }

        val audio = state.audioBadge
        binding.tvAudioTrackBadge.text = audio.orEmpty()
        binding.audioBadgeContainer.visibility = if (audio.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvAudioTrackBadge.contentDescription = audio?.let {
            getString(R.string.series_inferred_audio_from_filename, it)
        }

        val subtitle = state.subtitleBadge
        binding.tvSubtitleBadge.text = subtitle.orEmpty()
        binding.subtitleBadgeContainer.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvSubtitleBadge.contentDescription = subtitle?.let {
            getString(R.string.series_inferred_subtitles_from_filename, it)
        }
        binding.metaRow2.visibility = if (
            !quality.isNullOrBlank() || !audio.isNullOrBlank() || !subtitle.isNullOrBlank()
        ) View.VISIBLE else View.GONE

        // Genres Row
        if (genres.isNotEmpty()) {
            binding.tvGenres?.visibility = View.VISIBLE
            val translatedGenres = genres.map { translateGenreToPortuguese(it) }
            val joinedGenres = translatedGenres.take(4).joinToString(", ")
            binding.tvGenres?.text = joinedGenres
        } else {
            binding.tvGenres?.visibility = View.GONE
        }

        // 5. Synopsis
        val rawSynopsis = state.aniListMetadata?.synopsis ?: state.animeEntry?.synopsis
        val cleanSynopsis = rawSynopsis?.takeIf(String::isNotBlank)?.let {
            it
                .replace(Regex("<br\\s*/?>"), " ")
                .replace(Regex("<.*?>"), "")
                .trim()
        }
        synopsisDisclosure.bind(cleanSynopsis)

        // 5. Action Buttons
        val canResume = state.continueWatchingItem?.progressPercent in 1..94
        val actionRes = if (isMovie) {
            if (canResume) R.string.series_action_continue_movie else R.string.series_action_play
        } else {
            if (canResume) R.string.series_action_continue else R.string.series_action_play
        }
        binding.tvPrimaryActionTitle.setText(actionRes)
        binding.tvPrimaryActionSubtitle.text = state.continueWatchingSubtitle
        binding.tvPrimaryActionSubtitle.visibility = if (state.continueWatchingSubtitle.isBlank()) View.GONE else View.VISIBLE
        binding.btnPrimaryAction.setOnClickListener {
            state.continueWatchingItem?.let { playEpisode(it) }
                ?: Toast.makeText(requireContext(), getString(R.string.no_episodes_available), Toast.LENGTH_SHORT).show()
        }

        binding.btnTrailer.setOnClickListener {
            val promoItem = state.trailerItem
            if (promoItem != null) {
                playEpisode(promoItem, standalone = true)
            } else {
                val trailerUrl = state.aniListMetadata?.trailerUrl
                if (!trailerUrl.isNullOrBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(trailerUrl))
                        startActivity(intent)
                    } catch (e: Exception) {
                        AppLog.w("SeriesDetail", "Could not open trailer url: $trailerUrl", e)
                        Toast.makeText(requireContext(), getString(R.string.trailer_not_opened), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.trailer_not_available), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Star / Favorite button
        updateFavoriteButton(state.isStarred)
        binding.btnFavorite.setOnClickListener {
            viewModel.toggleStar()
        }

        updateFollowButton(state.isFollowed)
        binding.btnFollow.setOnClickListener {
            viewModel.toggleFollow()
        }

        // Mark Watched button
        binding.btnMarkWatched.visibility = if (isMovie) View.GONE else View.VISIBLE
        binding.btnMarkWatched.setOnClickListener {
            viewModel.markSeasonWatched()
            Toast.makeText(requireContext(), getString(R.string.season_marked_watched), Toast.LENGTH_SHORT).show()
        }

        // 6. Navigation Tabs & Panels
        binding.tabButtonEpisodes.isSelected = state.selectedNavTab == SeriesNavTab.EPISODES
        binding.tabButtonDetails.isSelected = state.selectedNavTab == SeriesNavTab.DETAILS
        binding.tabButtonMoreLikeThis.isSelected = state.selectedNavTab == SeriesNavTab.MORE_LIKE_THIS

        val showEpisodes = !isMovie && state.selectedNavTab == SeriesNavTab.EPISODES
        binding.panelEpisodesTab.visibility = if (showEpisodes) View.VISIBLE else View.GONE
        binding.panelDetailsTab.visibility = if (state.selectedNavTab == SeriesNavTab.DETAILS) View.VISIBLE else View.GONE
        binding.panelMoreLikeThisTab.visibility = if (state.selectedNavTab == SeriesNavTab.MORE_LIKE_THIS) View.VISIBLE else View.GONE

        binding.tvDetailsFullSynopsis.text = cleanSynopsis ?: ""

        if (isMovie) {
            binding.tabsContainer.visibility = View.GONE
            binding.panelEpisodesTab.visibility = View.GONE
            binding.rvSeasonTabs.visibility = View.GONE
            binding.layoutSeasonsColumn.visibility = View.GONE
            binding.tvEpisodesHeading?.visibility = View.GONE
            binding.rvEpisodes.visibility = View.VISIBLE
            episodeAdapter.submitList(state.currentEpisodes)
            binding.tvEmptyEpisodes.visibility = View.GONE
            binding.tvPartialListingNotice.visibility = View.GONE
        } else {
            binding.tabsContainer.visibility = View.VISIBLE
            val hasSeasons = state.seasonTabs.isNotEmpty()
            binding.layoutSeasonsColumn.visibility = if (hasSeasons) View.VISIBLE else View.GONE
            binding.rvSeasonTabs.visibility = if (hasSeasons) View.VISIBLE else View.GONE
            seasonAdapter.submitList(state.seasonTabs)

            binding.rvEpisodes.visibility = View.VISIBLE
            episodeAdapter.submitList(state.currentEpisodes)
            if (state.currentEpisodes.isEmpty()) {
                binding.tvEmptyEpisodes.visibility = View.VISIBLE
            } else {
                binding.tvEmptyEpisodes.visibility = View.GONE
            }
        }

        updateFocusChain(isMovie, hasSeasonTabs = !isMovie && state.seasonTabs.isNotEmpty())

        // Do not steal focus again when the metadata enrichment state arrives, unless focus was lost.
        if (!hasRestoredInitialFocus) {
            hasRestoredInitialFocus = true
            val viewModelFocusRestored = viewModel.lastFocusedPosition?.let { pos ->
                binding.rvEpisodes.post {
                    binding.rvEpisodes.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                }
                true
            } ?: false
            val restored = viewModelFocusRestored || FocusMemory.restore(binding.rvEpisodes, "details.episodes.${args.folderId}") { pos ->
                episodeAdapter.currentList.getOrNull(pos)?.file?.id
            }
            if (!restored) {
                binding.btnPrimaryAction.post {
                    val currentBinding = _binding ?: return@post
                    currentBinding.scrollView.scrollTo(0, 0)
                    currentBinding.btnPrimaryAction.requestFocus()
                }
            }
        } else if (binding.root.findFocus() == null) {
            binding.btnPrimaryAction.post {
                val currentBinding = _binding ?: return@post
                if (currentBinding.root.findFocus() == null) {
                    currentBinding.scrollView.scrollTo(0, 0)
                    currentBinding.btnPrimaryAction.requestFocus()
                }
            }
        }
    }

    private fun updateFavoriteButton(isStarred: Boolean) {
        if (isStarred) {
            binding.ivFavoriteIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_filled_24)
            )
            binding.ivFavoriteIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.amber_500))
            binding.tvFavoriteText.setText(R.string.series_action_in_watchlist)
        } else {
            binding.ivFavoriteIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_outline_24)
            )
            binding.ivFavoriteIcon.clearColorFilter()
            binding.tvFavoriteText.setText(R.string.series_action_watchlist)
        }
    }

    private fun updateFollowButton(isFollowed: Boolean) {
        binding.tvFollowText.setText(
            if (isFollowed) R.string.series_action_following else R.string.series_action_follow
        )
        binding.ivFollowIcon.clearColorFilter()
    }

    private fun showEpisodeContextMenu(item: SeriesEpisodeItem, focusAnchor: View) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (item.progressPercent in 1..94) {
            options.add(getString(R.string.episode_action_continue, item.progressPercent))
            actions.add { playEpisode(item, startFromBeginning = false) }

            options.add(getString(R.string.episode_action_restart))
            actions.add { playEpisode(item, startFromBeginning = true) }

            options.add(getString(R.string.episode_action_mark_watched))
            actions.add { viewModel.setEpisodeWatched(item.file, true) }

            options.add(getString(R.string.episode_action_clear_progress))
            actions.add { viewModel.resetEpisodeProgress(item.file) }
        } else if (item.progressPercent >= 95) {
            options.add(getString(R.string.episode_action_watch_again))
            actions.add { playEpisode(item, startFromBeginning = true) }

            options.add(getString(R.string.episode_action_mark_unwatched))
            actions.add { viewModel.setEpisodeWatched(item.file, false) }
        } else {
            options.add(getString(R.string.episode_action_play))
            actions.add { playEpisode(item, startFromBeginning = false) }

            options.add(getString(R.string.episode_action_mark_watched))
            actions.add { viewModel.setEpisodeWatched(item.file, true) }
        }

        options.add(getString(R.string.episode_action_copy_filename))
        actions.add {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Arquivo", item.file.name))
            Toast.makeText(requireContext(), getString(R.string.file_name_copied), Toast.LENGTH_SHORT).show()
        }

        options.add(getString(R.string.add_to_queue))
        actions.add { viewModel.addToQueue(item.file) }

        SeriesEpisodeContextMenu.show(
            context = requireContext(),
            focusAnchor = focusAnchor,
            title = item.displayTitle,
            options = options
        ) { which ->
            actions.getOrNull(which)?.invoke()
        }
    }

    private fun playEpisode(
        item: SeriesEpisodeItem,
        startFromBeginning: Boolean = false,
        standalone: Boolean = false
    ) {
        val pos = episodeAdapter.currentList.indexOfFirst { it.file.id == item.file.id }.coerceAtLeast(0)
        FocusMemory.remember("details.episodes.${args.folderId}", pos, item.file.id)
        val currentState = viewModel.uiState.value as? SeriesDetailUiState.Success ?: return

        // A trailer must not inherit the narrative playlist or autoplay into an episode.
        val playlistEpisodes = if (standalone) listOf(item) else currentState.currentEpisodes
        val playlist = ArrayList(playlistEpisodes.map { ep ->
            PlaylistItem(
                fileId = ep.file.id,
                title = ep.file.name,
                thumbnailLink = ep.file.thumbnailLarge ?: ep.file.thumbnailLink
            )
        })

        val file = item.file
        val fileId = file.id
        val thumb = file.thumbnailLarge ?: file.thumbnailLink ?: file.posterUrl
        val startPos = if (startFromBeginning || item.progressPercent >= 95) {
            0L
        } else if (item.progressPercent in 1..94) {
            item.watchedDuration
        } else {
            -1L
        }

        val isMovie = currentState.isMovie
        val canonicalTitle = currentState.aniListMetadata?.titleRomaji
            ?: currentState.animeEntry?.title?.takeIf { it.isNotBlank() }
            ?: currentState.seriesTitle

        val launchTitle = if (isMovie) {
            canonicalTitle
        } else {
            file.name
        }
        val seriesTitle = if (isMovie) {
            canonicalTitle
        } else {
            args.name
        }

        PlayerLauncher.launch(
            context = requireContext(),
            playerType = mainViewModel.currentPlayerIndex,
            fileId = fileId,
            title = launchTitle,
            seriesTitle = seriesTitle,
            thumbnailLink = thumb,
            themeIndex = mainViewModel.currentThemeIndex,
            playlist = playlist,
            startPosition = startPos,
            isMovie = isMovie
        )
    }

    private fun formatRating(rating: String?): String? {
        if (rating.isNullOrBlank()) return null
        val lower = rating.lowercase(Locale.ROOT)
        return when {
            lower.contains("18+") || lower.contains("rx") || lower.contains("r+") -> "18+"
            lower.contains("17+") || lower.contains("r -") -> "16+"
            lower.contains("13+") || lower.contains("pg-13") -> "14+"
            lower.contains("pg") -> "10+"
            lower.contains("g") -> "L"
            else -> null
        }
    }

    private fun translateGenreToPortuguese(genre: String): String {
        return when (genre.trim().lowercase(Locale.ROOT)) {
            "action", "ação" -> "Ação"
            "adventure", "aventura" -> "Aventura"
            "comedy", "comédia" -> "Comédia"
            "drama" -> "Drama"
            "fantasy", "fantasia" -> "Fantasia"
            "mystery", "mistério" -> "Mistério"
            "psychological", "psicológico" -> "Psicológico"
            "romance" -> "Romance"
            "sci-fi", "science fiction", "ficção científica" -> "Ficção Científica"
            "supernatural", "sobrenatural" -> "Sobrenatural"
            "thriller", "suspense" -> "Suspense"
            "slice of life", "cotidiano" -> "Cotidiano"
            "horror", "terror" -> "Terror"
            "sports", "esporte", "esportes" -> "Esportes"
            "mecha" -> "Mecha"
            "music", "música" -> "Música"
            "ecchi" -> "Ecchi"
            "magic", "magia" -> "Magia"
            "school", "escolar" -> "Escolar"
            "shounen" -> "Shounen"
            "seinen" -> "Seinen"
            "shoujo" -> "Shoujo"
            "josei" -> "Josei"
            else -> genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun showEpisodeSearchDialog() {
        val state = viewModel.uiState.value
        if (state !is SeriesDetailUiState.Success) return
        val episodes = state.currentEpisodes
        if (episodes.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.series_search_episode)
                .setMessage(R.string.series_no_episode_loaded)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val titles = episodes.map { ep ->
            val num = ep.episodeNumber?.let { getString(R.string.series_episode_number_short, it) } ?: ep.displayTitle
            "$num • ${ep.displayTitle}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(R.string.series_search_episode)
            .setItems(titles) { dialog, which ->
                dialog.dismiss()
                episodes.getOrNull(which)?.let { playEpisode(it) }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (hasRestoredInitialFocus && ::episodeAdapter.isInitialized) {
            FocusMemory.restore(binding.rvEpisodes, "details.episodes.${args.folderId}") { pos ->
                episodeAdapter.currentList.getOrNull(pos)?.file?.id
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        _binding = null
    }
}
