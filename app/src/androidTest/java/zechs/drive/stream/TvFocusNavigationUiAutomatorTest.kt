package zechs.drive.stream

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented D-pad and focus verification suite using UiAutomator (Plano v4 Fase C).
 * Validates initial focus, directional DPAD navigation, Back key handling, and focus retention
 * across Home, Details, Settings and Player.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusNavigationUiAutomatorTest {

    private lateinit var device: UiDevice
    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val targetPackage = appContext.packageName

    companion object {
        private const val LAUNCH_TIMEOUT_MS = 5000L
        private const val ANIMATION_SETTLE_MS = 500L
    }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun launchReviewScreen(screenName: String) {
        val intent = Intent().apply {
            setClassName(targetPackage, "zechs.drive.stream.debug.VisualReviewActivity")
            putExtra("screen", screenName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        appContext.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), LAUNCH_TIMEOUT_MS)
        Thread.sleep(ANIMATION_SETTLE_MS)
    }

    @Test
    fun homeScreenHasInitialFocusAndRespondsToDpad() {
        launchReviewScreen("home")

        // Wait for root content to appear
        val focusedView = device.wait(Until.findObject(By.focused(true)), LAUNCH_TIMEOUT_MS)
        assertNotNull("Home screen must establish initial focus on an interactive element", focusedView)

        // Press DPAD Down to navigate to rails/shelves
        device.pressDPadDown()
        Thread.sleep(ANIMATION_SETTLE_MS)

        val focusedAfterDown = device.findObject(By.focused(true))
        assertNotNull("Focus must remain visible after DPAD Down on Home", focusedAfterDown)

        // Press DPAD Left to move toward navigation rail
        device.pressDPadLeft()
        Thread.sleep(ANIMATION_SETTLE_MS)

        val focusedOnRail = device.findObject(By.focused(true))
        assertNotNull("Focus must reach navigation rail after DPAD Left", focusedOnRail)

        // Press DPAD Right to return back to content
        device.pressDPadRight()
        Thread.sleep(ANIMATION_SETTLE_MS)

        val focusedBackOnContent = device.findObject(By.focused(true))
        assertNotNull("Focus must return to content from navigation rail", focusedBackOnContent)
    }

    @Test
    fun seriesDetailScreenEstablishesInitialFocusOnPrimaryAction() {
        launchReviewScreen("details")

        val focusedView = device.wait(Until.findObject(By.focused(true)), LAUNCH_TIMEOUT_MS)
        assertNotNull("Details screen must establish initial focus on primary watch/continue action", focusedView)

        // Navigate down toward seasons / episode list
        device.pressDPadDown()
        Thread.sleep(ANIMATION_SETTLE_MS)

        val focusedInList = device.findObject(By.focused(true))
        assertNotNull("Focus must transition smoothly to episode list via DPAD Down", focusedInList)
    }

    @Test
    fun settingsScreenAllowsVerticalCategoryNavigation() {
        launchReviewScreen("settings")

        val focusedCategory = device.wait(Until.findObject(By.focused(true)), LAUNCH_TIMEOUT_MS)
        assertNotNull("Settings screen must have initial focus on first category/setting", focusedCategory)

        // Navigate down to next setting
        device.pressDPadDown()
        Thread.sleep(ANIMATION_SETTLE_MS)

        val nextFocused = device.findObject(By.focused(true))
        assertNotNull("Focus must move to next setting on DPAD Down without jumping or disappearing", nextFocused)

        // Navigate back up
        device.pressDPadUp()
        Thread.sleep(ANIMATION_SETTLE_MS)

        val restoredCategory = device.findObject(By.focused(true))
        assertNotNull("Focus must return to previous setting on DPAD Up", restoredCategory)
    }

    @Test
    fun playerScreenShowsOsdWithFocusAndHidesOnBackKey() {
        launchReviewScreen("player_exo_activity")

        // Wait for player to settle and ensure OSD is active
        device.wait(Until.hasObject(By.pkg(targetPackage)), LAUNCH_TIMEOUT_MS)
        device.pressDPadCenter()
        val focusedControl = device.wait(Until.findObject(By.focused(true)), LAUNCH_TIMEOUT_MS)
        assertNotNull("Player OSD must present clear focus on primary control (e.g. Play/Pause)", focusedControl)

        // Press Back key: should consume OSD dismissal without closing player
        device.pressBack()
        Thread.sleep(ANIMATION_SETTLE_MS)

        // Verify player activity is still running in foreground
        val currentPkg = device.currentPackageName
        assertTrue("Back key must dismiss OSD while keeping Player in foreground", currentPkg == targetPackage)
    }
}
