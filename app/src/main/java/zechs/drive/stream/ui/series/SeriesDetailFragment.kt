package zechs.drive.stream.ui.series

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
        seasonAdapter = SeriesDetailSeasonAdapter { seasonTab ->
            viewModel.selectSeasonTab(seasonTab)
        }
        binding.rvSeasonTabs.adapter = seasonAdapter

        episodeAdapter = SeriesDetailEpisodeAdapter(
            onEpisodeClick = { episodeItem ->
                playEpisode(episodeItem)
            },
            onEpisodeLongClick = { episodeItem, focusAnchor ->
                showEpisodeContextMenu(episodeItem, focusAnchor)
            }
        )
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun setupFocusAnimations() {
        val actionButtons = listOf(
            binding.btnPrimaryAction,
            binding.btnFavorite,
            binding.btnMoreActions,
            binding.btnBack,
            binding.btnSearch
        )

        for (btn in actionButtons) {
            FocusEngine.attach(btn, FocusEngine.FocusKind.BUTTON)
        }
    }

    private fun setupFocusChain() {
        if (!DeviceUi.isTenFootExperience(requireContext())) return

        // Define focus chain for action buttons
        binding.btnBack.nextFocusRightId = binding.btnSearch.id
        binding.btnSearch.nextFocusRightId = binding.btnPrimaryAction.id
        binding.btnPrimaryAction.nextFocusRightId = binding.btnFavorite.id
        binding.btnFavorite.nextFocusRightId = binding.btnMoreActions.id

        // Reverse direction
        binding.btnMoreActions.nextFocusLeftId = binding.btnFavorite.id
        binding.btnFavorite.nextFocusLeftId = binding.btnPrimaryAction.id
        binding.btnPrimaryAction.nextFocusLeftId = binding.btnSearch.id
        binding.btnSearch.nextFocusLeftId = binding.btnBack.id

        // Connect to season tabs
        listOf(binding.btnPrimaryAction, binding.btnFavorite, binding.btnMoreActions).forEach {
            it.nextFocusDownId = binding.rvSeasonTabs.id
            it.nextFocusUpId = binding.btnBack.id
        }
        binding.rvSeasonTabs.nextFocusUpId = binding.btnMoreActions.id

        // Connect season tabs to episodes
        binding.rvSeasonTabs.nextFocusDownId = binding.rvEpisodes.id
        binding.rvEpisodes.nextFocusUpId = binding.rvSeasonTabs.id
    }

    private fun updateSynopsisFocusChain(hasDisclosure: Boolean) {
        val upTarget = if (hasDisclosure) binding.tvSynopsisMore.id else binding.btnBack.id
        listOf(binding.btnPrimaryAction, binding.btnFavorite, binding.btnMoreActions)
            .forEach { it.nextFocusUpId = upTarget }
        binding.tvSynopsisMore.nextFocusUpId = binding.btnBack.id
        binding.tvSynopsisMore.nextFocusDownId = binding.btnPrimaryAction.id
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is SeriesDetailUiState.Loading -> {
                            binding.pbLoading.visibility = View.VISIBLE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.tvPartialListingNotice.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
                            binding.tvRomajiTitle.text = state.seriesTitle.ifBlank { args.name }
                            binding.tvEnglishSubtitle.visibility = View.GONE
                            state.posterUrl?.let { poster ->
                                MediaImageLoader.poster(binding.ivSeriesPosterCard, poster)
                                MediaImageLoader.backdrop(binding.ivHeroBackdrop, poster)
                            }
                        }

                        is SeriesDetailUiState.Error -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.tvPartialListingNotice.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.VISIBLE
                            binding.tvEmptyEpisodes.text = state.message
                            binding.btnRetry.visibility = View.VISIBLE
                        }

                        is SeriesDetailUiState.Success -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
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

        // Ambient Backdrop (Banner or subtle ambient poster)
        val bannerOrBackdrop = state.aniListMetadata?.bannerUrl ?: state.fallbackPosterUrl ?: sharpPoster
        if (bannerOrBackdrop != null) {
            MediaImageLoader.backdrop(binding.ivHeroBackdrop, bannerOrBackdrop)
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
                Log.w("SeriesDetail", "Could not parse dominant color: $dominantHex")
            }
        }

        // 2. Titles Block
        val japaneseTitle = state.aniListMetadata?.titleNative ?: state.animeEntry?.titleJapanese
        if (!japaneseTitle.isNullOrBlank()) {
            binding.tvJapaneseTitle.visibility = View.VISIBLE
            binding.tvJapaneseTitle.text = japaneseTitle
        } else {
            binding.tvJapaneseTitle.visibility = View.GONE
        }

        val canonicalTitle = state.aniListMetadata?.titleRomaji
            ?: state.animeEntry?.title?.takeIf { it.isNotBlank() }
            ?: state.seriesTitle
        binding.tvRomajiTitle.text = canonicalTitle

        val englishSubtitle = state.aniListMetadata?.titleEnglish
            ?: state.animeEntry?.titleEnglish
            ?: canonicalTitle
        binding.tvEnglishSubtitle.text = englishSubtitle
        binding.tvEnglishSubtitle.visibility = if (englishSubtitle.isNotBlank() && !englishSubtitle.equals(canonicalTitle, true) && !englishSubtitle.equals(japaneseTitle, true)) View.VISIBLE else View.GONE

        // 3. Meta Row 1
        val ageRating = formatRating(state.animeEntry?.rating)
        if (ageRating != null) {
            binding.tvAgeRating.visibility = View.VISIBLE
            binding.tvAgeRating.text = ageRating
        } else {
            binding.tvAgeRating.visibility = View.GONE
        }

        val releaseYear = state.aniListMetadata?.year ?: state.animeEntry?.year
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

        val scoreVal = state.aniListMetadata?.score ?: state.animeEntry?.score
        binding.layoutRating.visibility = if (scoreVal != null) View.VISIBLE else View.GONE
        binding.metaRow1.visibility = if (ageRating != null || releaseYear != null || airingStatusLabel != null || scoreVal != null) View.VISIBLE else View.GONE
        if (scoreVal != null) {
            binding.tvMalScore.visibility = View.VISIBLE
            binding.tvMalScore.text = String.format(Locale.US, "%.1f", scoreVal)
        } else {
            binding.tvMalScore.visibility = View.GONE
        }

        // 4. Meta Row 2 (Tech Specs)
        binding.tvQualityBadge.text = state.qualityBadge.orEmpty()
        binding.qualityBadgeContainer.visibility = if (state.qualityBadge == null) View.GONE else View.VISIBLE
        binding.tvQualityBadge.contentDescription = state.qualityBadge?.let {
            getString(R.string.series_inferred_resolution_from_filename, it)
        }

        binding.tvAudioTrackBadge.text = state.audioBadge.orEmpty()
        binding.audioBadgeContainer.visibility = if (state.audioBadge == null) View.GONE else View.VISIBLE
        binding.tvAudioTrackBadge.contentDescription = state.audioBadge?.let {
            getString(R.string.series_inferred_audio_from_filename, it)
        }

        binding.tvSubtitleBadge.text = state.subtitleBadge.orEmpty()
        binding.subtitleBadgeContainer.visibility = if (state.subtitleBadge == null) View.GONE else View.VISIBLE
        binding.tvSubtitleBadge.contentDescription = state.subtitleBadge?.let {
            getString(R.string.series_inferred_subtitles_from_filename, it)
        }
        binding.metaRow2.visibility = if (
            state.qualityBadge != null || state.audioBadge != null || state.subtitleBadge != null
        ) View.VISIBLE else View.GONE

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
        binding.tvPrimaryActionTitle.setText(
            if (canResume) R.string.series_action_continue else R.string.series_action_play
        )
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
                        Log.w("SeriesDetail", "Could not open trailer url: $trailerUrl", e)
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
        binding.btnMarkWatched.setOnClickListener {
            viewModel.markSeasonWatched()
            Toast.makeText(requireContext(), getString(R.string.season_marked_watched), Toast.LENGTH_SHORT).show()
        }

        // 6. Season Tabs
        seasonAdapter.submitList(state.seasonTabs)

        // 7. Episodes Rail
        episodeAdapter.submitList(state.currentEpisodes)
        if (state.currentEpisodes.isEmpty()) {
            binding.tvEmptyEpisodes.visibility = View.VISIBLE
        } else {
            binding.tvEmptyEpisodes.visibility = View.GONE
        }

        // Do not steal focus again when the metadata enrichment state arrives.
        if (!hasRestoredInitialFocus) {
            hasRestoredInitialFocus = true
            binding.btnPrimaryAction.post { binding.btnPrimaryAction.requestFocus() }
        }
    }

    private fun updateFavoriteButton(isStarred: Boolean) {
        if (isStarred) {
            binding.ivFavoriteIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_filled_24)
            )
            binding.ivFavoriteIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.amber_500))
            binding.tvFavoriteText.setText(R.string.series_action_favorited)
        } else {
            binding.ivFavoriteIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_outline_24)
            )
            binding.ivFavoriteIcon.clearColorFilter()
            binding.tvFavoriteText.setText(R.string.series_action_favorite)
        }
    }

    private fun updateFollowButton(isFollowed: Boolean) {
        binding.tvFollowText.setText(
            if (isFollowed) R.string.series_action_following else R.string.series_action_follow
        )
        binding.ivFollowIcon.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (isFollowed) R.color.cyan_400 else R.color.textColor
            )
        )
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

        PlayerLauncher.launch(
            context = requireContext(),
            playerType = mainViewModel.currentPlayerIndex,
            fileId = fileId,
            title = file.name,
            seriesTitle = args.name,
            thumbnailLink = thumb,
            themeIndex = mainViewModel.currentThemeIndex,
            playlist = playlist,
            startPosition = startPos
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
