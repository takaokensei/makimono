package zechs.drive.stream.ui.signin

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSignInBinding
import zechs.drive.stream.utils.QRCodeGenerator

@AndroidEntryPoint
class SignInFragment : Fragment(R.layout.fragment_sign_in) {

    private val viewModel: PairingViewModel by viewModels()
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var renderedQrUrl: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSignInBinding.bind(view)
        binding.btnGeneratePairing.setOnClickListener { viewModel.retryWithNewQr() }
        collectState()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startPairingIfNeeded()
    }

    override fun onStop() {
        viewModel.stopPolling()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        renderedQrUrl = null
        super.onDestroyView()
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            PairingEffect.OpenHome ->
                                (requireActivity() as zechs.drive.stream.ui.main.MainActivity).onPairingCompleted()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: PairingUiState) = with(binding) {
        pairingLoading.isVisible = state is PairingUiState.Loading
        when (state) {
            PairingUiState.Idle, PairingUiState.Loading -> {
                ivPairingQrCode.isVisible = false
                tvPairingUrl.isVisible = false
                tvPairingCode.isVisible = false
                tvPairingCodeLabel.isVisible = false
                tvPairingStatus.isVisible = false
                btnGeneratePairing.isVisible = false
            }
            is PairingUiState.Ready -> {
                ivPairingQrCode.isVisible = true
                tvPairingUrl.isVisible = true
                tvPairingCodeLabel.isVisible = true
                tvPairingCode.isVisible = true
                tvPairingStatus.isVisible = true
                btnGeneratePairing.isVisible = true
                if (renderedQrUrl != state.qrUrl) {
                    val bitmap = QRCodeGenerator.generateBitmap(
                        content = state.qrUrl,
                        sizePx = 768,
                        darkColor = Color.BLACK,
                        lightColor = Color.WHITE
                    )
                    if (bitmap == null) {
                        viewModel.onQrRenderFailed()
                        return@with
                    }
                    ivPairingQrCode.setImageBitmap(bitmap)
                    renderedQrUrl = state.qrUrl
                }
                tvPairingUrl.text = Uri.parse(state.qrUrl).let { uri ->
                    uri.host.orEmpty() + uri.path.orEmpty()
                }
                tvPairingCode.text = state.confirmCode.chunked(3).joinToString(" ")
                tvPairingStatus.text = getString(
                    R.string.pairing_expiration_status,
                    getString(state.statusMessageRes),
                    formatRemaining(state.remainingSeconds)
                )
                if (!root.hasFocus()) btnGeneratePairing.post { btnGeneratePairing.requestFocus() }
            }
            is PairingUiState.Error -> {
                ivPairingQrCode.isVisible = false
                tvPairingUrl.isVisible = false
                tvPairingCodeLabel.isVisible = false
                tvPairingCode.isVisible = false
                tvPairingStatus.isVisible = true
                tvPairingStatus.setText(state.messageRes)
                btnGeneratePairing.isVisible = true
                renderedQrUrl = null
                btnGeneratePairing.requestFocus()
            }
        }
    }

    private fun formatRemaining(seconds: Long): String =
        getString(R.string.pairing_countdown, seconds / 60, seconds % 60)
}
