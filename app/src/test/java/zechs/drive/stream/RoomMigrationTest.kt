package zechs.drive.stream

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import zechs.drive.stream.data.local.WatchListDatabase

class RoomMigrationTest {

    @Test
    fun migration_1_2_addsThumbnailLinkColumn() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        WatchListDatabase.MIGRATION_1_2.migrate(db)

        verify(exactly = 1) {
            db.execSQL("ALTER TABLE watch_list ADD COLUMN thumbnailLink TEXT")
        }
    }

    @Test
    fun migration_2_3_createsFolderMetadataTable() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        WatchListDatabase.MIGRATION_2_3.migrate(db)

        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `folder_metadata`") })
        }
    }

    @Test
    fun migration_3_4_createsFavoriteFolderTable() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        WatchListDatabase.MIGRATION_3_4.migrate(db)

        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `favorite_folder`") })
        }
    }

    @Test
    fun migration_4_5_addsProfileIdQueueAndCatalog() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        WatchListDatabase.MIGRATION_4_5.migrate(db)

        verify(exactly = 1) {
            db.execSQL("ALTER TABLE `watch_list` ADD COLUMN `profileId` TEXT NOT NULL DEFAULT 'caua'")
        }
        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `watch_queue`") })
        }
        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `catalog_entry`") })
        }
        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `favorite_folder_new`") })
        }
    }

    @Test
    fun migration_5_6_createsFollowedFolderTable() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        WatchListDatabase.MIGRATION_5_6.migrate(db)

        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS `followed_folder`") })
        }
        verify(exactly = 1) {
            db.execSQL(match { it.contains("CREATE INDEX IF NOT EXISTS `index_followed_folder_profileId`") })
        }
    }
}
