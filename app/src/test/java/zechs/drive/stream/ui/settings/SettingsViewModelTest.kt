package zechs.drive.stream.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.utils.AppSettings
import zechs.drive.stream.utils.BackupSerializer
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.MotionPreference
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.SubtitleStyle

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val profileManager = mockk<ProfileManager>(relaxed = true)
    private val appSettings = mockk<AppSettings>(relaxed = true)
    private val malSessionManager = mockk<MalSessionManager>(relaxed = true)
    private val malRepository = mockk<MalRepository>(relaxed = true)
    private val driveRepository = mockk<DriveRepository>(relaxed = true)
    private val backupSerializer = mockk<BackupSerializer>(relaxed = true)
    private val profileArtCatalog = mockk<ProfileArtCatalog>(relaxed = true)

    private val testProfile = UserProfile(
        id = "1",
        name = "Cauã",
        avatarResName = "avatar_caua",
        avatarUrl = null
    )

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { profileManager.activeProfileFlow } returns MutableStateFlow(testProfile)
        every { profileManager.getLibraryRoot() } returns Pair("root_123", "Anime")
        every { profileManager.getActiveProfile() } returns testProfile

        coEvery { appSettings.fetchMotionPreference("1") } returns MotionPreference.SYSTEM
        coEvery { appSettings.fetchSubtitleStyle() } returns SubtitleStyle()
        coEvery { appSettings.fetchAutoFrameRate() } returns false
        coEvery { appSettings.fetchMatchDynamicRange() } returns true
        coEvery { appSettings.fetchPredictiveBuffering() } returns true

        every { malSessionManager.isLoggedIn() } returns true
        every { malSessionManager.getUsername() } returns "caua_vitor"
        every { malSessionManager.isSyncEnabled() } returns true
        every { malSessionManager.isAutoSkipEnabled() } returns true
        every { malSessionManager.isGesturesEnabled() } returns false

        viewModel = SettingsViewModel(
            profileManager,
            appSettings,
            malSessionManager,
            malRepository,
            driveRepository,
            backupSerializer,
            profileArtCatalog
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateLoadsActiveProfileAndSettings() = runBlocking {
        val state = viewModel.uiState.value

        assertEquals("Cauã", state.activeProfile?.name)
        assertEquals("root_123", state.libraryRootId)
        assertEquals("Anime", state.libraryRootName)
        assertTrue(state.isMalLoggedIn)
        assertEquals("caua_vitor", state.malUsername)
        assertTrue(state.isMalSyncEnabled)
        assertTrue(state.isAutoSkipEnabled)
        assertFalse(state.isGesturesEnabled)
        assertFalse(state.isAutoFrameRateEnabled)
        assertTrue(state.isMatchDynamicRangeEnabled)
        assertTrue(state.isPredictiveBufferingEnabled)
    }

    @Test
    fun selectCategoryUpdatesState() {
        viewModel.onEvent(SettingsEvent.SelectCategory(3))
        assertEquals(3, viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun saveFocusUpdatesLastFocusedRowId() {
        viewModel.onEvent(SettingsEvent.SaveFocus(12345))
        assertEquals(12345, viewModel.uiState.value.lastFocusedRowId)
    }

    @Test
    fun saveMotionPreferencePersistsAndUpdatesState() = runBlocking {
        coEvery { appSettings.saveMotionPreference(MotionPreference.REDUCED, "1") } returns Unit

        viewModel.onEvent(SettingsEvent.SaveMotionPreference(MotionPreference.REDUCED))

        assertEquals(MotionPreference.REDUCED, viewModel.uiState.value.motionPreference)
        coVerify { appSettings.saveMotionPreference(MotionPreference.REDUCED, "1") }
    }

    @Test
    fun setAutoSkipTogglesSetting() = runBlocking {
        viewModel.onEvent(SettingsEvent.SetAutoSkip(false))

        assertFalse(viewModel.uiState.value.isAutoSkipEnabled)
        verify { malSessionManager.setAutoSkipEnabled(false) }
    }

    @Test
    fun disconnectMalClearsSessionAndResetsState() = runBlocking {
        viewModel.onEvent(SettingsEvent.DisconnectMal)

        assertFalse(viewModel.uiState.value.isMalLoggedIn)
        assertEquals(null, viewModel.uiState.value.malUsername)
        assertFalse(viewModel.uiState.value.isMalSyncEnabled)
        verify { malSessionManager.clearSession() }
    }

    @Test
    fun resetSessionRevokesOnlyThisTvAndSendsRestartAppEffect() = runBlocking {
        viewModel.onEvent(SettingsEvent.ResetSession)

        coVerify { driveRepository.logoutCurrentDevice() }
        val effect = viewModel.effects.first()
        assertTrue(effect is SettingsUiEffect.RestartApp)
    }

    @Test
    fun disconnectAllDriveDevicesRevokesGoogleGrantAndSendsRestartAppEffect() = runBlocking {
        viewModel.onEvent(SettingsEvent.DisconnectAllDriveDevices)

        coVerify { driveRepository.disconnectAllDevices() }
        val effect = viewModel.effects.first()
        assertTrue(effect is SettingsUiEffect.RestartApp)
    }

    @Test
    fun setSmartDownloadsUpdatesStateAndSavesToAppSettings() = runBlocking {
        viewModel.onEvent(SettingsEvent.SetSmartDownloads(false))

        assertFalse(viewModel.uiState.value.isSmartDownloadsEnabled)
        coVerify { appSettings.saveSmartDownloads(false, testProfile.id) }
    }
}
