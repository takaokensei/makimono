package zechs.drive.stream.ui.series.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import zechs.drive.stream.R
import zechs.drive.stream.databinding.ItemSeriesSeasonTabBinding
import zechs.drive.stream.domain.library.CategoryChip
import zechs.drive.stream.domain.library.SeriesMediaCategory
import zechs.drive.stream.utils.FocusEngine

data class SeasonTab(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val releaseYear: Int? = null,
    val score: Float? = null,
    val scoreSource: String = "MAL",
    val iconRes: Int? = null,
    val malId: Int? = null,
    val isSelected: Boolean = false,
    val chips: List<CategoryChip> = emptyList(),
    val selectedCategory: SeriesMediaCategory = SeriesMediaCategory.EPISODES
)

class SeriesDetailSeasonAdapter(
    private val onTabClick: (SeasonTab) -> Unit,
    private val onChipClick: ((SeriesMediaCategory) -> Unit)? = null
) : ListAdapter<SeasonTab, SeriesDetailSeasonAdapter.SeasonViewHolder>(DiffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
        val binding = ItemSeriesSeasonTabBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SeasonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SeasonViewHolder(
        private val binding: ItemSeriesSeasonTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.tabPillContainer.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTabClick(getItem(position))
                }
            }

            FocusEngine.attach(binding.tabPillContainer, FocusEngine.FocusKind.BUTTON)
            FocusEngine.attach(binding.chipEpisodes, FocusEngine.FocusKind.BUTTON)
            FocusEngine.attach(binding.chipSpecials, FocusEngine.FocusKind.BUTTON)
            FocusEngine.attach(binding.chipMusic, FocusEngine.FocusKind.BUTTON)
            FocusEngine.attach(binding.chipPreviews, FocusEngine.FocusKind.BUTTON)

            binding.chipEpisodes.setOnClickListener { onChipClick?.invoke(SeriesMediaCategory.EPISODES) }
            binding.chipSpecials.setOnClickListener { onChipClick?.invoke(SeriesMediaCategory.SPECIALS) }
            binding.chipMusic.setOnClickListener { onChipClick?.invoke(SeriesMediaCategory.MUSIC) }
            binding.chipPreviews.setOnClickListener { onChipClick?.invoke(SeriesMediaCategory.PREVIEWS) }
        }

        fun bind(tab: SeasonTab) {
            val context = binding.root.context
            binding.tabPillContainer.isSelected = tab.isSelected
            binding.tvTabTitle.text = tab.title

            if (!tab.subtitle.isNullOrBlank()) {
                binding.tvTabSubtitle.visibility = View.VISIBLE
                binding.tvTabSubtitle.text = tab.subtitle
            } else {
                binding.tvTabSubtitle.visibility = View.GONE
            }

            // Load Arc Thumbnail
            val thumbUrl = tab.posterUrl ?: tab.bannerUrl
            if (!thumbUrl.isNullOrBlank()) {
                Glide.with(context)
                    .load(thumbUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.kodi_primary_bg)
                    .error(R.drawable.kodi_primary_bg)
                    .centerCrop()
                    .into(binding.ivArcThumb)
            } else {
                binding.ivArcThumb.setImageResource(R.drawable.kodi_primary_bg)
            }

            // Category Chips (only visible when arc is selected and has chips)
            if (tab.isSelected && tab.chips.isNotEmpty()) {
                binding.layoutArcChips.visibility = View.VISIBLE

                val epChip = tab.chips.firstOrNull { it.category == SeriesMediaCategory.EPISODES }
                if (epChip != null && epChip.count > 0) {
                    binding.chipEpisodes.visibility = View.VISIBLE
                    binding.chipEpisodes.text = context.getString(R.string.chip_category_episodes, epChip.count)
                    binding.chipEpisodes.isSelected = (tab.selectedCategory == SeriesMediaCategory.EPISODES)
                } else {
                    binding.chipEpisodes.visibility = View.GONE
                }

                val spChip = tab.chips.firstOrNull { it.category == SeriesMediaCategory.SPECIALS }
                if (spChip != null && spChip.count > 0) {
                    binding.chipSpecials.visibility = View.VISIBLE
                    binding.chipSpecials.text = context.getString(R.string.chip_category_specials, spChip.count)
                    binding.chipSpecials.isSelected = (tab.selectedCategory == SeriesMediaCategory.SPECIALS)
                } else {
                    binding.chipSpecials.visibility = View.GONE
                }

                val musChip = tab.chips.firstOrNull { it.category == SeriesMediaCategory.MUSIC }
                if (musChip != null && musChip.count > 0) {
                    binding.chipMusic.visibility = View.VISIBLE
                    binding.chipMusic.text = context.getString(R.string.chip_category_music, musChip.count)
                    binding.chipMusic.isSelected = (tab.selectedCategory == SeriesMediaCategory.MUSIC)
                } else {
                    binding.chipMusic.visibility = View.GONE
                }

                val pvChip = tab.chips.firstOrNull { it.category == SeriesMediaCategory.PREVIEWS }
                if (pvChip != null && pvChip.count > 0) {
                    binding.chipPreviews.visibility = View.VISIBLE
                    binding.chipPreviews.text = context.getString(R.string.chip_category_previews, pvChip.count)
                    binding.chipPreviews.isSelected = (tab.selectedCategory == SeriesMediaCategory.PREVIEWS)
                } else {
                    binding.chipPreviews.visibility = View.GONE
                }
            } else {
                binding.layoutArcChips.visibility = View.GONE
            }

            if (tab.iconRes != null) {
                binding.ivTabIcon.visibility = View.VISIBLE
                binding.ivTabIcon.setImageDrawable(
                    ContextCompat.getDrawable(context, tab.iconRes)
                )
            } else {
                binding.ivTabIcon.visibility = View.GONE
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SeasonTab>() {
        override fun areItemsTheSame(oldItem: SeasonTab, newItem: SeasonTab): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SeasonTab, newItem: SeasonTab): Boolean =
            oldItem == newItem
    }
}
