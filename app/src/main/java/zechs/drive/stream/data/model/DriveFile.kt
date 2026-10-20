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
    val posterUrl: String? = null,
    /** RFC 3339 timestamp supplied by Drive; absent for local/synthetic items. */
    val modifiedTime: String? = null,
    /** Optional user-authored Drive description; never inferred from a filename. */
    val description: String? = null,
    /** Drive checksum and duration are absent for shortcuts and synthetic entries. */
    val md5Checksum: String? = null,
    val durationMillis: Long? = null,
    val isMovie: Boolean = false,
    /** Where the bytes live; defaults to DRIVE so legacy call sites are unaffected. */
    val source: MediaSource = MediaSource.DRIVE,
    /** content:// or file:// URI; only set when [source] is LOCAL. */
    val localUri: String? = null
) {
    val humanSize = size?.let { Converter.toHumanSize(it) }

    val isTransportStream = mimeType.equals("video/mp2t", ignoreCase = true) ||
            mimeType.equals("video/vnd.dlna.mpeg-tts", ignoreCase = true) ||
            name.endsWith(".ts", ignoreCase = true) ||
            name.endsWith(".m2ts", ignoreCase = true) ||
            name.endsWith(".mts", ignoreCase = true) ||
            name.endsWith(".tp", ignoreCase = true) ||
            shortcutDetails.targetMimeType.equals("video/mp2t", ignoreCase = true) ||
            shortcutDetails.targetMimeType.equals("video/vnd.dlna.mpeg-tts", ignoreCase = true)

    val isVideoFile = mimeType.startsWith("video/") ||
            mimeType == "application/x-matroska" ||
            isTransportStream ||
            name.endsWith(".mkv", ignoreCase = true) ||
            name.endsWith(".mp4", ignoreCase = true) ||
            name.endsWith(".webm", ignoreCase = true) ||
            name.endsWith(".avi", ignoreCase = true) ||
            name.endsWith(".mov", ignoreCase = true) ||
            name.endsWith(".m4v", ignoreCase = true) ||
            name.endsWith(".mpg", ignoreCase = true) ||
            name.endsWith(".mpeg", ignoreCase = true) ||
            (shortcutDetails.targetMimeType?.startsWith("video/") == true) ||
            shortcutDetails.targetMimeType == "application/x-matroska"

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

    val isShortcutVideo = (shortcutDetails.targetMimeType?.startsWith("video/") == true) ||
            shortcutDetails.targetMimeType == "application/x-matroska" ||
            isTransportStream ||
            name.endsWith(".mkv", ignoreCase = true) ||
            name.endsWith(".mp4", ignoreCase = true) ||
            name.endsWith(".webm", ignoreCase = true) ||
            name.endsWith(".avi", ignoreCase = true) ||
            name.endsWith(".mov", ignoreCase = true) ||
            name.endsWith(".m4v", ignoreCase = true) ||
            name.endsWith(".mpg", ignoreCase = true) ||
            name.endsWith(".mpeg", ignoreCase = true)

    val iconLink128 = iconLink?.replace("16", "128")

    /**
     * Drive returns thumbnailLink capped at a small size (usually ~220px on
     * the longest side). Bump it up so 16:9 preview cards on TV don't look
     * blurry, the same way [iconLink128] upsizes the file-type icon.
     */
    val thumbnailMedium = ThumbnailUrl.medium(thumbnailLink)

    val thumbnailLarge = ThumbnailUrl.large(thumbnailLink)
}
