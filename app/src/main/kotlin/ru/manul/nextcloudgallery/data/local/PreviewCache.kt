package ru.manul.nextcloudgallery.data.local

import java.io.File
import java.io.InputStream
import ru.manul.nextcloudgallery.core.result.AppResult

interface PreviewCache {
    suspend fun get(key: String): AppResult<File?>

    suspend fun put(
        key: String,
        input: InputStream,
        maxEntryBytes: Long,
    ): AppResult<File>

    suspend fun clear(): AppResult<Unit>
}
