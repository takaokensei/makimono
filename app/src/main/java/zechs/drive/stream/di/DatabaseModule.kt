package zechs.drive.stream.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import zechs.drive.stream.data.local.CatalogDao
import zechs.drive.stream.data.local.FavoriteDao
import zechs.drive.stream.data.local.FollowedFolderDao
import zechs.drive.stream.data.local.FolderMetadataDao
import zechs.drive.stream.data.local.WatchListDao
import zechs.drive.stream.data.local.WatchListDatabase
import zechs.drive.stream.data.local.WatchQueueDao
import zechs.drive.stream.data.repository.CatalogRepository
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.repository.FolderMetadataRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.utils.ProfileManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val WATCHLIST_DATABASE_NAME = "watch_list.db"

    @Singleton
    @Provides
    fun provideWatchListDatabase(
        @ApplicationContext appContext: Context
    ) = Room.databaseBuilder(
        appContext,
        WatchListDatabase::class.java,
        WATCHLIST_DATABASE_NAME
    )
        .addMigrations(
            WatchListDatabase.MIGRATION_1_2,
            WatchListDatabase.MIGRATION_2_3,
            WatchListDatabase.MIGRATION_3_4,
            WatchListDatabase.MIGRATION_4_5,
            WatchListDatabase.MIGRATION_5_6
        )
        .build()

    @Singleton
    @Provides
    fun provideWatchListDao(
        db: WatchListDatabase
    ): WatchListDao {
        return db.getWatchListDao()
    }

    @Singleton
    @Provides
    fun provideFolderMetadataDao(
        db: WatchListDatabase
    ): FolderMetadataDao {
        return db.getFolderMetadataDao()
    }

    @Singleton
    @Provides
    fun provideFavoriteDao(
        db: WatchListDatabase
    ): FavoriteDao {
        return db.getFavoriteDao()
    }

    @Singleton
    @Provides
    fun provideWatchQueueDao(
        db: WatchListDatabase
    ): WatchQueueDao {
        return db.getWatchQueueDao()
    }

    @Singleton
    @Provides
    fun provideCatalogDao(
        db: WatchListDatabase
    ): CatalogDao {
        return db.getCatalogDao()
    }

    @Singleton
    @Provides
    fun provideWatchListRepository(
        watchListDao: WatchListDao,
        profileManager: ProfileManager
    ) = WatchListRepository(watchListDao, profileManager)

    @Singleton
    @Provides
    fun provideFolderMetadataRepository(
        folderMetadataDao: FolderMetadataDao
    ) = FolderMetadataRepository(folderMetadataDao)

    @Singleton
    @Provides
    fun provideFavoriteRepository(
        favoriteDao: FavoriteDao,
        profileManager: ProfileManager
    ) = FavoriteRepository(favoriteDao, profileManager)

    @Singleton
    @Provides
    fun provideWatchQueueRepository(
        watchQueueDao: WatchQueueDao,
        profileManager: ProfileManager
    ) = WatchQueueRepository(watchQueueDao, profileManager)

    @Singleton
    @Provides
    fun provideCatalogRepository(
        catalogDao: CatalogDao
    ) = CatalogRepository(catalogDao)

    @Singleton
    @Provides
    fun provideFollowedFolderDao(
        db: WatchListDatabase
    ): FollowedFolderDao = db.getFollowedFolderDao()

    @Singleton
    @Provides
    fun provideFollowedFolderRepository(
        followedFolderDao: FollowedFolderDao,
        profileManager: ProfileManager
    ) = FollowedFolderRepository(followedFolderDao, profileManager)

}