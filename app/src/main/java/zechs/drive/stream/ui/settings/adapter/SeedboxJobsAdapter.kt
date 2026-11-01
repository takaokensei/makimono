package zechs.drive.stream.ui.settings.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.data.model.SeedboxJobItem
import zechs.drive.stream.databinding.ItemSeedboxJobBinding
import zechs.drive.stream.utils.FocusEngine

class SeedboxJobsAdapter(
    private val onCancelJobClick: (SeedboxJobItem) -> Unit
) : ListAdapter<SeedboxJobItem, SeedboxJobsAdapter.SeedboxJobViewHolder>(JobDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeedboxJobViewHolder {
        val binding = ItemSeedboxJobBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SeedboxJobViewHolder(binding, onCancelJobClick)
    }

    override fun onBindViewHolder(holder: SeedboxJobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SeedboxJobViewHolder(
        private val binding: ItemSeedboxJobBinding,
        private val onCancelJobClick: (SeedboxJobItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            FocusEngine.attach(binding.cardJobRoot, FocusEngine.FocusKind.CARD)
            FocusEngine.attach(binding.btnCancelJob, FocusEngine.FocusKind.BUTTON)
        }

        fun bind(job: SeedboxJobItem) {
            val context = binding.root.context
            binding.tvJobTitle.text = job.animeQuery

            binding.tvJobFilter.text = when (job.episodesFilter.lowercase()) {
                "all", "todos", "*" -> "Temporada"
                "1" -> "Ep. 1"
                else -> "Eps: ${job.episodesFilter}"
            }

            val statusUpper = job.status.uppercase()
            val (badgeTextRes, colorRes) = when (statusUpper) {
                "PENDING" -> Pair(R.string.seedbox_mgmt_status_pending, R.color.obsidian_warning)
                "SEARCHING" -> Pair(R.string.seedbox_mgmt_status_searching, R.color.obsidian_accent_cyan)
                "DOWNLOADING" -> Pair(R.string.seedbox_mgmt_status_downloading, R.color.obsidian_accent_cyan)
                "UPLOADING" -> Pair(R.string.seedbox_mgmt_status_uploading, R.color.obsidian_accent_cyan)
                "COMPLETED" -> Pair(R.string.seedbox_mgmt_status_completed, R.color.obsidian_success)
                "FAILED" -> Pair(R.string.seedbox_mgmt_status_failed, R.color.obsidian_danger)
                "CANCELLED" -> Pair(R.string.seedbox_mgmt_status_cancelled, R.color.obsidian_text_tertiary)
                else -> Pair(R.string.seedbox_mgmt_status_pending, R.color.obsidian_text_secondary)
            }

            binding.tvJobStatusBadge.setText(badgeTextRes)
            binding.tvJobStatusBadge.setTextColor(ContextCompat.getColor(context, colorRes))

            val detailText = when {
                !job.transferSpeed.isNullOrBlank() -> job.transferSpeed
                !job.statusDetail.isNullOrBlank() -> job.statusDetail
                statusUpper == "COMPLETED" -> context.getString(R.string.seedbox_mgmt_status_completed)
                statusUpper == "CANCELLED" -> context.getString(R.string.seedbox_stage_cancelled)
                else -> ""
            }
            binding.tvJobDetail.text = detailText

            val isTransferring = statusUpper == "DOWNLOADING" || statusUpper == "UPLOADING"
            if (isTransferring && job.progressPct > 0) {
                binding.progressJob.isVisible = true
                binding.progressJob.progress = job.progressPct
            } else {
                binding.progressJob.isGone = true
            }

            val isCancellable = statusUpper in listOf("PENDING", "SEARCHING", "DOWNLOADING", "UPLOADING")
            binding.btnCancelJob.isVisible = isCancellable
            binding.ivJobCompleted.isVisible = (statusUpper == "COMPLETED")

            binding.btnCancelJob.setOnClickListener {
                onCancelJobClick(job)
            }
        }
    }

    class JobDiffCallback : DiffUtil.ItemCallback<SeedboxJobItem>() {
        override fun areItemsTheSame(oldItem: SeedboxJobItem, newItem: SeedboxJobItem): Boolean {
            return oldItem.jobId == newItem.jobId
        }

        override fun areContentsTheSame(oldItem: SeedboxJobItem, newItem: SeedboxJobItem): Boolean {
            return oldItem == newItem
        }
    }
}
