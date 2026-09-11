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
        // v1 -> v2 added WatchList.thumbnailLink; the watch list is a local
        // cache of playback progress, not source-of-truth data, so it's safe
        // to just rebuild it instead of writing a Migration.
        .fallbackToDestructiveMigration()
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
    fun provideWatchListRepository(
        watchListDao: WatchListDao
    ) = WatchListRepository(watchListDao)

    @Singleton
    @Provides
    fun provideFolderMetadataRepository(
        folderMetadataDao: zechs.drive.stream.data.local.FolderMetadataDao
    ) = zechs.drive.stream.data.repository.FolderMetadataRepository(folderMetadataDao)

}