package zechs.drive.stream.ui.home

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.domain.library.EpisodeParser

internal object ContinueWatchingContextMenu {
    enum class Action {
        RESUME,
        RESTART,
        OPEN_SERIES,
        MARK_FINISHED,
        REMOVE
    }

    private data class Option(val label: String, val action: Action)

    fun show(
        context: Context,
        watchItem: WatchList,
        focusAnchor: View? = null,
        onAction: (Action) -> Unit
    ): AlertDialog {
        val parsed = EpisodeParser.parse(watchItem.name)
        val title = parsed.showTitle.ifBlank { parsed.cleanTitle }
        val options = listOf(
            Option(context.getString(R.string.home_continue_at, formatPosition(context, watchItem.watchedDuration)), Action.RESUME),
            Option(context.getString(R.string.home_restart_at_zero), Action.RESTART),
            Option(context.getString(R.string.home_open_series), Action.OPEN_SERIES),
            Option(context.getString(R.string.home_mark_finished), Action.MARK_FINISHED),
            Option(context.getString(R.string.home_remove_continue), Action.REMOVE)
        )

        return MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(title)
            .setItems(options.map(Option::label).toTypedArray()) { _, index ->
                options.getOrNull(index)?.let { onAction(it.action) }
            }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    focusAnchor?.let { anchor ->
                        zechs.drive.stream.utils.LifecycleDelays.postDelayed(anchor, FOCUS_RESTORE_DELAY_MS) {
                            if (anchor.isShown) {
                                if (anchor.isInTouchMode) {
                                    anchor.requestFocusFromTouch()
                                } else {
                                    anchor.requestFocus()
                                }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun formatPosition(context: Context, positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
        return context.getString(
            R.string.home_playback_position,
            totalSeconds / 60L,
            totalSeconds % 60L
        )
    }

    private const val FOCUS_RESTORE_DELAY_MS = 150L
}

/** The TV Menu key opens the focused card's context actions once on key-down. */
internal object ContinueWatchingInputPolicy {
    fun shouldOpenContextMenu(keyCode: Int, action: Int, repeatCount: Int, hasHandler: Boolean): Boolean =
        keyCode == android.view.KeyEvent.KEYCODE_MENU &&
            action == android.view.KeyEvent.ACTION_DOWN &&
            repeatCount == 0 &&
            hasHandler
}
