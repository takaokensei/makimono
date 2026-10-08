package zechs.drive.stream.ui.settings

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.databinding.DialogLibraryRootPickerBinding
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.state.Resource

/**
 * Drive folder browser used to select a library root. The loader is injected so the view can be
 * exercised with local fixtures without creating or changing a real Drive session.
 */
class DriveFolderPickerDialog(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val initialSelectionName: String?,
    private val loadPage: suspend (parentId: String?, pageToken: String?) -> Resource<FilesResponse>,
    private val onSelected: (id: String?, name: String?) -> Unit,
    private val onDismiss: () -> Unit = {}
) {
    private data class PathNode(val id: String?, val name: String)

    private val binding = DialogLibraryRootPickerBinding.inflate(LayoutInflater.from(context))
    private val folderAdapter = FolderAdapter(::openFolder)
    private val path = mutableListOf(PathNode(id = null, name = context.getString(R.string.my_drive)))
    private val folders = mutableListOf<File>()
    private var pageToken: String? = null
    private var loadingJob: Job? = null
    private var dialog: Dialog? = null

    fun show() {
        compactStateViewForLandscape()
        binding.rvFolderPicker.layoutManager = LinearLayoutManager(context)
        binding.rvFolderPicker.adapter = folderAdapter
        binding.btnFolderParent.setOnClickListener {
            if (path.size > 1) openParent() else openMyDrive()
        }
        binding.btnPickerSelect.setOnClickListener {
            val selected = path.last()
            onSelected(selected.id, selected.id?.let { selected.name })
            dialog?.dismiss()
        }
        binding.btnPickerCancel.setOnClickListener { dialog?.dismiss() }
        binding.btnPickerLoadMore.setOnClickListener { loadFolders(append = true) }
        binding.btnPickerSelect.nextFocusUpId = R.id.rvFolderPicker
        binding.btnPickerCancel.nextFocusUpId = R.id.btnPickerSelect
        binding.btnPickerLoadMore.nextFocusUpId = R.id.rvFolderPicker
        binding.btnPickerLoadMore.nextFocusDownId = R.id.btnPickerSelect

        dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setView(binding.root)
            .create()
            .also { picker ->
                picker.setOnShowListener {
                    picker.window?.let { window ->
                        TvFocusRing.install(window.decorView)
                        val density = context.resources.displayMetrics.density
                        val metrics = context.resources.displayMetrics
                        val width = minOf((760 * density).toInt(), metrics.widthPixels - (48 * density).toInt())
                        val height = minOf((680 * density).toInt(), metrics.heightPixels - (48 * density).toInt())
                        window.setLayout(width.coerceAtLeast(1), height.coerceAtLeast(1))
                        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    }
                    updatePathChrome()
                    loadFolders(append = false)
                    binding.btnFolderParent.requestFocus()
                }
                picker.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && path.size > 1) {
                        openParent()
                        true
                    } else false
                }
                picker.setOnDismissListener {
                    loadingJob?.cancel()
                    onDismiss()
                }
                picker.show()
            }
    }

    /** The picker has a compact viewport in landscape; keep empty/error copy visible there. */
    private fun compactStateViewForLandscape() {
        val content = binding.stateFolderPicker.getChildAt(0) as? ViewGroup ?: return
        content.setPadding(0, 0, 0, 0)

        binding.stateFolderPicker.findViewById<ImageView>(R.id.ivStateIcon)?.let { icon ->
            val size = (32 * context.resources.displayMetrics.density).toInt()
            icon.layoutParams = icon.layoutParams.apply {
                width = size
                height = size
            }
        }
        binding.stateFolderPicker.findViewById<TextView>(R.id.tvStateTitle)?.let { title ->
            title.textSize = 20f
            (title.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.topMargin = (8 * context.resources.displayMetrics.density).toInt()
                title.layoutParams = params
            }
        }
        binding.stateFolderPicker.findViewById<TextView>(R.id.tvStateMessage)?.let { message ->
            message.textSize = 16f
            (message.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.topMargin = (4 * context.resources.displayMetrics.density).toInt()
                message.layoutParams = params
            }
        }
    }

    private fun openFolder(folder: File) {
        if (folder.mimeType != FOLDER_MIME_TYPE) return
        path += PathNode(folder.id, folder.name)
        updatePathChrome()
        loadFolders(append = false)
    }

    private fun openParent() {
        if (path.size <= 1) return
        path.removeAt(path.lastIndex)
        updatePathChrome()
        loadFolders(append = false)
    }

    private fun openMyDrive() {
        if (path.size <= 1) return
        path.clear()
        path += PathNode(id = null, name = context.getString(R.string.my_drive))
        updatePathChrome()
        loadFolders(append = false)
    }

    private fun updatePathChrome() {
        val current = path.last()
        binding.tvFolderBreadcrumbs.text = path.joinToString("  ›  ") { it.name }
        binding.tvPickerCurrentSelection.text = if (initialSelectionName.isNullOrBlank()) {
            context.getString(R.string.library_root_unconfigured)
        } else {
            context.getString(R.string.library_root_saved_selection, initialSelectionName)
        }
        binding.btnFolderParent.isVisible = true
        binding.btnFolderParent.text = if (path.size > 1) {
            context.getString(R.string.library_root_go_parent_short)
        } else {
            context.getString(R.string.my_drive)
        }
        binding.btnFolderParent.contentDescription = if (path.size > 1) {
            context.getString(R.string.library_root_go_parent)
        } else {
            context.getString(R.string.library_root_my_drive)
        }
        binding.btnFolderParent.nextFocusDownId = R.id.rvFolderPicker
        binding.btnPickerSelect.text = context.getString(R.string.library_root_select_current)
        binding.btnPickerSelect.contentDescription = context.getString(
            R.string.library_root_select_named,
            current.name
        )
    }

    private fun loadFolders(append: Boolean) {
        loadingJob?.cancel()
        if (!append) {
            folders.clear()
            pageToken = null
            folderAdapter.submitList(emptyList())
        }
        val requestToken = if (append) pageToken else null
        val parentId = path.last().id
        binding.stateFolderPicker.isGone = true
        binding.btnPickerLoadMore.isGone = true
        binding.progressFolderPicker.isVisible = true
        if (!append) binding.rvFolderPicker.isGone = true

        loadingJob = lifecycleScope.launch {
            when (val response = loadPage(parentId, requestToken)) {
                is Resource.Success -> {
                    val pageFolders = response.data.files
                        .filter { it.mimeType == FOLDER_MIME_TYPE }
                        .distinctBy(File::id)
                    val existingIds = folders.asSequence().map(File::id).toHashSet()
                    folders += pageFolders.filterNot { it.id in existingIds }
                    pageToken = response.data.nextPageToken?.takeIf(String::isNotBlank)
                    folderAdapter.submitList(folders.toList())
                    folderAdapter.hasMore = pageToken != null
                    binding.progressFolderPicker.isGone = true
                    binding.btnPickerLoadMore.isVisible = pageToken != null
                    binding.btnPickerLoadMore.text = context.getString(R.string.library_root_load_more)
                    if (folders.isEmpty()) {
                        binding.rvFolderPicker.isGone = true
                        binding.stateFolderPicker.isVisible = true
                        binding.stateFolderPicker.showState(
                            iconRes = R.drawable.ic_folder_24,
                            title = context.getString(R.string.library_root_empty_title),
                            message = context.getString(R.string.library_root_empty_message)
                        )
                        binding.btnPickerSelect.requestFocus()
                    } else {
                        binding.stateFolderPicker.isGone = true
                        binding.rvFolderPicker.isVisible = true
                        focusFirstFolderWhenReady()
                    }
                }
                is Resource.Error -> {
                    binding.progressFolderPicker.isGone = true
                    if (folders.isEmpty()) {
                        binding.rvFolderPicker.isGone = true
                        binding.stateFolderPicker.isVisible = true
                        binding.stateFolderPicker.showState(
                            iconRes = R.drawable.ic_error_72,
                            title = context.getString(R.string.library_root_error_title),
                            message = zechs.drive.stream.ui.common.StateView.sanitizeErrorMessage(response.message),
                            actionText = context.getString(R.string.library_root_retry),
                            onActionClick = { loadFolders(append = false) }
                        )
                    } else {
                        binding.rvFolderPicker.isVisible = true
                        binding.btnPickerLoadMore.isVisible = true
                        binding.btnPickerLoadMore.text = context.getString(R.string.library_root_retry)
                    }
                }
                is Resource.Loading -> {
                    binding.progressFolderPicker.isVisible = true
                }
            }
        }
    }

    private fun focusFirstFolderWhenReady() {
        binding.rvFolderPicker.post {
            val first = binding.rvFolderPicker.findViewHolderForAdapterPosition(0)?.itemView
            if (first != null) {
                first.requestFocus()
            } else {
                binding.rvFolderPicker.post {
                    binding.rvFolderPicker.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
    }

    private class FolderAdapter(
        private val onFolderClick: (File) -> Unit
    ) : RecyclerView.Adapter<FolderViewHolder>() {
        private var items: List<File> = emptyList()
        var hasMore: Boolean = false

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FolderViewHolder {
            val item = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_library_folder_picker, parent, false) as com.google.android.material.button.MaterialButton
            return FolderViewHolder(item, onFolderClick)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            holder.bind(items[position])
            holder.itemView.nextFocusDownId = if (position == items.lastIndex) {
                if (hasMore) R.id.btnPickerLoadMore else R.id.btnPickerSelect
            } else View.NO_ID
            holder.itemView.nextFocusUpId = if (position == 0) R.id.btnFolderParent else View.NO_ID
        }
        override fun getItemCount(): Int = items.size

        fun submitList(next: List<File>) {
            items = next
            notifyDataSetChanged()
        }
    }

    private class FolderViewHolder(
        private val button: com.google.android.material.button.MaterialButton,
        private val onFolderClick: (File) -> Unit
    ) : RecyclerView.ViewHolder(button) {
        private var folder: File? = null

        init {
            button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder_24, 0, R.drawable.ic_arrow_forward_24, 0)
            button.compoundDrawablePadding = (12 * button.resources.displayMetrics.density).toInt()
            button.setOnClickListener { folder?.let(onFolderClick) }
            button.isFocusable = true
            button.isClickable = true
        }

        fun bind(item: File) {
            folder = item
            button.text = item.name
            button.contentDescription = item.name
        }
    }

    companion object {
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}
