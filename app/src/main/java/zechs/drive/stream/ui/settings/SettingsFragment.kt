package zechs.drive.stream.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.LinearLayout
import android.content.res.Configuration
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.databinding.ItemThemePreviewBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.BackupSerializer
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.state.Resource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.utils.MalSessionManager

@AndroidEntryPoint
class SettingsFragment : BaseFragment() {

    companion object {
        const val TAG = "SettingsFragment"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel by activityViewModels<MainViewModel>()

    @Inject
    lateinit var malRepository: MalRepository

    @Inject
    lateinit var malSessionManager: MalSessionManager

    @Inject
    lateinit var profileManager: zechs.drive.stream.utils.ProfileManager

    @Inject
    lateinit var appSettings: zechs.drive.stream.utils.AppSettings

    @Inject
    lateinit var backupSerializer: BackupSerializer

    @Inject
    lateinit var sessionManager: zechs.drive.stream.utils.SessionManager

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportBackupToUri(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            promptImportModeAndExecute(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(
            inflater, container, /* attachToParent */false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupUserProfileSection()
        setupLibraryRootSetting()
        setupThemeMenu()
        setupDefaultPlayerMenu()
        setupSubtitleAppearanceSetting()
        refreshConfigurableSummaries()
        setupStorageAndCacheSetting()
        setupCheckForUpdates()
        setupMalIntegration()
        setupPlaybackExperience()
        setupBackupRestore()
        setupLogOut()
        setupTvFocus()
    }

    private fun setupTvFocus() {
        val isTv = zechs.drive.stream.utils.DeviceUi.isTenFootExperience(requireContext())
        if (!isTv && resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return

        val rows = listOfNotNull(
            binding.settingSelectProfile,
            binding.settingLibraryRoot,
            binding.settingSelectTheme,
            binding.settingDefaultPlayer,
            binding.settingSubtitleSize,
            binding.settingClearCache,
            binding.settingCheckForUpdate,
            binding.settingMalAccount,
            binding.settingMalSyncToggle,
            binding.settingAutoSkipToggle,
            binding.settingGesturesToggle,
            binding.settingExportBackup,
            binding.settingImportBackup,
            binding.settingLogOut
        )

        rows.forEachIndexed { index, row ->
            row.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.015f else 1f)
                    .scaleY(if (hasFocus) 1.015f else 1f)
                    .translationZ(if (hasFocus) 6f else 0f)
                    .setDuration(120L)
                    .start()
            }
            row.nextFocusUpId = rows.getOrNull(index - 1)?.id ?: binding.toolbar.id
            row.nextFocusDownId = rows.getOrNull(index + 1)?.id ?: row.id
        }

        binding.switchMalSync.isFocusable = false
        binding.switchAutoSkip.isFocusable = false
        binding.switchGestures.isFocusable = false
        binding.settingSelectProfile?.post { binding.settingSelectProfile?.requestFocus() }
    }

    private fun setupUserProfileSection() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.activeProfileFlow.collect { profile ->
                    binding.tvCurrentProfileName.text = "Perfil ativo: ${profile.name}"
                    val avatarRes = profileManager.getAvatarDrawableRes(profile.avatarResName)
                    binding.ivSettingProfileAvatar.setImageResource(avatarRes)
                }
            }
        }

