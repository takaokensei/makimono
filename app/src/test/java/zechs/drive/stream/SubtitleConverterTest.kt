package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.utils.SubtitleConverter

class SubtitleConverterTest {

    @Test
    fun parseAssTime_usesCentiseconds() {
        val ms = SubtitleConverter.parseAssTimeToMs("0:11:49.58")
        assertEquals(709_580L, ms)
    }

    @Test
    fun parseAssTime_secondExampleFromReport() {
        val start = SubtitleConverter.parseAssTimeToMs("0:11:49.58")
        val end = SubtitleConverter.parseAssTimeToMs("0:11:53.25")
        assertEquals(370L, SubtitleConverter.parseAssTimeToMs("0:11:53.62") - end)
        assert(end > start)
    }
}
