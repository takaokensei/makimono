package zechs.drive.stream.data.model

import androidx.annotation.Keep
import zechs.drive.stream.utils.ThumbnailUrl
import zechs.drive.stream.utils.util.Converter

@Keep
data class DriveFile(
    val id: String,
    val name: String,
    val size: Long?,
    val mimeType: String,
    val iconLink: String?,
    val thumbnailLink: String? = null,
    val shortcutDetails: ShortcutDetails,
    val starred: Starred,
    val posterUrl: String? = null
) {
    val humanSize = size?.let { Converter.toHumanSize(it) }

    val isVideoFile = mimeType.startsWith("video/")

    val isSubtitleFile = name.endsWith(".ass", ignoreCase = true) ||
            name.endsWith(".srt", ignoreCase = true) ||
            name.endsWith(".vtt", ignoreCase = true) ||
            name.endsWith(".ssa", ignoreCase = true) ||
            name.endsWith(".sub", ignoreCase = true) ||
            mimeType == "text/x-ssa" ||
            mimeType == "text/vtt" ||
            mimeType == "application/x-subrip"

    val isFolder = mimeType == "application/vnd.google-apps.folder"
            || mimeType == "drive#drive"

    val isShortcut = mimeType == "application/vnd.google-apps.shortcut"

    val isShortcutFolder = shortcutDetails.targetMimeType == "application/vnd.google-apps.folder"

    val isShortcutVideo = shortcutDetails.targetMimeType?.startsWith("video/") ?: false

    val iconLink128 = iconLink?.replace("16", "128")

    /**
     * Drive returns thumbnailLink capped at a small size (usually ~220px on
     * the longest side). Bump it up so 16:9 preview cards on TV don't look
     * blurry, the same way [iconLink128] upsizes the file-type icon.
     */
    val thumbnailMedium = ThumbnailUrl.medium(thumbnailLink)

    val thumbnailLarge = ThumbnailUrl.large(thumbnailLink)
}