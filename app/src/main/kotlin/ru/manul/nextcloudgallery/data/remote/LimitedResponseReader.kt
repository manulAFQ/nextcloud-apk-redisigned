package ru.manul.nextcloudgallery.data.remote

import okio.Buffer
import okio.BufferedSource

/** Reads a complete UTF-8 response while refusing bodies larger than [maxBytes]. */
internal fun BufferedSource.readUtf8Within(maxBytes: Long): String? {
    require(maxBytes >= 0) { "Response limit cannot be negative" }
    val buffer = Buffer()
    var remaining = maxBytes + 1L

    while (remaining > 0L) {
        val read = read(buffer, remaining)
        if (read == -1L) break
        remaining -= read
    }

    if (buffer.size > maxBytes) return null
    return buffer.readUtf8()
}
