package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.utils.ThumbnailUrl

class ThumbnailUrlTest {
    @Test
    fun replacesDriveSizeSuffixWithoutAppendingGarbage() {
        val source = "https://lh3.googleusercontent.com/file=s220"
        assertEquals(
            "https://lh3.googleusercontent.com/file=s720",
            ThumbnailUrl.large(source)
        )
    }

    @Test
    fun keepsUnknownUrlShapeUntouched() {
        val source = "https://cdn.example.test/poster.jpg?token=abc"
        assertEquals(source, ThumbnailUrl.poster(source))
    }

    @Test
    fun handlesEmptyUrl() {
        assertNull(ThumbnailUrl.medium(null))
        assertNull(ThumbnailUrl.medium(""))
    }
}
