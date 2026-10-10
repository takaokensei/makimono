package zechs.drive.stream.ui.home.controller

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.home.HomeEvent
import zechs.drive.stream.ui.home.preserveFocusedShelfOrder
import zechs.drive.stream.utils.ext.resolveThemeColor
import java.util.Locale

/**
 * Controlador de apresentação do catálogo e busca no cabeçalho (Header Search & Grid/List view toggle).
 * Desacopla ordenação, pesquisa textual/voz e alternância de grade/lista do HomeFragment per Seção 4.10.
 */
class CatalogPresentationController(
    private val context: Context,
    private val binding: FragmentHomeBinding,
    private val viewModel: zechs.drive.stream.ui.home.HomeViewModel,
    private val animeAdapter: FilesAdapter,
    private val railController: RailController,
    private val voiceSearchLauncher: ActivityResultLauncher<Intent>,
    private val reduceMotionProvider: () -> Boolean,
    private val onGridModeChanged: (Boolean) -> Unit
) {

    var isGridMode: Boolean = true
        private set

    var catalogSort: Int = 0
        private set

    fun setupHeaderSearch() {
        val prefs = context.getSharedPreferences("catalog_presentation", Context.MODE_PRIVATE)
        catalogSort = prefs.getInt("sort", 0).coerceIn(0, 2)
        binding.root.findViewById<View>(R.id.btnCatalogSort)?.setOnClickListener { button ->
            MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Ordenar catálogo")
                .setSingleChoiceItems(arrayOf("Ordem da biblioteca", "Nome: A–Z", "Nome: Z–A"), catalogSort) { dialog, choice ->
                    catalogSort = choice
                    prefs.edit().putInt("sort", choice).apply()
                    submitCatalog()
                    binding.rvAnimeLibrary.scrollToPosition(0)
                    dialog.dismiss()
                }
                .setOnDismissListener { button.requestFocus() }
                .show()
        }

        binding.etSearchAnime.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty()
            binding.btnClearSearch.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
            if (query.isNotBlank()) {
                if (railController.previousTabBeforeSearch == null && railController.currentTab != "Animes") {
                    railController.previousTabBeforeSearch = railController.currentTab
                }
                if (railController.currentTab != "Animes") {
                    railController.selectTab("Animes")
                }
            } else {
                railController.previousTabBeforeSearch?.let { prevTab ->
                    railController.previousTabBeforeSearch = null
                    railController.selectTab(prevTab)
                }
            }
            viewModel.onEvent(HomeEvent.Search(query))
        }

        binding.btnClearSearch.setOnClickListener { binding.etSearchAnime.setText("") }
        binding.etSearchAnime.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (binding.etSearchAnime.selectionStart == 0) {
                    if (railController.isCollapsibleRail()) railController.expandSidebar()
                    else railController.focusCurrentNavItem()
                    true
                } else false
            } else false
        }
    }

    fun setupVoiceSearch() {
        binding.btnVoiceSearch.setOnClickListener { launchVoiceSearch() }
        binding.btnVoiceSearch.setOnFocusChangeListener { v, hasFocus ->
            animateFocus(v, hasFocus, scale = 1.15f)
            val activeColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
            val inactiveColor = context.resolveThemeColor(R.attr.colorTextSecondary)
            binding.btnVoiceSearch.imageTintList = ColorStateList.valueOf(
                if (hasFocus) activeColor else inactiveColor
            )
        }
    }

    fun setupViewToggle(onRebindGrid: () -> Unit) {
        val gridBtn = binding.btnToggleGrid
        val listBtn = binding.btnToggleList
        if (gridBtn == null || listBtn == null) return

        gridBtn.setOnClickListener {
            if (!isGridMode) {
                isGridMode = true
                animeAdapter.isGridMode = true
                gridBtn.setBackgroundResource(R.drawable.bg_segmented_active)
                gridBtn.imageTintList = ColorStateList.valueOf(Color.WHITE)
                listBtn.background = null
                listBtn.imageTintList = ColorStateList.valueOf(context.resolveThemeColor(R.attr.colorTextSecondary))
                onGridModeChanged(true)
                onRebindGrid()
            }
        }

        listBtn.setOnClickListener {
            if (isGridMode) {
                isGridMode = false
                animeAdapter.isGridMode = false
                listBtn.setBackgroundResource(R.drawable.bg_segmented_active)
                listBtn.imageTintList = ColorStateList.valueOf(Color.WHITE)
                gridBtn.background = null
                gridBtn.imageTintList = ColorStateList.valueOf(context.resolveThemeColor(R.attr.colorTextSecondary))
                onGridModeChanged(false)
                onRebindGrid()
            }
        }
    }

    fun submitCatalog() {
        val source = viewModel.filteredAnimes.value
        val titles = compareBy<DriveFile> {
            AnimePosterResolver.cleanAnimeTitle(it.name).lowercase(Locale.ROOT)
        }
        val titlesDesc = compareByDescending<DriveFile> {
            AnimePosterResolver.cleanAnimeTitle(it.name).lowercase(Locale.ROOT)
        }
        val sorted = when (catalogSort) {
            1 -> source.sortedWith(titles)
            2 -> source.sortedWith(titlesDesc)
            else -> source
        }
        val focusedItems = (0 until animeAdapter.itemCount).mapNotNull { position ->
            (animeAdapter.currentList.getOrNull(position) as? FilesDataModel.File)?.driveFile
        }
        val ordered = if (catalogSort == 0 && binding.rvAnimeLibrary.hasFocus()) {
            preserveFocusedShelfOrder(focusedItems, sorted)
        } else {
            sorted
        }
        animeAdapter.submitList(ordered.map { FilesDataModel.File(it) })
    }

    fun animateFocus(view: View, hasFocus: Boolean, scale: Float, elevation: Float = 0f) {
        if (reduceMotionProvider()) {
            view.animate().cancel()
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationZ = 0f
            return
        }
        val targetScale = if (hasFocus) scale else 1f
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationZ(if (hasFocus) elevation else 0f)
            .setDuration(view.resources.getInteger(R.integer.motion_focus).toLong())
            .start()
    }

    private fun launchVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o nome do anime...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.voice_search_not_supported), Toast.LENGTH_SHORT).show()
        }
    }
}
