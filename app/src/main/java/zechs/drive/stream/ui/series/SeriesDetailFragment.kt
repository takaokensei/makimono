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
import zechs.drive.stream.utils.VideoPlayer
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

        setupTopHeader()
        setupAdapters()
        setupFocusAnimations()
        setupFocusChain()
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
            onEpisodeLongClick = { episodeItem ->
                showEpisodeContextMenu(episodeItem)
            }
        )
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun setupFocusAnimations() {
        val actionButtons = listOf(
            binding.btnPrimaryAction,
            binding.btnTrailer,
            binding.btnFavorite,
            binding.btnFollow,
            binding.btnMarkWatched,
            binding.btnBack,
            binding.btnSearch
        )

        for (btn in actionButtons) {
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150L).start()
                }
            }
        }
    }

    private fun setupFocusChain() {
        if (!DeviceUi.isTenFootExperience(requireContext())) return

        // Define focus chain for action buttons
        binding.btnBack.nextFocusRightId = binding.btnSearch.id
        binding.btnSearch.nextFocusRightId = binding.btnPrimaryAction.id
        binding.btnPrimaryAction.nextFocusRightId = binding.btnTrailer.id
        binding.btnTrailer.nextFocusRightId = binding.btnFavorite.id
        binding.btnFavorite.nextFocusRightId = binding.btnFollow.id
        binding.btnFollow.nextFocusRightId = binding.btnMarkWatched.id

        // Reverse direction
        binding.btnMarkWatched.nextFocusLeftId = binding.btnFollow.id
        binding.btnFollow.nextFocusLeftId = binding.btnFavorite.id
        binding.btnFavorite.nextFocusLeftId = binding.btnTrailer.id
        binding.btnTrailer.nextFocusLeftId = binding.btnPrimaryAction.id
        binding.btnPrimaryAction.nextFocusLeftId = binding.btnSearch.id
        binding.btnSearch.nextFocusLeftId = binding.btnBack.id

        // Connect to season tabs
        binding.btnMarkWatched.nextFocusDownId = binding.rvSeasonTabs.id
        binding.rvSeasonTabs.nextFocusUpId = binding.btnMarkWatched.id

        // Connect season tabs to episodes
        binding.rvSeasonTabs.nextFocusDownId = binding.rvEpisodes.id
        binding.rvEpisodes.nextFocusUpId = binding.rvSeasonTabs.id
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is SeriesDetailUiState.Loading -> {
                            binding.pbLoading.visibility = View.VISIBLE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
                            binding.tvRomajiTitle.text = state.seriesTitle.ifBlank { args.name }
                            binding.tvEnglishSubtitle.text = state.seriesTitle.ifBlank { args.name }
                                .uppercase(Locale.ROOT)
                            state.posterUrl?.let { poster ->
                                MediaImageLoader.poster(binding.ivSeriesPosterCard, poster)
                                MediaImageLoader.backdrop(binding.ivHeroBackdrop, poster)
                            }
                        }

                        is SeriesDetailUiState.Error -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.VISIBLE
                            binding.tvEmptyEpisodes.text = state.message
                            binding.btnRetry.visibility = View.VISIBLE
                        }

                        is SeriesDetailUiState.Success -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            binding.btnRetry.visibility = View.GONE
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
        val dominantHex = state.aniListMetadata?.dominantColor
        if (!dominantHex.isNullOrBlank()) {
            try {
                val parsedColor = android.graphics.Color.parseColor(dominantHex)
                val tintColor = android.graphics.Color.argb(
                    75,
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
        binding.tvEnglishSubtitle.text = englishSubtitle.uppercase(Locale.ROOT)

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

        val statusRaw = state.animeEntry?.status
        binding.tvSeriesStatus.text = when {
            statusRaw.isNullOrBlank() -> "—"
            statusRaw.contains("Finished", true) -> "Completo"
            else -> "Em exibição"
        }

        val scoreVal = state.aniListMetadata?.score ?: state.animeEntry?.score
        if (scoreVal != null) {
            binding.tvMalScore.visibility = View.VISIBLE
            binding.tvMalScore.text = String.format(Locale.US, "%.1f", scoreVal)
        } else {
            binding.tvMalScore.visibility = View.GONE
        }

        // 4. Meta Row 2 (Tech Specs)
        binding.tvQualityBadge.text = state.qualityBadge
        binding.tvAudioTrackBadge.text = state.audioBadge
        binding.tvSubtitleBadge.text = state.subtitleBadge

        // 5. Synopsis
        val rawSynopsis = state.aniListMetadata?.synopsis ?: state.animeEntry?.synopsis
        if (!rawSynopsis.isNullOrBlank()) {
            val cleanSynopsis = rawSynopsis
                .replace(Regex("<br\\s*/?>"), " ")
                .replace(Regex("<.*?>"), "")
                .trim()
            binding.tvSynopsis.visibility = View.VISIBLE
            binding.tvSynopsis.text = cleanSynopsis
        } else {
            binding.tvSynopsis.visibility = View.GONE
        }

        // 5. Action Buttons
        binding.tvPrimaryActionSubtitle.text = state.continueWatchingSubtitle
        binding.btnPrimaryAction.setOnClickListener {
            state.continueWatchingItem?.let { playEpisode(it) }
                ?: Toast.makeText(requireContext(), getString(R.string.no_episodes_available), Toast.LENGTH_SHORT).show()
        }

        binding.btnTrailer.setOnClickListener {
            val promoItem = state.currentEpisodes.firstOrNull {
                it.file.name.contains("PV", true) || it.file.name.contains("Trailer", true)
            }
            if (promoItem != null) {
                playEpisode(promoItem)
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
            binding.tvFavoriteText.text = "Favoritado"
        } else {
            binding.ivFavoriteIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_outline_24)
            )
            binding.ivFavoriteIcon.clearColorFilter()
            binding.tvFavoriteText.text = "Favoritar"
        }
    }

    private fun updateFollowButton(isFollowed: Boolean) {
        binding.tvFollowText.text = if (isFollowed) "Seguindo" else "Seguir"
        binding.ivFollowIcon.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (isFollowed) R.color.cyan_400 else R.color.textColor
            )
        )
    }

    private fun showEpisodeContextMenu(item: SeriesEpisodeItem) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (item.progressPercent in 1..94) {
            options.add("Continuar assistindo (${item.progressPercent}%)")
            actions.add { playEpisode(item, startFromBeginning = false) }

            options.add("Assistir do início (0:00)")
            actions.add { playEpisode(item, startFromBeginning = true) }

            options.add("Marcar como assistido (100%)")
            actions.add { viewModel.setEpisodeWatched(item.file, true) }

            options.add("Limpar progresso")
            actions.add { viewModel.resetEpisodeProgress(item.file) }
        } else if (item.progressPercent >= 95) {
            options.add("Assistir novamente (do início)")
            actions.add { playEpisode(item, startFromBeginning = true) }

            options.add("Marcar como não assistido")
            actions.add { viewModel.setEpisodeWatched(item.file, false) }
        } else {
            options.add("Assistir episódio")
            actions.add { playEpisode(item, startFromBeginning = false) }

            options.add("Marcar como assistido")
            actions.add { viewModel.setEpisodeWatched(item.file, true) }
        }

        options.add("Copiar nome do arquivo")
        actions.add {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Arquivo", item.file.name))
            Toast.makeText(requireContext(), getString(R.string.file_name_copied), Toast.LENGTH_SHORT).show()
        }

        options.add("Adicionar à fila")
        actions.add { viewModel.addToQueue(item.file) }

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(item.displayTitle)
            .setItems(options.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }

    private fun playEpisode(item: SeriesEpisodeItem, startFromBeginning: Boolean = false) {
        val currentState = viewModel.uiState.value as? SeriesDetailUiState.Success ?: return

        // Build playlist from current episodes
        val playlist = ArrayList(currentState.currentEpisodes.map { ep ->
            PlaylistItem(
                fileId = ep.file.id,
                title = ep.file.name,
                thumbnailLink = ep.file.thumbnailLarge ?: ep.file.thumbnailLink
            )
        })

        val file = item.file
        val fileId = file.id
        val thumb = file.thumbnailLarge ?: file.thumbnailLink ?: file.posterUrl
        val startPos = if (startFromBeginning) {
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
                .setTitle("Buscar episódio")
                .setMessage("Nenhum episódio carregado ainda.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val titles = episodes.map { ep ->
            val num = ep.episodeNumber?.let { "Ep. ${it.toString().padStart(2, '0')}" } ?: ep.displayTitle
            "$num • ${ep.displayTitle}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle("Buscar episódio")
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
