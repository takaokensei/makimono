package zechs.drive.stream.playback.osd

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.R
import zechs.drive.stream.utils.AppLog

/**
 * Host interface providing views and lifecycle callbacks to [OsdController].
 */
interface OsdViewHost {
    val rootControllerView: View?
    val titleBlock: View?
    val controlsScrollView: View?
    val mainControlsRoot: View?
    val topScrim: View?
    val skipIntroRow: View?
    val nextEpisodeCard: View?

    fun onOsdVisibilityChanged(visible: Boolean) {}
    fun onElevationPolicyChanged(elevated: Boolean) {}
    fun onShowPrepared() {}
}

/**
 * Shared, interruptible, and idempotent OSD controller for ExoPlayer and MPV.
 *
 * Implements Plano v4 Fase I:
 * - Single ValueAnimator with continuous progress p in [0.0, 1.0].
 * - Single owner of inactivity timeout (eradicates H1).
 * - Idempotent hide/show transitions driven by [OsdStateMachine] (eradicates H2).
 * - Smooth interruptibility without alpha flash or jump (eradicates H3).
 * - Single controller for satellite elements like scrim, next episode card, skip intro (eradicates H4).
 * - Supports system and profile-level reduce motion.
 */
class OsdController(
    private val context: Context,
    private val host: OsdViewHost,
    private val isReviewMode: Boolean = false,
    private val getReduceMotion: () -> Boolean = { false }
) {
    private val stateMachine = OsdStateMachine()
    private val handler = Handler(Looper.getMainLooper())
    private var currentProgress = 0f
    private var activeAnimator: ValueAnimator? = null

    val defaultInactivityTimeoutMs: Long
        get() = if (isReviewMode) 30_000L else 4_500L

    private val hideRunnable = Runnable {
        if (BuildConfig.DEBUG) {
            AppLog.d(TAG, "[OSD_CONTROLLER] Inactivity timeout expired (${defaultInactivityTimeoutMs}ms) -> hiding")
        }
        hide()
    }

    val state: OsdState
        get() = stateMachine.state

    val isVisible: Boolean
        get() = stateMachine.isVisible

    val isHiding: Boolean
        get() = stateMachine.isHiding

    val shouldConsumeBack: Boolean
        get() = stateMachine.shouldConsumeBack

    fun resetInactivityTimer(customTimeoutMs: Long = defaultInactivityTimeoutMs) {
        handler.removeCallbacks(hideRunnable)
        if (stateMachine.isVisible) {
            handler.postDelayed(hideRunnable, customTimeoutMs)
        }
    }

    fun cancelInactivityTimer() {
        handler.removeCallbacks(hideRunnable)
    }

    fun show(autoHide: Boolean = true) {
        val action = stateMachine.onShowRequested()
        if (BuildConfig.DEBUG) {
            AppLog.d(TAG, "[OSD_CONTROLLER] show() called -> action=$action, state=${stateMachine.state}")
        }
        when (action) {
            OsdAction.StartShow, OsdAction.ReverseToShow -> {
                host.onShowPrepared()
                host.onElevationPolicyChanged(true)
                host.rootControllerView?.visibility = View.VISIBLE
                host.onOsdVisibilityChanged(true)
                animateTo(1f)
                if (autoHide) {
                    resetInactivityTimer()
                } else {
                    cancelInactivityTimer()
                }
            }
            OsdAction.ResetInactivityTimer -> {
                if (autoHide) {
                    resetInactivityTimer()
                } else {
                    cancelInactivityTimer()
                }
            }
            else -> Unit
        }
    }

    fun hide(onComplete: (() -> Unit)? = null) {
        cancelInactivityTimer()
        val action = stateMachine.onHideRequested()
        if (BuildConfig.DEBUG) {
            AppLog.d(TAG, "[OSD_CONTROLLER] hide() called -> action=$action, state=${stateMachine.state}")
        }
        when (action) {
            OsdAction.StartHide, OsdAction.ReverseToHide -> {
                host.onElevationPolicyChanged(false)
                animateTo(0f) {
                    host.rootControllerView?.visibility = View.GONE
                    host.onOsdVisibilityChanged(false)
                    onComplete?.invoke()
                }
            }
            OsdAction.None -> {
                // Idempotent: already hiding or hidden.
                onComplete?.invoke()
            }
            else -> Unit
        }
    }

    fun forceHide() {
        cancelInactivityTimer()
        activeAnimator?.cancel()
        activeAnimator = null
        stateMachine.forceHidden()
        currentProgress = 0f
        applyProgress(0f)
        host.onElevationPolicyChanged(false)
        host.rootControllerView?.visibility = View.GONE
        host.onOsdVisibilityChanged(false)
    }

    fun forceShow() {
        cancelInactivityTimer()
        activeAnimator?.cancel()
        activeAnimator = null
        stateMachine.forceShown()
        currentProgress = 1f
        applyProgress(1f)
        host.onShowPrepared()
        host.onElevationPolicyChanged(true)
        host.rootControllerView?.visibility = View.VISIBLE
        host.onOsdVisibilityChanged(true)
        resetInactivityTimer()
    }

    private fun animateTo(targetProgress: Float, onEnd: (() -> Unit)? = null) {
        activeAnimator?.cancel()

        if (getReduceMotion()) {
            currentProgress = targetProgress
            applyProgress(targetProgress)
            if (targetProgress == 1f) {
                stateMachine.onShowAnimationCompleted()
            } else {
                stateMachine.onHideAnimationCompleted()
            }
            onEnd?.invoke()
            return
        }

        val startProgress = currentProgress
        if (startProgress == targetProgress) {
            if (targetProgress == 1f) stateMachine.onShowAnimationCompleted()
            else stateMachine.onHideAnimationCompleted()
            onEnd?.invoke()
            return
        }

        val totalDuration = if (targetProgress > startProgress) {
            context.resources.getInteger(R.integer.motion_spec_normal).toLong() // 220ms
        } else {
            context.resources.getInteger(R.integer.motion_spec_fast).toLong()   // 150ms
        }

        val fractionRemaining = kotlin.math.abs(targetProgress - startProgress)
        val duration = (totalDuration * fractionRemaining).toLong().coerceAtLeast(30L)

        val interpolator = if (targetProgress > startProgress) {
            try {
                LinearOutSlowInInterpolator()
            } catch (_: Throwable) {
                DecelerateInterpolator()
            }
        } else {
            try {
                FastOutLinearInInterpolator()
            } catch (_: Throwable) {
                AccelerateInterpolator()
            }
        }

        activeAnimator = ValueAnimator.ofFloat(startProgress, targetProgress).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                currentProgress = p
                applyProgress(p)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var isCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!isCancelled) {
                        currentProgress = targetProgress
                        applyProgress(targetProgress)
                        if (targetProgress == 1f) {
                            stateMachine.onShowAnimationCompleted()
                        } else {
                            stateMachine.onHideAnimationCompleted()
                        }
                        onEnd?.invoke()
                    }
                }
            })
            start()
        }
    }

    private fun applyProgress(p: Float) {
        val density = context.resources.displayMetrics.density
        val cardOffset = context.resources.getDimensionPixelOffset(R.dimen.next_episode_card_offset).toFloat()

        host.titleBlock?.let {
            it.alpha = p
            it.translationY = -8f * density * (1f - p)
        }
        host.controlsScrollView?.let {
            it.alpha = p
            it.translationY = 8f * density * (1f - p)
        }
        host.mainControlsRoot?.alpha = p
        host.topScrim?.alpha = p
        host.skipIntroRow?.alpha = p

        host.nextEpisodeCard?.let { card ->
            if (card.visibility == View.VISIBLE || card.alpha > 0f) {
                card.translationY = -cardOffset * p
            }
        }
    }

    companion object {
        private const val TAG = "OsdController"
    }
}
