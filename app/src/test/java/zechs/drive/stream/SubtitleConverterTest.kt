package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.utils.SubtitleConverter
import zechs.drive.stream.utils.SubtitleConverter.TimedLine

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

    @Test
    fun parseAssTime_leadingZeroOmitted() {
        val ms = SubtitleConverter.parseAssTimeToMs("0:11:08.58")
        assertEquals(668580L, ms)
    }

    // ---- Cues simultâneos / sobrepostos: nunca podem coexistir no tempo -------------------------

    /** Nenhum par de cues de saída pode estar ativo ao mesmo tempo. */
    private fun assertNoOverlap(cues: List<TimedLine>) {
        cues.zipWithNext().forEach { (a, b) ->
            assertTrue("Sobreposição entre $a e $b", a.endMs <= b.startMs)
        }
    }

    @Test
    fun mergeOverlappingCues_sameTimestamp_stacksWithLineBreak() {
        // Exemplo do relato: mesmas marcas de tempo, duas linhas de Dialogue.
        val start = SubtitleConverter.parseAssTimeToMs("0:11:01.12")
        val end = SubtitleConverter.parseAssTimeToMs("0:11:07.41")
        val input = listOf(
            TimedLine(start, end, "Com isso, amigos,"),
            TimedLine(start, end, "vamos realizar aquela tradição agora mesmo!"),
            TimedLine(
                SubtitleConverter.parseAssTimeToMs("0:11:07.66"),
                SubtitleConverter.parseAssTimeToMs("0:11:08.58"),
                "\"Tradição\"?"
            )
        )
        val out = SubtitleConverter.mergeOverlappingCues(input)
        assertEquals(2, out.size)
        assertEquals(TimedLine(start, end, "Com isso, amigos,\nvamos realizar aquela tradição agora mesmo!"), out[0])
        assertEquals("\"Tradição\"?", out[1].text)
        assertNoOverlap(out)
    }

    @Test
    fun mergeOverlappingCues_sequentialCues_areNotMerged() {
        val input = listOf(
            TimedLine(1_000, 2_000, "A"),
            TimedLine(3_000, 4_000, "B")
        )
        assertEquals(input, SubtitleConverter.mergeOverlappingCues(input))
    }

    @Test
    fun mergeOverlappingCues_touchingCues_areNotMerged() {
        // Fim de um == início do outro: é sequência, não sobreposição.
        val input = listOf(
            TimedLine(1_000, 2_000, "A"),
            TimedLine(2_000, 3_000, "B")
        )
        assertEquals(input, SubtitleConverter.mergeOverlappingCues(input))
    }

    @Test
    fun mergeOverlappingCues_partialOverlapBelowHalf_isSplitWithoutOverlap() {
        // Antes: só mesclava com >50% de sobreposição, deixando A e B simultâneos em 4500–5000.
        val out = SubtitleConverter.mergeOverlappingCues(
            listOf(TimedLine(1_000, 5_000, "Fala A"), TimedLine(4_500, 8_000, "Fala B"))
        )
        assertEquals(
            listOf(
                TimedLine(1_000, 4_500, "Fala A"),
                TimedLine(4_500, 5_000, "Fala A\nFala B"),
                TimedLine(5_000, 8_000, "Fala B")
            ),
            out
        )
        assertNoOverlap(out)
    }

    @Test
    fun mergeOverlappingCues_chainedOverlaps_produceNoOverlap() {
        val out = SubtitleConverter.mergeOverlappingCues(
            listOf(TimedLine(0, 4_000, "A"), TimedLine(3_000, 7_000, "B"), TimedLine(6_000, 9_000, "C"))
        )
        assertEquals(
            listOf(
                TimedLine(0, 3_000, "A"),
                TimedLine(3_000, 4_000, "A\nB"),
                TimedLine(4_000, 6_000, "B"),
                TimedLine(6_000, 7_000, "B\nC"),
                TimedLine(7_000, 9_000, "C")
            ),
            out
        )
        assertNoOverlap(out)
    }

    @Test
    fun mergeOverlappingCues_threeSimultaneousCues_keepOriginalOrder() {
        val out = SubtitleConverter.mergeOverlappingCues(
            listOf(TimedLine(0, 4_000, "L1"), TimedLine(0, 4_000, "L2"), TimedLine(0, 4_000, "L3"))
        )
        assertEquals(listOf(TimedLine(0, 4_000, "L1\nL2\nL3")), out)
    }

    @Test
    fun mergeOverlappingCues_duplicateText_isDeduplicated() {
        val out = SubtitleConverter.mergeOverlappingCues(
            listOf(TimedLine(1_000, 3_000, "Oi"), TimedLine(1_000, 3_000, "Oi"))
        )
        assertEquals(listOf(TimedLine(1_000, 3_000, "Oi")), out)
    }

    @Test
    fun mergeOverlappingCues_unsortedInput_isHandled() {
        val out = SubtitleConverter.mergeOverlappingCues(
            listOf(TimedLine(3_000, 5_000, "B"), TimedLine(1_000, 4_000, "A"))
        )
        assertNoOverlap(out)
        assertEquals(1_000L, out.first().startMs)
        assertEquals(5_000L, out.last().endMs)
    }

    // ---- Normalização de quebras de linha (\r\n, \r, \n, \N do ASS) ---------------------------------

    @Test
    fun mergeText_normalizesCrLfAndLfAndCr() {
        assertEquals("a\nb\nc", SubtitleConverter.mergeText("a\r\nb", "c"))
        assertEquals("a\nb\nc", SubtitleConverter.mergeText("a\nb", "c"))
        assertEquals("a\nb\nc", SubtitleConverter.mergeText("a\rb", "c"))
    }

    @Test
    fun mergeText_crlfAndLf_doNotProduceDuplicates() {
        // "x\r\ny" e "x\ny" representam as mesmas linhas.
        assertEquals("x\ny", SubtitleConverter.mergeText("x\r\ny", "x\ny"))
    }

    @Test
    fun cleanAssText_convertsAssBreaksAndStripsTags() {
        assertEquals("Linha 1\nLinha 2", SubtitleConverter.cleanAssText("{\\an2}Linha 1\\NLinha 2"))
        assertEquals("Linha 1\nLinha 2", SubtitleConverter.cleanAssText("Linha 1\r\nLinha 2"))
        assertEquals("a b", SubtitleConverter.cleanAssText("a\\hb"))
    }
}
