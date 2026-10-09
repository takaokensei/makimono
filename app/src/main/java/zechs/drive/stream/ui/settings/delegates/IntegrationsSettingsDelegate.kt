package zechs.drive.stream.ui.settings.delegates

import android.content.Context
import android.content.res.ColorStateList
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.settings.MalAuthDialog
import zechs.drive.stream.ui.settings.SettingsEvent
import zechs.drive.stream.ui.settings.SettingsUiState
import zechs.drive.stream.ui.settings.SettingsViewModel
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.ext.resolveThemeColor

/**
 * Delegado responsável pela seção de Integrações (MyAnimeList OAuth, Scrobble e sincronização).
 * Conforme Seção 4.10 do Plano v2.
 */
class IntegrationsSettingsDelegate(
    private val context: Context,
    private val binding: FragmentSettingsBinding,
    private val viewModel: SettingsViewModel,
    private val fragmentManager: FragmentManager,
    private val showSnackBar: (String) -> Unit
) {

    fun setup() {
        binding.settingMalSyncToggle.setOnClickListener {
            binding.switchMalSync.toggle()
        }
        binding.switchMalSync.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetMalSync(isChecked))
        }

        binding.settingMalAccount.setOnClickListener {
            if (viewModel.uiState.value.isMalLoggedIn) {
                showDisconnectDialog()
            } else {
                handleLoginClick()
            }
        }
    }

    private fun showDisconnectDialog() {
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle("Desconectar do MyAnimeList?")
            .setMessage("O scrobble automático de episódios será desativado.")
            .setPositiveButton("Desconectar") { d, _ ->
                viewModel.onEvent(SettingsEvent.DisconnectMal)
                d.dismiss()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        }
        dialog.show()
    }

    private fun handleLoginClick() {
        val clientId = viewModel.getMalClientId()
        val clientSecret = viewModel.getMalClientSecret()
        if (clientId.isBlank() || clientSecret.isBlank()) {
            val fields = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 0, 48, 0)
            }
            val clientIdInput = android.widget.EditText(context).apply {
                hint = "Client ID"
                setText(clientId)
                isSingleLine = true
                setBackgroundResource(R.drawable.glass_input_bg)
                setPadding(32, 24, 32, 24)
            }
            val clientSecretInput = android.widget.EditText(context).apply {
                hint = "Client Secret"
                setText(clientSecret)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSingleLine = true
                setBackgroundResource(R.drawable.glass_input_bg)
                setPadding(32, 24, 32, 24)
            }
            fields.addView(clientIdInput)
            fields.addView(clientSecretInput)
            val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Configurar MyAnimeList")
                .setMessage("Crie uma aplicação OAuth no MyAnimeList e informe as credenciais. Elas serão armazenadas criptografadas.")
                .setView(fields)
                .setPositiveButton("Continuar") { _, _ ->
                    if (clientIdInput.text.isNullOrBlank() || clientSecretInput.text.isNullOrBlank()) {
                        showSnackBar("Informe o Client ID e o Client Secret")
                    } else {
                        viewModel.onEvent(
                            SettingsEvent.SaveMalCredentials(
                                clientIdInput.text.toString(),
                                clientSecretInput.text.toString()
                            )
                        )
                    }
                }
                .setNegativeButton("Cancelar", null)
                .create()

            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                clientIdInput.requestFocus()
            }
            dialog.show()
        } else {
            openMalAuthDialog(clientId)
        }
    }

    fun openMalAuthDialog(clientId: String) {
        val authDialog = MalAuthDialog(
            onCodeReceived = { code, verifier ->
                viewModel.onEvent(SettingsEvent.ExchangeMalToken(code, verifier))
            },
            clientId = clientId
        )
        authDialog.show(fragmentManager, MalAuthDialog.TAG)
    }

    fun render(state: SettingsUiState) {
        val activeTint = context.resolveThemeColor(R.attr.colorAccentPrimary)
        val inactiveTint = context.resolveThemeColor(R.attr.colorTextSecondary)
        if (state.isMalLoggedIn) {
            binding.tvMalTitle.text = "MyAnimeList"
            binding.tvMalSubtitle.text = "Conectado como: ${state.malUsername ?: "Usuário"} • Toque para desconectar"
            binding.ivMalIcon.setImageResource(R.drawable.ic_mal_24)
            binding.ivMalIcon.imageTintList = ColorStateList.valueOf(activeTint)
        } else {
            binding.tvMalTitle.text = "MyAnimeList"
            binding.tvMalSubtitle.text = "Conectar conta para scrobble automático"
            binding.ivMalIcon.setImageResource(R.drawable.ic_mal_24)
            binding.ivMalIcon.imageTintList = ColorStateList.valueOf(inactiveTint)
        }
        binding.switchMalSync.isChecked = state.isMalSyncEnabled
        binding.settingMalSyncToggle.isVisible = state.isMalLoggedIn
        binding.settingMalTransferToTv.isVisible = false
    }
}
