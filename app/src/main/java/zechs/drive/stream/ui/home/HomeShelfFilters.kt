package zechs.drive.stream.ui.home

import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.model.DriveFile

/**
 * Funções puras de filtragem e mesclagem de estantes da biblioteca (ADR-001 / Seção 4.10).
 */

fun mergeCatalogSnapshot(
    refreshedFiles: List<DriveFile>,
    cachedFiles: List<DriveFile>,
    isComplete: Boolean
): List<DriveFile> {
    if (isComplete) return refreshedFiles

    val refreshedIds = refreshedFiles.mapTo(mutableSetOf()) { it.id }
    return (refreshedFiles + cachedFiles.filterNot { it.id in refreshedIds })
        .distinctBy { it.id }
}

/**
 * The configured library folder is a container, never a catalog item. Drive
 * shortcuts can point back at that same container, so compare both file and
 * shortcut target ids before promoting entries to a title or hero.
 */
fun excludeLibraryRoot(
    files: List<DriveFile>,
    libraryRootId: String
): List<DriveFile> = files.filterNot { file ->
    file.id == libraryRootId || file.shortcutDetails.targetId == libraryRootId
}

fun filterStarredLibraryFolders(
    starredFiles: List<DriveFile>,
    libraryFiles: List<DriveFile>
): List<DriveFile> {
    val starredIds = starredFiles.flatMap { file ->
        listOfNotNull(file.id, file.shortcutDetails.targetId)
    }.toSet()
    return libraryFiles
        .filter { file ->
            (file.isFolder || file.isShortcutFolder) &&
                (file.id in starredIds || file.shortcutDetails.targetId?.let(starredIds::contains) == true)
        }
        .distinctBy { it.shortcutDetails.targetId ?: it.id }
}

/** Returns only series folders with a Drive timestamp, newest first. */
fun recentlyModifiedLibraryFolders(
    files: List<DriveFile>,
    limit: Int = 20
): List<DriveFile> = files
    .asSequence()
    .filter { it.isFolder || it.isShortcutFolder }
    .filter { !it.modifiedTime.isNullOrBlank() }
    .sortedByDescending { it.modifiedTime }
    .take(limit.coerceAtLeast(0))
    .toList()

fun filterFollowedLibraryFolders(
    followedFolders: List<FollowedFolder>,
    libraryFiles: List<DriveFile>
): List<DriveFile> {
    val byId = buildMap {
        libraryFiles.forEach { file ->
            put(file.id, file)
            file.shortcutDetails.targetId?.let { put(it, file) }
        }
    }
    return followedFolders
        .sortedByDescending { it.followedAt }
        .mapNotNull { byId[it.folderId] }
        .filter { it.isFolder || it.isShortcutFolder }
        .distinctBy { it.shortcutDetails.targetId ?: it.id }
}
