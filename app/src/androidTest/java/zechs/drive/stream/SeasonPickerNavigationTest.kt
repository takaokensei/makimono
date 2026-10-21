package zechs.drive.stream

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeasonPickerNavigationTest {
    @Test fun dpadSelectionBackAndReopenPreserveCurrentSeason() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context.startActivity(Intent().apply {
            setClassName(context.packageName, "zechs.drive.stream.debug.VisualReviewActivity")
            putExtra("screen", "details_many_seasons")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        val triggerSelector = By.res(context.packageName, "btnSeasonPicker")
        assertTrue(device.wait(Until.hasObject(triggerSelector.focused(true)), 5_000))
        val original = device.findObject(triggerSelector).text
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.res("android", "select_dialog_listview")), 5_000))
        val selected = device.findObject(By.checked(true))
        assertNotNull("Current season must be checked", selected)
        assertEquals(selected.text, device.findObject(By.focused(true)).text)
        val bounds = device.findObject(By.res("android", "select_dialog_listview")).visibleBounds
        val density = context.resources.displayMetrics.density
        assertTrue("Picker violates horizontal safe area", bounds.left >= (48 * density).toInt())
        assertTrue("Picker violates vertical safe area", bounds.top >= (27 * density).toInt())
        assertTrue(bounds.right <= device.displayWidth - (48 * density).toInt())
        assertTrue(bounds.bottom <= device.displayHeight - (27 * density).toInt())
        device.pressDPadDown()
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(triggerSelector.focused(true)), 5_000))
        assertNotEquals("Selection must update trigger", original, device.findObject(triggerSelector).text)
        val updated = device.findObject(triggerSelector).text
        device.pressDPadCenter()
        assertTrue(device.wait(Until.hasObject(By.res("android", "select_dialog_listview")), 5_000))
        val reopenedSelection = device.findObject(By.checked(true))
        assertNotNull(reopenedSelection)
        assertTrue(updated.contains(reopenedSelection.text))
        assertEquals(reopenedSelection.text, device.findObject(By.focused(true)).text)
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(triggerSelector.focused(true)), 5_000))
        assertEquals(updated, device.findObject(triggerSelector).text)
    }
}
