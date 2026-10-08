package zechs.drive.stream.ui.home

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingInputPolicyTest {
    @Test
    fun tvMenuOpensCardActionsOnlyOnFirstKeyDownWhenHandlerExists() {
        assertTrue(
            ContinueWatchingInputPolicy.shouldOpenContextMenu(
                KeyEvent.KEYCODE_MENU,
                KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                hasHandler = true
            )
        )
        assertFalse(
            ContinueWatchingInputPolicy.shouldOpenContextMenu(
                KeyEvent.KEYCODE_MENU,
                KeyEvent.ACTION_UP,
                repeatCount = 0,
                hasHandler = true
            )
        )
        assertFalse(
            ContinueWatchingInputPolicy.shouldOpenContextMenu(
                KeyEvent.KEYCODE_MENU,
                KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                hasHandler = true
            )
        )
        assertFalse(
            ContinueWatchingInputPolicy.shouldOpenContextMenu(
                KeyEvent.KEYCODE_MENU,
                KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                hasHandler = false
            )
        )
        assertFalse(
            ContinueWatchingInputPolicy.shouldOpenContextMenu(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                hasHandler = true
            )
        )
    }
}
