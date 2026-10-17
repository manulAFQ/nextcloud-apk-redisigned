package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import ru.manul.nextcloudgallery.core.model.RemotePath
import ru.manul.nextcloudgallery.core.model.UploadReceipt
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import kotlin.coroutines.resume

/** Authenticated, user-scoped WebDAV uploader with Nextcloud chunking v2 support. */
class WebDavUploadGateway(
    private val client: OkHttpClient = uploadClient(),
    private val capabilitiesGateway: NextcloudGateway = OcsNextcloudGateway(),
    private val chunkThresholdBytes: Long = DEFAULT_CHUNK_THRESHOLD_BYTES,
    private val preferredChunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : UploadGateway {
    override suspend fun resolveUniqueRemotePath(
        session: UserSession,
        requestedRemotePath: String,
    ): AppResult<String> {
        val requested = parseFilePath(requestedRemotePath)
            ?: return AppResult.Failure(AppFailure.InvalidRemotePath)
        for (suffix in 0..MAX_NAME_ATTEMPTS) {
            val candidate = if (suffix == 0) requested else withNumericSuffix(requested, suffix)
            when (val found = probeFile(session, candidate)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> if (found.value == null) return AppResult.Success(candidate.value)
            }
        }
        return AppResult.Failure(AppFailure.RemoteFileAlreadyExists)
    }

    override suspend fun upload(
        session: UserSession,
        uploadId: String,
        remotePath: String,
        sizeBytes: Long,
        recoverCompletedUpload: Boolean,
        mimeType: String?,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): AppResult<UploadReceipt> {
        if (sizeBytes < 0) return AppResult.Failure(AppFailure.LocalContentUnavailable)
        val path = parseFilePath(remotePath) ?: return AppResult.Failure(AppFailure.InvalidRemotePath)

        // A previous request may have completed while its HTTP response was lost.
        when (val existing = probeFile(session, path)) {
            is AppResult.Failure -> return existing
            is AppResult.Success -> existing.value?.let { entry ->
                return if (recoverCompletedUpload && entry.contentLength == sizeBytes) {
                    AppResult.Success(receipt(path, entry))
                } else {
                    AppResult.Failure(AppFailure.RemoteFileAlreadyExists)
                }
            }
        }

        when (val prepared = ensureParentCollections(session, path)) {
            is AppResult.Failure -> return prepared
            is AppResult.Success -> Unit
        }

        val capabilities = if (sizeBytes >= chunkThresholdBytes) {
            capabilitiesGateway.capabilities(session)
        } else {
            null
        }
        val useChunks = capabilities is AppResult.Success && capabilities.value.chunkedUpload
        return if (useChunks) {
            uploadChunked(session, uploadId, path, sizeBytes, mimeType, source, onProgress)
        } else {
            uploadDirect(session, path, sizeBytes, mimeType, source, onProgress)
        }
    }

    override suspend fun abort(session: UserSession, uploadId: String): AppResult<Unit> {
        val request = authenticated(
            session,
            Request.Builder().url(buildUploadUrl(session, safeUploadId(uploadId))),
        ).delete().build()
        return execute(request) { response ->
            when (response.code) {
                HTTP_NO_CONTENT, HTTP_NOT_FOUND -> AppResult.Success(Unit)
                else -> AppResult.Failure(httpFailure(response.code))
            }
        }
    }

    private suspend fun uploadDirect(
        session: UserSession,
        path: RemotePath,
        sizeBytes: Long,
        mimeType: String?,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): AppResult<UploadReceipt> {
        val request = authenticated(session, Request.Builder().url(buildFileUrl(session, path)))
            .put(ProgressRequestBody(sizeBytes, mimeType, source, onProgress))
            .header("If-None-Match", "*")
            .build()
        val sent = execute(request) { response ->
            when (response.code) {
                HTTP_CREATED, HTTP_NO_CONTENT -> AppResult.Success(Unit)
                HTTP_PRECONDITION_FAILED -> AppResult.Failure(AppFailure.RemoteFileAlreadyExists)
                else -> AppResult.Failure(httpFailure(response.code))
            }
        }
        if (sent is AppResult.Failure) {
            return if (sent.error == AppFailure.ServerUnavailable) {
                val verified = verifyFinal(session, path, sizeBytes)
                if (verified is AppResult.Success) verified else sent
            } else {
                sent
            }
        }
        return verifyFinal(session, path, sizeBytes)
    }

    private suspend fun ensureParentCollections(session: UserSession, path: RemotePath): AppResult<Unit> {
        if (path.segments.size <= 1) return AppResult.Success(Unit)
        for (depth in 1 until path.segments.size) {
            val parent = RemotePath.parse(path.segments.take(depth).joinToString("/"))
                .getOrElse { return AppResult.Failure(AppFailure.InvalidRemotePath) }
            val request = authenticated(session, Request.Builder().url(buildFileUrl(session, parent)))
                .method("MKCOL", EMPTY_BODY)
                .build()
            when (val created = execute(request) { response ->
                when (response.code) {
                    HTTP_CREATED, HTTP_METHOD_NOT_ALLOWED -> AppResult.Success(Unit)
                    else -> AppResult.Failure(httpFailure(response.code))
                }
            }) {
                is AppResult.Failure -> return created
                is AppResult.Success -> Unit
            }
        }
        return AppResult.Success(Unit)
    }

    private suspend fun uploadChunked(
        session: UserSession,
        uploadId: String,
        path: RemotePath,
        sizeBytes: Long,
        mimeType: String?,
        source: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): AppResult<UploadReceipt> {
        val uploadUrl = buildUploadUrl(session, safeUploadId(uploadId))
        val destination = buildFileUrl(session, path).toString()
        val chunkBytes = chunkSizeFor(sizeBytes)
        val chunks = ceilDiv(sizeBytes, chunkBytes)
        if (chunks > MAX_CHUNKS) return AppResult.Failure(AppFailure.LocalContentUnavailable)

        val create = authenticated(session, Request.Builder().url(uploadUrl))
            .method("MKCOL", EMPTY_BODY)
            .header("Destination", destination)
            .header("OC-Total-Length", sizeBytes.toString())
            .build()
        when (val created = execute(create) { response ->
            when (response.code) {
                HTTP_CREATED, HTTP_METHOD_NOT_ALLOWED -> AppResult.Success(Unit)
                else -> AppResult.Failure(httpFailure(response.code))
            }
        }) {
            is AppResult.Failure -> return created
            is AppResult.Success -> Unit
        }

        val uploaded = when (val listed = listChunks(session, uploadUrl)) {
            is AppResult.Failure -> return listed
            is AppResult.Success -> listed.value
        }
        onProgress(0)
        val input = try { source() } catch (_: Exception) {
            return AppResult.Failure(AppFailure.LocalContentUnavailable)
        }
        input.use { stream ->
            var offset = 0L
            for (index in 0 until chunks.toInt()) {
                val length = minOf(chunkBytes, sizeBytes - offset)
                val name = chunkName(index + 1)
                val remoteLength = uploaded[name]
                if (remoteLength != null && remoteLength != length) {
                    return AppResult.Failure(AppFailure.UploadIntegrityMismatch)
                }
                if (remoteLength == length) {
                    if (!discardExactly(stream, length)) {
                        return AppResult.Failure(AppFailure.LocalContentUnavailable)
                    }
                    offset += length
                    onProgress(offset)
                    continue
                }
                val request = authenticated(
                    session,
                    Request.Builder().url(uploadUrl.newBuilder().addPathSegment(name).build()),
                )
                    .put(ChunkRequestBody(length, mimeType, stream, offset, onProgress))
                    .header("Destination", destination)
                    .header("OC-Total-Length", sizeBytes.toString())
                    .build()
                when (val sent = execute(request) { response ->
                    when (response.code) {
                        HTTP_CREATED, HTTP_NO_CONTENT -> AppResult.Success(Unit)
                        else -> AppResult.Failure(httpFailure(response.code))
                    }
                }) {
                    is AppResult.Failure -> return sent
                    is AppResult.Success -> Unit
                }
                offset += length
            }
            if (stream.read() != -1) return AppResult.Failure(AppFailure.LocalContentUnavailable)
        }

        val move = authenticated(
            session,
            Request.Builder().url(uploadUrl.newBuilder().addPathSegment(".file").build()),
        )
            .method("MOVE", null)
            .header("Destination", destination)
            .header("OC-Total-Length", sizeBytes.toString())
            .header("Overwrite", "F")
            .build()
        val moved = execute(move) { response ->
            when (response.code) {
                HTTP_CREATED, HTTP_NO_CONTENT -> AppResult.Success(Unit)
                HTTP_PRECONDITION_FAILED -> AppResult.Failure(AppFailure.RemoteFileAlreadyExists)
                else -> AppResult.Failure(httpFailure(response.code))
            }
        }
        if (moved is AppResult.Failure && moved.error == AppFailure.ServerUnavailable) {
            val verified = verifyFinal(session, path, sizeBytes)
            return if (verified is AppResult.Success) verified else moved
        }
        if (moved is AppResult.Failure) return moved
        return verifyFinal(session, path, sizeBytes)
    }

    private suspend fun listChunks(session: UserSession, uploadUrl: HttpUrl): AppResult<Map<String, Long>> {
        val request = authenticated(session, Request.Builder().url(uploadUrl))
            .method("PROPFIND", PROPFIND_BODY)
            .header("Depth", "1")
            .header("Accept", "application/xml, text/xml")
            .build()
        return execute(request) { response ->
            if (response.code != HTTP_MULTI_STATUS) return@execute AppResult.Failure(httpFailure(response.code))
            val entries = parseDav(response) ?: return@execute AppResult.Failure(AppFailure.InvalidServerResponse)
            AppResult.Success(entries.filterNot(DavEntry::isCollection).associate { entry ->
                decodedLastSegment(entry.href) to entry.contentLength
            })
        }
    }

    private suspend fun verifyFinal(
        session: UserSession,
        path: RemotePath,
        expectedSize: Long,
    ): AppResult<UploadReceipt> = when (val found = probeFile(session, path)) {
        is AppResult.Failure -> found
        is AppResult.Success -> {
            val entry = found.value ?: return AppResult.Failure(AppFailure.UploadIntegrityMismatch)
            if (entry.contentLength != expectedSize) {
                AppResult.Failure(AppFailure.UploadIntegrityMismatch)
            } else {
                AppResult.Success(receipt(path, entry))
            }
        }
    }

    private suspend fun probeFile(session: UserSession, path: RemotePath): AppResult<DavEntry?> {
        val request = authenticated(session, Request.Builder().url(buildFileUrl(session, path)))
            .method("PROPFIND", PROPFIND_BODY)
            .header("Depth", "0")
            .header("Accept", "application/xml, text/xml")
            .build()
        return execute(request) { response ->
            when (response.code) {
                HTTP_NOT_FOUND -> AppResult.Success(null)
                HTTP_MULTI_STATUS -> {
                    val entry = parseDav(response)?.firstOrNull { !it.isCollection }
                        ?: return@execute AppResult.Failure(AppFailure.InvalidServerResponse)
                    AppResult.Success(entry)
                }
                else -> AppResult.Failure(httpFailure(response.code))
            }
        }
    }

    private fun parseDav(response: Response): List<DavEntry>? = try {
        val body = response.body ?: return null
        WebDavMultiStatusParser().parse(LimitedInputStream(body.byteStream(), MAX_DAV_RESPONSE_BYTES))
    } catch (_: Exception) {
        null
    }

    private suspend fun <T> execute(
        request: Request,
        mapper: (Response) -> AppResult<T>,
    ): AppResult<T> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!continuation.isActive) return
                continuation.resume(
                    AppResult.Failure(
                        if (error.hasLocalContentCause()) AppFailure.LocalContentUnavailable
                        else AppFailure.ServerUnavailable,
                    ),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                continuation.resume(response.use {
                    if (it.isRedirect) AppResult.Failure(AppFailure.UnsafeRedirect) else mapper(it)
                })
            }
        })
    }

    private fun receipt(path: RemotePath, entry: DavEntry) = UploadReceipt(
        remotePath = path.value,
        sizeBytes = entry.contentLength,
        etag = entry.etag?.trim()?.trim('"'),
    )

    private fun parseFilePath(value: String): RemotePath? = RemotePath.parse(value).getOrNull()
        ?.takeIf { it.segments.isNotEmpty() }

    private fun withNumericSuffix(path: RemotePath, suffix: Int): RemotePath {
        val filename = path.segments.last()
        val dot = filename.lastIndexOf('.').takeIf { it > 0 } ?: filename.length
        val candidate = filename.substring(0, dot) + " ($suffix)" + filename.substring(dot)
        return RemotePath.parse((path.segments.dropLast(1) + candidate).joinToString("/")).getOrThrow()
    }

    private fun chunkSizeFor(totalBytes: Long): Long = maxOf(
        preferredChunkBytes,
        ceilDiv(totalBytes, MAX_CHUNKS),
    ).coerceAtMost(MAX_CHUNK_BYTES)

    private fun safeUploadId(uploadId: String): String = uploadId.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9._-]"), "_")
        .take(MAX_UPLOAD_ID_LENGTH)
        .ifBlank { "upload" }

    private fun chunkName(index: Int): String = index.toString().padStart(CHUNK_NAME_WIDTH, '0')

    private fun decodedLastSegment(href: String): String = URLDecoder.decode(
        href.substringBefore('?').trimEnd('/').substringAfterLast('/'),
        StandardCharsets.UTF_8.name(),
    )

    private fun discardExactly(input: InputStream, bytes: Long): Boolean {
        val buffer = ByteArray(BUFFER_BYTES)
        var remaining = bytes
        while (remaining > 0) {
            val read = try {
                input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            } catch (_: IOException) {
                return false
            }
            if (read < 0) return false
            remaining -= read
        }
        return true
    }

    private fun authenticated(session: UserSession, builder: Request.Builder): Request.Builder = builder
        .header("Authorization", Credentials.basic(session.account.username, session.appPassword.reveal()))

    private fun buildFileUrl(session: UserSession, path: RemotePath) = baseDavUrl(session, "files").newBuilder().apply {
        path.segments.forEach(::addPathSegment)
    }.build()

    private fun buildUploadUrl(session: UserSession, uploadId: String) = baseDavUrl(session, "uploads")
        .newBuilder().addPathSegment(uploadId).build()

    private fun baseDavUrl(session: UserSession, area: String) =
        session.account.serverAddress.value.toHttpUrl().newBuilder().apply {
            listOf("remote.php", "dav", area, session.account.userId).forEach(::addPathSegment)
        }.build()

    private fun httpFailure(code: Int): AppFailure = when (code) {
        401 -> AppFailure.Unauthorized
        403 -> AppFailure.Forbidden
        HTTP_PRECONDITION_FAILED -> AppFailure.RemoteFileAlreadyExists
        507 -> AppFailure.InsufficientStorage
        else -> AppFailure.Http(code)
    }

    private fun Throwable.hasLocalContentCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is LocalContentIOException) return true
            current = current.cause
        }
        return false
    }

    private class ProgressRequestBody(
        private val sizeBytes: Long,
        mimeType: String?,
        private val source: () -> InputStream,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        private val type = mimeType?.toMediaTypeOrNull() ?: BINARY_MEDIA_TYPE
        override fun contentType(): MediaType = type
        override fun contentLength(): Long = sizeBytes
        override fun writeTo(sink: BufferedSink) {
            val input = try { source() } catch (error: Exception) { throw LocalContentIOException(error) }
            input.use { stream ->
                writeExactly(sink, stream, sizeBytes, 0, onProgress)
                if (stream.read() != -1) {
                    throw LocalContentIOException(IllegalStateException("Source size changed"))
                }
            }
        }
    }

    private class ChunkRequestBody(
        private val length: Long,
        mimeType: String?,
        private val input: InputStream,
        private val offset: Long,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        private val type = mimeType?.toMediaTypeOrNull() ?: BINARY_MEDIA_TYPE
        override fun contentType(): MediaType = type
        override fun contentLength(): Long = length
        override fun writeTo(sink: BufferedSink) = writeExactly(sink, input, length, offset, onProgress)
    }

    private class LocalContentIOException(cause: Throwable) : IOException(cause)

    private companion object {
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_MULTI_STATUS = 207
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val HTTP_PRECONDITION_FAILED = 412
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_CHUNKS = 10_000L
        const val CHUNK_NAME_WIDTH = 5
        const val MAX_UPLOAD_ID_LENGTH = 64
        const val MAX_NAME_ATTEMPTS = 999
        const val MAX_DAV_RESPONSE_BYTES = 4L * 1024 * 1024
        const val DEFAULT_CHUNK_THRESHOLD_BYTES = 10L * 1024 * 1024
        const val DEFAULT_CHUNK_BYTES = 10L * 1024 * 1024
        const val MAX_CHUNK_BYTES = 5_000_000_000L
        val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody()
        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:getcontentlength/><d:getetag/><d:resourcetype/></d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        fun ceilDiv(value: Long, divisor: Long): Long = if (value == 0L) 0 else 1 + (value - 1) / divisor

        fun writeExactly(
            sink: BufferedSink,
            input: InputStream,
            length: Long,
            offset: Long,
            onProgress: (Long) -> Unit,
        ) {
            val buffer = ByteArray(BUFFER_BYTES)
            var written = 0L
            onProgress(offset)
            while (written < length) {
                val read = try {
                    input.read(buffer, 0, minOf(buffer.size.toLong(), length - written).toInt())
                } catch (error: IOException) {
                    throw LocalContentIOException(error)
                }
                if (read < 0) throw LocalContentIOException(IllegalStateException("Source size changed"))
                sink.write(buffer, 0, read)
                written += read
                onProgress(offset + written)
            }
        }

        fun uploadClient(): OkHttpClient = OkHttpNextcloudGateway.defaultClient().newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
