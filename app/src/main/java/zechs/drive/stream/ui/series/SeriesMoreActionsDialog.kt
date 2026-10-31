package zechs.drive.stream.ui.series

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import zechs.drive.stream.R
import zechs.drive.stream.ui.common.ActionMenuDialog

/** Availability-based secondary actions, using the shared safe modal surface. */
object SeriesMoreActionsDialog {
    fun show(
        context: Context, focusAnchor: View, isFollowed: Boolean,
        hasTrailer: Boolean = true, canRestartContinueEpisode: Boolean = false,
        isMovie: Boolean = false, onTrailer: () -> Unit, onFollowToggle: () -> Unit,
        onMarkWatched: () -> Unit, onRestartFromBeginning: (() -> Unit)? = null
    ): AlertDialog {
        val actions = buildList {
            if (canRestartContinueEpisode && onRestartFromBeginning != null)
                add(context.getString(R.string.episode_action_restart) to onRestartFromBeginning)
            if (hasTrailer) add(context.getString(R.string.series_action_trailer) to onTrailer)
            add(context.getString(if (isFollowed) R.string.action_unfollow else R.string.action_follow) to onFollowToggle)
            if (!isMovie) add(context.getString(R.string.series_action_mark_watched) to onMarkWatched)
        }
        return ActionMenuDialog.show(context, focusAnchor, context.getString(R.string.series_more_actions), actions)
    }
}
