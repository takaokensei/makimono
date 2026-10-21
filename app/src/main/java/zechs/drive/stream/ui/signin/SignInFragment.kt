package zechs.drive.stream.ui.signin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.QRCodeGenerator

@AndroidEntryPoint
class SignInFragment : Fragment(R.layout.fragment_sign_in) {

    companion object {
        private const val TAG = "SignInFragment"
    }

    private val viewModel: PairingViewModel by viewModels()
    private var _binding: FragmentSignInBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var renderedQrUrl: String? = null
    private var isQrExpandedOnMobile = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSignInBinding.bind(view)
        setupActions()
        collectState()
    }

    private fun setupActions() = with(binding) {
        btnGeneratePairing.setOnClickListener { viewModel.retryWithNewQr() }

        btnOpenBrowser.setOnClickListener {
            val state = viewModel.uiState.value as? PairingUiState.Ready ?: return@setOnClickListener
            val isTenFoot = DeviceUi.isTenFootExperience(requireContext())
            val presentation = SignInPresentationPolicy.resolve(
                isTenFoot = isTenFoot,
                qrUrl = state.qrUrl,
                confirmCode = state.confirmCode,
                isQrExpandedOnMobile = isQrExpandedOnMobile
            )
            val cleanCode = state.confirmCode.filter { it.isDigit() }
            copyToClipboard(getString(R.string.pairing_code_label), cleanCode)
            val toastMessage = getString(R.string.pairing_mobile_code_copied, presentation.formattedConfirmCode)
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show()

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(presentation.directAuthUrl))
                startActivity(intent)
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to launch browser for direct authentication", e)
            }
        }

        btnCopyCode.setOnClickListener {
            val state = viewModel.uiState.value as? PairingUiState.Ready ?: return@setOnClickListener
            val cleanCode = state.confirmCode.filter { it.isDigit() }
            val formatted = cleanCode.chunked(3).joinToString(" ")
            copyToClipboard(getString(R.string.pairing_code_label), cleanCode)
            val toastMessage = getString(R.string.pairing_mobile_code_copied, formatted)
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show()
        }

        btnCopyUrl.setOnClickListener {
            val state = viewModel.uiState.value as? PairingUiState.Ready ?: return@setOnClickListener
            val isTenFoot = DeviceUi.isTenFootExperience(requireContext())
            val presentation = SignInPresentationPolicy.resolve(
                isTenFoot = isTenFoot,
                qrUrl = state.qrUrl,
                confirmCode = state.confirmCode,
                isQrExpandedOnMobile = isQrExpandedOnMobile
            )
            copyToClipboard(getString(R.string.pairing_mobile_title), presentation.directAuthUrl)
            Toast.makeText(requireContext(), R.string.pairing_mobile_link_copied, Toast.LENGTH_SHORT).show()
        }

        btnToggleQr.setOnClickListener {
            isQrExpandedOnMobile = !isQrExpandedOnMobile
            (viewModel.uiState.value as? PairingUiState.Ready)?.let { render(it) }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    override fun onStart() {
        super.onStart()
        val isTenFoot = DeviceUi.isTenFootExperience(requireContext())
        val deviceName = SignInPresentationPolicy.deviceName(isTenFoot)
        viewModel.startPairingIfNeeded(deviceName)
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
        val isTenFoot = DeviceUi.isTenFootExperience(requireContext())

        when (state) {
            PairingUiState.Idle, PairingUiState.Loading -> {
                flQrContainer.isVisible = false
                ivPairingQrCode.isVisible = false
                btnOpenBrowser.isVisible = false
                btnCopyCode.isVisible = false
                btnCopyUrl.isVisible = false
                btnToggleQr.isVisible = false
                tvPairingUrl.isVisible = false
                tvPairingCode.isVisible = false
                tvPairingCodeLabel.isVisible = false
                tvPairingStatus.isVisible = false
                btnGeneratePairing.isVisible = false
            }
            is PairingUiState.Ready -> {
                val presentation = SignInPresentationPolicy.resolve(
                    isTenFoot = isTenFoot,
                    qrUrl = state.qrUrl,
                    confirmCode = state.confirmCode,
                    isQrExpandedOnMobile = isQrExpandedOnMobile
                )

                tvPairingTitle.setText(presentation.titleRes)
                tvPairingDescription.setText(presentation.descriptionRes)
                tvPairingInstructions.setText(presentation.instructionsRes)
                tvPairingCode.text = presentation.formattedConfirmCode

                flQrContainer.isVisible = presentation.isQrCodeVisible
                ivPairingQrCode.isVisible = presentation.isQrCodeVisible
                btnOpenBrowser.isVisible = presentation.isDirectBrowserActionVisible
                btnCopyCode.isVisible = presentation.isCopyActionsVisible
                btnCopyUrl.isVisible = presentation.isCopyActionsVisible
                btnToggleQr.isVisible = presentation.isToggleQrActionVisible
                btnToggleQr.setText(presentation.toggleQrButtonTextRes)

                tvPairingUrl.isVisible = true
                tvPairingCodeLabel.isVisible = true
                tvPairingStatus.isVisible = true
                btnGeneratePairing.isVisible = true

                if (presentation.isQrCodeVisible && renderedQrUrl != state.qrUrl) {
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

                tvPairingStatus.text = getString(
                    R.string.pairing_expiration_status,
                    getString(state.statusMessageRes),
                    formatRemaining(state.remainingSeconds)
                )

                if (isTenFoot && !root.hasFocus()) {
                    btnGeneratePairing.post { btnGeneratePairing.requestFocus() }
                }
            }
            is PairingUiState.Error -> {
                flQrContainer.isVisible = false
                ivPairingQrCode.isVisible = false
                btnOpenBrowser.isVisible = false
                btnCopyCode.isVisible = false
                btnCopyUrl.isVisible = false
                btnToggleQr.isVisible = false
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
