package ru.manul.nextcloudgallery.data.local

import java.io.Closeable
import java.io.InputStream
import ru.manul.nextcloudgallery.core.result.AppResult

data class UploadSourceInfo(
    val sizeBytes: Long,
    val mimeType: String?,
)

class OpenUploadSource(
    val input: InputStream,
    val mimeType: String?,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() = closeAction()
}

interface UploadSource {
    fun inspect(localUri: String): AppResult<UploadSourceInfo>

    fun open(localUri: String): AppResult<OpenUploadSource>
}
