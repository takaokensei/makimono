package zechs.drive.stream.ui.player

import android.content.Context
import androidx.annotation.StringRes
import zechs.drive.stream.R

object PlaybackLabel {
    @StringRes
    fun subtitleRes(isMovie: Boolean, episode: Int?): Int {
        return if (isMovie || episode == null || episode <= 0) {
            R.string.playback_movie_full
        } else {
            R.string.playback_episode_number
        }
    }

    fun renderSubtitle(context: Context, isMovie: Boolean, episode: Int?): String {
        return if (isMovie || episode == null || episode <= 0) {
            context.getString(R.string.playback_movie_full)
        } else {
            context.getString(R.string.playback_episode_number, episode)
        }
    }

    fun format(isMovie: Boolean, episode: Int?): String {
        return if (isMovie || episode == null || episode <= 0) {
            "Filme Completo"
        } else {
            "Episódio %02d".format(episode)
        }
    }
}
