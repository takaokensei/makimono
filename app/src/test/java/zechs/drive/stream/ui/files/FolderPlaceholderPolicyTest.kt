package zechs.drive.stream.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPlaceholderPolicyTest {

    @Test
    fun standardFolderName_returnsFirstTwoLettersUppercase() {
        assertEquals("JU", FolderPlaceholderPolicy.initials("Jujutsu Kaisen"))
        assertEquals("BL", FolderPlaceholderPolicy.initials("Bleach"))
    }

    @Test
    fun singleLetterName_returnsLetter() {
        assertEquals("X", FolderPlaceholderPolicy.initials("X"))
    }

    @Test
    fun symbolsOrBrackets_cleansAndReturnsLetters() {
        assertEquals("SN", FolderPlaceholderPolicy.initials("[1080p] Shingeki no Kyojin"))
        assertEquals("FA", FolderPlaceholderPolicy.initials("★ Fate/stay night"))
    }

    @Test
    fun blankOrEmpty_returnsDefaultPlaceholder() {
        assertEquals("MK", FolderPlaceholderPolicy.initials(""))
        assertEquals("MK", FolderPlaceholderPolicy.initials("   "))
    }
}
