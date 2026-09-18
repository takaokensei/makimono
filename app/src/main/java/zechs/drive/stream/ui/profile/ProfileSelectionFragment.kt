package zechs.drive.stream.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.databinding.FragmentProfileSelectionBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.ext.navigateSafe
import javax.inject.Inject

@AndroidEntryPoint
class ProfileSelectionFragment : BaseFragment() {

    private var _binding: FragmentProfileSelectionBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var profileManager: ProfileManager

    private lateinit var profilesAdapter: ProfilesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupActions()
        observeProfiles()
    }

    private fun setupRecyclerView() {
        profilesAdapter = ProfilesAdapter(
            onProfileSelected = { profile ->
                profileManager.setActiveProfile(profile.id)
                findNavController().navigateSafe(R.id.action_profileSelectionFragment_to_homeFragment)
            },
            onAddProfileClicked = {
                showAddProfileDialog()
            },
            onEditProfileClicked = { profile ->
                showEditProfileDialog(profile)
            }
        )

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        binding.rvProfiles.apply {
            adapter = profilesAdapter
            layoutManager = if (isLandscape) {
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            } else {
                androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
            }
        }
    }

    private fun setupActions() {
        binding.btnManageProfiles.setOnClickListener {
            profilesAdapter.isManageMode = !profilesAdapter.isManageMode
            binding.btnManageProfiles.text = if (profilesAdapter.isManageMode) "CONCLUÍDO" else "GERENCIAR PERFIS"
        }

        binding.btnProfileSettings.setOnClickListener {
            findNavController().navigateSafe(R.id.action_profileSelectionFragment_to_settingsFragment)
        }
    }

    private fun observeProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.profilesFlow.collect { profiles ->
                    val activeId = profileManager.getActiveProfile().id
                    val items = mutableListOf<ProfileUiModel>()
                    profiles.forEach { profile ->
                        items.add(ProfileUiModel.ProfileItem(profile, isActive = profile.id == activeId))
                    }
                    items.add(ProfileUiModel.AddProfileItem)
                    profilesAdapter.submitList(items) {
                        binding.rvProfiles.postDelayed({
                            val firstChild = binding.rvProfiles.layoutManager?.findViewByPosition(0)
                            firstChild?.requestFocus()
                        }, 200L)
                    }
                }
            }
        }
    }

    private fun showAddProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etNewProfileName)
        var selectedAvatar = "avatar_caua"

        val rgAvatar = dialogView.findViewById<RadioGroup>(R.id.rgAvatarChoice)
        rgAvatar?.setOnCheckedChangeListener { _, checkedId ->
            selectedAvatar = if (checkedId == R.id.rbAvatarAnime) "avatar_anime" else "avatar_caua"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Novo Perfil")
            .setView(dialogView)
            .setPositiveButton("Criar") { _, _ ->
                val name = etName?.text?.toString()?.trim()
                if (!name.isNullOrBlank()) {
                    val created = profileManager.addProfile(name, selectedAvatar)
                    profileManager.setActiveProfile(created.id)
                    findNavController().navigateSafe(R.id.action_profileSelectionFragment_to_homeFragment)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditProfileDialog(profile: UserProfile) {
        val options = arrayOf("Renomear", "Excluir Perfil")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(profile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(profile)
                    1 -> {
                        if (profileManager.getProfiles().size <= 1) {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Não é possível excluir o único perfil existente.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Excluir " + profile.name + "?")
                                .setMessage("O perfil e suas preferências locais serão removidos.")
                                .setPositiveButton("Excluir") { _, _ ->
                                    profileManager.deleteProfile(profile.id)
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(profile: UserProfile) {
        val input = EditText(requireContext()).apply {
            setText(profile.name)
            setSelection(profile.name.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Renomear Perfil")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    profileManager.updateProfile(profile.id, newName, profile.avatarResName)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
