package zechs.drive.stream.ui.settings

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.settings.delegates.AppearanceSettingsDelegate
import zechs.drive.stream.ui.settings.delegates.DataSessionSettingsDelegate
import zechs.drive.stream.ui.settings.delegates.IntegrationsSettingsDelegate
import zechs.drive.stream.ui.settings.delegates.PlaybackSettingsDelegate
import zechs.drive.stream.ui.settings.delegates.ProfileLibrarySettingsDelegate
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.SingleTapActivation
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.TvFocusRing

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

    private lateinit var profileLibraryDelegate: ProfileLibrarySettingsDelegate
    private lateinit var appearanceDelegate: AppearanceSettingsDelegate
    private lateinit var playbackDelegate: PlaybackSettingsDelegate
    private lateinit var integrationsDelegate: IntegrationsSettingsDelegate
    private lateinit var dataSessionDelegate: DataSessionSettingsDelegate

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            dataSessionDelegate.exportBackupToUri(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            dataSessionDelegate.promptImportModeAndExecute(uri)
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

        initDelegates()
        profileLibraryDelegate.setup()
        appearanceDelegate.setup()
        playbackDelegate.setup()
        integrationsDelegate.setup()
        dataSessionDelegate.setup()

        setupCategoryNavigation()
        setupTvFocus()
        observeUiStateAndEffects()
    }

    private fun initDelegates() {
        profileLibraryDelegate = ProfileLibrarySettingsDelegate(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            navController = findNavController(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )

        appearanceDelegate = AppearanceSettingsDelegate(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            onReduceMotionChanged = { reduced ->
                reduceMotionEnabled = reduced
                if (reduced) {
                    binding.root.findFocus()?.let { focused ->
                        focused.animate().cancel()
                        focused.scaleX = 1f
                        focused.scaleY = 1f
                        focused.translationZ = 0f
                    }
                }
            }
        )

        playbackDelegate = PlaybackSettingsDelegate(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            mainViewModel = mainViewModel
        )

        integrationsDelegate = IntegrationsSettingsDelegate(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            fragmentManager = parentFragmentManager,
            showSnackBar = ::showSnackBar
        )

        dataSessionDelegate = DataSessionSettingsDelegate(
            context = requireContext(),
            binding = binding,
            viewModel = viewModel,
            mainViewModel = mainViewModel,
            lifecycleOwner = viewLifecycleOwner,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            exportBackupLauncher = exportBackupLauncher,
            importBackupLauncher = importBackupLauncher,
            showSnackBar = ::showSnackBar
        )
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
        profileLibraryDelegate.render(state)
        appearanceDelegate.render(state)
        playbackDelegate.render(state)
        integrationsDelegate.render(state)
        dataSessionDelegate.render(state)
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
                integrationsDelegate.openMalAuthDialog(effect.clientId)
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
                        view.animate().scaleX(1.025f).scaleY(1.025f).setDuration(view.resources.getInteger(R.integer.motion_focus).toLong()).start()
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
        val isTv = DeviceUi.isTenFootExperience(requireContext())
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
                    .setDuration(view.resources.getInteger(R.integer.motion_focus).toLong())
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

    private fun showSnackBar(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvFocusEnabled = false
        _binding = null
    }
}
