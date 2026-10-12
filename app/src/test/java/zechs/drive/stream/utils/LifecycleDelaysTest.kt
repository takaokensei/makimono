package zechs.drive.stream.utils

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleDelaysTest {

    @Test
    fun `when view is attached and lifecycle is active, runnable runs action`() {
        val view = mockk<View>(relaxed = true)
        every { view.isAttachedToWindow } returns true

        val lifecycle = mockk<Lifecycle>(relaxed = true)
        every { lifecycle.currentState } returns Lifecycle.State.RESUMED
        val owner = mockk<LifecycleOwner> {
            every { this@mockk.lifecycle } returns lifecycle
        }

        var executed = false
        val runnable = LifecycleDelays.postDelayed(view, 50L, owner) {
            executed = true
        }

        runnable.run()
        assertTrue(executed)
    }

    @Test
    fun `when view is detached, runnable does not run action`() {
        val view = mockk<View>(relaxed = true)
        every { view.isAttachedToWindow } returns false

        val lifecycle = mockk<Lifecycle>(relaxed = true)
        every { lifecycle.currentState } returns Lifecycle.State.RESUMED
        val owner = mockk<LifecycleOwner> {
            every { this@mockk.lifecycle } returns lifecycle
        }

        var executed = false
        val runnable = LifecycleDelays.postDelayed(view, 50L, owner) {
            executed = true
        }

        runnable.run()
        assertFalse(executed)
    }

    @Test
    fun `when lifecycle is destroyed, runnable does not run action`() {
        val view = mockk<View>(relaxed = true)
        every { view.isAttachedToWindow } returns true

        val lifecycle = mockk<Lifecycle>(relaxed = true)
        every { lifecycle.currentState } returns Lifecycle.State.DESTROYED
        val owner = mockk<LifecycleOwner> {
            every { this@mockk.lifecycle } returns lifecycle
        }

        var executed = false
        val runnable = LifecycleDelays.postDelayed(view, 50L, owner) {
            executed = true
        }

        runnable.run()
        assertFalse(executed)
    }

    @Test
    fun `when lifecycle triggers ON_DESTROY, view callbacks are removed`() {
        val view = mockk<View>(relaxed = true)
        every { view.isAttachedToWindow } returns true

        val observerSlot = slot<LifecycleEventObserver>()
        val lifecycle = mockk<Lifecycle>(relaxed = true)
        every { lifecycle.currentState } returns Lifecycle.State.STARTED
        every { lifecycle.addObserver(capture(observerSlot)) } returns Unit

        val owner = mockk<LifecycleOwner> {
            every { this@mockk.lifecycle } returns lifecycle
        }

        val runnable = LifecycleDelays.postDelayed(view, 100L, owner) {}

        assertTrue(observerSlot.isCaptured)
        observerSlot.captured.onStateChanged(owner, Lifecycle.Event.ON_DESTROY)

        verify { view.removeCallbacks(runnable) }
    }
}
