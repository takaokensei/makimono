package zechs.drive.stream.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

object MatroskaChapterParser {
    private const val TAG = "MatroskaChapterParser"

    enum class ChapterType {
        RECAP,
        OPENING,
        ENDING,
        OTHER
    }

    data class ParsedChapter(
        val index: Int,
        val title: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val type: ChapterType
    )

    suspend fun extractChapters(streamUrl: String, accessToken: String? = null): List<ParsedChapter> =
        withContext(Dispatchers.IO) {
            try {
                // First pass: Fetch the initial 1MB of the stream where EBML header, SeekHead,
                // and chapters typically reside.
                val initialBytes = fetchRange(streamUrl, accessToken, 0L, 1048576L)
                if (initialBytes == null || initialBytes.isEmpty()) {
                    return@withContext emptyList()
                }

                val parseResult = parseEbmlFromBytes(initialBytes)
                if (parseResult.chapters.isNotEmpty()) {
                    Log.d(TAG, "Successfully found ${parseResult.chapters.size} chapters in initial 1MB")
                    return@withContext parseResult.chapters
                }

                // If chapters were not in the first 1MB but SeekHead identified the offset:
                if (parseResult.seekHeadChaptersOffset != null && parseResult.segmentDataStartOffset != null) {
                    val targetOffset = parseResult.segmentDataStartOffset + parseResult.seekHeadChaptersOffset
                    Log.d(TAG, "SeekHead pointed to chapters at absolute offset: $targetOffset")

                    val chapterBytes = fetchRange(streamUrl, accessToken, targetOffset, targetOffset + 131072L)
                    if (chapterBytes != null && chapterBytes.isNotEmpty()) {
                        val remoteChapters = parseChaptersDirect(chapterBytes)
                        if (remoteChapters.isNotEmpty()) {
                            Log.d(TAG, "Parsed ${remoteChapters.size} chapters from remote offset $targetOffset")
                            return@withContext remoteChapters
                        }
                    }
                }

                emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to extract MKV chapters: ${e.message}")
                emptyList()
            }
        }

    private fun fetchRange(streamUrl: String, accessToken: String?, startByte: Long, endByte: Long): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(streamUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (!accessToken.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                setRequestProperty("Range", "bytes=$startByte-$endByte")
                connectTimeout = 6000
                readTimeout = 6000
            }

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                Log.w(TAG, "Failed to connect for chapters: HTTP $responseCode")
                return null
            }

            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRange failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private data class EbmlParseResult(
        val chapters: List<ParsedChapter>,
        val seekHeadChaptersOffset: Long?,
        val segmentDataStartOffset: Long?
    )

