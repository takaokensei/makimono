package zechs.drive.stream.ui.series.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.databinding.ItemSeriesSeasonTabBinding

data class SeasonTab(
    val id: String,
    val title: String,
    val iconRes: Int? = null,
    val malId: Int? = null,
    val isSelected: Boolean = false
)

class SeriesDetailSeasonAdapter(
    private val onTabClick: (SeasonTab) -> Unit
) : ListAdapter<SeasonTab, SeriesDetailSeasonAdapter.SeasonViewHolder>(DiffCallback) {

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

            binding.tabPillContainer.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.06f).scaleY(1.06f).translationZ(8f).setDuration(150L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150L).start()
                }
            }
        }

        fun bind(tab: SeasonTab) {
            binding.tabPillContainer.isSelected = tab.isSelected
            binding.tvTabTitle.text = tab.title

            if (tab.iconRes != null) {
                binding.ivTabIcon.visibility = android.view.View.VISIBLE
                binding.ivTabIcon.setImageDrawable(
                    ContextCompat.getDrawable(binding.root.context, tab.iconRes)
                )
            } else {
                binding.ivTabIcon.visibility = android.view.View.GONE
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
