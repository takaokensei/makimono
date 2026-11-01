package zechs.drive.stream.ui.home

import zechs.drive.stream.data.model.DriveFile

internal data class CatalogSections(val library: List<DriveFile>, val discovery: List<DriveFile>)

/** External discovery is never presented as an available library item. */
internal fun catalogSections(items: List<DriveFile>, searching: Boolean): CatalogSections {
    val (external, library) = items.partition { it.isSeedboxCatalogItem }
    return CatalogSections(library, if (searching) external else emptyList())
}
