package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaFolderChange
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.RemotePath
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import kotlin.coroutines.resume

class WebDavMediaGateway internal constructor(
    private val client: OkHttpClient = OkHttpNextcloudGateway.defaultClient(),
    private val parser: WebDavMultiStatusParser = WebDavMultiStatusParser(),
) : NextcloudGateway {
    override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> =
        AppResult.Failure(AppFailure.UnsupportedServer)

    override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
        AppResult.Failure(AppFailure.UnsupportedServer)

    override suspend fun listMedia(
        session: UserSession,
        remotePath: String,
    ): AppResult<List<MediaItem>> = when (
        val result = readMediaFolder(session, remotePath, previousDirectoryEtag = null)
    ) {
        is AppResult.Success -> when (val change = result.value) {
            is MediaFolderChange.Snapshot -> AppResult.Success(change.items)
            is MediaFolderChange.Unchanged -> AppResult.Success(emptyList())
        }
        is AppResult.Failure -> result
    }

    override suspend fun readMediaFolder(
        session: UserSession,
        remotePath: String,
        previousDirectoryEtag: String?,
    ): AppResult<MediaFolderChange> {
        val path = RemotePath.parse(remotePath).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }

        var probedDirectoryEtag: String? = null
        if (previousDirectoryEtag != null) {
            when (val shallow = execute(session, path, depth = 0)) {
                is AppResult.Failure -> return shallow
                is AppResult.Success -> {
                    val currentEtag = shallow.value.directoryEtag()
                    probedDirectoryEtag = currentEtag
                    if (currentEtag != null && currentEtag == normalizeEtag(previousDirectoryEtag)) {
                        return AppResult.Success(MediaFolderChange.Unchanged(currentEtag))
                    }
                }
            }
        }

        return when (val full = execute(session, path, depth = 1)) {
            is AppResult.Failure -> full
            is AppResult.Success -> {
                val media = full.value.mapNotNull { entry -> entry.toMediaItem(path) }
                    .sortedByDescending(MediaItem::modifiedAt)
                AppResult.Success(
                    MediaFolderChange.Snapshot(
                        directoryEtag = full.value.directoryEtag() ?: probedDirectoryEtag,
                        items = media,
                    ),
                )
            }
        }
    }

    private suspend fun execute(
        session: UserSession,
        path: RemotePath,
        depth: Int,
    ): AppResult<List<DavEntry>> {
        val request = Request.Builder()
            .url(buildDavUrl(session, path))
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth.toString())
            .header("Accept", "application/xml, text/xml")
            .header("Authorization", Credentials.basic(session.account.username, session.appPassword.reveal()))
            .header("Cache-Control", "no-store")
            .build()

        return execute(request)
    }

    private suspend fun execute(request: Request): AppResult<List<DavEntry>> =
        suspendCancellableCoroutine { continuation ->
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
                        response.use {
                            if (!continuation.isActive) return
                            continuation.resume(mapResponse(response))
                        }
                    }
                },
            )
        }

    private fun mapResponse(response: Response): AppResult<List<DavEntry>> {
        if (response.isRedirect) return AppResult.Failure(AppFailure.UnsafeRedirect)
        if (response.code != HTTP_MULTI_STATUS) return AppResult.Failure(httpFailure(response.code))
        val body = response.body ?: return AppResult.Failure(AppFailure.InvalidServerResponse)

        return try {
            AppResult.Success(parser.parse(LimitedInputStream(body.byteStream(), MAX_DAV_RESPONSE_BYTES)))
        } catch (error: Exception) {
            if (error.hasCause<ResponseTooLargeException>()) {
                AppResult.Failure(AppFailure.ResponseTooLarge)
            } else {
                AppResult.Failure(AppFailure.InvalidServerResponse)
            }
        }
    }

    private fun List<DavEntry>.directoryEtag(): String? =
        firstOrNull(DavEntry::isCollection)?.etag?.let(::normalizeEtag)

    private fun normalizeEtag(value: String): String = value.trim().trim('"')

    private fun DavEntry.toMediaItem(parent: RemotePath): MediaItem? {
        if (isCollection) return null
        val mime = contentType?.lowercase()?.substringBefore(';') ?: return null
        val kind = when {
            mime.startsWith("image/") -> MediaKind.IMAGE
            mime.startsWith("video/") -> MediaKind.VIDEO
            else -> return null
        }
        val name = decodedFileName(href)?.takeIf(String::isNotBlank) ?: return null
        val path = parent.child(name).getOrNull() ?: return null
        return MediaItem(
            remotePath = path.value,
            displayName = name,
            mimeType = mime,
            kind = kind,
            sizeBytes = contentLength.coerceAtLeast(0),
            etag = etag?.let(::normalizeEtag),
            capturedAt = null,
            modifiedAt = parseWebDavDate(lastModified),
        )
    }

    private fun buildDavUrl(session: UserSession, path: RemotePath): HttpUrl {
        val builder = session.account.serverAddress.value.toHttpUrl().newBuilder()
        listOf("remote.php", "dav", "files", session.account.userId).forEach(builder::addPathSegment)
        path.segments.forEach(builder::addPathSegment)
        return builder.build()
    }

    private fun decodedFileName(href: String): String? = runCatching {
        val rawPath = URI(href).rawPath ?: return@runCatching null
        val encodedSegment = rawPath.trimEnd('/').substringAfterLast('/')
        HttpUrl.Builder()
            .scheme("http")
            .host("local.invalid")
            .addEncodedPathSegment(encodedSegment)
            .build()
            .pathSegments
            .lastOrNull()
    }.getOrNull()

    private fun parseWebDavDate(value: String?): Instant = runCatching {
        ZonedDateTime.parse(requireNotNull(value), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }.getOrDefault(Instant.EPOCH)

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private fun httpFailure(code: Int): AppFailure = when (code) {
        401 -> AppFailure.Unauthorized
        403 -> AppFailure.Forbidden
        404 -> AppFailure.Http(404)
        507 -> AppFailure.InsufficientStorage
        else -> AppFailure.Http(code)
    }

    private companion object {
        const val HTTP_MULTI_STATUS = 207
        const val MAX_DAV_RESPONSE_BYTES = 16L * 1024 * 1024
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        val PROPFIND_BODY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              <d:prop>
                <d:getlastmodified/>
                <d:getcontentlength/>
                <d:getcontenttype/>
                <d:resourcetype/>
                <d:getetag/>
                <oc:fileid/>
                <oc:size/>
                <nc:has-preview/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }
}
