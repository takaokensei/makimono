package zechs.drive.stream.ui.player

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.databinding.DialogPlayerEpisodeDrawerBinding
import zechs.drive.stream.databinding.ItemPlayerDrawerEpisodeBinding
import zechs.drive.stream.databinding.ItemPlayerDrawerSeasonBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.utils.EpisodeParser
import zechs.drive.stream.utils.SeasonEpisodeGrouper
import zechs.drive.stream.utils.SeasonGroup

class PlayerEpisodeDrawerDialog(
    private val activity: Activity,
    private val showTitle: String,
    private val currentPlayingFileId: String?,
    private val playlist: List<PlaylistItem>,
    private val tenraiService: TenraiAnimeService? = null,
    private val onEpisodeSelected: (PlaylistItem) -> Unit
) {

    private var dialog: Dialog? = null
    private var binding: DialogPlayerEpisodeDrawerBinding? = null

    private var seasonGroups: List<SeasonGroup> = emptyList()
    private var activeGroup: SeasonGroup? = null
    private var tenraiArcs: List<TenraiAnimeService.TenraiFranchiseArc> = emptyList()

    fun show() {
        if (playlist.isEmpty()) return

        val inflater = LayoutInflater.from(activity)
        val drawerBinding = DialogPlayerEpisodeDrawerBinding.inflate(inflater)
        binding = drawerBinding

        val d = Dialog(activity, R.style.ThemeOverlay_DriveStream_Dialog)
        dialog = d
        d.setContentView(drawerBinding.root)

        d.window?.apply {
            setLayout((380 * activity.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.END)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
            setWindowAnimations(android.R.style.Animation_Translucent)
        }

        val cleanShow = EpisodeParser.cleanShowTitle(showTitle).ifBlank { "Episódios" }
        drawerBinding.tvDrawerTitle.text = cleanShow
        drawerBinding.btnDrawerClose.setOnClickListener {
            d.dismiss()
        }

        seasonGroups = SeasonEpisodeGrouper.groupPlaylist(cleanShow, playlist)

        // Find which arc contains the currently playing episode
        val currentGroup = seasonGroups.firstOrNull { group ->
            group.id != "season_all" && group.playlistItems.any { it.fileId == currentPlayingFileId }
        }

        setupSeasonsList(drawerBinding, currentGroup)

        // If only 1 group (or 1 season), show episodes directly
        if (seasonGroups.size <= 1) {
            val single = seasonGroups.firstOrNull() ?: SeasonGroup("all", "Episódios", playlistItems = playlist)
            showEpisodesForGroup(single, showBackButton = false)
        } else {
            // Multiple seasons/arcs: show seasons list first
            drawerBinding.btnDrawerBack.visibility = View.GONE
            drawerBinding.tvDrawerSubtitle.text = "Temporadas & Arcos (${seasonGroups.size})"
            drawerBinding.rvDrawerSeasons.visibility = View.VISIBLE
            drawerBinding.rvDrawerEpisodes.visibility = View.GONE
        }

        drawerBinding.btnDrawerBack.setOnClickListener {
            navigateBackToSeasons()
        }

        // Custom back button handler for TV remote
        d.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (binding?.rvDrawerEpisodes?.isVisible == true && seasonGroups.size > 1) {
                        navigateBackToSeasons()
                        return@setOnKeyListener true
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (binding?.rvDrawerEpisodes?.isVisible == true && seasonGroups.size > 1) {
                        navigateBackToSeasons()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        d.show()

        // Fetch official franchise relations and canonical episode titles via Tenrai API asynchronously
        if (tenraiService != null) {
            val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
            lifecycleOwner?.lifecycleScope?.launch {
                try {
                    val arcs = tenraiService.resolveFranchiseArcs(cleanShow)
                    if (arcs.isNotEmpty() && dialog?.isShowing == true) {
                        applyTenraiArcs(arcs)
                    }
                } catch (e: Exception) {
                    android.util.Log.d("PlayerEpisodeDrawer", "Tenrai resolution error: ${e.message}")
                }
            }
        }
    }

    private fun applyTenraiArcs(arcs: List<TenraiAnimeService.TenraiFranchiseArc>) {
        tenraiArcs = arcs
        val updatedGroups = seasonGroups.map { group ->
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
                    val arc = arcs.firstOrNull { it.type.contains("Special", ignoreCase = true) || it.type.contains("OVA", ignoreCase = true) }
                    if (arc != null) group.copy(name = arc.title) else group
                }
                group.id.startsWith("stem_") -> {
                    val matchingArc = arcs.firstOrNull {
                        it.title.contains(group.name, ignoreCase = true) || group.name.contains(it.title, ignoreCase = true)
                    }
                    if (matchingArc != null) group.copy(name = matchingArc.title) else group
                }
                else -> group
            }
        }
        seasonGroups = updatedGroups

        val currentGroup = seasonGroups.firstOrNull { group ->
            group.id != "season_all" && group.playlistItems.any { it.fileId == currentPlayingFileId }
        }

        binding?.let { b ->
            if (b.rvDrawerSeasons.isVisible) {
                b.rvDrawerSeasons.adapter = SeasonsAdapter(seasonGroups, currentGroup) { group ->
                    showEpisodesForGroup(group, showBackButton = true)
                }
            }
            val currentActiveGroup = activeGroup
            if (currentActiveGroup != null && b.rvDrawerEpisodes.isVisible) {
                val updatedActive = seasonGroups.firstOrNull { it.id == currentActiveGroup.id }
                    ?: currentActiveGroup
                showEpisodesForGroup(updatedActive, showBackButton = seasonGroups.size > 1)
            }
        }
    }

    private fun navigateBackToSeasons() {
        binding?.apply {
            btnDrawerBack.visibility = View.GONE
            tvDrawerSubtitle.text = "Temporadas & Arcos (${seasonGroups.size})"
            rvDrawerEpisodes.visibility = View.GONE
            rvDrawerSeasons.visibility = View.VISIBLE
            rvDrawerSeasons.requestFocus()
        }
    }

    private fun setupSeasonsList(
        b: DialogPlayerEpisodeDrawerBinding,
        currentGroup: SeasonGroup?
    ) {
        b.rvDrawerSeasons.layoutManager = LinearLayoutManager(activity)
        b.rvDrawerSeasons.adapter = SeasonsAdapter(seasonGroups, currentGroup) { group ->
            showEpisodesForGroup(group, showBackButton = true)
        }
    }

    private fun showEpisodesForGroup(group: SeasonGroup, showBackButton: Boolean) {
        activeGroup = group
        binding?.apply {
            btnDrawerBack.isVisible = showBackButton
            tvDrawerSubtitle.text = group.name
            rvDrawerSeasons.visibility = View.GONE
            rvDrawerEpisodes.visibility = View.VISIBLE

            // Find canonical episode titles from matching Tenrai arc
            val matchingArc = when {
                group.id == "season_1" -> tenraiArcs.firstOrNull { it.seasonNumber == 1 }
                group.id == "season_2" -> tenraiArcs.firstOrNull { it.seasonNumber == 2 }
                else -> tenraiArcs.firstOrNull { it.title.contains(group.name, ignoreCase = true) }
            }
            val titlesMap = matchingArc?.episodes ?: emptyMap()

            val epsAdapter = EpisodesAdapter(group.playlistItems, currentPlayingFileId, titlesMap) { ep ->
                dialog?.dismiss()
                onEpisodeSelected(ep)
            }
            rvDrawerEpisodes.layoutManager = LinearLayoutManager(activity)
            rvDrawerEpisodes.adapter = epsAdapter

            // Scroll to the playing episode if in this group
            val playingIndex = group.playlistItems.indexOfFirst { it.fileId == currentPlayingFileId }
            if (playingIndex >= 0) {
                rvDrawerEpisodes.scrollToPosition(playingIndex)
            }
            rvDrawerEpisodes.post {
                val targetPos = if (playingIndex >= 0) playingIndex else 0
                val targetHolder = rvDrawerEpisodes.findViewHolderForAdapterPosition(targetPos)
                targetHolder?.itemView?.requestFocus() ?: rvDrawerEpisodes.requestFocus()
            }
        }
    }

    private class SeasonsAdapter(
        private val items: List<SeasonGroup>,
        private val activeGroup: SeasonGroup?,
        private val onSelected: (SeasonGroup) -> Unit
    ) : RecyclerView.Adapter<SeasonsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPlayerDrawerSeasonBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isCurrent = item.id == activeGroup?.id

            holder.binding.tvSeasonName.text = item.name
            holder.binding.tvSeasonEpisodes.text = item.subtitle ?: "${item.count} episódios"

            if (isCurrent) {
                holder.binding.tvSeasonEpisodes.text = "${item.subtitle ?: "${item.count} eps"} • Em reprodução"
                holder.binding.tvSeasonEpisodes.setTextColor(Color.parseColor("#22D3EE"))
            } else {
                holder.binding.tvSeasonEpisodes.setTextColor(Color.parseColor("#94A3B8"))
            }

            holder.binding.cardSeasonItem.setOnClickListener {
                onSelected(item)
            }

            holder.binding.cardSeasonItem.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(6f).setDuration(120L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(val binding: ItemPlayerDrawerSeasonBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private class EpisodesAdapter(
        private val items: List<PlaylistItem>,
        private val currentPlayingFileId: String?,
        private val canonicalTitles: Map<Int, String> = emptyMap(),
        private val onSelected: (PlaylistItem) -> Unit
    ) : RecyclerView.Adapter<EpisodesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPlayerDrawerEpisodeBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val parsed = EpisodeParser.parse(item.title)
            val isPlaying = item.fileId == currentPlayingFileId

            val epBadgeStr = if (parsed.isSpecial && parsed.episodeBadge.isNotBlank()) {
                parsed.episodeBadge
            } else {
                parsed.episode?.let { ep ->
                    if (ep % 1.0 == 0.0) ep.toInt().toString() else ep.toString()
                } ?: (position + 1).toString()
            }
            holder.binding.tvEpNumberBadge.text = epBadgeStr

            val epNum = parsed.episode?.toInt()
            val officialTitle = if (epNum != null && !parsed.isSpecial) canonicalTitles[epNum] else null

            val titleText = when {
                parsed.isSpecial -> parsed.episodeLabel
                !officialTitle.isNullOrBlank() -> "Episódio $epBadgeStr - $officialTitle"
                parsed.episodeTitle != null && parsed.episodeTitle.isNotBlank() -> {
                    "Episódio $epBadgeStr - ${parsed.episodeTitle}"
                }
                else -> "Episódio $epBadgeStr"
            }
            holder.binding.tvEpTitle.text = titleText

            val techTags = EpisodeParser.extractCleanTechnicalTags(item.title)
            if (!techTags.isNullOrBlank()) {
                holder.binding.tvEpSubtitle.visibility = View.VISIBLE
                holder.binding.tvEpSubtitle.text = techTags
            } else {
                holder.binding.tvEpSubtitle.visibility = View.GONE
            }

            if (isPlaying) {
                holder.binding.ivPlayingBadge.visibility = View.VISIBLE
                holder.binding.tvEpTitle.setTextColor(Color.parseColor("#22D3EE"))
                holder.binding.layoutEpItemContainer.setBackgroundResource(R.drawable.nav_item_active_bg)
            } else {
                holder.binding.ivPlayingBadge.visibility = View.GONE
                holder.binding.tvEpTitle.setTextColor(Color.WHITE)
                holder.binding.layoutEpItemContainer.setBackgroundResource(R.drawable.glass_card_bg)
            }

            holder.binding.cardEpItem.setOnClickListener {
                onSelected(item)
            }

            holder.binding.cardEpItem.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(6f).setDuration(120L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                }
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(val binding: ItemPlayerDrawerEpisodeBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
