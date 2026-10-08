package zechs.drive.stream.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGestureStateTest {
    @Test
    fun completedTapActivatesOnceAndResetsGesture() {
        val gesture = TapGestureState()

        gesture.begin()

        assertTrue(gesture.end())
        assertFalse(gesture.end())
    }

    @Test
    fun movingOutsideTargetCancelsActivation() {
        val gesture = TapGestureState()

        gesture.begin()
        gesture.cancelActivation()

        assertFalse(gesture.end())
    }

    @Test
    fun canceledGestureCannotActivate() {
        val gesture = TapGestureState()

        gesture.begin()
        gesture.cancel()

        assertFalse(gesture.end())
    }
}
