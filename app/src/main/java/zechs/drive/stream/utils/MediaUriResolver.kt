package zechs.drive.stream.utils

import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.MediaSource
import zechs.drive.stream.utils.util.Constants

object MediaUriResolver {

    const val LOCAL_ID_PREFIX = "local:"

    /**
     * Checks if a given URI string refers to a local file/content scheme.
     */
    fun isLocalUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return uriString.startsWith("content://") ||
                uriString.startsWith("file://") ||
                uriString.startsWith("/")
    }

    /**
     * Checks if a file ID corresponds to a local item.
     */
    fun isLocalId(fileId: String?): Boolean {
        if (fileId.isNullOrBlank()) return false
        return fileId.startsWith(LOCAL_ID_PREFIX)
    }

    /**
     * Resolves the playable playback URI for a given file ID and optional local URI.
     * When [localUri] is present or [fileId] is prefixed with `local:`, returns the local URI.
     * Otherwise returns the authenticated Drive stream URL.
     */
    fun resolveStreamUri(fileId: String, localUri: String? = null): String {
        if (!localUri.isNullOrBlank()) {
            return localUri
        }
        if (isLocalId(fileId)) {
            // If only fileId is provided and it has prefix, strip prefix if it was stored as local:<path>
            val path = fileId.removePrefix(LOCAL_ID_PREFIX)
            if (isLocalUri(path)) return path
        }
        return "${Constants.DRIVE_API}/files/$fileId?supportsAllDrives=True&alt=media"
    }

    /**
     * Resolves the playable playback URI for a [DriveFile].
     */
    fun resolveStreamUri(file: DriveFile): String {
        return if (file.source == MediaSource.LOCAL || !file.localUri.isNullOrBlank()) {
            file.localUri ?: file.id.removePrefix(LOCAL_ID_PREFIX)
        } else {
            resolveStreamUri(file.id, file.localUri)
        }
    }
}
