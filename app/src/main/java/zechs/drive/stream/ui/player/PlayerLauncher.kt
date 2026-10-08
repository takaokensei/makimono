package zechs.drive.stream.ui.player

import android.content.Context
import android.content.Intent
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.ui.player2.MPVActivity
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.AppTheme

object PlayerLauncher {

    const val EXTRA_FILE_ID = "fileId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_SERIES_TITLE = "seriesTitle"
    const val EXTRA_ACCESS_TOKEN = "accessToken"
    const val EXTRA_THUMBNAIL_LINK = "thumbnailLink"
    const val EXTRA_THEME = "theme"
    const val EXTRA_PLAYLIST = "playlist"
    const val EXTRA_SUBTITLES = "subtitles"
    const val EXTRA_START_POSITION = "startPosition"
    const val EXTRA_SIBLING_FOLDER_ID = "siblingFolderId"
    const val EXTRA_PROFILE_ID = "playbackProfileId"
    const val EXTRA_IS_MOVIE = "isMovie"

    fun createIntent(
        context: Context,
        playerType: VideoPlayer = VideoPlayer.EXO_PLAYER,
        fileId: String,
        title: String,
        seriesTitle: String? = null,
        accessToken: String? = null,
        thumbnailLink: String? = null,
        themeIndex: Int = AppTheme.OBSIDIAN.value,
        playlist: ArrayList<PlaylistItem>? = null,
        subtitles: ArrayList<SubtitleItem>? = null,
        siblingFolderId: String? = null,
        startPosition: Long = -1L,
        isMovie: Boolean = false
    ): Intent {
        val targetClass = when (playerType) {
            VideoPlayer.EXO_PLAYER -> PlayerActivity::class.java
            VideoPlayer.MPV -> MPVActivity::class.java
        }
        return Intent(context, targetClass).apply {
            putExtra(EXTRA_FILE_ID, fileId)
            putExtra(EXTRA_TITLE, title)
            seriesTitle?.let { putExtra(EXTRA_SERIES_TITLE, it) }
            accessToken?.let { putExtra(EXTRA_ACCESS_TOKEN, it) }
            thumbnailLink?.let { putExtra(EXTRA_THUMBNAIL_LINK, it) }
            putExtra(EXTRA_THEME, themeIndex)
            putExtra(EXTRA_IS_MOVIE, isMovie)
            playlist?.let { putExtra(EXTRA_PLAYLIST, it) }
            subtitles?.let { putExtra(EXTRA_SUBTITLES, it) }
            siblingFolderId?.let { putExtra(EXTRA_SIBLING_FOLDER_ID, it) }
            // Zero is an explicit "start from beginning" request; -1 means resume
            // from the saved watch position (or begin at zero when none exists).
            if (startPosition >= 0L) {
                putExtra(EXTRA_START_POSITION, startPosition)
            }
        }
    }

    fun launch(
        context: Context,
        playerType: VideoPlayer = VideoPlayer.EXO_PLAYER,
        fileId: String,
        title: String,
        seriesTitle: String? = null,
        accessToken: String? = null,
        thumbnailLink: String? = null,
        themeIndex: Int = AppTheme.OBSIDIAN.value,
        playlist: ArrayList<PlaylistItem>? = null,
        subtitles: ArrayList<SubtitleItem>? = null,
        siblingFolderId: String? = null,
        startPosition: Long = -1L,
        isMovie: Boolean = false
    ) {
        val intent = createIntent(
            context = context,
            playerType = playerType,
            fileId = fileId,
            title = title,
            seriesTitle = seriesTitle,
            accessToken = accessToken,
            thumbnailLink = thumbnailLink,
            themeIndex = themeIndex,
            playlist = playlist,
            subtitles = subtitles,
            siblingFolderId = siblingFolderId,
            startPosition = startPosition,
            isMovie = isMovie
        )
        context.startActivity(intent)
    }
}
