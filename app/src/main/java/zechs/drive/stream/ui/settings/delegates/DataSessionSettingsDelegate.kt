package zechs.drive.stream.ui.settings.delegates

import android.content.Context
import android.net.Uri
import zechs.drive.stream.utils.AppLog
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isInvisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.settings.SettingsEvent
import zechs.drive.stream.ui.settings.SettingsUiState
import zechs.drive.stream.ui.settings.SettingsViewModel
import zechs.drive.stream.utils.BackupSerializer
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.state.Resource
import java.io.File

/**
 * Delegado responsável pela seção de Dados & Sessão (Armazenamento/Cache, Atualizações, Backup/Restauração e Logout).
 * Conforme Seção 4.10 do Plano v2.
 */
class DataSessionSettingsDelegate(
    private val context: Context,
    private val binding: FragmentSettingsBinding,
    private val viewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val exportBackupLauncher: ActivityResultLauncher<String>,
    private val importBackupLauncher: ActivityResultLauncher<Array<String>>,
    private val showSnackBar: (String) -> Unit
) {

    companion object {
        private const val TAG = "DataSessionSettings"
    }

    fun setup() {
        setupStorageAndCacheSetting()
        setupCheckForUpdates()
        setupBackupRestore()
        setupLogOut()
    }

    private fun setupStorageAndCacheSetting() {
        fun updateCacheDisplay() {
            lifecycleScope.launch(Dispatchers.IO) {
                val sizeBytes = calculateTotalCacheBytes()
                withContext(Dispatchers.Main) {
                    viewModel.onEvent(SettingsEvent.UpdateCacheSize(sizeBytes))
                }
            }
        }

        updateCacheDisplay()

        binding.settingClearCache.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Limpar Cache de Mídia e Legendas?")
                .setMessage("Isso liberará espaço em disco removendo miniaturas temporárias e legendas baixadas. Seus perfis, favoritos e histórico de reprodução NÃO serão apagados.")
                .setPositiveButton("Limpar") { d, _ ->
                    d.dismiss()
                    showSnackBar("Limpando cache...")
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            Glide.get(context.applicationContext).clearDiskCache()
                            val subDir = File(context.filesDir, "subtitles")
                            if (subDir.exists()) subDir.deleteRecursively()
                            val subCache = File(context.cacheDir, "subtitles")
                            if (subCache.exists()) subCache.deleteRecursively()

                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }

                            withContext(Dispatchers.Main) {
                                Glide.get(context.applicationContext).clearMemory()
                                updateCacheDisplay()
                                showSnackBar("Cache limpo com sucesso! Espaço liberado.")
                            }
                        } catch (e: Exception) {
                            AppLog.e(TAG, "Error clearing cache", e)
                            withContext(Dispatchers.Main) {
                                showSnackBar("Erro ao limpar cache: ${e.message}")
                            }
                        }
                    }
                }
                .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                .create()

            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
            }
            dialog.show()
        }
    }

    private fun calculateTotalCacheBytes(): Long {
        var total = 0L
        fun addDir(file: File?) {
            if (file == null || !file.exists()) return
            if (file.isDirectory) {
                file.listFiles()?.forEach { addDir(it) }
            } else {
                total += file.length()
            }
        }

        try {
            addDir(context.cacheDir)
            addDir(context.externalCacheDir)
            addDir(File(context.filesDir, "subtitles"))
        } catch (e: Exception) {
            AppLog.w(TAG, "Error calculating cache size", e)
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

        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.lastUpdated.collect {
                    if (it != null) {
                        val last = context.getString(R.string.last_checked, it)
                        TransitionManager.beginDelayedTransition(binding.settingCheckForUpdate)
                        binding.lastCheckedLabel.text = last
                    }
                }
            }
        }

        fun isChecking(bool: Boolean) {
            binding.progressBarChecking.isInvisible = !bool
        }

        mainViewModel.latest.observe(lifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> isChecking(true)
                is Resource.Error -> {
                    isChecking(false)
                    val errorMsg = state.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.unable_to_check_updates)
                    showSnackBar(errorMsg)
                }
                is Resource.Success -> {
                    isChecking(false)
                    val release = state.data
                    if (release.isLatest() && isUserClick) {
                        showSnackBar(context.getString(R.string.already_latest_version))
                    }
                }
            }
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

    fun exportBackupToUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = viewModel.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                showSnackBar("Backup exportado com sucesso!")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error exporting backup", e)
                showSnackBar("Erro ao exportar backup: ${e.message}")
            }
        }
    }

    fun promptImportModeAndExecute(uri: Uri) {
        val options = arrayOf(
            "Mesclar com perfis existentes (Recomendado)",
            "Substituir todos os dados existentes"
        )
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
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
            .create()

        dialog.setOnShowListener {
            dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
        }
        dialog.show()
    }

    private fun executeImport(uri: Uri, mode: BackupSerializer.ImportMode) {
        lifecycleScope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: run {
                    showSnackBar("Não foi possível ler o arquivo selecionado")
                    return@launch
                }

                val result = viewModel.importBackupJson(json, mode)
                if (result.isSuccess) {
                    val summary = "Backup restaurado: ${result.importedProfiles} perfis, ${result.importedWatches} históricos, ${result.importedFavorites} favoritos"
                    showSnackBar(summary)
                } else {
                    val errorMsg = result.errorMessage ?: "Erro desconhecido ao importar backup"
                    showSnackBar(errorMsg)
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error importing backup", e)
                showSnackBar("Falha na restauração: ${e.message}")
            }
        }
    }

    private fun setupLogOut() {
        binding.settingLogOut?.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(context.getString(R.string.log_out_dialog_title))
                .setMessage(context.getString(R.string.log_out_dialog_message))
                .setNegativeButton(context.getString(R.string.no)) { d, _ -> d.dismiss() }
                .setPositiveButton(context.getString(R.string.yes)) { d, _ ->
                    d.dismiss()
                    viewModel.onEvent(SettingsEvent.ResetSession)
                }
                .create()

            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
            }
            dialog.show()
        }
    }

    fun render(state: SettingsUiState) {
        if (state.cacheSizeText.isNotBlank()) {
            binding.tvCacheSizeValue.text = state.cacheSizeText
        }
    }
}
