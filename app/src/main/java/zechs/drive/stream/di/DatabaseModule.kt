package zechs.drive.stream.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import zechs.drive.stream.data.local.WatchListDao
import zechs.drive.stream.data.local.WatchListDatabase
import zechs.drive.stream.data.repository.WatchListRepository
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
            WatchListDatabase.MIGRATION_3_4
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
    ): zechs.drive.stream.data.local.FolderMetadataDao {
        return db.getFolderMetadataDao()
    }

    @Singleton
    @Provides
    fun provideFavoriteDao(
        db: WatchListDatabase
    ): zechs.drive.stream.data.local.FavoriteDao {
        return db.getFavoriteDao()
    }

    @Singleton
    @Provides
    fun provideWatchListRepository(
        watchListDao: WatchListDao
    ) = WatchListRepository(watchListDao)

    @Singleton
    @Provides
    fun provideFolderMetadataRepository(
        folderMetadataDao: zechs.drive.stream.data.local.FolderMetadataDao
    ) = zechs.drive.stream.data.repository.FolderMetadataRepository(folderMetadataDao)

    @Singleton
    @Provides
    fun provideFavoriteRepository(
        favoriteDao: zechs.drive.stream.data.local.FavoriteDao
    ) = zechs.drive.stream.data.repository.FavoriteRepository(favoriteDao)

}