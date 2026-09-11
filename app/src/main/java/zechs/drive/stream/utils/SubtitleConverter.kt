package zechs.drive.stream.utils

import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.util.MimeTypes
import zechs.drive.stream.data.model.SubtitleItem
import java.io.File

object SubtitleConverter {

    private const val TAG = "SubtitleConverter"

    /**
     * Prepares an external subtitle file for ExoPlayer.
     * ExoPlayer's built-in SsaDecoder fails on modern complex ASS subtitles (e.g. from Crunchyroll/Erai-raws).
     * Converting them to clean SubRip (.srt) allows ExoPlayer's SubripDecoder to render them 100% reliably.
     *
     * @return Pair of (Uri to load, MimeType)
     */
    fun prepareSubtitleForExoPlayer(cacheDir: File, sub: SubtitleItem, sourceFile: File): Pair<Uri, String> {
        val ext = sub.ext.lowercase()
        if (ext == "ass" || ext == "ssa") {
            val srtFile = File(cacheDir, "subtitles/${sub.id}_converted.srt")
            if (srtFile.exists() && srtFile.length() > 0) {
                return Pair(Uri.fromFile(srtFile), MimeTypes.APPLICATION_SUBRIP)
            }

            val success = convertAssToSrt(sourceFile, srtFile)
            if (success && srtFile.exists() && srtFile.length() > 0) {
                Log.d(TAG, "Successfully converted ${sub.name} to SRT for ExoPlayer: ${srtFile.absolutePath}")
                return Pair(Uri.fromFile(srtFile), MimeTypes.APPLICATION_SUBRIP)
            }
            Log.w(TAG, "Failed converting ASS to SRT, falling back to original file with TEXT_SSA")
            return Pair(Uri.fromFile(sourceFile), MimeTypes.TEXT_SSA)
        }

        val mimeType = when (ext) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        return Pair(Uri.fromFile(sourceFile), mimeType)
    }

    private fun convertAssToSrt(assFile: File, outputFile: File): Boolean {
        return try {
            val lines = assFile.readLines(Charsets.UTF_8)
            val srtBuilder = StringBuilder()
            var counter = 1
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
                    val parts = payload.split(",", limit = maxCol + 1)
                    if (parts.size > maxCol) {
                        val startRaw = parts[startIndex].trim()
                        val endRaw = parts[endIndex].trim()
                        val rawText = parts[textIndex]

                        val startSrt = formatAssTimeToSrt(startRaw)
                        val endSrt = formatAssTimeToSrt(endRaw)
                        val cleanText = cleanAssText(rawText)

                        if (cleanText.isNotBlank()) {
                            srtBuilder.append(counter++).append("\n")
                            srtBuilder.append(startSrt).append(" --> ").append(endSrt).append("\n")
                            srtBuilder.append(cleanText).append("\n\n")
                        }
                    }
                }
            }

            if (counter > 1) {
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(srtBuilder.toString(), Charsets.UTF_8)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ASS file: ${assFile.name}", e)
            false
        }
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

    private fun formatAssTimeToSrt(assTime: String): String {
        val parts = assTime.split(":")
        if (parts.size != 3) return assTime
        val hours = parts[0].padStart(2, '0')
        val minutes = parts[1].padStart(2, '0')
        val secParts = parts[2].split(".")
        val seconds = secParts[0].padStart(2, '0')
        val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3) else "000"
        return "$hours:$minutes:$seconds,$millis"
    }

    private fun cleanAssText(rawText: String): String {
        var text = rawText
        // Strip ASS override tags like {\an8}, {\pos(x,y)}, {\i1}, etc.
        text = text.replace(Regex("\\{.*?\\}"), "")
        // Handle explicit newline escape sequences
        text = text.replace("\\N", "\n").replace("\\n", "\n")
        text = text.replace("\\h", " ")
        return text.trim()
    }

}
