package ru.manul.nextcloudgallery.data.local

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class AndroidUploadSource(
    private val contentResolver: ContentResolver,
) : UploadSource {
    override fun inspect(localUri: String): AppResult<UploadSourceInfo> {
        val uri = parseContentUri(localUri) ?: return unavailable()
        retainReadPermission(uri)
        return try {
            val size = querySize(uri) ?: contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0 }
            }
            if (size == null || size < 0) unavailable() else AppResult.Success(
                UploadSourceInfo(
                    sizeBytes = size,
                    mimeType = contentResolver.getType(uri),
                ),
            )
        } catch (_: Exception) {
            unavailable()
        }
    }

    override fun open(localUri: String): AppResult<OpenUploadSource> {
        val uri = parseContentUri(localUri) ?: return unavailable()
        return try {
            val input = contentResolver.openInputStream(uri) ?: return unavailable()
            AppResult.Success(
                OpenUploadSource(
                    input = input,
                    mimeType = contentResolver.getType(uri),
                    closeAction = input::close,
                ),
            )
        } catch (_: Exception) {
            unavailable()
        }
    }

    private fun querySize(uri: Uri): Long? = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
    }

    private fun retainReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun parseContentUri(value: String): Uri? = runCatching { Uri.parse(value) }
        .getOrNull()
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }

    private fun <T> unavailable(): AppResult<T> = AppResult.Failure(AppFailure.LocalContentUnavailable)
}
