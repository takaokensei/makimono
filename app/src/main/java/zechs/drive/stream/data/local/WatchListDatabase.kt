package zechs.drive.stream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import zechs.drive.stream.data.model.WatchList

@Database(
    entities = [WatchList::class, FolderMetadata::class, FavoriteFolder::class],
    version = 4,
    exportSchema = true
)
abstract class WatchListDatabase : RoomDatabase() {

    abstract fun getWatchListDao(): WatchListDao
    abstract fun getFolderMetadataDao(): FolderMetadataDao
    abstract fun getFavoriteDao(): FavoriteDao

    companion object {
        /**
         * v1 -> v2: added WatchList.thumbnailLink
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_list ADD COLUMN thumbnailLink TEXT")
            }
        }

        /**
         * v2 -> v3: added folder_metadata table for cached posters & last opened timestamps
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `folder_metadata` (
                        `folderId` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `posterUrl` TEXT,
                        `lastOpened` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`folderId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 -> v4: added favorite_folder table for SEC-03 local favorites
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_folder` (
                        `folderId` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL DEFAULT '',
                        `isFavorite` INTEGER NOT NULL DEFAULT 1,
                        `addedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`folderId`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}