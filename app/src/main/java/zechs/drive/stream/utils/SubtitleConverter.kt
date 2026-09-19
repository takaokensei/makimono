package zechs.drive.stream.utils

import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import zechs.drive.stream.data.model.SubtitleItem
import java.io.File

object SubtitleConverter {

    private const val TAG = "SubtitleConverter"
    private const val CONVERTER_VERSION = "v3"

    internal data class TimedLine(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    /**
     * Prepares an external subtitle file for ExoPlayer.
     * ExoPlayer's built-in SsaDecoder fails on modern complex ASS subtitles (e.g. from Crunchyroll/Erai-raws).
     * Converting them to clean SubRip (.srt) allows ExoPlayer's SubripDecoder to render them reliably.
     *
     * @return Pair of (Uri to load, MimeType)
     */
    fun prepareSubtitleForExoPlayer(
        cacheDir: File,
        sub: SubtitleItem,
        sourceFile: File,
        bridgeGapMs: Long = 450L,
        delayMs: Long = 0L
    ): Pair<Uri, String> {
        val ext = sub.ext.lowercase()
        if (ext == "ass" || ext == "ssa") {
            val cacheKey = if (delayMs != 0L) "${sub.id}_${CONVERTER_VERSION}_delay${delayMs}" else "${sub.id}_${CONVERTER_VERSION}"
            val srtFile = File(cacheDir, "subtitles/${cacheKey}_converted.srt")
            if (srtFile.exists() && srtFile.length() > 0) {
                return Pair(Uri.fromFile(srtFile), MimeTypes.APPLICATION_SUBRIP)
            }

            val success = convertAssToSrt(sourceFile, srtFile, bridgeGapMs, delayMs)
            if (success && srtFile.exists() && srtFile.length() > 0) {
                Log.d(TAG, "Successfully converted ${sub.name} to SRT for ExoPlayer: ${srtFile.absolutePath}")
                return Pair(Uri.fromFile(srtFile), MimeTypes.APPLICATION_SUBRIP)
            }
            Log.w(TAG, "Failed converting ASS to SRT, falling back to original file with TEXT_SSA")
            return Pair(Uri.fromFile(sourceFile), MimeTypes.TEXT_SSA)
        }

        // Apply delay to SRT files if needed
        if (ext == "srt" && delayMs != 0L) {
            val cacheKey = "${sub.id}_delay${delayMs}"
            val delayedFile = File(cacheDir, "subtitles/${cacheKey}.srt")
            if (delayedFile.exists() && delayedFile.length() > 0) {
                return Pair(Uri.fromFile(delayedFile), MimeTypes.APPLICATION_SUBRIP)
            }
            
            val success = shiftSrtTimestamps(sourceFile, delayedFile, delayMs)
            if (success && delayedFile.exists() && delayedFile.length() > 0) {
                Log.d(TAG, "Successfully applied ${delayMs}ms delay to ${sub.name}")
                return Pair(Uri.fromFile(delayedFile), MimeTypes.APPLICATION_SUBRIP)
            }
        }

        val mimeType = when (ext) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        return Pair(Uri.fromFile(sourceFile), mimeType)
    }

    private fun convertAssToSrt(assFile: File, outputFile: File, bridgeGapMs: Long, delayMs: Long = 0L): Boolean {
        return try {
            val lines = assFile.readLines(Charsets.UTF_8)
            val events = mutableListOf<TimedLine>()
            var inEvents = false
            var startIndex = 1
            var endIndex = 2
            var textIndex = 9

            for (line in lines) {
                val cleanLine = line.removePrefix("\uFEFF").trim()
                if (cleanLine.equals("[Events]", ignoreCase = true) || cleanLine.endsWith("[Events]", ignoreCase = true)) {
                    inEvents = true
                    continue
                }
                if (!inEvents) continue

                if (cleanLine.startsWith("Format:", ignoreCase = true)) {
                    val fields = cleanLine.substringAfter(":").split(",").map { it.trim().lowercase() }
                    val s = fields.indexOf("start")
                    if (s != -1) startIndex = s
                    val e = fields.indexOf("end")
                    if (e != -1) endIndex = e
                    val t = fields.indexOf("text")
                    if (t != -1) textIndex = t
                    continue
                }

                if (cleanLine.startsWith("Dialogue:", ignoreCase = true)) {
                    val payload = cleanLine.substringAfter("Dialogue:").trim()
                    val maxCol = maxOf(textIndex, startIndex, endIndex)
                    // Split carefully - text field may contain commas within curly braces
                    val parts = parseAssDialogueLine(payload, maxCol)
                    if (parts.size > maxCol) {
                        val startRaw = parts[startIndex].trim()
                        val endRaw = parts[endIndex].trim()
                        val rawText = parts.drop(textIndex).joinToString(",")

                        val startMs = parseAssTimeToMs(startRaw)
                        val endMs = parseAssTimeToMs(endRaw)
                        val cleanText = cleanAssText(rawText)

                        // Apply delay if specified
                        val adjustedStartMs = maxOf(0L, startMs + delayMs)
                        val adjustedEndMs = maxOf(0L, endMs + delayMs)

                        if (cleanText.isNotBlank() && adjustedEndMs > adjustedStartMs) {
                            events.add(TimedLine(adjustedStartMs, adjustedEndMs, cleanText))
                        }
                    }
                }
            }

            if (events.isEmpty()) return false

            // Sort by start time and process for overlapping cues and small gaps
            val sorted = events.sortedBy { it.startMs }
            val merged = mergeOverlappingCues(sorted)
            val bridged = bridgeSmallGaps(merged, bridgeGapMs)
            
            val srtBuilder = StringBuilder()
            var counter = 1
            for (event in bridged) {
                srtBuilder.append(counter++).append("\n")
                srtBuilder.append(formatMsToSrtTime(event.startMs))
                    .append(" --> ")
                    .append(formatMsToSrtTime(event.endMs))
                    .append("\n")
                srtBuilder.append(event.text).append("\n\n")
            }

            outputFile.parentFile?.mkdirs()
            outputFile.writeText(srtBuilder.toString(), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ASS file: ${assFile.name}", e)
            false
        }
    }

    /**
     * Parses ASS dialogue line carefully, handling commas within curly braces.
     * ASS format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
     */
    private fun parseAssDialogueLine(payload: String, maxCol: Int): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inBraces = false
        var colCount = 0
        
        for (char in payload) {
            when {
                char == '{' -> {
                    inBraces = true
                    current.append(char)
                }
                char == '}' -> {
                    inBraces = false
                    current.append(char)
                }
                char == ',' && !inBraces -> {
                    result.add(current.toString())
                    current = StringBuilder()
                    colCount++
                    // If we've reached the text column (last column), collect the rest
                    if (colCount >= maxCol - 1) {
                        val remaining = payload.substring(payload.indexOf(char) + 1)
                        result.add(remaining)
                        return result
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        
        return result
    }

    /**
     * Merges overlapping or nearly-overlapping cues to prevent subtitle clutter.
     * Cues that overlap significantly (>50% overlap) are merged into a single cue.
     */
    private fun mergeOverlappingCues(events: List<TimedLine>): List<TimedLine> {
        if (events.size <= 1) return events
        
        val result = mutableListOf<TimedLine>()
        var current = events[0]
        
        for (i in 1 until events.size) {
            val next = events[i]
            val overlapDuration = minOf(current.endMs, next.endMs) - maxOf(current.startMs, next.startMs)
            val currentDuration = current.endMs - current.startMs
            val nextDuration = next.endMs - next.startMs
            
            // Merge if there's significant overlap (>50% of either cue)
            val shouldMerge = overlapDuration > 0 && 
                             (overlapDuration > currentDuration * 0.5 || overlapDuration > nextDuration * 0.5)
            
            if (shouldMerge) {
                // Merge the text and extend the time range
                val mergedText = mergeText(current.text, next.text)
                current = current.copy(
                    endMs = maxOf(current.endMs, next.endMs),
                    text = mergedText
                )
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        
        return result
    }

    /**
     * Merges text from two cues, avoiding duplicates and combining lines logically.
     */
    private fun mergeText(text1: String, text2: String): String {
        val lines1 = text1.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val lines2 = text2.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Combine unique lines
        val combined = (lines1 + lines2).distinct()
        
        return combined.joinToString("\n")
    }

    /**
     * Extends a cue slightly when the next one starts soon after, avoiding flicker between ASS split lines.
     * Uses intelligent gap bridging: smaller gaps are bridged more aggressively, larger gaps only if they seem intentional.
     */
    internal fun bridgeSmallGaps(events: List<TimedLine>, maxGapMs: Long): List<TimedLine> {
        if (events.size <= 1 || maxGapMs <= 0L) return events
        val result = events.toMutableList()
        
        for (i in 0 until result.size - 1) {
            val current = result[i]
            val next = result[i + 1]
            val gap = next.startMs - current.endMs
            
            // Don't bridge if the gap is negative (overlapping)
            if (gap <= 0) continue
            
            // Adaptive bridging: smaller gaps are always bridged, larger gaps only if they seem like split lines
            val shouldBridge = when {
                gap <= 200 -> true // Very small gaps are always bridged
                gap <= maxGapMs -> {
                    // For medium gaps, check if this looks like a split line
                    // Split lines often have similar duration and are part of the same "thought"
                    val currentDuration = current.endMs - current.startMs
                    val nextDuration = next.endMs - next.startMs
                    val durationRatio = minOf(currentDuration, nextDuration).toFloat() / maxOf(currentDuration, nextDuration)
                    
                    // Bridge if durations are similar (likely split) or if the gap is very small
                    durationRatio > 0.7f || gap <= 300
                }
                else -> false
            }
            
            if (shouldBridge) {
                result[i] = current.copy(endMs = next.startMs)
            }
        }
        return result
    }

    /**
     * Shifts SRT subtitle timestamps by [offsetMs] (Kodi Subtitle Delay feature).
     */
    fun shiftSrtTimestamps(sourceFile: File, outputFile: File, offsetMs: Long): Boolean {
        return try {
            val lines = sourceFile.readLines(Charsets.UTF_8)
            val srtPattern = Regex("""(\d{2}:\d{2}:\d{2}[,\.]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[,\.]\d{3})""")
            val result = StringBuilder()

            for (line in lines) {
                val match = srtPattern.find(line)
                if (match != null) {
                    val startMs = parseSrtTimeToMs(match.groupValues[1])
                    val endMs = parseSrtTimeToMs(match.groupValues[2])
                    val newStartMs = maxOf(0L, startMs + offsetMs)
                    val newEndMs = maxOf(0L, endMs + offsetMs)
                    val newLine = "${formatMsToSrtTime(newStartMs)} --> ${formatMsToSrtTime(newEndMs)}"
                    result.append(newLine).append("\n")
                } else {
                    result.append(line).append("\n")
                }
            }

            outputFile.parentFile?.mkdirs()
            outputFile.writeText(result.toString(), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed shifting SRT timestamps", e)
            false
        }
    }

    internal fun parseAssTimeToMs(assTime: String): Long {
        val parts = assTime.trim().split(":")
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secParts = parts[2].split(".")
        val seconds = secParts[0].toLongOrNull() ?: 0L
        // ASS fractional part is centiseconds (2 digits), not milliseconds.
        val centis = if (secParts.size > 1) {
            secParts[1].take(2).padEnd(2, '0').toLongOrNull() ?: 0L
        } else {
            0L
        }
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + centis * 10L
    }

    private fun parseSrtTimeToMs(time: String): Long {
        val parts = time.replace('.', ',').split(":")
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secParts = parts[2].split(",")
        val seconds = secParts[0].toLongOrNull() ?: 0L
        val millis = if (secParts.size > 1) secParts[1].take(3).padEnd(3, '0').toLongOrNull() ?: 0L else 0L
        return hours * 3600_000L + minutes * 60_000L + seconds * 1000L + millis
    }

    private fun formatMsToSrtTime(ms: Long): String {
        val hours = ms / 3600_000L
        val minutes = (ms % 3600_000L) / 60_000L
        val seconds = (ms % 60_000L) / 1000L
        val millis = ms % 1000L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun cleanAssText(rawText: String): String {
        var text = rawText
        text = text.replace(Regex("\\{.*?\\}"), "")
        text = text.replace("\\N", "\n").replace("\\n", "\n")
        text = text.replace("\\h", " ")
        return text.trim()
    }
}
