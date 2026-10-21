package zechs.drive.stream.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.databinding.ItemGenreFilterChipBinding
import zechs.drive.stream.utils.FocusEngine

class GenreFilterAdapter(
    private val genres: List<String>,
    private val onGenreSelected: (String) -> Unit
) : RecyclerView.Adapter<GenreFilterAdapter.GenreViewHolder>() {

    var selectedGenre: String = genres.firstOrNull() ?: "Todos"
        set(value) {
            val prev = field
            field = value
            val prevIdx = genres.indexOf(prev)
            val newIdx = genres.indexOf(value)
            if (prevIdx >= 0) notifyItemChanged(prevIdx)
            if (newIdx >= 0) notifyItemChanged(newIdx)
        }

    inner class GenreViewHolder(
        val binding: ItemGenreFilterChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(genre: String) {
            binding.tvGenreName.text = genre
            val isSelected = (genre == selectedGenre)
            binding.btnGenreChip.isSelected = isSelected

            binding.btnGenreChip.setOnClickListener {
                if (selectedGenre != genre) {
                    selectedGenre = genre
                    onGenreSelected(genre)
                }
            }

            FocusEngine.attach(binding.btnGenreChip, FocusEngine.FocusKind.BUTTON)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val binding = ItemGenreFilterChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GenreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        holder.bind(genres[position])
    }

    override fun getItemCount(): Int = genres.size
}
