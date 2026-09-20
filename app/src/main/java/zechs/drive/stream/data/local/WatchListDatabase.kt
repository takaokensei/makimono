package zechs.drive.stream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem

@Database(
    entities = [
        WatchList::class,
        FolderMetadata::class,
        FavoriteFolder::class,
        WatchQueueItem::class,
        CatalogEntry::class,
        FollowedFolder::class
    ],
    version = 6,
    exportSchema = true
)
abstract class WatchListDatabase : RoomDatabase() {

    abstract fun getWatchListDao(): WatchListDao
    abstract fun getFolderMetadataDao(): FolderMetadataDao
    abstract fun getFavoriteDao(): FavoriteDao
    abstract fun getWatchQueueDao(): WatchQueueDao
    abstract fun getCatalogDao(): CatalogDao
    abstract fun getFollowedFolderDao(): FollowedFolderDao
    abstract fun getProfileCleanupDao(): ProfileCleanupDao

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

        /**
         * v4 -> v5:
         * 1. Added profileId to watch_list and index (profileId, videoId)
         * 2. Migrated favorite_folder to composite primary key (profileId, folderId)
         * 3. Created watch_queue table for FEAT-03
         * 4. Created catalog_entry table for FEAT-04
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add profileId column to watch_list and create index
                db.execSQL("ALTER TABLE `watch_list` ADD COLUMN `profileId` TEXT NOT NULL DEFAULT 'caua'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_list_profileId_videoId` ON `watch_list` (`profileId`, `videoId`)")

                // 2. Migrate favorite_folder to composite primary key (profileId, folderId)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_folder_new` (
                        `profileId` TEXT NOT NULL DEFAULT 'caua',
                        `folderId` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL DEFAULT '',
                        `isFavorite` INTEGER NOT NULL DEFAULT 1,
                        `addedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`profileId`, `folderId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `favorite_folder_new` (`profileId`, `folderId`, `folderName`, `isFavorite`, `addedAt`)
                    SELECT 'caua', `folderId`, `folderName`, `isFavorite`, `addedAt` FROM `favorite_folder`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `favorite_folder`")
                db.execSQL("ALTER TABLE `favorite_folder_new` RENAME TO `favorite_folder`")

                // 3. Create watch_queue table for FEAT-03 (Assistir depois)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_queue` (
                        `profileId` TEXT NOT NULL DEFAULT 'caua',
                        `fileId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `posterUrl` TEXT,
                        `orderIndex` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` INTEGER NOT NULL DEFAULT 0,
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_watch_queue_profileId_fileId` ON `watch_queue` (`profileId`, `fileId`)")

                // 4. Create catalog_entry table for FEAT-04 (Cache offline)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_entry` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `parentId` TEXT,
                        `posterUrl` TEXT,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v5 -> v6: FEAT-05 — Creates followed_folder table for periodic new-episode checks.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `followed_folder` (
                        `profileId` TEXT NOT NULL,
                        `folderId` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `lastKnownCount` INTEGER NOT NULL DEFAULT 0,
                        `followedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`profileId`, `folderId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_followed_folder_profileId` ON `followed_folder` (`profileId`)"
                )
            }
        }
    }
}