package zechs.drive.stream.ui.downloads

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DownloadItem
import zechs.drive.stream.data.model.DownloadStatus
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.databinding.FragmentDownloadsBinding
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.FocusEngine

@AndroidEntryPoint
class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadsViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var downloadsAdapter: DownloadsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupSmartDownloads()
        setupRecyclerView()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnDeleteAllDownloads.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.download_delete_all)
                .setMessage(R.string.download_delete_all_confirm)
                .setPositiveButton(R.string.download_delete) { _, _ ->
                    viewModel.deleteAll()
                    Toast.makeText(requireContext(), R.string.download_deleted_toast, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.exit_dialog_cancel, null)
                .show()
        }
    }

    private fun setupSmartDownloads() {
        binding.cardSmartDownloads.setOnClickListener {
            val nextState = !binding.switchSmartDownloads.isChecked
            viewModel.setSmartDownloadsEnabled(nextState)
            val msgRes = if (nextState) R.string.smart_downloads_enabled_toast else R.string.smart_downloads_disabled_toast
            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
        }

        FocusEngine.attach(
            binding.cardSmartDownloads,
            FocusEngine.FocusKind.CARD
        ) { _, _ -> }
    }

    private fun setupRecyclerView() {
        downloadsAdapter = DownloadsAdapter(
            onItemClick = { item -> playDownloadedItem(item) },
            onDeleteClick = { item -> confirmDelete(item) }
        )

        binding.rvDownloads.apply {
            adapter = downloadsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            if (DeviceUi.isTenFootExperience(requireContext())) {
                isFocusable = true
                isFocusableInTouchMode = true
                descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.downloads.collect { list ->
                        downloadsAdapter.submitList(list)
                        val isEmpty = list.isEmpty()
                        binding.layoutEmptyDownloads.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        binding.btnDeleteAllDownloads.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.isSmartDownloadsEnabled.collect { enabled ->
                        binding.switchSmartDownloads.isChecked = enabled
                    }
                }

                launch {
                    viewModel.storageUsage.collect { usage ->
                        val context = requireContext()
                        val makimonoStr = Formatter.formatFileSize(context, usage.makimonoBytes)
                        val freeStr = Formatter.formatFileSize(context, usage.freeBytes)

                        binding.tvStorageUsageMakimono.text = getString(R.string.downloads_storage_used, makimonoStr)
                        binding.tvStorageUsageFree.text = getString(R.string.downloads_storage_free, freeStr)
                        binding.pbStorageUsage.progress = usage.makimonoPercent
                    }
                }
            }
        }
    }

    private fun confirmDelete(item: DownloadItem) {
        val isDownloadingOrQueued = item.downloadStatus == DownloadStatus.DOWNLOADING ||
            item.downloadStatus == DownloadStatus.QUEUED
        val titleRes = if (isDownloadingOrQueued) R.string.download_cancel else R.string.download_delete
        val messageRes = if (isDownloadingOrQueued) R.string.download_cancel_confirm else R.string.download_delete_confirm
        val confirmRes = if (isDownloadingOrQueued) R.string.download_cancel else R.string.download_delete
        val toastRes = if (isDownloadingOrQueued) R.string.download_canceled_toast else R.string.download_deleted_toast

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(confirmRes) { _, _ ->
                viewModel.deleteDownload(item.fileId)
                Toast.makeText(requireContext(), toastRes, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.exit_dialog_cancel, null)
            .show()
    }

    private fun playDownloadedItem(item: DownloadItem) {
        if (!item.isComplete) {
            Toast.makeText(
                requireContext(),
                getString(R.string.download_in_progress_toast, item.progressPercent),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val playlist = arrayListOf(
            PlaylistItem(
                fileId = item.fileId,
                title = item.name,
                thumbnailLink = item.thumbnailUrl,
                localUri = "file://${item.localPath}"
            )
        )

        PlayerLauncher.launch(
            context = requireContext(),
            playerType = mainViewModel.currentPlayerIndex,
            fileId = item.fileId,
            title = item.name,
            seriesTitle = item.seriesTitle,
            thumbnailLink = item.thumbnailUrl,
            themeIndex = mainViewModel.currentThemeIndex,
            playlist = playlist,
            startPosition = 0L,
            localUri = "file://${item.localPath}"
        )
    }
}
