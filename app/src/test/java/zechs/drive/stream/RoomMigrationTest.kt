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
}
