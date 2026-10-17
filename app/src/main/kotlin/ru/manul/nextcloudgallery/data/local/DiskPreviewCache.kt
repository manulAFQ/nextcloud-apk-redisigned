package ru.manul.nextcloudgallery.data.local

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class DiskPreviewCache(
    private val directory: File,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : PreviewCache {
    private val mutex = Mutex()

    init {
        require(maxCacheBytes > 0) { "Cache size must be positive" }
    }

    override suspend fun get(key: String): AppResult<File?> = protect {
        mutex.withLock {
            ensureDirectory()
            val file = fileFor(key)
            if (!file.isFile || file.length() <= 0L) {
                file.delete()
                null
            } else {
                file.setLastModified(clock())
                file
            }
        }
    }

    override suspend fun put(
        key: String,
        input: InputStream,
        maxEntryBytes: Long,
    ): AppResult<File> {
        if (maxEntryBytes <= 0 || maxEntryBytes > maxCacheBytes) {
            return AppResult.Failure(AppFailure.ResponseTooLarge)
        }
        return protect {
            mutex.withLock {
                ensureDirectory()
                removeAbandonedTemporaryFiles()
                val temporary = File.createTempFile("preview-", ".tmp", directory)
                try {
                    copyBounded(input, temporary, maxEntryBytes)
                    val target = fileFor(key)
                    if (target.exists() && !target.delete()) error("Unable to replace cached preview")
                    if (!temporary.renameTo(target)) {
                        temporary.copyTo(target, overwrite = true)
                        temporary.delete()
                    }
                    target.setLastModified(clock())
                    trimToLimit(protectedFile = target)
                    check(target.isFile) { "New preview was evicted unexpectedly" }
                    target
                } finally {
                    temporary.delete()
                }
            }
        }
    }

    override suspend fun clear(): AppResult<Unit> = protect {
        mutex.withLock {
            if (!directory.exists()) return@withLock
            directory.listFiles().orEmpty().forEach { file ->
                if (file.isFile && !file.delete()) error("Unable to clear preview cache")
            }
        }
    }

    private suspend fun <T> protect(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(withContext(dispatcher) { block() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: EntryTooLargeException) {
        AppResult.Failure(AppFailure.ResponseTooLarge)
    } catch (_: Exception) {
        AppResult.Failure(AppFailure.LocalStorageUnavailable)
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) error("Unable to create preview cache")
        check(directory.isDirectory) { "Preview cache path is not a directory" }
    }

    private suspend fun copyBounded(input: InputStream, target: File, limit: Long) {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw EntryTooLargeException()
                output.write(buffer, 0, read)
            }
        }
        if (target.length() <= 0L) error("Empty preview response")
    }

    private fun trimToLimit(protectedFile: File) {
        val files = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == CACHE_EXTENSION }
            .sortedBy(File::lastModified)
        var total = files.sumOf(File::length)
        for (file in files) {
            if (total <= maxCacheBytes) break
            if (file == protectedFile) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun removeAbandonedTemporaryFiles() {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "tmp" }
            .forEach { file -> file.delete() }
    }

    private fun fileFor(key: String): File = File(directory, "${sha256(key)}.$CACHE_EXTENSION")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private class EntryTooLargeException : Exception()

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 256L * 1024 * 1024
        private const val COPY_BUFFER_BYTES = 16 * 1024
        private const val CACHE_EXTENSION = "preview"
    }
}
