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
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.MotionPreference
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.TvAccessibilityLayout
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

    @Inject
    lateinit var appSettings: AppSettings

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
            },
            profileArtCatalog = posterResolver
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
        TvAccessibilityLayout.adaptProfileActions(
            requireContext(),
            listOf(binding.btnManageProfiles, binding.btnProfileSettings)
        )
        binding.btnManageProfiles.setOnClickListener {
            profilesAdapter.isManageMode = !profilesAdapter.isManageMode
            binding.btnManageProfiles.setText(
                if (profilesAdapter.isManageMode) R.string.profile_manage_done else R.string.profile_manage_action
            )
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
                    binding.tvProfilesTitle.setText(R.string.profile_load_error)
                    binding.btnManageProfiles.isEnabled = false
                    return@repeatOnLifecycle
                }
                profileManager.profilesFlow.collect { profiles ->
                    val activeProfile = profileManager.getActiveProfile()
                    val motionPreference = try {
                        appSettings.fetchMotionPreference(activeProfile.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        MotionPreference.SYSTEM
                    }
                    profilesAdapter.reduceMotion = MotionPolicy.shouldReduceMotion(
                        motionPreference,
                        MotionPolicy.areSystemAnimationsEnabled()
                    )
                    Glide.with(this@ProfileSelectionFragment)
                        .load(activeProfile.backgroundUrl)
                        .placeholder(R.drawable.bg_profile_fantasy)
                        .error(R.drawable.bg_profile_fantasy)
                        .format(DecodeFormat.PREFER_RGB_565)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivFantasyBg)
                    val activeId = activeProfile.id
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
        val options = arrayOf(
            getString(R.string.profile_edit_menu_action),
            getString(R.string.profile_delete_menu_action)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(profile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> ProfileEditorDialog(this, profileManager, posterResolver).show(profile)
                    1 -> {
                        if (profileManager.getProfiles().size <= 1) {
                            android.widget.Toast.makeText(
                                requireContext(),
                                getString(R.string.profile_delete_only_profile),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.profile_delete_title, profile.name))
                                .setMessage(R.string.profile_delete_message)
                                .setPositiveButton(R.string.profile_delete_action) { _, _ ->
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        try {
                                            when (profileManager.deleteProfile(profile.id)) {
                                                null -> Unit
                                                zechs.drive.stream.utils.ProfileManagementPolicy.DeleteBlockReason.LAST_ADMINISTRATOR ->
                                                    android.widget.Toast.makeText(context, R.string.profile_last_admin_required, android.widget.Toast.LENGTH_LONG).show()
                                                zechs.drive.stream.utils.ProfileManagementPolicy.DeleteBlockReason.LAST_PROFILE ->
                                                    android.widget.Toast.makeText(context, R.string.profile_last_required, android.widget.Toast.LENGTH_SHORT).show()
                                                zechs.drive.stream.utils.ProfileManagementPolicy.DeleteBlockReason.PROFILE_NOT_FOUND ->
                                                    android.widget.Toast.makeText(context, R.string.profile_not_found, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) {
                                            android.widget.Toast.makeText(context, R.string.profile_delete_error, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.profile_cancel_action, null)
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
