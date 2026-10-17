package ru.manul.nextcloudgallery.core.model

import java.io.Closeable
import java.io.File
import java.io.InputStream

enum class PreviewMode(val wireValue: String) {
    FILL("fill"),
    COVER("cover"),
}

data class ByteRange(
    val start: Long,
    val endInclusive: Long? = null,
) {
    init {
        require(start >= 0) { "Range start cannot be negative" }
        require(endInclusive == null || endInclusive >= start) { "Range end precedes start" }
    }

    fun toHeaderValue(): String = "bytes=$start-${endInclusive.orEmpty()}"

    private fun Long?.orEmpty(): String = this?.toString().orEmpty()
}

class RemoteMediaStream(
    val input: InputStream,
    val contentType: String?,
    val contentLength: Long?,
    val etag: String?,
    val rangeStart: Long?,
    val rangeEndInclusive: Long?,
    val totalLength: Long?,
    val acceptsByteRanges: Boolean,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() = closeAction()
}

data class CachedPreview(
    val file: File,
    val fromCache: Boolean,
)
