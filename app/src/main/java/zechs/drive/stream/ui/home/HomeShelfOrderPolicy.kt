package zechs.drive.stream.ui.home

import zechs.drive.stream.data.model.DriveFile

/**
 * Keeps a focused shelf spatially stable during asynchronous refreshes.
 * Existing entries retain their visible order, use the newest data, and new
 * entries are appended. Entries removed from the incoming result are dropped.
 */
internal fun preserveFocusedShelfOrder(
    previous: List<DriveFile>,
    incoming: List<DriveFile>
): List<DriveFile> {
    val incomingById = incoming.associateBy(DriveFile::id).toMutableMap()
    val retained = previous.mapNotNull { previousFile -> incomingById.remove(previousFile.id) }
    return retained + incoming.filter { incomingById.remove(it.id) != null }
}
