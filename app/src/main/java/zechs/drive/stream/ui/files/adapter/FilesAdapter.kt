package zechs.drive.stream.ui.files.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.databinding.ItemDriveFileBinding
import zechs.drive.stream.databinding.ItemDriveFileGridBinding
import zechs.drive.stream.databinding.ItemLoadingBinding

class FilesAdapter(
    val onClickListener: (DriveFile) -> Unit,
    val onLongClickListener: (DriveFile) -> Unit,
    val onStarClickListener: (DriveFile, Boolean) -> Unit,
    val onPlayClickListener: ((DriveFile) -> Unit)? = null
) : ListAdapter<FilesDataModel, FilesViewHolder>(FilesItemDiffCallback()) {

    var isGridMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    var onDpadLeftListener: ((android.view.View) -> Boolean)? = null
    var onFocusItemListener: ((android.view.View) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): FilesViewHolder {
        return when (viewType) {
            R.layout.item_loading -> FilesViewHolder.LoadingViewHolder(
                ItemLoadingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent, false
                )
            )
            R.layout.item_drive_file_grid -> FilesViewHolder.DriveFileGridViewHolder(
                ItemDriveFileGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent, false
                ),
                filesAdapter = this
            )
            R.layout.item_drive_file -> FilesViewHolder.DriveFileViewHolder(
                ItemDriveFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent, false
                ),
                filesAdapter = this
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: FilesViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is FilesViewHolder.LoadingViewHolder -> item as FilesDataModel.Loading
            is FilesViewHolder.DriveFileViewHolder -> holder.bind(item as FilesDataModel.File)
            is FilesViewHolder.DriveFileGridViewHolder -> holder.bind(item as FilesDataModel.File)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FilesDataModel.Loading -> R.layout.item_loading
            is FilesDataModel.File -> if (isGridMode) R.layout.item_drive_file_grid else R.layout.item_drive_file
        }
    }
}