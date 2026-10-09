package zechs.drive.stream.ui.settings

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.databinding.ItemThemePreviewBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.BackupSerializer
import zechs.drive.stream.utils.MotionPolicy
import zechs.drive.stream.utils.MotionPreference
import zechs.drive.stream.utils.SingleTapActivation
import zechs.drive.stream.utils.SubtitleStyle
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.ext.resolveThemeColor
import zechs.drive.stream.utils.state.Resource
import java.io.File

@AndroidEntryPoint
class SettingsFragment : BaseFragment() {

    companion object {
        const val TAG = "SettingsFragment"
        private const val STATE_SETTINGS_CATEGORY = "settings_category"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var tvFocusEnabled = false
    private var reduceMotionEnabled = false
    private var selectedSettingsCategory = 0
    private val categoryButtons = mutableListOf<MaterialButton>()

    private data class SettingsCategory(
        val labelRes: Int,
        val sectionId: Int,
        val rowIds: List<Int>
    )

    private val mainViewModel by activityViewModels<MainViewModel>()
    private val viewModel by viewModels<SettingsViewModel>()

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
        selectedSettingsCategory = savedInstanceState?.getInt(STATE_SETTINGS_CATEGORY, 0)
            ?: viewModel.uiState.value.selectedCategory

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.title = getString(R.string.app_settings)
        binding.toolbar.navigationContentDescription = getString(R.string.back)

        setupUserProfileSection()
        setupLibraryRootSetting()
        setupThemeMenu()
        setupDefaultPlayerMenu()
        setupSubtitleAppearanceSetting()
        setupMotionPreference()
        refreshConfigurableSummaries()
        setupStorageAndCacheSetting()
        setupCheckForUpdates()
        setupMalIntegration()
        setupPlaybackExperience()
        setupBackupRestore()
        setupLogOut()
        setupCategoryNavigation()
        setupTvFocus()
        observeUiStateAndEffects()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SETTINGS_CATEGORY, selectedSettingsCategory)
        super.onSaveInstanceState(outState)
    }

    private fun observeUiStateAndEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderUiState(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        handleEffect(effect)
                    }
                }
            }
        }
    }

    private fun renderUiState(state: SettingsUiState) {
        // Active Profile
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

        // Library Root
        binding.tvLibraryRootValue.text = state.libraryRootName
            ?: if (state.libraryRootId.isNullOrBlank()) getString(R.string.my_drive)
            else state.libraryRootId

        // Motion preference
        binding.tvMotionPreferenceValue.text = when (state.motionPreference) {
            MotionPreference.REDUCED -> getString(R.string.motion_reduced)
            MotionPreference.SYSTEM -> getString(R.string.motion_follows_device)
        }
        reduceMotionEnabled = MotionPolicy.shouldReduceMotion(
            state.motionPreference,
            MotionPolicy.areSystemAnimationsEnabled()
        )
        if (reduceMotionEnabled) {
            binding.root.findFocus()?.let { focused ->
                focused.animate().cancel()
                focused.scaleX = 1f
                focused.scaleY = 1f
                focused.translationZ = 0f
            }
        }

        // Subtitle Summary
        if (state.subtitleSummary.isNotBlank()) {
            binding.tvSubtitleSizeValue.text = state.subtitleSummary
        }

        // Cache Summary
        if (state.cacheSizeText.isNotBlank()) {
            binding.tvCacheSizeValue.text = state.cacheSizeText
        }

        // MAL integration
        val activeTint = requireContext().resolveThemeColor(R.attr.colorAccentPrimary)
        val inactiveTint = requireContext().resolveThemeColor(R.attr.colorTextSecondary)
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

        // Playback switches
        binding.switchAutoSkip.isChecked = state.isAutoSkipEnabled
        binding.switchGestures.isChecked = state.isGesturesEnabled
        binding.switchAutoFrameRate?.isChecked = state.isAutoFrameRateEnabled
        binding.switchMatchDynamicRange?.isChecked = state.isMatchDynamicRangeEnabled
        binding.switchPredictiveBuffering?.isChecked = state.isPredictiveBufferingEnabled
    }

    private fun handleEffect(effect: SettingsUiEffect) {
        when (effect) {
            is SettingsUiEffect.ShowSnackbar -> showSnackBar(effect.message)
            is SettingsUiEffect.RestartApp -> {
                val activity = requireActivity()
                activity.finish()
                activity.startActivity(activity.intent)
            }
            is SettingsUiEffect.OpenMalAuthDialog -> {
                val authDialog = MalAuthDialog(
                    onCodeReceived = { code, verifier ->
                        viewModel.onEvent(SettingsEvent.ExchangeMalToken(code, verifier))
                    },
                    clientId = effect.clientId
                )
                authDialog.show(parentFragmentManager, MalAuthDialog.TAG)
            }
        }
    }

    private fun setupCategoryNavigation() {
        val categories = listOf(
            SettingsCategory(
                R.string.settings_category_profile,
                R.id.settingsSectionProfile,
                listOf(R.id.settingSelectProfile, R.id.settingLibraryRoot)
            ),
            SettingsCategory(
                R.string.settings_category_appearance,
                R.id.settingsSectionProfile,
                listOf(R.id.settingSelectTheme, R.id.settingReduceMotion)
            ),
            SettingsCategory(
                R.string.settings_category_playback,
                R.id.settingsSectionPlayback,
                listOf(
                    R.id.settingDefaultPlayer,
                    R.id.settingSubtitleSize,
                    R.id.settingAutoSkipToggle,
                    R.id.settingGesturesToggle,
                    R.id.settingAutoFrameRateToggle,
                    R.id.settingMatchDynamicRangeToggle,
                    R.id.settingPredictiveBufferingToggle
                )
            ),
            SettingsCategory(
                R.string.settings_category_integrations,
                R.id.settingsSectionIntegrations,
                listOf(
                    R.id.settingMalAccount,
                    R.id.settingMalTransferToTv,
                    R.id.settingMalSyncToggle
                )
            ),
            SettingsCategory(
                R.string.settings_category_data,
                R.id.settingsSectionData,
                listOf(
                    R.id.settingClearCache,
                    R.id.settingCheckForUpdate,
                    R.id.settingExportBackup,
                    R.id.settingImportBackup,
                    R.id.settingLogOut
                )
            )
        )
        selectedSettingsCategory = selectedSettingsCategory.coerceIn(categories.indices)
        val categoryBar = binding.settingsCategoryBar
        categoryBar.removeAllViews()
        categoryButtons.clear()

        categories.forEachIndexed { index, category ->
            val button = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = getString(category.labelRes)
                isAllCaps = false
                minHeight = resources.getDimensionPixelSize(R.dimen.settings_category_min_height)
                minimumHeight = minHeight
                insetTop = 0
                insetBottom = 0
                cornerRadius = resources.getDimensionPixelSize(R.dimen.settings_category_corner_radius)
                strokeWidth = resources.getDimensionPixelSize(R.dimen.settings_category_stroke_width)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { selectSettingsCategory(index, categories) }
                SingleTapActivation.install(this, disallowParentIntercept = false)
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus && !reduceMotionEnabled) {
                        view.animate().scaleX(1.025f).scaleY(1.025f).setDuration(100L).start()
                    } else {
                        view.animate().cancel()
                        view.scaleX = 1f
                        view.scaleY = 1f
                    }
                }
            }
            val margin = resources.getDimensionPixelSize(R.dimen.settings_category_spacing)
            val isVerticalMenu = categoryBar.orientation == LinearLayout.VERTICAL
            categoryBar.addView(button, LinearLayout.LayoutParams(
                if (isVerticalMenu) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.settings_category_min_height)
            ).apply {
                if (isVerticalMenu) {
                    topMargin = margin / 2
                    bottomMargin = margin / 2
                } else {
                    marginStart = if (index == 0) 0 else margin
                    marginEnd = margin
                }
            })
            categoryButtons += button
        }
        applySettingsCategory(categories)
    }

    private fun selectSettingsCategory(index: Int, categories: List<SettingsCategory>) {
        if (index !in categories.indices) return
        selectedSettingsCategory = index
        viewModel.onEvent(SettingsEvent.SelectCategory(index))
        applySettingsCategory(categories)
        binding.settingsScroll.smoothScrollTo(0, 0)
        updateSettingsFocusOrder()
        if (tvFocusEnabled) {
            val firstRow = categories[index].rowIds.firstOrNull()?.let { id ->
                binding.root.findViewById<View>(id)
            }
            firstRow?.post { firstRow.requestFocus() }
        }
    }

    private fun applySettingsCategory(categories: List<SettingsCategory>) {
        val visibleCategory = categories.getOrNull(selectedSettingsCategory) ?: return
        val allRowIds = categories.flatMap(SettingsCategory::rowIds).toSet()
        allRowIds.forEach { id -> binding.root.findViewById<View>(id)?.isVisible = id in visibleCategory.rowIds }
        listOf(
            R.id.settingsSectionProfile,
            R.id.settingsSectionIntegrations,
            R.id.settingsSectionPlayback,
            R.id.settingsSectionData,
            R.id.settingsSectionSession
        ).forEach { id ->
            binding.root.findViewById<View>(id)?.isVisible = id == visibleCategory.sectionId ||
                (id == R.id.settingsSectionSession && selectedSettingsCategory == 4)
        }
        binding.settingsSectionProfile.setText(
            if (selectedSettingsCategory == 1) R.string.settings_section_appearance
            else R.string.settings_section_profile
        )
        categoryButtons.forEachIndexed { index, button ->
            val selected = index == selectedSettingsCategory
            button.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor(if (selected) "#2630D5E8" else "#121A2633")
            )
            button.strokeColor = ColorStateList.valueOf(
                Color.parseColor(if (selected) "#7430D5E8" else "#3B71849A")
            )
            button.setTextColor(Color.parseColor(if (selected) "#FF45DDF0" else "#FFE2EAF2"))
            button.contentDescription = getString(
                R.string.settings_category_accessibility,
                getString(categories[index].labelRes),
                if (selected) getString(R.string.settings_category_selected_suffix) else ""
            )
        }
    }

    private fun setupTvFocus() {
        val isTv = zechs.drive.stream.utils.DeviceUi.isTenFootExperience(requireContext())
        if (!isTv && resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        tvFocusEnabled = true

        TvFocusRing.install(binding.root)

        val rows = allSettingsRows()
        TvAccessibilityLayout.adaptSettingsRows(requireContext(), rows)

        rows.forEach { row ->
            row.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    viewModel.onEvent(SettingsEvent.SaveFocus(view.id))
                }
                if (reduceMotionEnabled) {
                    view.animate().cancel()
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.translationZ = 0f
                    return@setOnFocusChangeListener
                }
                view.animate()
                    .scaleX(if (hasFocus) 1.015f else 1f)
                    .scaleY(if (hasFocus) 1.015f else 1f)
                    .translationZ(if (hasFocus) 6f else 0f)
                    .setDuration(120L)
                    .start()
            }
        }
        updateSettingsFocusOrder(rows)

        binding.switchMalSync.isFocusable = false
        binding.switchAutoSkip.isFocusable = false
        binding.switchGestures.isFocusable = false
        binding.switchAutoFrameRate?.isFocusable = false
        binding.switchMatchDynamicRange?.isFocusable = false
        binding.switchPredictiveBuffering?.isFocusable = false
        binding.settingSelectProfile.apply { if (isTv) isFocusableInTouchMode = true }
        categoryButtons.forEach { it.isFocusableInTouchMode = true }
        categoryButtons.firstOrNull()?.let { button -> button.post { button.requestFocus() } }
    }

    private fun allSettingsRows(): List<View> = listOfNotNull(
        binding.settingSelectProfile,
        binding.settingLibraryRoot,
        binding.settingSelectTheme,
        binding.settingReduceMotion,
        binding.settingDefaultPlayer,
        binding.settingSubtitleSize,
        binding.settingClearCache,
        binding.settingCheckForUpdate,
        binding.settingMalAccount,
        binding.settingMalTransferToTv,
        binding.settingMalSyncToggle,
        binding.settingAutoSkipToggle,
        binding.settingGesturesToggle,
        binding.settingAutoFrameRateToggle,
        binding.settingMatchDynamicRangeToggle,
        binding.settingPredictiveBufferingToggle,
        binding.settingExportBackup,
        binding.settingImportBackup,
        binding.settingLogOut
    )

    private fun updateSettingsFocusOrder(rows: List<View>? = null) {
        if (!tvFocusEnabled || _binding == null) return
        val candidates = rows ?: allSettingsRows()
        val visibleRows = candidates.filter { it.isShown && it.isFocusable }
        val backButton = findToolbarBackButton()
        val verticalMenu = binding.settingsCategoryBar.orientation == LinearLayout.VERTICAL
        categoryButtons.forEachIndexed { index, button ->
            if (verticalMenu) {
                button.nextFocusLeftId = button.id
                button.nextFocusRightId = visibleRows.firstOrNull()?.id ?: button.id
                button.nextFocusUpId = categoryButtons.getOrNull(index - 1)?.id
                    ?: backButton?.id
                    ?: button.id
                button.nextFocusDownId = categoryButtons.getOrNull(index + 1)?.id ?: button.id
            } else {
                button.nextFocusLeftId = categoryButtons.getOrNull(index - 1)?.id ?: button.id
                button.nextFocusRightId = categoryButtons.getOrNull(index + 1)?.id ?: button.id
                button.nextFocusDownId = visibleRows.firstOrNull()?.id ?: button.id
                button.nextFocusUpId = backButton?.id ?: button.id
            }
        }
        if (visibleRows.isNotEmpty()) {
            backButton?.nextFocusDownId = categoryButtons.firstOrNull()?.id ?: visibleRows.first().id
        }
        visibleRows.forEachIndexed { index, row ->
            row.nextFocusUpId = visibleRows.getOrNull(index - 1)?.id
                ?: categoryButtons.getOrNull(selectedSettingsCategory)?.id
                ?: backButton?.id?.takeIf { it != View.NO_ID }
                ?: row.id
            row.nextFocusDownId = visibleRows.getOrNull(index + 1)?.id ?: row.id
            if (verticalMenu) {
                row.nextFocusLeftId = categoryButtons.getOrNull(selectedSettingsCategory)?.id ?: row.id
                row.nextFocusRightId = row.id
            }
        }
    }

    private fun findToolbarBackButton(): View? = (0 until binding.toolbar.childCount)
        .asSequence()
        .map(binding.toolbar::getChildAt)
        .firstOrNull { it.contentDescription == binding.toolbar.navigationContentDescription }
        ?.also { if (it.id == View.NO_ID) it.id = View.generateViewId() }

    private fun setupUserProfileSection() {
        binding.settingSelectProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_profileSelectionFragment)
        }
    }

    private fun setupLibraryRootSetting() {
        binding.settingLibraryRoot.setOnClickListener {
            val currentState = viewModel.uiState.value
            val effectiveName = currentState.libraryRootName
                ?: if (currentState.libraryRootId.isNullOrBlank()) getString(R.string.my_drive)
                else currentState.libraryRootId
            DriveFolderPickerDialog(
                context = requireContext(),
                lifecycleScope = viewLifecycleOwner.lifecycleScope,
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

    private fun setupThemeMenu() {
        binding.settingSelectTheme.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_theme_selector, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.llThemeContainer)

            val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setView(dialogView)
                .create()

            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
                val targetIndex = mainViewModel.currentThemeIndex.coerceAtLeast(0)
                container.getChildAt(targetIndex)?.requestFocus()
            }

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
                        viewModel.onEvent(SettingsEvent.SetTheme(theme))
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
            val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(getString(R.string.default_player))
                .setSingleChoiceItems(
                    players.toTypedArray(),
                    mainViewModel.currentPlayerIndex.value
                ) { d, item ->
                    val player = when (item) {
                        VideoPlayer.EXO_PLAYER.value -> VideoPlayer.EXO_PLAYER
                        VideoPlayer.MPV.value -> VideoPlayer.MPV
                        else -> throw IllegalArgumentException("Unknown default player")
                    }
                    mainViewModel.setPlayer(player)
                    viewModel.onEvent(SettingsEvent.SetPlayer(player))
                    binding.tvPlayerCurrentValue.text = players[item]
                    d.dismiss()
                }
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
            }
            dialog.show()
        }
    }

    private fun refreshConfigurableSummaries() {
        val theme = AppTheme.fromValue(mainViewModel.currentThemeIndex)
        binding.tvThemeCurrentValue.text = theme.displayName
        binding.tvPlayerCurrentValue.text = when (mainViewModel.currentPlayerIndex.value) {
            VideoPlayer.MPV.value -> getString(R.string.mpv)
            else -> getString(R.string.exoplayer)
        }
    }

    private fun setupSubtitleAppearanceSetting() {
        binding.settingSubtitleSize.setOnClickListener {
            val initial = SubtitleStyle()
            zechs.drive.stream.ui.player.SubtitleStyleDialog.show(requireContext(), initial) { updated ->
                viewModel.onEvent(SettingsEvent.SaveSubtitleStyle(updated))
            }
        }
    }

    private fun setupMotionPreference() {
        binding.settingReduceMotion.setOnClickListener {
            val options = arrayOf(
                getString(R.string.motion_follows_device),
                getString(R.string.motion_reduced)
            )
            val current = viewModel.uiState.value.motionPreference
            val selected = if (current == MotionPreference.REDUCED) 1 else 0
            val dialog = MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_DriveStream_Dialog
            )
                .setTitle(R.string.reduce_motion)
                .setSingleChoiceItems(options, selected) { dialogInterface, index ->
                    val preference = if (index == 1) MotionPreference.REDUCED else MotionPreference.SYSTEM
                    viewModel.onEvent(SettingsEvent.SaveMotionPreference(preference))
                    dialogInterface.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.window?.let { window -> TvFocusRing.install(window.decorView) }
            }
            dialog.show()
        }
    }

    private fun setupStorageAndCacheSetting() {
        fun updateCacheDisplay() {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val sizeBytes = calculateTotalCacheBytes()
                withContext(Dispatchers.Main) {
                    viewModel.onEvent(SettingsEvent.UpdateCacheSize(sizeBytes))
                }
            }
        }

        updateCacheDisplay()

        binding.settingClearCache.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle("Limpar Cache de Mídia e Legendas?")
                .setMessage("Isso liberará espaço em disco removendo miniaturas temporárias e legendas baixadas. Seus perfis, favoritos e histórico de reprodução NÃO serão apagados.")
                .setPositiveButton("Limpar") { d, _ ->
                    d.dismiss()
                    showSnackBar("Limpando cache...")
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            Glide.get(requireContext().applicationContext).clearDiskCache()
                            val subDir = File(requireContext().filesDir, "subtitles")
                            if (subDir.exists()) subDir.deleteRecursively()
                            val subCache = File(requireContext().cacheDir, "subtitles")
                            if (subCache.exists()) subCache.deleteRecursively()

                            requireContext().cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            requireContext().externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }

                            withContext(Dispatchers.Main) {
                                Glide.get(requireContext().applicationContext).clearMemory()
                                updateCacheDisplay()
                                showSnackBar("Cache limpo com sucesso! Espaço liberado.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error clearing cache", e)
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
            addDir(requireContext().cacheDir)
            addDir(requireContext().externalCacheDir)
            addDir(File(requireContext().filesDir, "subtitles"))
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
        binding.settingMalSyncToggle.setOnClickListener {
            binding.switchMalSync.toggle()
        }
        binding.switchMalSync.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetMalSync(isChecked))
        }

        binding.settingMalAccount.setOnClickListener {
            if (viewModel.uiState.value.isMalLoggedIn) {
                val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
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
            } else {
                val clientId = viewModel.getMalClientId()
                val clientSecret = viewModel.getMalClientSecret()
                if (clientId.isBlank() || clientSecret.isBlank()) {
                    val fields = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 0, 48, 0)
                    }
                    val clientIdInput = android.widget.EditText(requireContext()).apply {
                        hint = "Client ID"
                        setText(clientId)
                        isSingleLine = true
                        setBackgroundResource(R.drawable.glass_input_bg)
                        setPadding(32, 24, 32, 24)
                    }
                    val clientSecretInput = android.widget.EditText(requireContext()).apply {
                        hint = "Client Secret"
                        setText(clientSecret)
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        isSingleLine = true
                        setBackgroundResource(R.drawable.glass_input_bg)
                        setPadding(32, 24, 32, 24)
                    }
                    fields.addView(clientIdInput)
                    fields.addView(clientSecretInput)
                    val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
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
                    val authDialog = MalAuthDialog(
                        onCodeReceived = { code, verifier ->
                            viewModel.onEvent(SettingsEvent.ExchangeMalToken(code, verifier))
                        },
                        clientId = clientId
                    )
                    authDialog.show(parentFragmentManager, MalAuthDialog.TAG)
                }
            }
        }
    }

    private fun setupPlaybackExperience() {
        binding.settingAutoSkipToggle.setOnClickListener {
            binding.switchAutoSkip.toggle()
        }
        binding.switchAutoSkip.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetAutoSkip(isChecked))
        }

        binding.settingGesturesToggle.setOnClickListener {
            binding.switchGestures.toggle()
        }
        binding.switchGestures.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetGestures(isChecked))
        }

        binding.settingAutoFrameRateToggle?.setOnClickListener {
            binding.switchAutoFrameRate?.toggle()
        }
        binding.switchAutoFrameRate?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetAutoFrameRate(isChecked))
        }

        binding.settingMatchDynamicRangeToggle?.setOnClickListener {
            binding.switchMatchDynamicRange?.toggle()
        }
        binding.switchMatchDynamicRange?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetMatchDynamicRange(isChecked))
        }

        binding.settingPredictiveBufferingToggle?.setOnClickListener {
            binding.switchPredictiveBuffering?.toggle()
        }
        binding.switchPredictiveBuffering?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onEvent(SettingsEvent.SetPredictiveBuffering(isChecked))
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
                val json = viewModel.exportBackupJson()
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
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
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

    private fun executeImport(uri: android.net.Uri, mode: BackupSerializer.ImportMode) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val json = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
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
            val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_DriveStream_Dialog)
                .setTitle(getString(R.string.log_out_dialog_title))
                .setMessage(getString(R.string.log_out_dialog_message))
                .setNegativeButton(getString(R.string.no)) { d, _ -> d.dismiss() }
                .setPositiveButton(getString(R.string.yes)) { d, _ ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        tvFocusEnabled = false
        _binding = null
    }
}
