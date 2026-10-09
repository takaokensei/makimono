package zechs.drive.stream.ui.files

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.databinding.FragmentFilesBinding
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.player.GlassMenuItem
import zechs.drive.stream.ui.player.PlayerGlassMenuDialog
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.SeasonEpisodeGrouper
import zechs.drive.stream.utils.SeasonGroup
import zechs.drive.stream.utils.TvAccessibilityLayout

/**
 * Gerenciador de busca, filtragem por temporadas e alternância entre grade e lista do FilesFragment.
 * Conforme Seção 4.10 do Plano v2.
 */
class FilesSearchFilterHandler(
    private val context: Context,
    private val binding: FragmentFilesBinding,
    private val filesAdapter: FilesAdapter,
    private val folderName: String,
    private val tenraiService: dagger.Lazy<TenraiAnimeService>,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val isFragmentAdded: () -> Boolean,
    private val isLoadingProvider: () -> Boolean
) {

    companion object {
        private const val TAG = "FilesSearchFilter"
    }

    var isGridMode: Boolean = false
        private set

    var allFilesList: List<FilesDataModel> = emptyList()
        private set

    var availableSeasons: List<SeasonGroup> = emptyList()
        private set

    var selectedSeason: SeasonGroup? = null
        private set

    fun setup() {
        val prefs = context.getSharedPreferences("FILES_PREFS", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean(
            "IS_GRID_MODE",
            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        )
        updateLayoutMode()

        binding.btnToggleGrid.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit().putBoolean("IS_GRID_MODE", isGridMode).apply()
            updateLayoutMode()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                filterFiles(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.etSearch.requestFocus()
            binding.etSearch.setSelection(binding.etSearch.text?.length ?: 0)
        }
    }

    fun onFilesLoaded(files: List<FilesDataModel>) {
        allFilesList = files
        availableSeasons = SeasonEpisodeGrouper.groupFiles(folderName, files)
        if (availableSeasons.size > 1) {
            binding.seasonSelectorRow.isVisible = true
            selectedSeason = availableSeasons.firstOrNull()
            binding.tvSelectedSeason.text = selectedSeason?.name ?: "Temporadas"
            binding.btnSeasonSelector.setOnClickListener {
                showSeasonSelectionDialog()
            }

            lifecycleScope.launch {
                try {
                    val arcs = tenraiService.get().resolveFranchiseArcs(folderName)
                    if (arcs.isNotEmpty() && isFragmentAdded()) {
                        val updated = availableSeasons.map { group ->
                            when {
                                group.id == "season_1" -> {
                                    val arc = arcs.firstOrNull { it.seasonNumber == 1 }
                                    if (arc != null) group.copy(name = "${arc.title} (Temporada 1)") else group
                                }
                                group.id == "season_2" -> {
                                    val arc = arcs.firstOrNull { it.seasonNumber == 2 }
                                    if (arc != null) group.copy(name = "${arc.title} (Temporada 2)") else group
                                }
                                group.id == "season_prologue" -> {
                                    val arc = arcs.firstOrNull {
                                        it.type.contains("Special", ignoreCase = true) || it.type.contains("OVA", ignoreCase = true)
                                    }
                                    if (arc != null) group.copy(name = arc.title) else group
                                }
                                else -> group
                            }
                        }
                        availableSeasons = updated
                        if (selectedSeason != null) {
                            selectedSeason = updated.firstOrNull { it.id == selectedSeason?.id } ?: selectedSeason
                            binding.tvSelectedSeason.text = selectedSeason?.name ?: "Temporadas"
                        }
                    }
                } catch (e: Exception) {
                    AppLog.logSwallowed(TAG, "updating season selector options", e)
                }
            }
        } else {
            binding.seasonSelectorRow.isGone = true
            selectedSeason = null
        }

        val currentQuery = binding.etSearch.text?.toString()?.trim() ?: ""
        filterFiles(currentQuery)
    }

    fun updateLayoutMode() {
        val hasVideoFiles = allFilesList.any {
            it is FilesDataModel.File && (it.driveFile.isVideoFile || it.driveFile.isShortcutVideo)
        }
        val screenWidthDp = context.resources.configuration.screenWidthDp
        val baseSpanCount = if (hasVideoFiles) {
            when {
                screenWidthDp >= 1200 -> 4
                screenWidthDp >= 840 -> 4
                screenWidthDp >= 600 -> 3
                screenWidthDp >= 400 -> 2
                else -> 1
            }
        } else {
            when {
                screenWidthDp >= 1200 -> 6
                screenWidthDp >= 900 -> 5
                screenWidthDp >= 650 -> 4
                screenWidthDp >= 420 -> 3
                else -> 2
            }
        }
        val spanCount = TvAccessibilityLayout.gridColumnsForFontScale(
            baseSpanCount,
            context.resources.configuration.fontScale
        )

        if (isGridMode) {
            binding.btnToggleGrid.setImageResource(R.drawable.ic_list_view_24)
            binding.btnToggleGrid.contentDescription = context.getString(R.string.files_switch_to_list)
            val gridLayoutManager = GridLayoutManager(context, spanCount)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val viewType = filesAdapter.getItemViewType(position)
                    return if (viewType == R.layout.item_loading || viewType == R.layout.item_page_error) {
                        spanCount
                    } else {
                        1
                    }
                }
            }
            binding.rvList.layoutManager = gridLayoutManager
            filesAdapter.isGridMode = true
        } else {
            binding.btnToggleGrid.setImageResource(R.drawable.ic_grid_view_24)
            binding.btnToggleGrid.contentDescription = context.getString(R.string.files_switch_to_grid)
            binding.rvList.layoutManager = LinearLayoutManager(context)
            filesAdapter.isGridMode = false
        }
    }

    fun filterFiles(query: String) {
        val season = selectedSeason
        val baseList = if (season != null && season.id != "season_all" && season.fileItems.isNotEmpty()) {
            season.fileItems
        } else {
            allFilesList
        }

        val listToSubmit = if (query.isEmpty()) {
            baseList.toMutableList()
        } else {
            baseList.filter { item ->
                when (item) {
                    is FilesDataModel.File -> item.driveFile.name.contains(query, ignoreCase = true)
                    else -> true
                }
            }.toMutableList()
        }
        val count = listToSubmit.count { it is FilesDataModel.File }
        binding.containerItemCount.isVisible = count > 0
        binding.tvItemCount.text = if (count == 1) "1 item" else "$count itens"
        if (listToSubmit.isEmpty() && !isLoadingProvider()) {
            binding.rvList.isInvisible = true
            binding.error.root.isGone = true
            binding.stateView.isVisible = true
            when (FilesStatePolicy.emptyMessage(allFilesList.isNotEmpty(), query)) {
                FilesStatePolicy.EmptyFilesMessage.NO_FILES -> {
                    binding.stateView.setState(zechs.drive.stream.ui.common.StateView.StateType.EMPTY_FOLDER)
                    binding.etSearch.nextFocusDownId = binding.rvList.id
                    binding.btnClearSearch.nextFocusDownId = binding.rvList.id
                }
                FilesStatePolicy.EmptyFilesMessage.NO_SEARCH_RESULTS -> {
                    binding.stateView.showState(
                        iconRes = R.drawable.ic_search_24,
                        title = context.getString(R.string.files_search_empty_title),
                        message = context.getString(R.string.files_search_empty_message),
                        actionText = context.getString(R.string.files_search_clear_action),
                        onActionClick = {
                            binding.etSearch.text?.clear()
                            binding.etSearch.requestFocus()
                            binding.etSearch.setSelection(binding.etSearch.text?.length ?: 0)
                        }
                    )
                    binding.etSearch.nextFocusDownId = R.id.btnStateAction
                    binding.btnClearSearch.nextFocusDownId = R.id.btnStateAction
                    binding.stateView.findViewById<View>(R.id.btnStateAction)?.nextFocusUpId = binding.etSearch.id
                }
            }
        } else if (listToSubmit.isNotEmpty()) {
            binding.stateView.isGone = true
            binding.error.root.isGone = true
            binding.rvList.isVisible = true
            binding.etSearch.nextFocusDownId = binding.rvList.id
            binding.btnClearSearch.nextFocusDownId = binding.rvList.id
        }
        filesAdapter.submitList(listToSubmit)
    }

    private fun showSeasonSelectionDialog() {
        if (availableSeasons.isEmpty()) return

        val currentSelectedId = selectedSeason?.id ?: "season_all"
        val items = availableSeasons.map { season ->
            GlassMenuItem(
                id = season.id,
                title = season.name,
                subtitle = season.subtitle,
                isSelected = season.id == currentSelectedId,
                tag = season
            )
        }

        PlayerGlassMenuDialog(
            context = context,
            title = "Temporadas e Arcos",
            items = items
        ) { selected ->
            val season = selected.tag as? SeasonGroup ?: return@PlayerGlassMenuDialog
            selectedSeason = season
            binding.tvSelectedSeason.text = season.name
            filterFiles(binding.etSearch.text?.toString()?.trim() ?: "")
        }.show()
    }
}
