package zechs.drive.stream.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.ui.home.adapter.StarredShelfAdapter
import zechs.drive.stream.utils.ext.navigateSafe
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    companion object {
        const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel by activityViewModels<HomeViewModel>()
    private val mainViewModel by activityViewModels<zechs.drive.stream.ui.main.MainViewModel>()

    private val starredShelfAdapter = StarredShelfAdapter { driveFile ->
        onStarredItemClicked(driveFile)
    }

    private val continueWatchingAdapter = ContinueWatchingAdapter { watchItem ->
        playWatchItem(watchItem)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
            inflater, container, /* attachToParent */false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        binding.apply {

            // My drive
            navigateToFiles(
                view = btnMyDrive,
                name = getString(R.string.my_drive),
                query = "'root' in parents and trashed = false"
            )

            // Shared drives
            navigateToFiles(
                view = btnSharedDrives,
                name = getString(R.string.shared_drives),
                query = null
            )

            // Shared with me
            navigateToFiles(
                view = btnSharedWithMe,
                name = getString(R.string.shared_with_me),
                query = "sharedWithMe=true"
            )

            // Trashed
            navigateToFiles(
                view = btnTrash,
                name = getString(R.string.trashed),
                query = "'root' in parents and trashed=true"
            )

            btnSettings.setOnClickListener {
                findNavController().navigateSafe(R.id.action_homeFragment_to_settingsFragment)
            }

            setupRailFocus(
                btnMyDrive,
                btnSharedDrives,
                btnSharedWithMe,
                btnSettings,
                btnTrash
            )

            btnMyDrive.post {
                btnMyDrive.requestFocus()
            }

        }

        binding.rvStarredShelf.adapter = starredShelfAdapter
        binding.rvContinueWatchingShelf.adapter = continueWatchingAdapter

        setupToolbar()
        observeLogOutState()
        observeMpv()
        observeStarredFiles()
        observeRecentWatches()
    }

    private fun setupRailFocus(vararg views: View) {
        views.forEach { view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(8f).setDuration(120L).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120L).start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fetch both starred items and continue watching
        viewModel.getStarredFiles()
        viewModel.getRecentWatches()
        binding.root.post {
            if (activity?.currentFocus == null) {
                binding.btnMyDrive.requestFocus()
            }
        }
    }

    private fun observeMpv() {
        viewModel.mpvFile.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { res ->
                when (res) {
                    is zechs.drive.stream.utils.state.Resource.Success -> {
                        val fileToken = res.data!!
                        val intent = android.content.Intent(requireContext(), zechs.drive.stream.ui.player2.MPVActivity::class.java).apply {
                            putExtra("fileId", fileToken.fileId)
                            putExtra("title", fileToken.fileName)
                            putExtra("accessToken", fileToken.accessToken)
                            putExtra("thumbnailLink", fileToken.thumbnailLink)
                            putExtra("theme", mainViewModel.currentThemeIndex)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                    is zechs.drive.stream.utils.state.Resource.Error -> {
                        android.widget.Toast.makeText(requireContext(), res.message ?: "Erro ao obter token", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Shared by both the single "continue watching" hero card and each item
     * in the [rvContinueWatchingShelf] shelf, so the resume/launch behavior
     * (ExoPlayer vs MPV routing) only lives in one place.
     */
    private fun playWatchItem(watchItem: WatchList) {
        when (mainViewModel.currentPlayerIndex) {
            zechs.drive.stream.utils.VideoPlayer.EXO_PLAYER -> {
                val intent = android.content.Intent(requireContext(), zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
                    putExtra("fileId", watchItem.videoId)
                    putExtra("title", watchItem.name)
                    putExtra("thumbnailLink", watchItem.thumbnailLink)
                    putExtra("theme", mainViewModel.currentThemeIndex)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            zechs.drive.stream.utils.VideoPlayer.MPV -> {
                android.widget.Toast.makeText(requireContext(), "Iniciando MPV Player...", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(watchItem.videoId, watchItem.name, watchItem.thumbnailLink)
            }
        }
    }

    private fun observeStarredFiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.starredFiles.collect { items ->
                    val hasItems = items.isNotEmpty()
                    binding.tvStarredLabel.visibility = if (hasItems) View.VISIBLE else View.GONE
                    binding.rvStarredShelf.visibility = if (hasItems) View.VISIBLE else View.GONE
                    starredShelfAdapter.submitList(items)
                }
            }
        }
    }

    private fun onStarredItemClicked(file: DriveFile) {
        val isFolder = file.isFolder || file.isShortcutFolder
        if (isFolder) {
            val folderId = if (file.isShortcut) file.shortcutDetails.targetId!! else file.id
            viewModel.recordFolderOpened(folderId, file.name)
            val action = HomeFragmentDirections.actionHomeFragmentToFilesFragment(
                name = file.name,
                query = "'$folderId' in parents and trashed = false"
            )
            findNavController().navigateSafe(action)
        } else if (file.isVideoFile || file.isShortcutVideo) {
            val fileId = if (file.isShortcut) file.shortcutDetails.targetId!! else file.id
            when (mainViewModel.currentPlayerIndex) {
                zechs.drive.stream.utils.VideoPlayer.EXO_PLAYER -> {
                    val intent = android.content.Intent(requireContext(), zechs.drive.stream.ui.player.PlayerActivity::class.java).apply {
                        putExtra("fileId", fileId)
                        putExtra("title", file.name)
                        putExtra("thumbnailLink", file.thumbnailLarge)
                        putExtra("theme", mainViewModel.currentThemeIndex)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
                zechs.drive.stream.utils.VideoPlayer.MPV -> {
                    android.widget.Toast.makeText(requireContext(), "Iniciando MPV Player...", android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.fetchToken(fileId, file.name, file.thumbnailLarge)
                }
            }
        } else {
            android.widget.Toast.makeText(requireContext(), file.name, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeRecentWatches() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentWatches.collect { items ->
                    val hasItems = items.isNotEmpty()
                    binding.tvShelfLabel.visibility = if (hasItems) View.VISIBLE else View.GONE
                    binding.rvContinueWatchingShelf.visibility = if (hasItems) View.VISIBLE else View.GONE
                    continueWatchingAdapter.submitList(items)
                }
            }
        }
    }

    private fun navigateToFiles(
        view: View, name: String, query: String?
    ) {
        view.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToFilesFragment(
                name = name,
                query = query
            )
            findNavController().navigateSafe(action)
            Log.d(TAG, "navigateToFiles(name=$name, query=$query)")
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logOut -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.log_out_dialog_title))
                        .setNegativeButton(getString(R.string.no)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                            dialog.dismiss()
                            Log.d(TAG, "Logging out...")
                            viewModel.logOut()
                        }
                        .show()
                    return@setOnMenuItemClickListener true
                }

                else -> {
                    return@setOnMenuItemClickListener false
                }
            }
        }
    }

    private fun observeLogOutState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasLoggedOut.collect {
                    if (it) {
                        // restart activity
                        requireActivity().finish()
                        delay(250L)
                        requireActivity().startActivity(requireActivity().intent)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}