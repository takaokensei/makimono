package zechs.drive.stream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import zechs.drive.stream.data.model.WatchList


@Database(
    entities = [WatchList::class, FolderMetadata::class],
    version = 3,
    exportSchema = false
)
abstract class WatchListDatabase : RoomDatabase() {

    abstract fun getWatchListDao(): WatchListDao
    abstract fun getFolderMetadataDao(): FolderMetadataDao

}