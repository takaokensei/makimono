package zechs.drive.stream.ui.settings.delegates

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.repository.SeedboxRepository
import zechs.drive.stream.databinding.DialogSeedboxManagementBinding
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.settings.MalAuthDialog
import zechs.drive.stream.ui.settings.SettingsEvent
import zechs.drive.stream.ui.settings.SettingsUiState
import zechs.drive.stream.ui.settings.SettingsViewModel
import zechs.drive.stream.ui.settings.adapter.SeedboxJobsAdapter
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.ext.resolveThemeColor

/**
 * Delegado responsável pela seção de Integrações (MyAnimeList OAuth, Scrobble e Seedbox Colab P2P).
 * Conforme Seção 4.10 do Plano v2.
 */
class IntegrationsSettingsDelegate(
    private val context: Context,
    private val binding: FragmentSettingsBinding,
    private val viewModel: SettingsViewModel,
    private val fragmentManager: FragmentManager,
    private val showSnackBar: (String) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val seedboxRepoProvider: () -> SeedboxRepository
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

        binding.settingSeedbox?.setOnClickListener {
            showSeedboxDialog()
        }
    }

    private fun showSeedboxDialog() {
        val options = arrayOf(
            "📋 Obter Token do Daemon (MAKIMONO_SEEDBOX_TOKEN)",
            "⚡ Novo Pedido de Download (Anime / Magnet)",
            "📊 Central de Downloads e Fila"
        )

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(R.string.seedbox_title)
            .setItems(options) { d, which ->
                d.dismiss()
                when (which) {
                    0 -> showDaemonTokenDialog()
                    1 -> showNewJobPrompt()
                    2 -> showQueueStatusDialog()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
        }
        dialog.show()
    }

    private fun showDaemonTokenDialog() {
        showSnackBar("Gerando token do daemon...")
        lifecycleScope.launch {
            val res = seedboxRepoProvider().generateDaemonToken()
            res.onSuccess { token ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Makimono Seedbox Token", token))

                val msg = "Token gerado e copiado para a área de transferência:\n\n$token\n\nConfigure a variável MAKIMONO_SEEDBOX_TOKEN no seu notebook Colab."
                val tokenDialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                    .setTitle(R.string.seedbox_daemon_token_title)
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .create()
                tokenDialog.setOnShowListener {
                    tokenDialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                }
                tokenDialog.show()
            }.onFailure { err ->
                showSnackBar("Erro ao gerar token: ${err.message}")
            }
        }
    }

    private fun showNewJobPrompt(onEnqueued: (() -> Unit)? = null) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }
        val queryInput = android.widget.EditText(context).apply {
            hint = context.getString(R.string.seedbox_search_hint)
            isSingleLine = true
            setBackgroundResource(R.drawable.glass_input_bg)
            setPadding(32, 24, 32, 24)
        }
        container.addView(queryInput)

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(R.string.seedbox_title)
            .setMessage(R.string.seedbox_prompt_message)
            .setView(container)
            .setPositiveButton(R.string.seedbox_enqueue_action) { _, _ ->
                val query = queryInput.text.toString().trim()
                if (query.isBlank()) {
                    showSnackBar("Informe o nome do anime ou o link magnet")
                    return@setPositiveButton
                }
                showSnackBar("Adicionando pedido à fila...")
                lifecycleScope.launch {
                    val magnetUri = if (query.startsWith("magnet:?")) query else null
                    val res = seedboxRepoProvider().enqueueJob(
                        query = query,
                        episodesFilter = "all",
                        preferPtBr = true,
                        magnetUri = magnetUri
                    )
                    res.onSuccess { jobId ->
                        showSnackBar("Pedido adicionado à fila com sucesso (ID: ${jobId.take(8)})!")
                        onEnqueued?.invoke()
                    }.onFailure { err ->
                        showSnackBar("Falha ao agendar download: ${err.message}")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
            queryInput.requestFocus()
        }
        dialog.show()
    }

    private fun showQueueStatusDialog() {
        val dialog = Dialog(context, R.style.ThemeOverlay_DriveStream_Dialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
        val mgmtBinding = DialogSeedboxManagementBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(mgmtBinding.root)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88).toInt().coerceAtMost(860),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            TvFocusRing.install(decorView)
        }

        lateinit var jobsAdapter: SeedboxJobsAdapter
        jobsAdapter = SeedboxJobsAdapter { jobToCancel ->
            lifecycleScope.launch {
                val cancelRes = seedboxRepoProvider().cancelJob(jobToCancel.jobId)
                cancelRes.onSuccess {
                    showSnackBar(context.getString(R.string.seedbox_stage_cancelled) + ": ${jobToCancel.animeQuery}")
                    loadManagementQueue(mgmtBinding, jobsAdapter)
                }.onFailure { err ->
                    showSnackBar("Erro ao cancelar: ${err.message}")
                }
            }
        }

        mgmtBinding.rvSeedboxJobs.apply {
            adapter = jobsAdapter
            layoutManager = LinearLayoutManager(context)
        }

        mgmtBinding.btnCloseManagement.setOnClickListener {
            dialog.dismiss()
        }

        mgmtBinding.btnRefreshQueue.setOnClickListener {
            loadManagementQueue(mgmtBinding, jobsAdapter)
        }

        mgmtBinding.btnCancelAllJobs.setOnClickListener {
            MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.seedbox_mgmt_cancel_all_confirm_title)
                .setMessage(R.string.seedbox_mgmt_cancel_all_confirm_msg)
                .setPositiveButton(R.string.seedbox_mgmt_cancel_all) { _, _ ->
                    lifecycleScope.launch {
                        val res = seedboxRepoProvider().cancelAllJobs()
                        res.onSuccess { count ->
                            showSnackBar("$count downloads cancelados.")
                            loadManagementQueue(mgmtBinding, jobsAdapter)
                        }.onFailure { err ->
                            showSnackBar("Erro ao cancelar fila: ${err.message}")
                        }
                    }
                }
                .setNegativeButton(R.string.seedbox_confirm_download_no, null)
                .show()
        }

        mgmtBinding.btnNewJob.setOnClickListener {
            showNewJobPrompt {
                loadManagementQueue(mgmtBinding, jobsAdapter)
            }
        }

        mgmtBinding.btnCopyToken.setOnClickListener {
            showDaemonTokenDialog()
        }

        FocusEngine.attach(mgmtBinding.btnCloseManagement, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(mgmtBinding.btnRefreshQueue, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(mgmtBinding.btnNewJob, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(mgmtBinding.btnCancelAllJobs, FocusEngine.FocusKind.BUTTON)
        FocusEngine.attach(mgmtBinding.btnCopyToken, FocusEngine.FocusKind.BUTTON)

        loadManagementQueue(mgmtBinding, jobsAdapter)
        dialog.show()
    }

    private fun loadManagementQueue(
        mgmtBinding: DialogSeedboxManagementBinding,
        jobsAdapter: SeedboxJobsAdapter
    ) {
        mgmtBinding.progressLoadingQueue.isVisible = true
        lifecycleScope.launch {
            val statusRes = seedboxRepoProvider().getStatus()
            val jobsRes = seedboxRepoProvider().getJobs()

            mgmtBinding.progressLoadingQueue.isGone = true

            val jobs = jobsRes.getOrDefault(emptyList())
            val status = statusRes.getOrNull()

            if (status != null && status.isOnline) {
                val disk = status.freeDiskGb?.let { "${it}GB livres" } ?: ""
                val speed = status.activeSpeed?.let { " • $it" } ?: ""
                mgmtBinding.tvServerStatusTitle.text = context.getString(R.string.seedbox_status_colab_online)
                mgmtBinding.tvServerStatusMeta.text = if (disk.isNotEmpty()) "$disk$speed" else "Pronto para receber pedidos"
                mgmtBinding.viewServerStatusDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.obsidian_success)
                )
            } else {
                mgmtBinding.tvServerStatusTitle.text = context.getString(R.string.seedbox_status_colab_offline)
                mgmtBinding.tvServerStatusMeta.setText(R.string.seedbox_mgmt_colab_offline_hint)
                mgmtBinding.viewServerStatusDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.obsidian_text_tertiary)
                )
            }

            val activeCount = jobs.count { it.status.uppercase() in listOf("DOWNLOADING", "UPLOADING", "PENDING", "SEARCHING") }
            val completedCount = jobs.count { it.status.uppercase() == "COMPLETED" }

            mgmtBinding.tvActiveCountBadge.text = context.getString(R.string.seedbox_mgmt_active_count, activeCount)
            mgmtBinding.tvCompletedCountBadge.text = context.getString(R.string.seedbox_mgmt_completed_count, completedCount)

            mgmtBinding.btnCancelAllJobs.isVisible = activeCount > 0

            jobsAdapter.submitList(jobs)
            mgmtBinding.layoutEmptyQueue.isVisible = jobs.isEmpty()
            mgmtBinding.rvSeedboxJobs.isVisible = jobs.isNotEmpty()
        }
    }

    private fun showDisconnectDialog() {
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(R.string.settings_mal_disconnect_title)
            .setMessage(R.string.settings_mal_disconnect_message)
            .setPositiveButton("Desconectar") { d, _ ->
                viewModel.onEvent(SettingsEvent.DisconnectMal)
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
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
                .setTitle(R.string.settings_mal_configure_title)
                .setMessage(R.string.settings_mal_configure_message)
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
