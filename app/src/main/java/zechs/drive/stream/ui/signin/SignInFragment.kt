package zechs.drive.stream.ui.signin

import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import zechs.drive.stream.utils.AppLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveClient
import zechs.drive.stream.databinding.FragmentSignInBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.code.DialogCode
import zechs.drive.stream.utils.ext.hideKeyboardWhenOffFocus
import zechs.drive.stream.utils.ext.navigateSafe
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.util.Constants.Companion.GUIDE_TO_MAKE_DRIVE_CLIENT


class SignInFragment : BaseFragment() {

    companion object {
        const val TAG = "SignInFragment"
    }

    private var _binding: FragmentSignInBinding? = null
    private val binding get() = _binding!!

    private var _codeDialog: DialogCode? = null
    private val codeDialog get() = _codeDialog!!

    private val viewModel by activityViewModels<SignInViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignInBinding.inflate(
            inflater, container, /* attachToParent */false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSignInBinding.bind(view)

        binding.signInText.setOnClickListener {
            if (!updateClient()) {
                return@setOnClickListener
            }
            val client = viewModel.client
            if (client == null) {
                AppLog.e(TAG, "signInText click: client is null after updateClient() returned true")
                return@setOnClickListener
            }
            val authUri = client.authUrl()
            if (authUri == null) {
                showMessage(R.string.sign_in_invalid_configuration)
                return@setOnClickListener
            }
            openExternalUri(authUri)
        }


        binding.btnHelp.setOnClickListener {
            openExternalUri(Uri.parse(GUIDE_TO_MAKE_DRIVE_CLIENT))
        }

        binding.enterCode.setOnClickListener {
            if (viewModel.client == null) {
                updateClient()
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.authorization_code_note_title)
                .setMessage(getString(R.string.important_note_message))
                .setPositiveButton(R.string.continue_action) { dialog, _ ->
                    dialog.dismiss()
                    showCodeDialog()
                }.show()
        }

        binding.btnAdvancedConfig.setOnClickListener {
            val expanded = !binding.layoutAdvancedConfig.isVisible
            binding.layoutAdvancedConfig.isVisible = expanded
            val label = if (expanded) {
                R.string.advanced_connection_settings_hide
            } else {
                R.string.advanced_connection_settings
            }
            binding.btnAdvancedConfig.setText(label)
            binding.btnAdvancedConfig.contentDescription = getString(label)
        }

        listOf(binding.signInText, binding.enterCode, binding.btnHelp, binding.btnAdvancedConfig)
            .forEach { action ->
                action.setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        view.post {
                            if (view.isAttachedToWindow && view.hasFocus()) {
                                scrollActionIntoSafeArea(view)
                            }
                        }
                    }
                }
            }

        binding.clientId.editText?.hideKeyboardWhenOffFocus()
        binding.clientSecret.editText?.hideKeyboardWhenOffFocus()
        binding.redirectUri.editText?.hideKeyboardWhenOffFocus()

        if (binding.clientId.editText?.text.isNullOrEmpty()) {
            binding.clientId.editText?.setText(zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT_ID)
        }
        if (binding.clientSecret.editText?.text.isNullOrEmpty()) {
            binding.clientSecret.editText?.setText(zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT_SECRET)
        }
        if (binding.redirectUri.editText?.text.isNullOrEmpty()) {
            binding.redirectUri.editText?.setText(zechs.drive.stream.utils.util.Constants.DEFAULT_REDIRECT_URI)
        }
        if (binding.scopes.editText?.text.isNullOrEmpty()) {
            binding.scopes.editText?.setText(zechs.drive.stream.utils.util.Constants.DEFAULT_DRIVE_SCOPE)
        }
        updateClient()

        if (DeviceUi.isTenFootExperience(requireContext())) {
            binding.signInText.isFocusableInTouchMode = true
            binding.signInText.post {
                if (binding.signInText.isAttachedToWindow && binding.signInText.isShown) {
                    binding.signInText.requestFocus()
                }
            }
        }

        loginObserver()
    }

    private fun loginObserver() {
        viewModel.loginStatus.observe(viewLifecycleOwner) { response ->
            when (response) {
                is Resource.Success -> {
                    findNavController().navigateSafe(
                        R.id.action_signInFragment_to_homeFragment
                    )
                }
                is Resource.Error -> {
                    isLoading(false)
                    val messageResId = if (response.message == SignInViewModel.AUTHORIZATION_CODE_MISSING) {
                        R.string.sign_in_authorization_code_missing
                    } else {
                        R.string.sign_in_connection_failed
                    }
                    Snackbar.make(
                        binding.root,
                        getString(messageResId),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is Resource.Loading -> isLoading(true)
            }
        }
    }

    private fun showCodeDialog() {
        if (_codeDialog == null) {
            _codeDialog = DialogCode(
                context = requireContext(),
                onSubmitClickListener = { codeUri ->
                    viewModel.requestRefreshToken(codeUri)
                    codeDialog.dismiss()
                }
            )
        }

        codeDialog.also {
            it.show()
            it.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(MATCH_PARENT, WRAP_CONTENT)
            }
            it.setOnDismissListener {
                _codeDialog = null
            }
        }
    }

    private fun openExternalUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.sign_in_browser_unavailable)
        }
    }

    private fun showMessage(messageResId: Int) {
        _binding?.let { currentBinding ->
            Snackbar.make(currentBinding.root, messageResId, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun scrollActionIntoSafeArea(action: View) {
        val scroll = _binding?.signInScrollView ?: return
        val bounds = Rect()
        action.getDrawingRect(bounds)
        scroll.offsetDescendantRectToMyCoords(action, bounds)

        val visibleTop = scroll.scrollY + scroll.paddingTop
        val visibleBottom = scroll.scrollY + scroll.height - scroll.paddingBottom
        val scrollBy = when {
            bounds.bottom > visibleBottom -> bounds.bottom - visibleBottom
            bounds.top < visibleTop -> bounds.top - visibleTop
            else -> 0
        }
        if (scrollBy != 0) scroll.smoothScrollBy(0, scrollBy)
    }

    private fun isLoading(loading: Boolean) {
        binding.apply {
            this.loading.isVisible = loading
            layoutConfigure.isVisible = !loading
            enterCode.isVisible = !loading
        }
    }

    private fun updateClient(): Boolean {
        val clientId = binding.clientId.editText?.text?.toString()?.trim()
            ?.ifEmpty { zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT_ID }
            ?: zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT_ID
        val clientSecret = binding.clientSecret.editText?.text?.toString()?.trim()
            ?.ifEmpty { zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT_SECRET }
            ?: zechs.drive.stream.utils.util.Constants.DEFAULT_CLIENT_SECRET
        val redirectUri = binding.redirectUri.editText?.text?.toString()?.trim()
            ?.ifEmpty { zechs.drive.stream.utils.util.Constants.DEFAULT_REDIRECT_URI }
            ?: zechs.drive.stream.utils.util.Constants.DEFAULT_REDIRECT_URI
        val scopes = binding.scopes.editText?.text?.toString()?.trim()
            ?.ifEmpty { zechs.drive.stream.utils.util.Constants.DEFAULT_DRIVE_SCOPE }
            ?: zechs.drive.stream.utils.util.Constants.DEFAULT_DRIVE_SCOPE

        if (clientId.isEmpty()
            || clientSecret.isEmpty()
            || redirectUri.isEmpty()
            || scopes.isEmpty()
        ) {
            Snackbar.make(
                binding.root,
                getString(R.string.fill_all_fields),
                Snackbar.LENGTH_LONG
            ).show()
            return false
        }

        viewModel.client = DriveClient(
            clientId, clientSecret,
            redirectUri, listOf(scopes)
        )
        return true
    }
}
