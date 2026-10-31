package zechs.drive.stream.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.databinding.DialogPlayerKodiPanelBinding
import zechs.drive.stream.databinding.ItemPlayerGlassMenuBinding
import zechs.drive.stream.utils.TvFocusRing

class KodiMediaPanelController(
    private val context: Context
) {

    enum class PanelMode {
        SUBTITLES,
        AUDIO
    }

    private var dialog: AlertDialog? = null
    private var binding: DialogPlayerKodiPanelBinding? = null

    fun show(
        mode: PanelMode,
        title: String,
        subtitle: String,
        items: List<GlassMenuItem>,
        initialOffsetMs: Long,
        isNightModeActive: Boolean = false,
        onItemSelected: (GlassMenuItem) -> Unit,
        onOffsetChanged: (Long) -> Unit,
        onSizeSelected: ((Float) -> Unit)? = null,
        onColorSelected: ((Int) -> Unit)? = null,
        onNightModeToggled: (() -> Boolean)? = null,
        onBoostSelected: ((Int) -> Unit)? = null,
        onOnlineSearchClicked: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val inflater = LayoutInflater.from(context)
        val b = DialogPlayerKodiPanelBinding.inflate(inflater)
        binding = b

        b.tvPanelTitle.text = title
        b.tvPanelSubtitle.text = subtitle

        b.btnPanelClose.setOnClickListener {
            dialog?.dismiss()
        }

        var currentOffset = initialOffsetMs
        fun updateOffsetUi(offset: Long) {
            val sign = if (offset > 0) "+" else ""
            b.tvSyncOffset.text = "$sign$offset ms"
        }
        updateOffsetUi(currentOffset)

        // Setup Stepper Buttons
        b.btnSyncReset.setOnClickListener {
            currentOffset = 0L
            updateOffsetUi(currentOffset)
            onOffsetChanged(0L)
        }

        val fineStep = if (mode == PanelMode.AUDIO) 50L else 100L
        val coarseStep = if (mode == PanelMode.AUDIO) 250L else 500L

        b.btnSyncMinusFine.text = "- $fineStep ms"
        b.btnSyncPlusFine.text = "+ $fineStep ms"
        b.btnSyncMinusCoarse.text = "- $coarseStep ms"
        b.btnSyncPlusCoarse.text = "+ $coarseStep ms"

        b.btnSyncMinusFine.setOnClickListener {
            currentOffset -= fineStep
            updateOffsetUi(currentOffset)
            onOffsetChanged(currentOffset)
        }
        b.btnSyncPlusFine.setOnClickListener {
            currentOffset += fineStep
            updateOffsetUi(currentOffset)
            onOffsetChanged(currentOffset)
        }
        b.btnSyncMinusCoarse.setOnClickListener {
            currentOffset -= coarseStep
            updateOffsetUi(currentOffset)
            onOffsetChanged(currentOffset)
        }
        b.btnSyncPlusCoarse.setOnClickListener {
            currentOffset += coarseStep
            updateOffsetUi(currentOffset)
            onOffsetChanged(currentOffset)
        }

        // Setup Tracks Adapter
        val trackAdapter = KodiTrackAdapter(items.toMutableList()) { selected ->
            onItemSelected(selected)
            dialog?.dismiss()
        }
        b.rvPanelTracks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = trackAdapter
        }

        // Setup Tabs Navigation
        fun selectTab(activeTab: View, activePage: View) {
            listOf(b.tabTracks, b.tabSync, b.tabSettings, b.tabOnline).forEach {
                it.isSelected = (it == activeTab)
                it.setTextColor(
                    if (it == activeTab) ContextCompat.getColor(context, R.color.obsidian_text_primary)
                    else ContextCompat.getColor(context, R.color.obsidian_text_secondary)
                )
            }
            listOf(
                b.pageTracks,
                b.pageSync,
                b.pageSubtitleSettings,
                b.pageAudioSettings,
                b.pageOnlineSubtitles
            ).forEach { it.visibility = View.GONE }
            activePage.visibility = View.VISIBLE
        }

        if (mode == PanelMode.SUBTITLES) {
            b.tabSettings.text = context.getString(R.string.kodi_tab_appearance)
            b.tabOnline.visibility = if (onOnlineSearchClicked != null) View.VISIBLE else View.GONE

            b.tabTracks.setOnClickListener { selectTab(b.tabTracks, b.pageTracks) }
            b.tabSync.setOnClickListener { selectTab(b.tabSync, b.pageSync) }
            b.tabSettings.setOnClickListener { selectTab(b.tabSettings, b.pageSubtitleSettings) }
            b.tabOnline.setOnClickListener {
                selectTab(b.tabOnline, b.pageOnlineSubtitles)
                onOnlineSearchClicked?.invoke()
            }

            // Subtitle size actions
            b.btnSize16.setOnClickListener { onSizeSelected?.invoke(16f); dialog?.dismiss() }
            b.btnSize20.setOnClickListener { onSizeSelected?.invoke(20f); dialog?.dismiss() }
            b.btnSize24.setOnClickListener { onSizeSelected?.invoke(24f); dialog?.dismiss() }
            b.btnSize28.setOnClickListener { onSizeSelected?.invoke(28f); dialog?.dismiss() }

            // Subtitle color actions
            b.btnColorWhite.setOnClickListener { onColorSelected?.invoke(ContextCompat.getColor(context, R.color.kodi_subtitle_white)); dialog?.dismiss() }
            b.btnColorYellow.setOnClickListener { onColorSelected?.invoke(ContextCompat.getColor(context, R.color.kodi_subtitle_yellow)); dialog?.dismiss() }
            b.btnColorCyan.setOnClickListener { onColorSelected?.invoke(ContextCompat.getColor(context, R.color.kodi_subtitle_cyan)); dialog?.dismiss() }

        } else {
            b.tabSettings.text = context.getString(R.string.kodi_tab_processing)
            b.tabOnline.visibility = View.GONE

            b.tabTracks.setOnClickListener { selectTab(b.tabTracks, b.pageTracks) }
            b.tabSync.setOnClickListener { selectTab(b.tabSync, b.pageSync) }
            b.tabSettings.setOnClickListener { selectTab(b.tabSettings, b.pageAudioSettings) }

            // Audio Night Mode
            var nightMode = isNightModeActive
            fun updateNightModeBtn() {
                b.btnToggleNightMode.text = if (nightMode) {
                    context.getString(R.string.kodi_night_mode_enabled)
                } else {
                    context.getString(R.string.kodi_night_mode_disabled)
                }
                b.btnToggleNightMode.isSelected = nightMode
            }
            updateNightModeBtn()

            b.btnToggleNightMode.setOnClickListener {
                val newActive = onNightModeToggled?.invoke() ?: !nightMode
                nightMode = newActive
                updateNightModeBtn()
            }

            // Audio Boost
            b.btnBoost0.setOnClickListener { onBoostSelected?.invoke(0); dialog?.dismiss() }
            b.btnBoost4.setOnClickListener { onBoostSelected?.invoke(4); dialog?.dismiss() }
            b.btnBoost8.setOnClickListener { onBoostSelected?.invoke(8); dialog?.dismiss() }
            b.btnBoost12.setOnClickListener { onBoostSelected?.invoke(12); dialog?.dismiss() }
        }

        // Start on Tracks tab
        selectTab(b.tabTracks, b.pageTracks)

        val builder = AlertDialog.Builder(context, R.style.ThemeOverlay_Makimono_Glass)
            .setView(b.root)
        val d = builder.create()
        dialog = d

        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.65f)
        }

        TvFocusRing.install(b.root)
        d.setOnDismissListener {
            (context as? PlayerModalHost)?.onPlayerModalVisibilityChanged(false)
            onDismiss?.invoke()
        }
        (context as? PlayerModalHost)?.onPlayerModalVisibilityChanged(true)
        d.show()

        d.window?.apply {
            val width = minOf(
                (660 * context.resources.displayMetrics.density).toInt(),
                (context.resources.displayMetrics.widthPixels * 0.94f).toInt()
            )
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            b.cardKodiPanelRoot.setCardBackgroundColor(Color.TRANSPARENT)
            zechs.drive.stream.utils.FrostedWindow.apply(this)
        }

        b.rvPanelTracks.post {
            val selectedIdx = items.indexOfFirst { it.isSelected }
            if (selectedIdx >= 0) {
                b.rvPanelTracks.scrollToPosition(selectedIdx)
            }
        }
    }

    private class KodiTrackAdapter(
        private val items: MutableList<GlassMenuItem>,
        private val onClick: (GlassMenuItem) -> Unit
    ) : RecyclerView.Adapter<KodiTrackAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemPlayerGlassMenuBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPlayerGlassMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val b = holder.binding

            b.tvItemTitle.text = item.title
            if (!item.subtitle.isNullOrBlank()) {
                b.tvItemSubtitle.text = item.subtitle
                b.tvItemSubtitle.visibility = View.VISIBLE
            } else {
                b.tvItemSubtitle.visibility = View.GONE
            }

            val ctx = b.root.context
            if (item.isSelected) {
                b.ivItemCheck.setImageResource(R.drawable.ic_radio_checked)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.obsidian_accent))
                b.tvItemTitle.setTextColor(ContextCompat.getColor(ctx, R.color.obsidian_text_primary))
            } else {
                b.ivItemCheck.setImageResource(R.drawable.ic_radio_unchecked)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.obsidian_text_tertiary))
                b.tvItemTitle.setTextColor(ContextCompat.getColor(ctx, R.color.obsidian_text_secondary))
            }

            b.itemRoot.isClickable = item.isInteractive
            b.itemRoot.isFocusable = item.isInteractive
            b.itemRoot.setOnClickListener {
                if (item.isInteractive) onClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
