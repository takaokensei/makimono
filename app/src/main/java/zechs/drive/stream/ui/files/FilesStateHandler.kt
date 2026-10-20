package zechs.drive.stream.ui.files

import android.animation.ValueAnimator
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialFadeThrough
import zechs.drive.stream.databinding.FragmentFilesBinding
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.ui.common.StateView
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.utils.ShimmerController

/**
 * Gerenciador de estados de tela (Loading, Empty, Content e Error) do FilesFragment.
 * Conforme Seção 4.10 do Plano v2.
 */
class FilesStateHandler(
    private val binding: FragmentFilesBinding,
    private val viewModel: FilesViewModel,
    private val onContentReady: (List<FilesDataModel>) -> Unit,
    private val onReauthenticate: () -> Unit
) {

    private var shimmerAnimator: ValueAnimator? = null

    fun setLoading(show: Boolean) {
        binding.loading.isInvisible = !show
        binding.rvList.isInvisible = show
        if (show) {
            binding.filesSkeleton.root.isVisible = true
            shimmerAnimator?.cancel()
            shimmerAnimator = ShimmerController.start(binding.filesSkeleton.root)
        } else {
            binding.filesSkeleton.root.isGone = true
            shimmerAnimator?.cancel()
            shimmerAnimator = null
        }
    }

    fun render(state: FilesUiState) {
        when (state) {
            is FilesUiState.Loading -> {
                if (!viewModel.hasLoaded) {
                    setLoading(true)
                }
                binding.error.root.isGone = true
                binding.stateView.isGone = true
            }
            is FilesUiState.Empty -> {
                setLoading(false)
                viewModel.hasLoaded = true
                binding.error.root.isGone = true
                binding.stateView.isVisible = true
                binding.stateView.setState(StateView.StateType.EMPTY_FOLDER)
            }
            is FilesUiState.Content -> {
                binding.stateView.isGone = true
                binding.error.root.isGone = true
                if (!viewModel.hasLoaded) {
                    (binding.root as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it, MaterialFadeThrough()) }
                }
                setLoading(false)
                viewModel.hasLoaded = true
                onContentReady(state.files)
                viewModel.lastFocusedPosition?.let { pos ->
                    binding.rvList.post {
                        binding.rvList.scrollToPosition(pos)
                    }
                }
            }
            is FilesUiState.Error -> {
                if (state.partialFiles.any { it is FilesDataModel.File }) {
                    binding.stateView.isGone = true
                    setLoading(false)
                    viewModel.hasLoaded = true
                    onContentReady(state.partialFiles)
                } else {
                    setLoading(false)
                    binding.error.root.isGone = true
                    binding.stateView.isVisible = true
                    val stateType = when (state.error) {
                        is AppError.Network -> StateView.StateType.ERROR_NETWORK
                        is AppError.Authentication -> StateView.StateType.ERROR_AUTH
                        is AppError.NotFound -> StateView.StateType.ERROR_NOT_FOUND
                        else -> StateView.StateType.ERROR_GENERIC
                    }
                    binding.stateView.setState(stateType) {
                        if (state.error is AppError.Authentication) {
                            onReauthenticate()
                        } else {
                            viewModel.onEvent(FilesEvent.Retry)
                        }
                    }
                }
            }
        }
    }

    fun destroy() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
    }
}
