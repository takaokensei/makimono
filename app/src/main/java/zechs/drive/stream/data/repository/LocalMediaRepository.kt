package zechs.drive.stream.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.MediaSource
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.utils.AppLog
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LocalMediaRepository"
        const val LOCAL_BUCKET_PREFIX = "local_folder:"
    }

    /**
     * Retorna a permissão necessária de acordo com a versão do Android.
     */
    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Verifica se o app possui permissão concedida para ler vídeos locais.
     */
    fun hasPermission(): Boolean {
        val perm = getRequiredPermission()
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Escaneia todas as pastas que contêm vídeos no armazenamento local via MediaStore.
     * Retorna itens [DriveFile] com mimeType de pasta representando cada diretório (bucket).
     */
    suspend fun getLocalVideoFolders(): List<DriveFile> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            AppLog.w(TAG, "Cannot scan local folders: permission not granted")
            return@withContext emptyList()
        }

        val projection = arrayOf(
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val folderMap = mutableMapOf<String, String>() // bucketId -> bucketName

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val bucketId = it.getString(bucketIdCol) ?: continue
                    val bucketName = it.getString(bucketNameCol) ?: "Vídeos Locais"
                    if (!folderMap.containsKey(bucketId)) {
                        folderMap[bucketId] = bucketName
                    }
                }
            }

            // Fallback scan on MediaStore.Files for .ts / .m2ts / .mts files that Android MediaScanner
            // sometimes classifies as text/vnd.trolltech.linguist or generic files outside MediaStore.Video
            val tsExtClause = listOf("ts", "m2ts", "mts", "tp").joinToString(" OR ") {
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$it'"
            }
            val filesUri = MediaStore.Files.getContentUri("external")
            val filesProjection = arrayOf(
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
            )
            context.contentResolver.query(
                filesUri,
                filesProjection,
                tsExtClause,
                null,
                "${MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME} ASC"
            )?.use { tsCursor ->
                val bIdCol = tsCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                val bNameCol = tsCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                while (tsCursor.moveToNext()) {
                    val bId = tsCursor.getString(bIdCol) ?: continue
                    val bName = tsCursor.getString(bNameCol) ?: "Vídeos Locais"
                    if (!folderMap.containsKey(bId)) {
                        folderMap[bId] = bName
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error querying local video folders", e)
        }

        folderMap.map { (bucketId, bucketName) ->
            DriveFile(
                id = "$LOCAL_BUCKET_PREFIX$bucketId",
                name = "💾 $bucketName",
                size = null,
                mimeType = "application/vnd.google-apps.folder",
                iconLink = null,
                shortcutDetails = ShortcutDetails(null, null),
                starred = Starred.UNSTARRED,
                source = MediaSource.LOCAL
            )
        }
    }

    /**
     * Escaneia vídeos locais dentro de um bucket específico (ou todos se bucketId for null).
     */
    suspend fun getVideosInFolder(bucketId: String? = null): List<DriveFile> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            AppLog.w(TAG, "Cannot scan videos: permission not granted")
            return@withContext emptyList()
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val selection = if (bucketId != null) "${MediaStore.Video.Media.BUCKET_ID} = ?" else null
        val selectionArgs = if (bucketId != null) arrayOf(bucketId) else null
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        val videos = mutableListOf<DriveFile>()
        val seenIds = mutableSetOf<Long>()

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "video"
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol) ?: "video/*"
                    val dateModified = it.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId).toString()

                    seenIds.add(mediaId)
                    videos.add(
                        DriveFile(
                            id = "local:$mediaId",
                            name = name,
                            size = size,
                            mimeType = mime,
                            iconLink = null,
                            shortcutDetails = ShortcutDetails(null, null),
                            starred = Starred.UNSTARRED,
                            modifiedTime = dateModified.toString(),
                            source = MediaSource.LOCAL,
                            localUri = contentUri
                        )
                    )
                }
            }

            // Fallback scan for .ts / .m2ts / .mts files in MediaStore.Files
            val tsExtClause = listOf("ts", "m2ts", "mts", "tp").joinToString(" OR ") {
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$it'"
            }
            val tsSelection = if (bucketId != null) {
                "(${MediaStore.Files.FileColumns.BUCKET_ID} = ?) AND ($tsExtClause)"
            } else {
                tsExtClause
            }
            val filesUri = MediaStore.Files.getContentUri("external")
            val filesProjection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            context.contentResolver.query(
                filesUri,
                filesProjection,
                tsSelection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            )?.use { tsCursor ->
                val idCol = tsCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = tsCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = tsCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = tsCursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (tsCursor.moveToNext()) {
                    val fileId = tsCursor.getLong(idCol)
                    if (!seenIds.add(fileId)) continue
                    val name = tsCursor.getString(nameCol) ?: continue
                    val size = tsCursor.getLong(sizeCol)
                    val dateModified = tsCursor.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(filesUri, fileId).toString()

                    videos.add(
                        DriveFile(
                            id = "local:$fileId",
                            name = name,
                            size = size,
                            mimeType = "video/mp2t",
                            iconLink = null,
                            shortcutDetails = ShortcutDetails(null, null),
                            starred = Starred.UNSTARRED,
                            modifiedTime = dateModified.toString(),
                            source = MediaSource.LOCAL,
                            localUri = contentUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error querying local videos for bucket $bucketId", e)
        }

        videos
    }

    /**
     * Escaneia legendas locais (.srt, .ass, .vtt, .ssa) no mesmo bucket ou diretório.
     */
    suspend fun getSubtitlesInFolder(bucketId: String?): List<SubtitleItem> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val subtitles = mutableListOf<SubtitleItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA
        )

        val extensions = listOf("srt", "ass", "vtt", "ssa", "sub")
        val extClause = extensions.joinToString(separator = " OR ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$it'" }
        val selection = if (bucketId != null) {
            "(${MediaStore.Files.FileColumns.BUCKET_ID} = ?) AND ($extClause)"
        } else {
            extClause
        }
        val selectionArgs = if (bucketId != null) arrayOf(bucketId) else null

        try {
            val queryUri = MediaStore.Files.getContentUri("external")
            val cursor = context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (it.moveToNext()) {
                    val fileId = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: continue
                    val dataPath = if (dataCol >= 0) it.getString(dataCol) else null
                    val contentUri = ContentUris.withAppendedId(queryUri, fileId).toString()
                    val resolvedUri = if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                        "file://$dataPath"
                    } else {
                        contentUri
                    }

                    subtitles.add(
                        SubtitleItem(
                            id = "local_sub:$fileId",
                            name = name,
                            source = MediaSource.LOCAL,
                            uri = resolvedUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error querying local subtitles for bucket $bucketId: ${e.message}")
        }

        subtitles
    }
}
