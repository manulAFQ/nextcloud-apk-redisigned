package ru.manul.nextcloudgallery.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.manul.nextcloudgallery.core.model.ByteRange
import ru.manul.nextcloudgallery.core.model.CachedPreview
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.PreviewMode
import ru.manul.nextcloudgallery.core.model.RemoteMediaStream
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.PreviewCache
import ru.manul.nextcloudgallery.data.remote.MediaContentGateway
import ru.manul.nextcloudgallery.domain.repository.MediaContentRepository

class DefaultMediaContentRepository(
    private val gateway: MediaContentGateway,
    private val previewCache: PreviewCache,
    private val maxPreviewBytes: Long = DEFAULT_MAX_PREVIEW_BYTES,
) : MediaContentRepository {
    private val previewLocks = Array(PREVIEW_LOCK_COUNT) { Mutex() }

    override suspend fun preview(
        session: UserSession,
        item: MediaItem,
        width: Int,
        height: Int,
        mode: PreviewMode,
    ): AppResult<CachedPreview> {
        val key = previewKey(session, item, width, height, mode)
        val lock = previewLocks[(key.hashCode() and Int.MAX_VALUE) % previewLocks.size]
        return lock.withLock {
            when (val cached = previewCache.get(key)) {
                is AppResult.Failure -> return@withLock cached
                is AppResult.Success -> cached.value?.let { file ->
                    return@withLock AppResult.Success(CachedPreview(file, fromCache = true))
                }
            }

            when (
                val remote = gateway.openPreview(
                    session = session,
                    remotePath = item.remotePath,
                    width = width,
                    height = height,
                    mode = mode,
                    version = item.etag ?: item.modifiedAt.toEpochMilli().toString(),
                )
            ) {
                is AppResult.Failure -> remote
                is AppResult.Success -> remote.value.use { stream ->
                    if (stream.contentType?.startsWith("image/", ignoreCase = true) != true) {
                        return@withLock AppResult.Failure(AppFailure.InvalidServerResponse)
                    }
                    if (stream.contentLength != null && stream.contentLength > maxPreviewBytes) {
                        return@withLock AppResult.Failure(AppFailure.ResponseTooLarge)
                    }
                    when (val stored = previewCache.put(key, stream.input, maxPreviewBytes)) {
                        is AppResult.Failure -> stored
                        is AppResult.Success -> AppResult.Success(
                            CachedPreview(stored.value, fromCache = false),
                        )
                    }
                }
            }
        }
    }

    override suspend fun openOriginal(
        session: UserSession,
        remotePath: String,
        range: ByteRange?,
    ): AppResult<RemoteMediaStream> = gateway.openOriginal(session, remotePath, range)

    private fun previewKey(
        session: UserSession,
        item: MediaItem,
        width: Int,
        height: Int,
        mode: PreviewMode,
    ): String = listOf(
        session.account.id,
        item.remotePath,
        item.etag.orEmpty(),
        item.modifiedAt.toEpochMilli(),
        item.sizeBytes,
        width,
        height,
        mode.wireValue,
    ).joinToString(separator = "\u0000")

    private companion object {
        const val DEFAULT_MAX_PREVIEW_BYTES = 8L * 1024 * 1024
        const val PREVIEW_LOCK_COUNT = 64
    }
}
