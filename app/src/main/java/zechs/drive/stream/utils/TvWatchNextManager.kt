package zechs.drive.stream.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.ui.main.MainActivity
import zechs.drive.stream.ui.player.PlayerLauncher

/**
 * Android TV "Watch Next" / "Continuar assistindo" system row integration.
 *
 * Synchronizes in-progress media from `watch_list` with Android TV's
 * Leanback launcher channel via [TvContractCompat.WatchNextPrograms].
 *
 * Requirements (Section 7.7 Item 1):
 * - Fed by `watch_list`.
 * - Updated on every meaningful progress save.
 * - Removed when media is finished (>= 90% per [WatchList.hasFinished]).
 * - Safe execution across non-TV devices and environments without TV providers.
 */
object TvWatchNextManager {

    private const val TAG = "TvWatchNextManager"

    /**
     * Checks if the device supports Leanback / Android TV platform features.
     */
    fun isTv(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    pm.hasSystemFeature("android.hardware.type.television")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Updates or creates a Watch Next program entry for the specified [watch] item.
     */
    suspend fun updateWatchNext(
        context: Context,
        watch: WatchList,
        isMovie: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val existingId = findProgramIdByVideoId(resolver, watch.videoId)

            // Deep-link intent targeting MainActivity with playback extras
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                setClass(context, MainActivity::class.java)
                data = Uri.parse("makimono://watch/${watch.videoId}")
                putExtra(PlayerLauncher.EXTRA_FILE_ID, watch.videoId)
                putExtra(PlayerLauncher.EXTRA_TITLE, watch.name)
                putExtra(PlayerLauncher.EXTRA_IS_MOVIE, isMovie)
            }

            val programType = if (isMovie) {
                TvContractCompat.WatchNextPrograms.TYPE_MOVIE
            } else {
                TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
            }

            val builder = WatchNextProgram.Builder()
                .setTitle(watch.name)
                .setType(programType)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setInternalProviderId(watch.videoId)
                .setLastPlaybackPositionMillis(watch.watchedDuration.toInt().coerceAtLeast(0))
                .setDurationMillis(watch.totalDuration.toInt().coerceAtLeast(0))
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setIntentUri(Uri.parse(playIntent.toUri(Intent.URI_INTENT_SCHEME)))

            watch.thumbnailLink?.let { thumb ->
                if (thumb.isNotBlank()) {
                    builder.setPosterArtUri(Uri.parse(thumb))
                }
            }

            val program = builder.build()

            if (existingId != null) {
                val programUri = TvContractCompat.buildWatchNextProgramUri(existingId)
                val rows = resolver.update(programUri, program.toContentValues(), null, null)
                AppLog.d(TAG, "Updated WatchNextProgram id=$existingId ($rows rows updated) for ${watch.name}")
            } else {
                val insertedUri = resolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues()
                )
                AppLog.d(TAG, "Inserted WatchNextProgram uri=$insertedUri for ${watch.name}")
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to update Watch Next for videoId=${watch.videoId}: ${e.message}")
        }
    }

    /**
     * Removes a Watch Next program entry for the specified [videoId] (e.g. when completed).
     */
    suspend fun removeWatchNext(
        context: Context,
        videoId: String
    ) = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val existingId = findProgramIdByVideoId(resolver, videoId) ?: return@withContext
            val programUri = TvContractCompat.buildWatchNextProgramUri(existingId)
            val rows = resolver.delete(programUri, null, null)
            AppLog.d(TAG, "Deleted WatchNextProgram id=$existingId ($rows rows deleted) for videoId=$videoId")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to remove Watch Next for videoId=$videoId: ${e.message}")
        }
    }

    /**
     * Synchronizes a list of recent in-progress titles to the Watch Next system row.
     */
    suspend fun syncWatchNext(
        context: Context,
        recentWatches: List<WatchList>
    ) = withContext(Dispatchers.IO) {
        try {
            for (watch in recentWatches) {
                if (watch.hasFinished()) {
                    removeWatchNext(context, watch.videoId)
                } else {
                    updateWatchNext(context, watch)
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to batch sync Watch Next: ${e.message}")
        }
    }

    /**
     * Queries the system TV provider for an existing program with matching [COLUMN_INTERNAL_PROVIDER_ID].
     */
    private fun findProgramIdByVideoId(resolver: ContentResolver, videoId: String): Long? {
        val projection = arrayOf(
            TvContractCompat.WatchNextPrograms._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
        )
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                projection,
                "${TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf(videoId),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                if (idIndex != -1) cursor.getLong(idIndex) else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}
