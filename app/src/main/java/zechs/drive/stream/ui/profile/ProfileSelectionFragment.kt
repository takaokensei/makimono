package zechs.drive.stream.ui.profile

import android.os.Bundle
import android.view.KeyEvent
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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.data.repository.DriveRepository
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

    @Inject
    lateinit var sessionManager: zechs.drive.stream.utils.SessionManager

    @Inject
    lateinit var driveRepository: DriveRepository

    @Inject
    lateinit var mediaPreloadManager: zechs.drive.stream.data.repository.MediaPreloadManager

    private var initialCreateShown = false
    private var focusedProfileId: String? = null
    private var bgDebounceJob: Job? = null
    private lateinit var profilesAdapter: ProfilesAdapter
    private var activeBgIsA = true

    private fun transitionBackground(url: String?, reduceMotion: Boolean) {
        val currentView = if (activeBgIsA) binding.ivFantasyBgA else binding.ivFantasyBgB
        val nextView = if (activeBgIsA) binding.ivFantasyBgB else binding.ivFantasyBgA

        if (reduceMotion) {
            Glide.with(this@ProfileSelectionFragment)
                .load(url)
                .placeholder(R.drawable.bg_profile_fantasy)
                .error(R.drawable.bg_profile_fantasy)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(nextView)
            nextView.alpha = 1f
            currentView.alpha = 0f
            activeBgIsA = !activeBgIsA
            return
        }

        Glide.with(this@ProfileSelectionFragment)
            .load(url)
            .placeholder(R.drawable.bg_profile_fantasy)
            .error(R.drawable.bg_profile_fantasy)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (_binding == null) return false
                    nextView.animate().cancel()
                    currentView.animate().cancel()
                    nextView.alpha = 0f
                    nextView.setImageDrawable(resource)
                    nextView.animate()
                        .alpha(1f)
                        .setDuration(350L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    currentView.animate()
                        .alpha(0f)
                        .setDuration(350L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .withEndAction {
                            currentView.alpha = 0f
                        }
                        .start()
                    activeBgIsA = !activeBgIsA
                    return true
                }
            })
            .into(nextView)
    }

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
        setupBackNavigation()
        observeProfiles()
    }

    private fun setupRecyclerView() {
        profilesAdapter = ProfilesAdapter(
            onProfileSelected = { profile ->
                showProfileWelcomeScreen(profile)
            },
            onAddProfileClicked = {
                showAddProfileDialog()
            },
            onEditProfileClicked = { profile ->
                showEditProfileDialog(profile)
            },
            onProfileFocused = { profile ->
                if (focusedProfileId != profile.id) {
                    focusedProfileId = profile.id
                    bgDebounceJob?.cancel()
                    bgDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(120L)
                        if (!isAdded || _binding == null) return@launch
                        transitionBackground(profile.backgroundUrl, profilesAdapter.reduceMotion)
                    }
                }
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
        binding.btnLogOut.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val targetPos = profilesAdapter.currentList.indexOfFirst {
                    it is ProfileUiModel.ProfileItem && it.profile.id == focusedProfileId
                }.takeIf { it != -1 } ?: 0
                val child = binding.rvProfiles.layoutManager?.findViewByPosition(targetPos)
                if (child != null) {
                    child.requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }

        binding.btnLogOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(R.string.profile_logout_confirm_title)
                .setMessage(R.string.profile_logout_confirm_message)
                .setPositiveButton(R.string.profile_logout_action_confirm) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            driveRepository.logoutCurrentDevice()
                            Glide.get(requireContext().applicationContext).clearMemory()
                            findNavController().navigateSafe(R.id.action_profileSelectionFragment_to_signInFragment)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            findNavController().navigateSafe(R.id.action_profileSelectionFragment_to_signInFragment)
                        }
                    }
                }
                .setNegativeButton(R.string.profile_cancel_action, null)
                .show().also { dialog -> dialog.window?.decorView?.let(TvFocusRing::install) }
        }
    }

    private fun observeProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try { profileManager.awaitReady() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    binding.tvProfilesTitle.setText(R.string.profile_load_error)
                    binding.btnLogOut.isEnabled = false
                    return@repeatOnLifecycle
                }
                profileManager.profilesFlow.collect { profiles ->
                    binding.tvProfilesTitle.setText(if (profiles.isEmpty())
                        R.string.profile_create_first_title else R.string.profile_selection_title)
                    if (profiles.isEmpty() && !initialCreateShown) {
                        initialCreateShown = true
                        binding.root.post {
                            if (_binding != null && isAdded) {
                                showAddProfileDialog(required = profileManager.isInitialProfileSetupPending())
                            }
                        }
                    } else if (profileManager.isInitialProfileSetupPending() && !initialCreateShown) {
                        initialCreateShown = true
                        val setupProfile = profileManager.getInitialProfileSetupTarget()
                        binding.root.post {
                            if (_binding != null && isAdded && setupProfile != null) {
                                showRequiredProfileSetup(setupProfile)
                            }
                        }
                    }
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
                        .format(DecodeFormat.PREFER_ARGB_8888)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivFantasyBgA)
                    binding.ivFantasyBgA.alpha = 1f
                    binding.ivFantasyBgB.alpha = 0f
                    activeBgIsA = true
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

    private fun showAddProfileDialog(required: Boolean = false) {
        val firstProfile = profileManager.getProfiles().isEmpty()
        ProfileEditorDialog(this, profileManager, posterResolver).show(null, required) { saved ->
            if ((firstProfile || required) && isAdded && _binding != null) showProfileWelcomeScreen(saved)
        }
    }

    private fun showRequiredProfileSetup(profile: UserProfile) {
        ProfileEditorDialog(this, profileManager, posterResolver).show(profile, required = true) { saved ->
            if (isAdded && _binding != null) showProfileWelcomeScreen(saved)
        }
    }

    private fun showEditProfileDialog(profile: UserProfile) {
        ProfileEditorDialog(this, profileManager, posterResolver).show(profile)
    }

    private var isTransitioningToHome = false

    private fun showProfileWelcomeScreen(profile: UserProfile) {
        if (isTransitioningToHome) return
        isTransitioningToHome = true

        // 1. Immediately activate profile in data layer
        profileManager.setActiveProfile(profile.id)
        mediaPreloadManager.startPreload()

        // 2. Disable interactions on other views
        binding.btnLogOut.isEnabled = false
        binding.rvProfiles.isEnabled = false

        // 3. Prepare background image
        val bgUrl = profile.backgroundUrl?.takeIf { it.isNotBlank() }
        Glide.with(this)
            .load(bgUrl ?: R.drawable.bg_profile_fantasy)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(binding.ivWelcomeBg)

        // 4. Prepare circular avatar
        val avatarUrl = profile.avatarUrl?.takeIf { it.isNotBlank() }
        Glide.with(this)
            .load(avatarUrl ?: profileManager.getAvatarDrawableRes(profile.avatarResName))
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(binding.ivWelcomeAvatar)

        // 5. Welcome texts & fun loading phrase
        binding.tvWelcomeGreeting.text = getString(R.string.profile_welcome_greeting, profile.name)
        binding.tvWelcomeSubtitle.text = getRandomLoadingPhrase()

        // 6. Smoothly animate in the welcome screen overlay
        binding.layoutProfileWelcome.apply {
            visibility = View.VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(220L)
                .start()
        }

        binding.welcomeContentCenter.apply {
            scaleX = 0.90f
            scaleY = 0.90f
            alpha = 0f
            animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1f)
                .setDuration(350L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        // 7. Wait ~1.5 seconds for a polished feel, then navigate
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500L)
            if (!isAdded || _binding == null) return@launch
            findNavController().navigateSafe(R.id.action_profileSelectionFragment_to_homeFragment)
        }
    }

    private fun getRandomLoadingPhrase(): String {
        val phrases = listOf(
            R.string.profile_welcome_phrase_power,
            R.string.profile_welcome_phrase_dragonballs,
            R.string.profile_welcome_phrase_catalog,
            R.string.profile_welcome_phrase_chakra,
            R.string.profile_welcome_phrase_sync
        )
        return getString(phrases.random())
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isTransitioningToHome) {
                        return
                    }
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bgDebounceJob?.cancel()
        bgDebounceJob = null
        _binding = null
    }
}
