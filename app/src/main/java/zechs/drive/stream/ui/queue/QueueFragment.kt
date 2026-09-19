package zechs.drive.stream.ui.queue

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.databinding.FragmentQueueBinding
import zechs.drive.stream.ui.home.HomeViewModel
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.utils.DeviceUi

@AndroidEntryPoint
class QueueFragment : Fragment() {
    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var queueAdapter: QueueAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeQueue()
        setupFocusChain()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbarTitle.text = "Minha Fila"
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        queueAdapter = QueueAdapter(
            onQueueItemClick = { item -> playQueueItem(item) },
            onQueueItemRemove = { item -> removeFromQueue(item) },
            onQueueItemMove = { fromPosition, toPosition -> moveQueueItem(fromPosition, toPosition) }
        )

        binding.rvQueue.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(requireContext())
            
            if (DeviceUi.isTenFootExperience(requireContext())) {
                // TV optimizations
                isFocusable = true
                isFocusableInTouchMode = true
                descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
            }
        }
    }

    private fun observeQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.watchQueue.collect { queueItems ->
                if (queueItems.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvQueue.visibility = View.GONE
                    binding.tvEmptyMessage.text = "Sua fila está vazia\nAdicione animes para assistir depois"
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvQueue.visibility = View.VISIBLE
                    queueAdapter.submitList(queueItems)
                }
            }
        }
    }

    private fun setupFocusChain() {
        if (!DeviceUi.isTenFootExperience(requireContext())) return

        binding.btnBack.nextFocusRightId = binding.rvQueue.id
        binding.rvQueue.nextFocusUpId = binding.btnBack.id
    }

    private fun playQueueItem(item: WatchQueueItem) {
        val file = DriveFile(
            id = item.fileId,
            name = item.name,
            size = null,
            mimeType = "video/mp4",
            iconLink = null,
            thumbnailLink = item.posterUrl,
            shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
            starred = zechs.drive.stream.data.model.Starred.UNSTARRED
        )
        launchVideoPlayer(file)
    }

    private fun removeFromQueue(item: WatchQueueItem) {
        viewModel.removeFromQueue(item.fileId)
        Toast.makeText(requireContext(), "Removido da fila", Toast.LENGTH_SHORT).show()
    }

    private fun moveQueueItem(fromPosition: Int, toPosition: Int) {
        viewModel.reorderQueue(fromPosition, toPosition)
    }

    private fun launchVideoPlayer(file: DriveFile, startPosition: Long = -1L) {
        val fileId = file.id
        val thumb = file.thumbnailLarge ?: file.posterUrl ?: file.thumbnailLink
        when (mainViewModel.currentPlayerIndex) {
            zechs.drive.stream.utils.VideoPlayer.EXO_PLAYER -> {
                val intent = Intent(requireContext(), zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
                    putExtra("fileId", fileId)
                    putExtra("title", file.name)
                    putExtra("thumbnailLink", thumb)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    if (startPosition > 0L) {
                        putExtra("startPosition", startPosition)
                    }
                }
                startActivity(intent)
            }
            zechs.drive.stream.utils.VideoPlayer.MPV -> {
                Toast.makeText(requireContext(), getString(R.string.starting_mpv), Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), zechs.drive.stream.ui.player2.MPVActivity::class.java).apply {
                    putExtra("fileId", fileId)
                    putExtra("title", file.name)
                    putExtra("thumbnailLink", thumb)
                    if (startPosition > 0L) {
                        putExtra("startPosition", startPosition)
                    }
                }
                startActivity(intent)
            }
        }
    }
}