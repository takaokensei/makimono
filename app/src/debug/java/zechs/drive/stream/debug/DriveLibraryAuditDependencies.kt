package zechs.drive.stream.debug

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import zechs.drive.stream.data.repository.CatalogRepository
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.utils.ProfileManager

/** Read-only dependencies for an explicitly invoked, real-account device audit. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DriveLibraryAuditDependencies {
    fun driveRepository(): DriveRepository
    fun catalogRepository(): CatalogRepository
    fun profileManager(): ProfileManager
}
