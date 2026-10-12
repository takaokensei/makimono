package zechs.drive.stream.utils

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Utilitário para agendamento seguro de callbacks em Views vinculado ao ciclo de vida do LifecycleOwner.
 * Previne vazamentos de memória e crashes de execução fora de janela (postDelayed após onDestroy/detach).
 */
object LifecycleDelays {

    fun postDelayed(
        view: View,
        delayMillis: Long,
        lifecycleOwner: LifecycleOwner? = null,
        action: () -> Unit
    ): Runnable {
        val runnable = object : Runnable {
            override fun run() {
                if (lifecycleOwner != null && lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                    return
                }
                if (!view.isAttachedToWindow) {
                    return
                }
                action()
            }
        }

        if (lifecycleOwner != null) {
            val observer = object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        view.removeCallbacks(runnable)
                        lifecycleOwner.lifecycle.removeObserver(this)
                    }
                }
            }

            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                return runnable
            }
            lifecycleOwner.lifecycle.addObserver(observer)
        }

        view.postDelayed(runnable, delayMillis)
        return runnable
    }
}

/**
 * Extensão conveniente para agendamento em View seguro contra o ciclo de vida.
 */
fun View.postDelayedSafe(
    delayMillis: Long,
    lifecycleOwner: LifecycleOwner? = null,
    action: () -> Unit
): Runnable = LifecycleDelays.postDelayed(this, delayMillis, lifecycleOwner, action)
