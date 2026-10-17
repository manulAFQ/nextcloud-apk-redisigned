package ru.manul.nextcloudgallery.data.local

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.model.LocalMediaKind
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class AndroidMediaStoreSource(private val context: Context) : LocalMediaSource {
    private val resolver: ContentResolver = context.contentResolver

    override fun scan(includeVideos: Boolean, limit: Int): AppResult<List<LocalMediaCandidate>> {
        if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            return AppResult.Failure(AppFailure.MediaPermissionRequired)
        }
        val canReadVideos = includeVideos &&
            hasPermission(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_EXTERNAL_STORAGE)
        return try {
            val result = query(collection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI), LocalMediaKind.IMAGE, limit)
                .toMutableList()
            if (canReadVideos) {
                result += query(collection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI), LocalMediaKind.VIDEO, limit)
            }
            AppResult.Success(result.sortedByDescending(LocalMediaCandidate::capturedAtEpochMs).take(limit))
        } catch (_: SecurityException) {
            AppResult.Failure(AppFailure.MediaPermissionRequired)
        } catch (_: Exception) {
            AppResult.Failure(AppFailure.LocalContentUnavailable)
        }
    }

    private fun query(uri: Uri, kind: LocalMediaKind, limit: Int): List<LocalMediaCandidate> {
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.MediaColumns.DATA
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            pathColumn,
        )
        val selection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                "${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> "${MediaStore.MediaColumns.IS_PENDING} = 0"
            else -> null
        }
        val output = mutableListOf<LocalMediaCandidate>()
        resolver.query(
            uri,
            projection,
            selection,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
            while (cursor.moveToNext() && output.size < limit.coerceIn(1, MAX_SCAN_LIMIT)) {
                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                if (size < 0) continue
                val modified = cursor.getLong(modifiedColumn)
                val path = cursor.getString(pathIndex).orEmpty().let { raw ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) raw
                    else raw.substringBeforeLast('/', missingDelimiterValue = "")
                }
                val volume = uri.pathSegments.firstOrNull().orEmpty().ifBlank { "external" }
                output += LocalMediaCandidate(
                    localKey = "$volume:$id:$size:$modified",
                    contentUri = ContentUris.withAppendedId(uri, id).toString(),
                    displayName = name,
                    relativePath = path,
                    kind = kind,
                    mimeType = cursor.getString(mimeColumn),
                    sizeBytes = size,
                    capturedAtEpochMs = cursor.getLong(addedColumn) * 1_000,
                    modifiedAtEpochSeconds = modified,
                )
            }
        }
        return output
    }

    private fun collection(legacy: Uri): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (legacy) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
    } else {
        legacy
    }

    private fun hasPermission(modern: String, legacy: String): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) modern else legacy
        val fullAccess = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val selectedAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return fullAccess || selectedAccess
    }

    private companion object {
        const val MAX_SCAN_LIMIT = 10_000
    }
}