        binding.settingSelectProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_profileSelectionFragment)
        }
    }

    private fun setupLibraryRootSetting() {
        fun refreshValue() {
            val (rootId, rootName) = profileManager.getLibraryRoot()
            binding.tvLibraryRootValue.text = rootName ?: if (rootId.isNullOrBlank()) getString(R.string.my_drive) else rootId
        }

        refreshValue()
        binding.settingLibraryRoot.setOnClickListener {
            val input = android.widget.EditText(requireContext()).apply {
                hint = getString(R.string.library_folder_hint)
                setText(profileManager.getLibraryRoot().second.orEmpty())
                setSelection(text?.length ?: 0)
                isSingleLine = true
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pasta da biblioteca")
                .setMessage("Deixe vazio para usar a raiz do Meu Drive.")
                .setView(input)
                .setPositiveButton("Salvar") { _, _ ->
                    val name = input.text?.toString()?.trim().orEmpty()
                    profileManager.setLibraryRoot(null, name.ifBlank { null })
                    refreshValue()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun setupThemeMenu() {
        binding.settingSelectTheme.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_theme_selector, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.llThemeContainer)

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create()

            AppTheme.entries.forEach { theme ->
                val itemBinding = ItemThemePreviewBinding.inflate(LayoutInflater.from(requireContext()), container, false)
                itemBinding.apply {
                    tvThemeName.text = theme.displayName
                    tvThemeSubtitle.text = theme.subtitle

                    swatchBase.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.bgBaseHex))
                    }
                    swatchSurface.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.bgSurfaceHex))
                    }
                    swatchAccent.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.accentPrimaryHex))
                    }
                    swatchSecondary.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.accentSecondaryHex))
                    }

                    ivSelected.isVisible = (mainViewModel.currentThemeIndex == theme.value)

                    cardTheme.setOnClickListener {
                        mainViewModel.setTheme(theme)
                        binding.tvThemeCurrentValue.text = theme.displayName
                        dialog.dismiss()
                    }
                }
                container.addView(itemBinding.root)
            }

            dialog.show()
        }
    }

    private fun setupDefaultPlayerMenu() {
        val players = listOf(
            getString(R.string.exoplayer),
            getString(R.string.mpv)
        )
        binding.settingDefaultPlayer.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog).apply {
                setTitle(getString(R.string.default_player))
                setSingleChoiceItems(
                    players.toTypedArray(),
                    mainViewModel.currentPlayerIndex.value
                ) { dialog, item ->
                    val player = when (item) {
                        VideoPlayer.EXO_PLAYER.value -> VideoPlayer.EXO_PLAYER
                        VideoPlayer.MPV.value -> VideoPlayer.MPV
                        else -> throw IllegalArgumentException("Unknown default player")
                    }
                    mainViewModel.setPlayer(player)
                    binding.tvPlayerCurrentValue.text = players[item]
                    dialog.dismiss()
                }
            }.also { it.show() }
        }
    }

    private fun refreshConfigurableSummaries() {
        viewLifecycleOwner.lifecycleScope.launch {
            val theme = AppTheme.fromValue(mainViewModel.currentThemeIndex)
            binding.tvThemeCurrentValue.text = theme.displayName
            binding.tvPlayerCurrentValue.text = when (mainViewModel.currentPlayerIndex.value) {
                VideoPlayer.MPV.value -> getString(R.string.mpv)
                else -> getString(R.string.exoplayer)
            }
            try {
                binding.tvSubtitleSizeValue.text = appSettings.fetchSubtitleStyle().summaryLabel()
            } catch (e: Exception) {
                Log.w(TAG, "Could not load subtitle summary", e)
            }
        }
    }

    private fun setupSubtitleAppearanceSetting() {
        binding.settingSubtitleSize.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val initial = try {
                    appSettings.fetchSubtitleStyle()
                } catch (e: Exception) {
                    zechs.drive.stream.utils.SubtitleStyle()
                }
                zechs.drive.stream.ui.player.SubtitleStyleDialog.show(requireContext(), initial) { updated ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            appSettings.saveSubtitleStyle(updated)
                            binding.tvSubtitleSizeValue.text = updated.summaryLabel()
                            showSnackBar("Preferências de legenda salvas")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving subtitle style", e)
                        }
                    }
                }
            }
        }
    }

    private fun setupStorageAndCacheSetting() {
        fun updateCacheDisplay() {
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val sizeBytes = calculateTotalCacheBytes()
                val sizeMb = sizeBytes / (1024.0 * 1024.0)
                val displayStr = if (sizeMb < 0.1) {
                    "Cache limpo (< 100 KB)"
                } else {
                    String.format(java.util.Locale.ROOT, "%.1f MB em cache (Posters, capas e legendas)", sizeMb)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    binding.tvCacheSizeValue.text = displayStr
                }
            }
        }

        updateCacheDisplay()

        binding.settingClearCache.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Limpar Cache de Mídia e Legendas?")
                .setMessage("Isso liberará espaço em disco removendo miniaturas temporárias e legendas baixadas. Seus perfis, favoritos e histórico de reprodução NÃO serão apagados.")
                .setPositiveButton("Limpar") { dialog, _ ->
                    dialog.dismiss()
                    showSnackBar("Limpando cache...")
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // 1. Clear Glide disk cache
                            com.bumptech.glide.Glide.get(requireContext().applicationContext).clearDiskCache()

                            // 2. Clear temp subtitle files
                            val subDir = java.io.File(requireContext().filesDir, "subtitles")
                            if (subDir.exists()) {
                                subDir.deleteRecursively()
                            }
                            val subCache = java.io.File(requireContext().cacheDir, "subtitles")
                            if (subCache.exists()) {
                                subCache.deleteRecursively()
                            }

                            // 3. Clear app general cache
                            requireContext().cacheDir.listFiles()?.forEach { file ->
                                file.deleteRecursively()
                            }
                            requireContext().externalCacheDir?.listFiles()?.forEach { file ->
                                file.deleteRecursively()
                            }

                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.bumptech.glide.Glide.get(requireContext().applicationContext).clearMemory()
                                updateCacheDisplay()
                                showSnackBar("Cache limpo com sucesso! Espaço liberado.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error clearing cache", e)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                showSnackBar("Erro ao limpar cache: ${e.message}")
                            }
                        }
                    }
                }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun calculateTotalCacheBytes(): Long {
        var total = 0L
        fun addDir(file: java.io.File?) {
            if (file == null || !file.exists()) return
            if (file.isDirectory) {
                file.listFiles()?.forEach { addDir(it) }
            } else {
                total += file.length()
            }
        }

        try {
            addDir(requireContext().cacheDir)
            addDir(requireContext().externalCacheDir)
            addDir(java.io.File(requireContext().filesDir, "subtitles"))
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating cache size", e)
        }
        return total
    }

    private fun setupCheckForUpdates() {

        var isUserClick = false
        binding.settingCheckForUpdate.setOnClickListener {
            if (!mainViewModel.isChecking) {
                mainViewModel.getLatestRelease()
                isUserClick = true
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.lastUpdated.collect {
                    if (it != null) {
                        val last = getString(R.string.last_checked, it)
                        TransitionManager.beginDelayedTransition(
                            binding.settingCheckForUpdate,
                        )
                        binding.lastCheckedLabel.text = last
                    }
                }
            }
        }

        fun isChecking(bool: Boolean) {
            binding.progressBarChecking.isInvisible = !bool
        }

        mainViewModel.latest.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> isChecking(true)

                is Resource.Error -> {
                    isChecking(false)
                    val errorMsg = state.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.unable_to_check_updates)
                    showSnackBar(errorMsg)
                }

                is Resource.Success -> {
                    isChecking(false)
                    val release = state.data
                    if (release.isLatest() && isUserClick) {
                        showSnackBar(getString(R.string.already_latest_version))
                    }
                }
            }
        }

    }

    private fun setupMalIntegration() {
        fun updateMalUi() {
            val isLoggedIn = malSessionManager.isLoggedIn()
            val username = malSessionManager.getUsername()
            if (isLoggedIn) {
                binding.tvMalTitle.text = "MyAnimeList"
                binding.tvMalSubtitle.text = "Conectado como: ${username ?: "Usuário"} • Toque para desconectar"
                binding.ivMalIcon.setImageResource(R.drawable.ic_unlock_24)
            } else {
                binding.tvMalTitle.text = "MyAnimeList"
                binding.tvMalSubtitle.text = "Conectar conta para scrobble automático"
                binding.ivMalIcon.setImageResource(R.drawable.ic_lock_24)
            }
            binding.switchMalSync.isChecked = malSessionManager.isSyncEnabled()
            binding.settingMalSyncToggle.isVisible = isLoggedIn
            // Direct token transfer over the LAN was removed. The TV flow uses
            // the short-lived OAuth code handled by MalAuthDialog instead.
            binding.settingMalTransferToTv.isVisible = false
        }

        updateMalUi()

        binding.switchMalSync.setOnCheckedChangeListener { _, isChecked ->
            malSessionManager.setSyncEnabled(isChecked)
            showSnackBar(if (isChecked) "Sincronização com o MyAnimeList ativada" else "Sincronização com o MyAnimeList pausada")
        }

        binding.settingMalAccount.setOnClickListener {
            if (malSessionManager.isLoggedIn()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Desconectar do MyAnimeList?")
                    .setMessage("O scrobble automático de episódios será desativado.")
                    .setPositiveButton("Desconectar") { dialog, _ ->
                        malSessionManager.clearSession()
                        updateMalUi()
                        dialog.dismiss()
                        showSnackBar("Conta do MyAnimeList desconectada")
                    }
                    .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                val openAuth = {
                    val authDialog = MalAuthDialog(
                        onCodeReceived = { code, verifier ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                showSnackBar("Autenticando com MyAnimeList...")
                                val res = malRepository.exchangeToken(code, verifier)
                                if (res is Resource.Success) {
                                    updateMalUi()
                                    showSnackBar("MyAnimeList conectado com sucesso!")
                                } else {
                                    showSnackBar(res.message ?: "Falha ao conectar MyAnimeList")
                                }
                            }
                        },
                        clientId = malSessionManager.getClientId()
                    )
                    authDialog.show(parentFragmentManager, MalAuthDialog.TAG)
                }

                if (malSessionManager.getClientId().isBlank() || malSessionManager.getClientSecret().isBlank()) {
                    val fields = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 0, 48, 0)
                    }
                    val clientId = android.widget.EditText(requireContext()).apply {
                        hint = "Client ID"
                        setText(malSessionManager.getClientId())
                        isSingleLine = true
                    }
                    val clientSecret = android.widget.EditText(requireContext()).apply {
                        hint = "Client Secret"
                        setText(malSessionManager.getClientSecret())
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        isSingleLine = true
                    }
                    fields.addView(clientId)
                    fields.addView(clientSecret)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Configurar MyAnimeList")
                        .setMessage("Crie uma aplicação OAuth no MyAnimeList e informe as credenciais. Elas serão armazenadas criptografadas.")
                        .setView(fields)
                        .setPositiveButton("Continuar") { _, _ ->
                            if (clientId.text.isNullOrBlank() || clientSecret.text.isNullOrBlank()) {
                                showSnackBar("Informe o Client ID e o Client Secret")
                            } else {
                                malSessionManager.saveClientCredentials(clientId.text.toString(), clientSecret.text.toString())
                                openAuth()
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    openAuth()
                }
            }
        }
    }

    private fun setupPlaybackExperience() {
        binding.switchAutoSkip.isChecked = malSessionManager.isAutoSkipEnabled()
        binding.settingAutoSkipToggle.setOnClickListener {
            binding.switchAutoSkip.toggle()
        }
        binding.switchAutoSkip.setOnCheckedChangeListener { _, isChecked ->
            malSessionManager.setAutoSkipEnabled(isChecked)
            showSnackBar(if (isChecked) "Pulo de aberturas automático ativado" else "Pulo de aberturas automático desativado")
        }

        binding.switchGestures.isChecked = malSessionManager.isGesturesEnabled()
        binding.settingGesturesToggle.setOnClickListener {
            binding.switchGestures.toggle()
        }
        binding.switchGestures.setOnCheckedChangeListener { _, isChecked ->
            malSessionManager.setGesturesEnabled(isChecked)
            showSnackBar(if (isChecked) "Gestos touch ativados" else "Gestos touch desativados")
        }
    }

    private fun setupBackupRestore() {
        binding.settingExportBackup?.setOnClickListener {
            val fileName = "makimono_backup_${System.currentTimeMillis()}.json"
            exportBackupLauncher.launch(fileName)
        }

        binding.settingImportBackup?.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }
    }

    private fun exportBackupToUri(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = backupSerializer.exportBackupJson()
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                showSnackBar("Backup exportado com sucesso!")
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting backup", e)
                showSnackBar("Erro ao exportar backup: ${e.message}")
            }
        }
    }

    private fun promptImportModeAndExecute(uri: android.net.Uri) {
        val options = arrayOf(
            "Mesclar com perfis existentes (Recomendado)",
            "Substituir todos os dados existentes"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restaurar Backup")
            .setMessage("Escolha como deseja aplicar os dados do arquivo de backup:")
            .setItems(options) { _, which ->
                val mode = if (which == 0) {
                    BackupSerializer.ImportMode.MERGE
                } else {
                    BackupSerializer.ImportMode.REPLACE
                }
                executeImport(uri, mode)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executeImport(uri: android.net.Uri, mode: BackupSerializer.ImportMode) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: run {
                    showSnackBar("Não foi possível ler o arquivo selecionado")
                    return@launch
                }

                val result = backupSerializer.importBackupJson(json, mode)
                if (result.isSuccess) {
                    val summary = "Backup restaurado: ${result.importedProfiles} perfis, ${result.importedWatches} históricos, ${result.importedFavorites} favoritos"
                    showSnackBar(summary)
                } else {
                    val errorMsg = result.errorMessage ?: "Erro desconhecido ao importar backup"
                    showSnackBar(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing backup", e)
                showSnackBar("Falha na restauração: ${e.message}")
            }
        }
    }

    private fun showSnackBar(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun setupLogOut() {
        binding.settingLogOut?.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(getString(R.string.log_out_dialog_title))
                .setMessage(getString(R.string.log_out_dialog_message))
                .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                    dialog.dismiss()
                    lifecycleScope.launch {
                        sessionManager.resetDataStore()
                        val activity = requireActivity()
                        activity.finish()
                        kotlinx.coroutines.delay(250L)
                        activity.startActivity(activity.intent)
                    }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
