package zechs.drive.stream.ui.series

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
        observeUiState()

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
            findNavController().navigateUp()
        }
    }

    private fun setupAdapters() {
        seasonAdapter = SeriesDetailSeasonAdapter { seasonTab ->
            viewModel.selectSeasonTab(seasonTab)
        }
        binding.rvSeasonTabs.adapter = seasonAdapter

        episodeAdapter = SeriesDetailEpisodeAdapter { episodeItem ->
            playEpisode(episodeItem)
        }
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun setupFocusAnimations() {
        val actionButtons = listOf(
            binding.btnPrimaryAction,
            binding.btnTrailer,
            binding.btnFavorite,
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

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is SeriesDetailUiState.Loading -> {
                            binding.pbLoading.visibility = View.VISIBLE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                        }

                        is SeriesDetailUiState.Error -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.VISIBLE
                            binding.tvEmptyEpisodes.text = state.message
                        }

                        is SeriesDetailUiState.Success -> {
                            binding.pbLoading.visibility = View.GONE
                            binding.tvEmptyEpisodes.visibility = View.GONE
                            bindSeriesData(state)
                        }
                    }
                }
            }
        }
    }

    private fun bindSeriesData(state: SeriesDetailUiState.Success) {
        // 1. Hero Backdrop
        val backdropUrl = state.animeEntry?.imageUrl ?: args.posterUrl
        if (backdropUrl != null) {
            Glide.with(this)
                .load(backdropUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.DATA)
                .into(binding.ivHeroBackdrop)
        }

        // 2. Titles Block
        val japaneseTitle = state.animeEntry?.titleJapanese
        if (!japaneseTitle.isNullOrBlank()) {
            binding.tvJapaneseTitle.visibility = View.VISIBLE
            binding.tvJapaneseTitle.text = japaneseTitle
        } else {
            binding.tvJapaneseTitle.visibility = View.GONE
        }

        val canonicalTitle = state.animeEntry?.title?.takeIf { it.isNotBlank() } ?: state.seriesTitle
        binding.tvRomajiTitle.text = canonicalTitle

        val englishSubtitle = state.animeEntry?.titleEnglish ?: canonicalTitle
        binding.tvEnglishSubtitle.text = englishSubtitle.uppercase(Locale.ROOT)

        // 3. Meta Row 1
        binding.tvAgeRating.text = formatRating(state.animeEntry?.rating)
        binding.tvReleaseYear.text = state.animeEntry?.year?.toString() ?: "2015"
        binding.tvSeriesStatus.text = if (state.animeEntry?.status?.contains("Finished", true) == true) {
            "Completo"
        } else {
            "Em Exibição"
        }

        val score = state.animeEntry?.score?.let { String.format(Locale.US, "%.1f", it) } ?: "8.2"
        binding.tvMalScore.text = score

        // 4. Meta Row 2 (Tech Specs)
        binding.tvQualityBadge.text = state.qualityBadge
        binding.tvAudioTrackBadge.text = state.audioBadge
        binding.tvSubtitleBadge.text = state.subtitleBadge

        // 5. Action Buttons
        binding.tvPrimaryActionSubtitle.text = state.continueWatchingSubtitle
        binding.btnPrimaryAction.setOnClickListener {
            state.continueWatchingItem?.let { playEpisode(it) }
                ?: Toast.makeText(requireContext(), "Nenhum episódio disponível", Toast.LENGTH_SHORT).show()
        }

        binding.btnTrailer.setOnClickListener {
            val promoItem = state.currentEpisodes.firstOrNull {
                it.file.name.contains("PV", true) || it.file.name.contains("Trailer", true)
            }
            if (promoItem != null) {
                playEpisode(promoItem)
            } else {
                Toast.makeText(requireContext(), "Trailer não disponível nesta pasta", Toast.LENGTH_SHORT).show()
            }
        }

        // Star / Favorite button
        updateFavoriteButton(state.isStarred)
        binding.btnFavorite.setOnClickListener {
            viewModel.toggleStar()
        }

        // Mark Watched button
        binding.btnMarkWatched.setOnClickListener {
            viewModel.markSeasonWatched()
            Toast.makeText(requireContext(), "Temporada marcada como assistida!", Toast.LENGTH_SHORT).show()
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

        // Default D-pad focus to primary action button
        binding.btnPrimaryAction.requestFocus()
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

    private fun playEpisode(item: SeriesEpisodeItem) {
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

        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("fileId", fileId)
                    putExtra("title", file.name)
                    putExtra("seriesTitle", args.name)
                    putExtra("thumbnailLink", thumb)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    putExtra("playlist", playlist)
                    putExtra("startPosition", if (item.progressPercent in 1..94) item.watchedDuration else -1L)
                }
                startActivity(intent)
            }

            VideoPlayer.MPV -> {
                val intent = Intent(requireContext(), MPVActivity::class.java).apply {
                    putExtra("fileId", fileId)
                    putExtra("title", file.name)
                    putExtra("seriesTitle", args.name)
                    putExtra("thumbnailLink", thumb)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    putExtra("playlist", playlist)
                    putExtra("startPosition", if (item.progressPercent in 1..94) item.watchedDuration else -1L)
                }
                startActivity(intent)
            }
        }
    }

    private fun formatRating(rating: String?): String {
        if (rating == null) return "16+"
        val lower = rating.lowercase(Locale.ROOT)
        return when {
            lower.contains("18+") || lower.contains("rx") || lower.contains("r+") -> "18+"
            lower.contains("17+") || lower.contains("r -") -> "16+"
            lower.contains("13+") || lower.contains("pg-13") -> "14+"
            lower.contains("pg") -> "10+"
            lower.contains("g") -> "L"
            else -> "14+"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
