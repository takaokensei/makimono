package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.MediaSource
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred

class MediaUriResolverTest {

    @Test
    fun isLocalUri_identifiesContentAndFileSchemes() {
        assertTrue(MediaUriResolver.isLocalUri("content://media/external/video/media/42"))
        assertTrue(MediaUriResolver.isLocalUri("file:///storage/emulated/0/Movies/show.mkv"))
        assertTrue(MediaUriResolver.isLocalUri("/storage/emulated/0/Movies/show.mkv"))
        assertFalse(MediaUriResolver.isLocalUri("https://www.googleapis.com/drive/v3/files/xyz"))
        assertFalse(MediaUriResolver.isLocalUri(null))
        assertFalse(MediaUriResolver.isLocalUri(""))
    }

    @Test
    fun isLocalId_identifiesLocalPrefix() {
        assertTrue(MediaUriResolver.isLocalId("local:content://media/external/video/media/42"))
        assertTrue(MediaUriResolver.isLocalId("local:hash_1234"))
        assertFalse(MediaUriResolver.isLocalId("1jO_drive_file_id_567"))
        assertFalse(MediaUriResolver.isLocalId(null))
    }

    @Test
    fun resolveStreamUri_forDriveFile_returnsDriveApiUrl() {
        val driveUri = MediaUriResolver.resolveStreamUri("drive_123")
        assertEquals("https://www.googleapis.com/drive/v3/files/drive_123?supportsAllDrives=True&alt=media", driveUri)
    }

    @Test
    fun resolveStreamUri_forLocalFileWithUri_returnsLocalUriDirectly() {
        val contentUri = "content://media/external/video/media/99"
        val resolved = MediaUriResolver.resolveStreamUri("local:item99", contentUri)
        assertEquals(contentUri, resolved)
    }

    @Test
    fun resolveStreamUri_forDriveFileModel_respectsSource() {
        val localFile = DriveFile(
            id = "local:file_1",
            name = "Anime.S01E01.mkv",
            size = 500_000_000L,
            mimeType = "video/x-matroska",
            iconLink = null,
            shortcutDetails = ShortcutDetails(null, null),
            starred = Starred.UNSTARRED,
            source = MediaSource.LOCAL,
            localUri = "content://media/external/video/media/100"
        )
        val driveFile = DriveFile(
            id = "drive_file_abc",
            name = "Movie.mp4",
            size = 1_000_000_000L,
            mimeType = "video/mp4",
            iconLink = null,
            shortcutDetails = ShortcutDetails(null, null),
            starred = Starred.UNSTARRED,
            source = MediaSource.DRIVE
        )

        assertEquals("content://media/external/video/media/100", MediaUriResolver.resolveStreamUri(localFile))
        assertEquals("https://www.googleapis.com/drive/v3/files/drive_file_abc?supportsAllDrives=True&alt=media", MediaUriResolver.resolveStreamUri(driveFile))
    }
}
