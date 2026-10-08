package zechs.drive.stream.ui.series

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynopsisDisclosurePolicyTest {
    @Test
    fun keepsDisclosureHiddenWhenSynopsisFitsWithinCollapsedLines() {
        assertFalse(SynopsisDisclosurePolicy.isTruncated(lineCount = 2, ellipsisCount = 0, maxLines = 3))
        assertFalse(SynopsisDisclosurePolicy.isTruncated(lineCount = 3, ellipsisCount = 0, maxLines = 3))
    }

    @Test
    fun showsDisclosureWhenLastVisibleLineIsEllipsizedOrContentHasMoreLines() {
        assertTrue(SynopsisDisclosurePolicy.isTruncated(lineCount = 3, ellipsisCount = 8, maxLines = 3))
        assertTrue(SynopsisDisclosurePolicy.isTruncated(lineCount = 4, ellipsisCount = 0, maxLines = 3))
    }
}
