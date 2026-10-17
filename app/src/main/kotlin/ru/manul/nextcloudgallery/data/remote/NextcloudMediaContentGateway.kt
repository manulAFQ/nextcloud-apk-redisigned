package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import ru.manul.nextcloudgallery.core.model.ByteRange
import ru.manul.nextcloudgallery.core.model.PreviewMode
import ru.manul.nextcloudgallery.core.model.RemoteMediaStream
import ru.manul.nextcloudgallery.core.model.RemotePath
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import kotlin.coroutines.resume

class NextcloudMediaContentGateway(
    private val client: OkHttpClient = streamingClient(),
) : MediaContentGateway {
    override suspend fun openPreview(
        session: UserSession,
        remotePath: String,
        width: Int,
        height: Int,
        mode: PreviewMode,
        version: String?,
    ): AppResult<RemoteMediaStream> {
        val path = RemotePath.parse(remotePath).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        if (width !in MIN_PREVIEW_EDGE..MAX_PREVIEW_EDGE || height !in MIN_PREVIEW_EDGE..MAX_PREVIEW_EDGE) {
            return AppResult.Failure(AppFailure.InvalidPreviewRequest)
        }

        val request = authenticatedRequest(session, buildPreviewUrl(session, path, width, height, mode, version))
            .header("Accept", "image/*")
            .header("X-NC-Preview", "true")
            .build()
        return execute(request, expectedRange = null, isPreview = true)
    }

    override suspend fun openOriginal(
        session: UserSession,
        remotePath: String,
        range: ByteRange?,
    ): AppResult<RemoteMediaStream> {
        val path = RemotePath.parse(remotePath).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        val requestBuilder = authenticatedRequest(session, buildDavUrl(session, path))
            .header("Accept", "*/*")
        if (range != null) requestBuilder.header("Range", range.toHeaderValue())
        return execute(requestBuilder.build(), expectedRange = range, isPreview = false)
    }

    private suspend fun execute(
        request: Request,
        expectedRange: ByteRange?,
        isPreview: Boolean,
    ): AppResult<RemoteMediaStream> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(AppResult.Failure(AppFailure.ServerUnavailable))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    continuation.resume(mapResponse(response, expectedRange, isPreview))
                }
            },
        )
    }

    private fun mapResponse(
        response: Response,
        expectedRange: ByteRange?,
        isPreview: Boolean,
    ): AppResult<RemoteMediaStream> {
        if (response.isRedirect) return response.failure(AppFailure.UnsafeRedirect)
        if (isPreview && response.code != HTTP_OK) {
            return response.failure(httpFailure(response.code, isPreview = true))
        }
        if (!isPreview && response.code != HTTP_OK && response.code != HTTP_PARTIAL_CONTENT) {
            return response.failure(httpFailure(response.code, isPreview = false))
        }

        val body = response.body ?: return response.failure(AppFailure.InvalidServerResponse)
        val contentRange = response.header("Content-Range")?.let(::parseContentRange)
        if (response.code == HTTP_PARTIAL_CONTENT && contentRange == null) {
            return response.failure(AppFailure.InvalidServerResponse)
        }
        if (
            response.code == HTTP_PARTIAL_CONTENT &&
            body.contentLength() >= 0 &&
            contentRange != null &&
            body.contentLength() != contentRange.endInclusive - contentRange.start + 1
        ) {
            return response.failure(AppFailure.InvalidServerResponse)
        }
        if (expectedRange != null) {
            when {
                response.code == HTTP_OK ->
                    return response.failure(AppFailure.RangeNotSupported)
                response.code == HTTP_PARTIAL_CONTENT && contentRange?.start != expectedRange.start ->
                    return response.failure(AppFailure.InvalidServerResponse)
                response.code == HTTP_PARTIAL_CONTENT &&
                    expectedRange.endInclusive != null &&
                    contentRange != null &&
                    contentRange.endInclusive > expectedRange.endInclusive ->
                    return response.failure(AppFailure.InvalidServerResponse)
            }
        }

        return AppResult.Success(
            RemoteMediaStream(
                input = body.byteStream(),
                contentType = body.contentType()?.toString(),
                contentLength = body.contentLength().takeIf { it >= 0 },
                etag = response.header("ETag")?.trim()?.trim('"'),
                rangeStart = contentRange?.start,
                rangeEndInclusive = contentRange?.endInclusive,
                totalLength = contentRange?.totalLength,
                acceptsByteRanges = response.code == HTTP_PARTIAL_CONTENT ||
                    response.header("Accept-Ranges").equals("bytes", ignoreCase = true),
                closeAction = response::close,
            ),
        )
    }

    private fun authenticatedRequest(session: UserSession, url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .get()
        .header("Authorization", Credentials.basic(session.account.username, session.appPassword.reveal()))
        .header("Cache-Control", "no-store")

    private fun buildPreviewUrl(
        session: UserSession,
        path: RemotePath,
        width: Int,
        height: Int,
        mode: PreviewMode,
        version: String?,
    ): HttpUrl = session.account.serverAddress.value.toHttpUrl().newBuilder()
        .addPathSegment("index.php")
        .addPathSegment("core")
        .addPathSegment("preview.png")
        .addQueryParameter("file", path.value)
        .addQueryParameter("x", width.toString())
        .addQueryParameter("y", height.toString())
        .addQueryParameter("a", "1")
        .addQueryParameter("mode", mode.wireValue)
        .addQueryParameter("forceIcon", "0")
        .addQueryParameter("mimeFallback", "0")
        .apply { version?.takeIf(String::isNotBlank)?.let { addQueryParameter("v", it) } }
        .build()

    private fun buildDavUrl(session: UserSession, path: RemotePath): HttpUrl {
        val builder = session.account.serverAddress.value.toHttpUrl().newBuilder()
        listOf("remote.php", "dav", "files", session.account.userId).forEach(builder::addPathSegment)
        path.segments.forEach(builder::addPathSegment)
        return builder.build()
    }

    private fun parseContentRange(value: String): ParsedContentRange? {
        val match = CONTENT_RANGE.matchEntire(value.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val endInclusive = match.groupValues[2].toLongOrNull() ?: return null
        val totalLength = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        if (endInclusive < start || totalLength != null && endInclusive >= totalLength) return null
        return ParsedContentRange(start, endInclusive, totalLength)
    }

    private fun httpFailure(code: Int, isPreview: Boolean): AppFailure = when (code) {
        400, 404 -> if (isPreview) AppFailure.PreviewUnavailable else AppFailure.Http(code)
        401 -> AppFailure.Unauthorized
        403 -> AppFailure.Forbidden
        416 -> AppFailure.InvalidByteRange
        507 -> AppFailure.InsufficientStorage
        else -> AppFailure.Http(code)
    }

    private fun Response.failure(failure: AppFailure): AppResult.Failure {
        close()
        return AppResult.Failure(failure)
    }

    private data class ParsedContentRange(
        val start: Long,
        val endInclusive: Long,
        val totalLength: Long?,
    )

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val MIN_PREVIEW_EDGE = 16
        const val MAX_PREVIEW_EDGE = 2_048
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)

        fun streamingClient(): OkHttpClient = OkHttpNextcloudGateway.defaultClient().newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
