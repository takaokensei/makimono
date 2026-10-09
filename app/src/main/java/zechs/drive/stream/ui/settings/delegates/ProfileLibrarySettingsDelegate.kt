package zechs.drive.stream.ui.settings.delegates

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.navigation.NavController
import com.bumptech.glide.Glide
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.settings.DriveFolderPickerDialog
import zechs.drive.stream.ui.settings.SettingsEvent
import zechs.drive.stream.ui.settings.SettingsUiState
import zechs.drive.stream.ui.settings.SettingsViewModel

/**
 * Delegado responsável pela seção de Perfil & Biblioteca.
 * Conforme Seção 4.10 do Plano v2.
 */
class ProfileLibrarySettingsDelegate(
    private val context: Context,
    private val binding: FragmentSettingsBinding,
    private val viewModel: SettingsViewModel,
    private val navController: NavController,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    fun setup() {
        binding.settingSelectProfile.setOnClickListener {
            navController.navigate(R.id.action_settingsFragment_to_profileSelectionFragment)
        }

        binding.settingLibraryRoot.setOnClickListener {
            val currentState = viewModel.uiState.value
            val effectiveName = currentState.libraryRootName
                ?: if (currentState.libraryRootId.isNullOrBlank()) context.getString(R.string.my_drive)
                else currentState.libraryRootId
            DriveFolderPickerDialog(
                context = context,
                lifecycleScope = lifecycleScope,
                initialSelectionName = effectiveName,
                loadPage = { parentId, pageToken ->
                    viewModel.getDriveFolderPage(parentId, pageToken)
                },
                onSelected = { rootId, rootName ->
                    viewModel.onEvent(SettingsEvent.SetLibraryRoot(rootId, rootName))
                },
                onDismiss = {
                    binding.settingLibraryRoot.post { binding.settingLibraryRoot.requestFocus() }
                }
            ).show()
        }
    }

    fun render(state: SettingsUiState) {
        state.activeProfile?.let { profile ->
            binding.tvCurrentProfileName.text = "Perfil ativo: ${profile.name}"
            val avatarRes = viewModel.getAvatarDrawableRes(profile.avatarResName)
            val avatarSource: Any? = profile.avatarUrl
                ?: if (profile.avatarResName == "avatar_caua") avatarRes
                else viewModel.profileArtCatalog.defaultAvatarForProfile(profile.id)?.url
            Glide.with(binding.ivSettingProfileAvatar)
                .load(avatarSource)
                .placeholder(avatarRes.takeIf { profile.avatarResName == "avatar_caua" } ?: R.drawable.avatar_caua)
                .error(R.drawable.avatar_caua)
                .circleCrop()
                .into(binding.ivSettingProfileAvatar)
        }

        binding.tvLibraryRootValue.text = state.libraryRootName
            ?: if (state.libraryRootId.isNullOrBlank()) context.getString(R.string.my_drive)
            else state.libraryRootId
    }
}
