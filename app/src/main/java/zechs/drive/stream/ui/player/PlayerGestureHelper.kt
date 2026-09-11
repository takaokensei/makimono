package zechs.drive.stream.ui.player

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.isVisible
import zechs.drive.stream.R
import zechs.drive.stream.databinding.ViewPlayerGestureHudBinding
import java.util.Locale
import kotlin.math.abs

interface PlayerGestureCallback {
    fun onToggleControls()
    fun onSeekRelative(deltaMs: Long)
    fun onSeekTo(positionMs: Long)
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun onTogglePlayPause()
    fun onSetSpeed(speed: Float)
    fun isControlsLocked(): Boolean = false
    fun isControllerVisible(): Boolean = false
    fun getTouchIgnoredViews(): List<View> = emptyList()
}

class PlayerGestureHelper(
    private val activity: Activity,
    private val hudBinding: ViewPlayerGestureHudBinding,
    private val callback: PlayerGestureCallback
) {

    companion object {
        private const val TAG = "PlayerGestureHelper"
        private const val SEEK_STEP_MAX_MS = 90_000L
    }

    private enum class DragMode {
        NONE,
        BRIGHTNESS,
        VOLUME,
        SEEK
    }

    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

    private var dragMode = DragMode.NONE
    private var startX = 0f
    private var startY = 0f
    private var initialBrightness = 0.5f
    private var initialVolume = 0
    private var initialPosition = 0L
    private var pendingSeekTargetMs = 0L
    private var isLongPressSpeeding = false
    private var isGestureActive = false

    private val hideBrightnessRunnable = Runnable {
        hudBinding.gestureBrightnessPill.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { hudBinding.gestureBrightnessPill.visibility = View.GONE }
            .start()
    }

    private val hideVolumeRunnable = Runnable {
        hudBinding.gestureVolumePill.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { hudBinding.gestureVolumePill.visibility = View.GONE }
            .start()
    }

    private val hideSeekRunnable = Runnable {
        hudBinding.gestureSeekCard.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { hudBinding.gestureSeekCard.visibility = View.GONE }
            .start()
    }

    private val hideNotificationRunnable = Runnable {
        hudBinding.gestureNotificationPill.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { hudBinding.gestureNotificationPill.visibility = View.GONE }
            .start()
    }

    fun showNotification(text: String, durationMs: Long = 2500L) {
        handler.removeCallbacks(hideNotificationRunnable)
        hudBinding.tvGestureNotificationText.text = text
        hudBinding.gestureNotificationPill.animate().cancel()
        hudBinding.gestureNotificationPill.alpha = 1f
        hudBinding.gestureNotificationPill.visibility = View.VISIBLE
        handler.postDelayed(hideNotificationRunnable, durationMs)
    }

    private val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (callback.isControlsLocked()) return false
            callback.onToggleControls()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (callback.isControlsLocked()) return false
            val width = activity.resources.displayMetrics.widthPixels

            when {
                e.x < width * 0.35f -> {
                    callback.onSeekRelative(-10_000L)
                    showDoubleTapBubble(hudBinding.doubleTapLeftBubble)
                    return true
                }
                e.x > width * 0.65f -> {
                    callback.onSeekRelative(10_000L)
                    showDoubleTapBubble(hudBinding.doubleTapRightBubble)
                    return true
                }
                else -> {
                    callback.onTogglePlayPause()
                    return true
                }
            }
        }

        override fun onLongPress(e: MotionEvent) {
            if (callback.isControlsLocked() || dragMode != DragMode.NONE) return
            isLongPressSpeeding = true
            callback.onSetSpeed(2.0f)
            hudBinding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            hudBinding.gestureSpeedPill.animate().cancel()
            hudBinding.gestureSpeedPill.alpha = 0f
            hudBinding.gestureSpeedPill.visibility = View.VISIBLE
            hudBinding.gestureSpeedPill.animate().alpha(1f).setDuration(200).start()
        }
    })

    private var isInteractingWithControls = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (callback.isControlsLocked()) {
            return false
        }

        // If controller is visible and user clicked on an interactive view, let system handle it
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            isInteractingWithControls = false
            if (callback.isControllerVisible()) {
                val ignored = callback.getTouchIgnoredViews()
                val hitRect = Rect()
                for (v in ignored) {
                    if (v.isVisible) {
                        v.getGlobalVisibleRect(hitRect)
                        if (hitRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            isInteractingWithControls = true
                            return false
                        }
                    }
                }
            }
        }

        if (isInteractingWithControls) {
            return false
        }

        val width = activity.resources.displayMetrics.widthPixels
        val height = activity.resources.displayMetrics.heightPixels

        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                dragMode = DragMode.NONE
                isGestureActive = true

                val lp = activity.window.attributes
                initialBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                initialPosition = callback.getCurrentPosition()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - startX
                val deltaY = event.y - startY

                if (dragMode == DragMode.NONE && !isLongPressSpeeding) {
                    if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY) * 1.2f) {
                        dragMode = DragMode.SEEK
                    } else if (abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX) * 1.2f) {
                        dragMode = if (startX < width * 0.5f) {
                            DragMode.BRIGHTNESS
                        } else {
                            DragMode.VOLUME
                        }
                    }
                }

                when (dragMode) {
                    DragMode.BRIGHTNESS -> {
                        handler.removeCallbacks(hideBrightnessRunnable)
                        val deltaPercent = -(deltaY / (height * 0.75f))
                        val newBrightness = (initialBrightness + deltaPercent).coerceIn(0.01f, 1.0f)
                        val lp = activity.window.attributes
                        lp.screenBrightness = newBrightness
                        activity.window.attributes = lp

                        val percent = (newBrightness * 100).toInt()
                        hudBinding.pbGestureBrightness.progress = percent
                        hudBinding.tvGestureBrightnessText.text = "$percent%"

                        if (!hudBinding.gestureBrightnessPill.isVisible) {
                            hudBinding.gestureBrightnessPill.alpha = 1f
                            hudBinding.gestureBrightnessPill.visibility = View.VISIBLE
                        }
                        return true
                    }

                    DragMode.VOLUME -> {
                        handler.removeCallbacks(hideVolumeRunnable)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val deltaPercent = -(deltaY / (height * 0.75f))
                        val newVol = (initialVolume + (deltaPercent * maxVol)).toInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)

                        val percent = ((newVol.toFloat() / maxVol) * 100).toInt()
                        hudBinding.pbGestureVolume.progress = percent
                        hudBinding.tvGestureVolumeText.text = "$percent%"
                        hudBinding.ivGestureVolumeIcon.setImageResource(
                            if (newVol == 0) R.drawable.ic_volume_off_24 else R.drawable.ic_volume_up_24
                        )

                        if (!hudBinding.gestureVolumePill.isVisible) {
                            hudBinding.gestureVolumePill.alpha = 1f
                            hudBinding.gestureVolumePill.visibility = View.VISIBLE
                        }
                        return true
                    }

                    DragMode.SEEK -> {
                        handler.removeCallbacks(hideSeekRunnable)
                        val duration = callback.getDuration().coerceAtLeast(1L)
                        val deltaPercent = (deltaX / width)
                        val deltaMs = (deltaPercent * SEEK_STEP_MAX_MS).toLong()
                        pendingSeekTargetMs = (initialPosition + deltaMs).coerceIn(0L, duration)

                        val sign = if (deltaMs >= 0) "+" else "-"
                        hudBinding.tvGestureSeekDelta.text = "$sign${formatTime(abs(deltaMs))}"
                        hudBinding.tvGestureSeekTime.text = "${formatTime(pendingSeekTargetMs)} / ${formatTime(duration)}"

                        if (!hudBinding.gestureSeekCard.isVisible) {
                            hudBinding.gestureSeekCard.alpha = 1f
                            hudBinding.gestureSeekCard.visibility = View.VISIBLE
                        }
                        return true
                    }

                    else -> Unit
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isLongPressSpeeding) {
                    isLongPressSpeeding = false
                    callback.onSetSpeed(1.0f)
                    hudBinding.gestureSpeedPill.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction { hudBinding.gestureSpeedPill.visibility = View.GONE }
                        .start()
                }

                when (dragMode) {
                    DragMode.SEEK -> {
                        callback.onSeekTo(pendingSeekTargetMs)
                        handler.postDelayed(hideSeekRunnable, 500)
                    }
                    DragMode.BRIGHTNESS -> {
                        handler.postDelayed(hideBrightnessRunnable, 700)
                    }
                    DragMode.VOLUME -> {
                        handler.postDelayed(hideVolumeRunnable, 700)
                    }
                    else -> Unit
                }

                val wasDragging = dragMode != DragMode.NONE
                dragMode = DragMode.NONE
                isGestureActive = false
                return true
            }
        }

        return true
    }

    private fun showDoubleTapBubble(bubble: View) {
        bubble.animate().cancel()
        bubble.alpha = 0f
        bubble.scaleX = 0.8f
        bubble.scaleY = 0.8f
        bubble.visibility = View.VISIBLE

        bubble.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160)
            .withEndAction {
                bubble.animate()
                    .alpha(0f)
                    .setStartDelay(200)
                    .setDuration(240)
                    .withEndAction { bubble.visibility = View.GONE }
                    .start()
            }
            .start()
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
