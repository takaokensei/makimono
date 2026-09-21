package zechs.drive.stream.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.databinding.FragmentProfileSelectionBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.ext.navigateSafe
import javax.inject.Inject

@AndroidEntryPoint
class ProfileSelectionFragment : BaseFragment() {

    private var _binding: FragmentProfileSelectionBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var profileManager: ProfileManager

    @Inject
    lateinit var posterResolver: ProfileArtCatalog

    private var focusedProfileId: String? = null
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
            },
            onProfileFocused = { profile ->
                focusedProfileId = profile.id
                Glide.with(this)
                    .load(profile.backgroundUrl)
                    .placeholder(R.drawable.bg_profile_fantasy)
                    .error(R.drawable.bg_profile_fantasy)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivFantasyBg)
            }
        )

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        binding.rvProfiles.apply {
            if (isLandscape) layoutParams = layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            adapter = profilesAdapter
            layoutManager = if (isLandscape) {
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            } else {
                GridLayoutManager(requireContext(), 2)
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
                try { profileManager.awaitReady() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    binding.tvProfilesTitle.text = "Não foi possível abrir os perfis. Reinicie o app para tentar novamente."
                    binding.btnManageProfiles.isEnabled = false
                    return@repeatOnLifecycle
                }
                profileManager.profilesFlow.collect { profiles ->
                    Glide.with(this@ProfileSelectionFragment)
                        .load(profileManager.getActiveProfile().backgroundUrl)
                        .placeholder(R.drawable.bg_profile_fantasy)
                        .error(R.drawable.bg_profile_fantasy)
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivFantasyBg)
                    val activeId = profileManager.getActiveProfile().id
                    val items = mutableListOf<ProfileUiModel>()
                    profiles.forEach { profile ->
                        items.add(ProfileUiModel.ProfileItem(profile, isActive = profile.id == activeId))
                    }
                    items.add(ProfileUiModel.AddProfileItem)
                    profilesAdapter.submitList(items) {
                        val selectionBinding = _binding ?: return@submitList
                        selectionBinding.rvProfiles.doOnPreDraw {
                            if (_binding == null) return@doOnPreDraw
                            val targetPos = items.indexOfFirst {
                                it is ProfileUiModel.ProfileItem && it.profile.id == (focusedProfileId ?: activeId)
                            }.takeIf { it != -1 } ?: 0
                            val child = binding.rvProfiles.layoutManager?.findViewByPosition(targetPos)
                            child?.requestFocus()
                        }
                    }
                }
            }
        }
    }

    private fun showAddProfileDialog() {
        ProfileEditorDialog(this, profileManager, posterResolver).show(null)
    }

    private fun showEditProfileDialog(profile: UserProfile) {
        val options = arrayOf("Editar nome, imagens e status", "Excluir Perfil")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(profile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> ProfileEditorDialog(this, profileManager, posterResolver).show(profile)
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
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        try { profileManager.deleteProfile(profile.id) }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) {
                                            android.widget.Toast.makeText(context, "Falha ao excluir perfil", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancelar", null)
                                .show().also { dialog -> dialog.window?.decorView?.let(TvFocusRing::install) }
                        }
                    }
                }
            }
            .show().also { dialog -> dialog.window?.decorView?.let(TvFocusRing::install) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