    private fun parseEbmlFromBytes(bytes: ByteArray): EbmlParseResult {
        val stream = CountingInputStream(ByteArrayInputStream(bytes))
        val rawChapters = mutableListOf<RawChapter>()
        var seekHeadChaptersOffset: Long? = null
        var segmentDataStartOffset: Long? = null

        try {
            var currentPos = 0L
            while (currentPos < bytes.size) {
                val id = readElementId(stream) ?: break
                val size = readElementSize(stream) ?: break
                currentPos = stream.bytesRead

                when (id.id) {
                    0x1A45DFA3L -> { // EBML Header
                        if (size.value > 0) skipBytes(stream, size.value)
                        currentPos = stream.bytesRead
                    }
                    0x18538067L -> { // Segment
                        segmentDataStartOffset = stream.bytesRead
                    }
                    0x114D9B74L -> { // SeekHead
                        val seekMap = parseSeekHead(stream, size.value)
                        seekHeadChaptersOffset = seekMap[0x1043A770L]
                        currentPos = stream.bytesRead
                    }
                    0x1043A770L -> { // Chapters
                        parseChaptersElement(stream, size.value, rawChapters)
                        break
                    }
                    0x1F43B675L -> { // Cluster (Media data begins)
                        break
                    }
                    else -> {
                        if (size.value > 0) skipBytes(stream, size.value)
                        currentPos = stream.bytesRead
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "EBML scan ended: ${e.message}")
        }

        val chapters = buildParsedChapters(rawChapters)
        return EbmlParseResult(chapters, seekHeadChaptersOffset, segmentDataStartOffset)
    }

    private fun parseChaptersDirect(bytes: ByteArray): List<ParsedChapter> {
        val stream = ByteArrayInputStream(bytes)
        val rawChapters = mutableListOf<RawChapter>()
        try {
            val id = readElementId(stream) ?: return emptyList()
            val size = readElementSize(stream) ?: return emptyList()
            if (id.id == 0x1043A770L) {
                parseChaptersElement(stream, size.value, rawChapters)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct chapters parse error: ${e.message}")
        }
        return buildParsedChapters(rawChapters)
    }

    private fun buildParsedChapters(rawChapters: List<RawChapter>): List<ParsedChapter> {
        if (rawChapters.isEmpty()) return emptyList()

        val sorted = rawChapters.sortedBy { it.startMs }
        val result = mutableListOf<ParsedChapter>()

        for (i in sorted.indices) {
            val curr = sorted[i]
            val nextDistinctStart = sorted.drop(i + 1).firstOrNull { it.startMs > curr.startMs }?.startMs
            val endMs = if (curr.endMs > curr.startMs) {
                curr.endMs
            } else if (nextDistinctStart != null) {
                nextDistinctStart
            } else {
                curr.startMs + 90_000L
            }

            val normalizedTitle = curr.title.lowercase(Locale.ROOT).trim()
            val isEndMarker = normalizedTitle.endsWith("end") || normalizedTitle.endsWith("fim") ||
                    normalizedTitle.endsWith("stop") || normalizedTitle.contains("recap end") ||
                    normalizedTitle.contains("credits end") || normalizedTitle.contains("op end") ||
                    normalizedTitle.contains("ed end") || normalizedTitle.contains("preview end") ||
                    normalizedTitle.endsWith("(end)") || normalizedTitle.endsWith("[end]")

            val isPrologueOrStory = normalizedTitle.contains("prologue") ||
                    normalizedTitle.contains("prologo") ||
                    normalizedTitle.contains("prólogo") ||
                    normalizedTitle.contains("avant") ||
                    normalizedTitle.contains("part a") ||
                    normalizedTitle.contains("part b") ||
                    normalizedTitle.contains("preview") ||
                    normalizedTitle.contains("epilogue") ||
                    normalizedTitle.contains("epilogo") ||
                    normalizedTitle.contains("epílogo")

            val type = if (isEndMarker || isPrologueOrStory) {
                ChapterType.OTHER
            } else when {
                normalizedTitle.contains("recap") ||
                normalizedTitle.contains("resumo") ||
                normalizedTitle.contains("previously") ||
                normalizedTitle.contains("anteriormente") -> ChapterType.RECAP

                normalizedTitle.contains("intro") ||
                normalizedTitle.contains("opening") ||
                normalizedTitle.contains("abertura") ||
                normalizedTitle.contains("title sequence") ||
                normalizedTitle.matches(Regex(".*\\b(op|ncop)\\d*\\b.*", RegexOption.IGNORE_CASE)) -> ChapterType.OPENING

                normalizedTitle.contains("ending") ||
                normalizedTitle.contains("credits") ||
                normalizedTitle.contains("créditos") ||
                normalizedTitle.contains("creditos") ||
                normalizedTitle.contains("outro") ||
                normalizedTitle.contains("encerramento") ||
                normalizedTitle.contains("closing") ||
                normalizedTitle.matches(Regex(".*\\b(ed|nced)\\d*\\b.*", RegexOption.IGNORE_CASE)) -> ChapterType.ENDING

                else -> ChapterType.OTHER
            }

            result.add(
                ParsedChapter(
                    index = i,
                    title = curr.title.ifBlank { "Capítulo ${i + 1}" },
                    startTimeMs = curr.startMs,
                    endTimeMs = endMs,
                    type = type
                )
            )
        }

        return result
    }

    private data class RawChapter(
        var title: String = "",
        var startMs: Long = 0L,
        var endMs: Long = 0L
    )

    private fun parseSeekHead(stream: InputStream, elementSize: Long): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        var bytesRead = 0L
        while (bytesRead < elementSize) {
            val id = readElementId(stream) ?: break
            val size = readElementSize(stream) ?: break
            bytesRead += id.byteCount + size.byteCount

            if (id.id == 0x4DBBL) { // Seek entry
                val (seekId, seekPos) = parseSeekEntry(stream, size.value)
                if (seekId != null && seekPos != null) {
                    result[seekId] = seekPos
                }
                bytesRead += size.value
            } else {
                skipBytes(stream, size.value)
                bytesRead += size.value
            }
        }
        return result
    }

    private fun parseSeekEntry(stream: InputStream, elementSize: Long): Pair<Long?, Long?> {
        var seekId: Long? = null
        var seekPos: Long? = null
        var bytesRead = 0L

        while (bytesRead < elementSize) {
            val id = readElementId(stream) ?: break
            val size = readElementSize(stream) ?: break
            bytesRead += id.byteCount + size.byteCount

            when (id.id) {
                0x53ABL -> { // SeekID
                    seekId = readUint(stream, size.value)
                    bytesRead += size.value
                }
                0x53ACL -> { // SeekPosition
                    seekPos = readUint(stream, size.value)
                    bytesRead += size.value
                }
                else -> {
                    skipBytes(stream, size.value)
                    bytesRead += size.value
                }
            }
        }
        return Pair(seekId, seekPos)
    }

    private fun parseChaptersElement(stream: InputStream, elementSize: Long, out: MutableList<RawChapter>) {
        var bytesRead = 0L
        while (bytesRead < elementSize) {
            val id = readElementId(stream) ?: break
            val size = readElementSize(stream) ?: break
            bytesRead += id.byteCount + size.byteCount

            if (id.id == 0x45B9L) { // EditionEntry
                parseEditionEntry(stream, size.value, out)
                bytesRead += size.value
            } else {
                skipBytes(stream, size.value)
                bytesRead += size.value
            }
        }
    }

    private fun parseEditionEntry(stream: InputStream, elementSize: Long, out: MutableList<RawChapter>) {
        var bytesRead = 0L
        while (bytesRead < elementSize) {
            val id = readElementId(stream) ?: break
            val size = readElementSize(stream) ?: break
            bytesRead += id.byteCount + size.byteCount

            if (id.id == 0xB6L) { // ChapterAtom
                val ch = parseChapterAtom(stream, size.value)
                out.add(ch)
                bytesRead += size.value
            } else {
                skipBytes(stream, size.value)
                bytesRead += size.value
            }
        }
    }

    private fun parseChapterAtom(stream: InputStream, elementSize: Long): RawChapter {
        val chapter = RawChapter()
        var bytesRead = 0L

        while (bytesRead < elementSize) {
            val id = readElementId(stream) ?: break
            val size = readElementSize(stream) ?: break
            bytesRead += id.byteCount + size.byteCount

            when (id.id) {
                0x91L -> { // ChapterTimeStart
                    val ns = readUint(stream, size.value)
                    chapter.startMs = ns / 1_000_000L
                    bytesRead += size.value
                }
                0x92L -> { // ChapterTimeEnd
                    val ns = readUint(stream, size.value)
                    chapter.endMs = ns / 1_000_000L
                    bytesRead += size.value
                }
                0x80L -> { // ChapterDisplay
                    parseChapterDisplay(stream, size.value, chapter)
                    bytesRead += size.value
                }
                else -> {
                    skipBytes(stream, size.value)
                    bytesRead += size.value
                }
            }
        }
        return chapter
    }

    private fun parseChapterDisplay(stream: InputStream, elementSize: Long, chapter: RawChapter) {
        var bytesRead = 0L
        while (bytesRead < elementSize) {
            val id = readElementId(stream) ?: break
            val size = readElementSize(stream) ?: break
            bytesRead += id.byteCount + size.byteCount

            if (id.id == 0x85L) { // ChapString
                val strBytes = ByteArray(size.value.toInt())
                readFully(stream, strBytes)
                chapter.title = String(strBytes, StandardCharsets.UTF_8)
                bytesRead += size.value
            } else {
                skipBytes(stream, size.value)
                bytesRead += size.value
            }
        }
    }

    data class EbmlVint(val id: Long, val value: Long, val byteCount: Int)

    private fun readElementId(stream: InputStream): EbmlVint? {
        val firstByte = stream.read()
        if (firstByte == -1) return null

        var numBytes = 1
        var mask = 0x80
        while (numBytes <= 4 && (firstByte and mask) == 0) {
            mask = mask shr 1
            numBytes++
        }
        if (numBytes > 4) return null

        var id = firstByte.toLong()
        for (i in 1 until numBytes) {
            val b = stream.read()
            if (b == -1) return null
            id = (id shl 8) or (b and 0xFF).toLong()
        }
        return EbmlVint(id = id, value = id, byteCount = numBytes)
    }

    private fun readElementSize(stream: InputStream): EbmlVint? {
        val firstByte = stream.read()
        if (firstByte == -1) return null

        var numBytes = 1
        var mask = 0x80
        while (numBytes <= 8 && (firstByte and mask) == 0) {
            mask = mask shr 1
            numBytes++
        }
        if (numBytes > 8) return null

        var value = (firstByte and mask.inv()).toLong()
        for (i in 1 until numBytes) {
            val b = stream.read()
            if (b == -1) return null
            value = (value shl 8) or (b and 0xFF).toLong()
        }
        return EbmlVint(id = 0, value = value, byteCount = numBytes)
    }

    private fun readUint(stream: InputStream, byteCount: Long): Long {
        var value = 0L
        for (i in 0 until byteCount) {
            val b = stream.read()
            if (b == -1) break
            value = (value shl 8) or (b and 0xFF).toLong()
        }
        return value
    }

    private fun skipBytes(stream: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
    }

    private class CountingInputStream(private val inner: InputStream) : InputStream() {
        var bytesRead = 0L
            private set

        override fun read(): Int {
            val b = inner.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = inner.read(b, off, len)
            if (r > 0) bytesRead += r
            return r
        }

        override fun skip(n: Long): Long {
            val s = inner.skip(n)
            if (s > 0) bytesRead += s
            return s
        }
    }
}