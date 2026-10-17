package ru.manul.nextcloudgallery.data.remote

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import ru.manul.nextcloudgallery.core.model.ActivityEvent
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.RemoteAlbum
import ru.manul.nextcloudgallery.core.model.RemotePath
import ru.manul.nextcloudgallery.core.model.TrashItem
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.domain.repository.NextcloudSectionsRepository
import kotlin.coroutines.resume

class NextcloudSectionsGateway internal constructor(
    private val client: OkHttpClient = OkHttpNextcloudGateway.defaultClient(),
    private val parser: WebDavMultiStatusParser = WebDavMultiStatusParser(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NextcloudSectionsRepository {
    override suspend fun activities(session: UserSession, limit: Int): AppResult<List<ActivityEvent>> {
        val url = session.account.serverAddress.value.toHttpUrl().newBuilder()
            .addPathSegments("ocs/v2.php/apps/activity/api/v2/activity")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .build()
        val request = authenticated(session, url).header("OCS-APIRequest", "true").build()
        return execute(request) { response ->
            if (response.code == 204) return@execute AppResult.Success(emptyList())
            if (!response.isSuccessful) return@execute failure(response.code)
            val text = response.body?.string() ?: return@execute AppResult.Failure(AppFailure.InvalidServerResponse)
            runCatching {
                json.decodeFromString<ActivityEnvelope>(text).ocs.data.map { event ->
                    ActivityEvent(
                        id = event.activityId,
                        subject = event.subject,
                        message = event.message?.takeIf(String::isNotBlank),
                        app = event.app,
                        type = event.type,
                        actor = event.user?.takeIf(String::isNotBlank),
                        objectName = event.objectName?.takeIf(String::isNotBlank),
                        link = event.link?.takeIf(String::isNotBlank),
                        occurredAt = runCatching { Instant.parse(event.datetime) }.getOrDefault(Instant.EPOCH),
                    )
                }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppFailure.InvalidServerResponse) },
            )
        }
    }

    override suspend fun media(session: UserSession, limit: Int, offset: Int): AppResult<List<MediaItem>> {
        val safeLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val body = mediaSearchBody(
            username = session.account.userId,
            limit = safeLimit,
            offset = offset.coerceAtLeast(0),
            favoriteOnly = false,
        )
        val request = authenticated(session, davRootUrl(session))
            .method("SEARCH", body.toRequestBody(TEXT_XML))
            .header("Accept", "application/xml, text/xml")
            .build()
        val searched = execute(request) { response ->
            if (response.code != 207) return@execute failure(response.code)
            runCatching {
                parser.parse(response.body?.byteStream() ?: error("empty"))
                    .mapNotNull { it.toSearchMedia(session) }
                    .distinctBy(MediaItem::remotePath)
                    .sortedByDescending { it.capturedAt ?: it.modifiedAt }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppFailure.InvalidServerResponse) },
            )
        }
        if (searched is AppResult.Success && searched.value.isNotEmpty()) {
            // SearchDAV is capped by nresults. On large libraries some server
            // versions return a stable 2,000-item window even with ORDER BY,
            // which can hide a file that has just been uploaded to /Photos.
            // When the page is full, merge a direct listing of the upload
            // destination before applying the final newest-first limit.
            if (searched.value.size >= safeLimit && offset == 0) {
                val photos = RemotePath.parse(DEFAULT_UPLOAD_FOLDER).getOrThrow()
                val uploaded = propfind(session, davFilesCollectionUrl(session, photos), MEDIA_PROPS)
                    .successOrEmpty()
                    .drop(1)
                    .mapNotNull { it.toFolderMedia(photos) }
                return AppResult.Success(
                    (uploaded + searched.value)
                        .distinctBy(MediaItem::remotePath)
                        .sortedByDescending { it.capturedAt ?: it.modifiedAt }
                        .take(safeLimit),
                )
            }
            return searched
        }
        if (searched is AppResult.Success && offset > 0) return searched
        if (searched is AppResult.Failure && (searched.error == AppFailure.Unauthorized || searched.error == AppFailure.Forbidden)) {
            return searched
        }
        return scanMediaFolders(session, limit.coerceIn(1, MAX_SEARCH_RESULTS))
    }

    override suspend fun favorites(session: UserSession, limit: Int, offset: Int): AppResult<List<MediaItem>> {
        val body = mediaSearchBody(
            username = session.account.userId,
            limit = limit.coerceIn(1, MAX_SEARCH_RESULTS),
            offset = offset.coerceAtLeast(0),
            favoriteOnly = true,
        )
        val request = authenticated(session, davRootUrl(session))
            .method("SEARCH", body.toRequestBody(TEXT_XML))
            .header("Accept", "application/xml, text/xml")
            .build()
        return execute(request) { response ->
            if (response.code != 207) return@execute failure(response.code)
            runCatching {
                parser.parse(response.body.byteStream())
                    .mapNotNull { it.toSearchMedia(session) }
                    .distinctBy(MediaItem::remotePath)
                    .sortedByDescending { it.capturedAt ?: it.modifiedAt }
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppFailure.InvalidServerResponse) },
            )
        }
    }

    private suspend fun scanMediaFolders(session: UserSession, limit: Int): AppResult<List<MediaItem>> {
        val root = RemotePath.parse("/").getOrThrow()
        val folders = ArrayDeque<RemotePath>().apply { add(root) }
        val found = mutableListOf<MediaItem>()
        var inspected = 0
        while (folders.isNotEmpty() && found.size < limit && inspected < MAX_FALLBACK_FOLDERS) {
            val folder = folders.removeFirst()
            inspected++
            when (val listed = propfind(session, davFilesCollectionUrl(session, folder), MEDIA_PROPS)) {
                is AppResult.Failure -> if (folder.segments.isEmpty()) return listed
                is AppResult.Success -> listed.value.drop(1).forEach { entry ->
                    val name = decodedName(entry.href) ?: return@forEach
                    if (entry.isCollection) {
                        folder.child(name).getOrNull()?.let(folders::addLast)
                    } else {
                        entry.toFolderMedia(folder)?.let(found::add)
                    }
                }
            }
        }
        return AppResult.Success(found.distinctBy(MediaItem::remotePath)
            .sortedByDescending { it.capturedAt ?: it.modifiedAt }.take(limit))
    }

    override suspend fun albums(session: UserSession): AppResult<List<RemoteAlbum>> {
        val root = photosAlbumsPath(session)
        return propfind(session, davCollectionUrl(session, root), ALBUM_PROPS).map { entries ->
            entries.drop(1).mapNotNull { entry ->
                val pathName = decodedName(entry.href) ?: return@mapNotNull null
                val name = entry.originalName ?: pathName
                val path = root.child(pathName).getOrNull()?.value ?: return@mapNotNull null
                RemoteAlbum(
                    name = name,
                    remotePath = path,
                    modifiedAt = parseDate(entry.lastModified),
                    itemCount = entry.itemCount ?: 0,
                    coverFileId = entry.lastPhoto,
                )
            }.sortedBy(RemoteAlbum::name)
        }
    }

    override suspend fun albumMedia(session: UserSession, remotePath: String): AppResult<List<MediaItem>> {
        val path = RemotePath.parse(remotePath).getOrElse { return AppResult.Failure(AppFailure.InvalidRemotePath) }
        if (!isAlbumPath(session, path)) return AppResult.Failure(AppFailure.InvalidRemotePath)
        return propfind(session, davCollectionUrl(session, path), ALBUM_MEDIA_PROPS).map { entries ->
            entries.drop(1).mapNotNull { entry -> entry.toAlbumMedia(path) }
                .sortedByDescending { it.capturedAt ?: it.modifiedAt }
        }
    }

    override suspend fun createAlbum(session: UserSession, name: String): AppResult<RemoteAlbum> {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName == "." || cleanName == ".." || cleanName.any { it == '/' || it == '\\' || it == '\u0000' }) {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        val path = photosAlbumsPath(session).child(cleanName).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        val request = authenticated(session, davNamespaceUrl(session, path)).method("MKCOL", null).build()
        return execute(request) { response ->
            if (response.isSuccessful) AppResult.Success(RemoteAlbum(cleanName, path.value)) else failure(response.code)
        }
    }

    override suspend fun addToAlbum(
        session: UserSession,
        album: RemoteAlbum,
        items: List<MediaItem>,
    ): AppResult<Unit> {
        val albumPath = RemotePath.parse(album.remotePath).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        if (!isAlbumPath(session, albumPath)) return AppResult.Failure(AppFailure.InvalidRemotePath)
        for (item in items.distinctBy(MediaItem::remotePath)) {
            val sourcePath = RemotePath.parse(item.remotePath).getOrElse {
                return AppResult.Failure(AppFailure.InvalidRemotePath)
            }
            val destination = davNamespaceUrl(session, albumPath).newBuilder()
                .addPathSegment(item.displayName)
                .build()
            val request = authenticated(session, davFilesUrl(session, sourcePath))
                .method("COPY", null)
                .header("Destination", destination.toString())
                .header("Overwrite", "F")
                .build()
            when (val copied = execute(request) { response ->
                if (response.isSuccessful || response.code == 409) AppResult.Success(Unit) else failure(response.code)
            }) {
                is AppResult.Failure -> return copied
                is AppResult.Success -> Unit
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteMedia(session: UserSession, items: List<MediaItem>): AppResult<Unit> {
        for (item in items.distinctBy(MediaItem::remotePath)) {
            val path = RemotePath.parse(item.remotePath).getOrElse {
                return AppResult.Failure(AppFailure.InvalidRemotePath)
            }
            if (path.segments.isEmpty()) return AppResult.Failure(AppFailure.InvalidRemotePath)
            val request = authenticated(session, davFilesUrl(session, path)).delete().build()
            when (val deleted = execute(request) { response ->
                if (response.isSuccessful) AppResult.Success(Unit) else failure(response.code)
            }) {
                is AppResult.Failure -> return deleted
                is AppResult.Success -> Unit
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun setFavorite(session: UserSession, item: MediaItem, favorite: Boolean): AppResult<Unit> {
        val path = RemotePath.parse(item.remotePath).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        if (path.segments.isEmpty()) return AppResult.Failure(AppFailure.InvalidRemotePath)
        val value = if (favorite) "1" else "0"
        val body = """<?xml version="1.0"?><d:propertyupdate xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns"><d:set><d:prop><oc:favorite>$value</oc:favorite></d:prop></d:set></d:propertyupdate>"""
        val request = authenticated(session, davFilesUrl(session, path))
            .method("PROPPATCH", body.toRequestBody(XML))
            .build()
        return execute(request) { response ->
            if (response.code != 207) return@execute failure(response.code)
            val bodyText = response.body.string()
            if (Regex("HTTP/1\\.[01] 2\\d\\d").containsMatchIn(bodyText)) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(AppFailure.InvalidServerResponse)
            }
        }
    }

    override suspend fun trash(session: UserSession): AppResult<List<TrashItem>> {
        return propfind(session, trashUrl(session, "trash"), TRASH_PROPS).map { entries ->
            entries.drop(1).mapNotNull { entry ->
                val id = decodedName(entry.href) ?: return@mapNotNull null
                TrashItem(
                    id = id,
                    displayName = entry.trashFilename ?: id.substringBeforeLast(".d", id),
                    originalLocation = entry.trashOriginalLocation,
                    sizeBytes = entry.contentLength,
                    isFolder = entry.isCollection,
                    deletedAt = entry.trashDeletionTime?.let(Instant::ofEpochSecond),
                )
            }.sortedByDescending { it.deletedAt }
        }
    }

    override suspend fun restore(session: UserSession, item: TrashItem): AppResult<Unit> {
        val source = trashUrl(session, "trash", item.id)
        val destination = trashUrl(session, "restore", item.id)
        val request = authenticated(session, source).method("MOVE", null)
            .header("Destination", destination.toString()).header("Overwrite", "F").build()
        return execute(request) { response -> if (response.isSuccessful) AppResult.Success(Unit) else failure(response.code) }
    }

    override suspend fun deletePermanently(session: UserSession, item: TrashItem): AppResult<Unit> {
        val request = authenticated(session, trashUrl(session, "trash", item.id)).delete().build()
        return execute(request) { response -> if (response.isSuccessful) AppResult.Success(Unit) else failure(response.code) }
    }

    private suspend fun propfind(session: UserSession, url: HttpUrl, body: String): AppResult<List<DavEntry>> {
        val request = authenticated(session, url).method("PROPFIND", body.toRequestBody(XML))
            .header("Depth", "1").header("Accept", "application/xml, text/xml").build()
        return execute(request) { response ->
            if (response.code != 207) return@execute failure(response.code)
            runCatching { parser.parse(response.body?.byteStream() ?: error("empty")) }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(AppFailure.InvalidServerResponse) },
            )
        }
    }

    private fun authenticated(session: UserSession, url: HttpUrl): Request.Builder = Request.Builder().url(url)
        .header("Authorization", Credentials.basic(session.account.username, session.appPassword.reveal()))
        .header("Accept", "application/json").header("Cache-Control", "no-store")

    private suspend fun <T> execute(request: Request, mapper: (Response) -> AppResult<T>): AppResult<T> =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resume(AppResult.Failure(AppFailure.ServerUnavailable))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { if (continuation.isActive) continuation.resume(mapper(response)) }
                }
            })
        }

    private fun davFilesUrl(session: UserSession, path: RemotePath): HttpUrl =
        session.account.serverAddress.value.toHttpUrl().newBuilder().apply {
            listOf("remote.php", "dav", "files", session.account.userId).forEach(::addPathSegment)
            path.segments.forEach(::addPathSegment)
        }.build()

    private fun davFilesCollectionUrl(session: UserSession, path: RemotePath): HttpUrl =
        davFilesUrl(session, path).newBuilder().addPathSegment("").build()

    private fun davRootUrl(session: UserSession): HttpUrl =
        session.account.serverAddress.value.toHttpUrl().newBuilder().apply {
            listOf("remote.php", "dav").forEach(::addPathSegment)
            addPathSegment("")
        }.build()

    private fun davNamespaceUrl(session: UserSession, path: RemotePath): HttpUrl =
        davRootUrl(session).newBuilder().apply { path.segments.forEach(::addPathSegment) }.build()

    private fun davCollectionUrl(session: UserSession, path: RemotePath): HttpUrl =
        davNamespaceUrl(session, path).newBuilder().addPathSegment("").build()

    private fun photosAlbumsPath(session: UserSession): RemotePath =
        RemotePath.parse("/photos/${session.account.userId}/albums").getOrThrow()

    private fun isAlbumPath(session: UserSession, path: RemotePath): Boolean {
        val root = photosAlbumsPath(session).segments
        return path.segments.size > root.size && path.segments.take(root.size) == root
    }

    private fun trashUrl(session: UserSession, area: String, id: String? = null): HttpUrl =
        session.account.serverAddress.value.toHttpUrl().newBuilder().apply {
            listOf("remote.php", "dav", "trashbin", session.account.userId, area).forEach(::addPathSegment)
            id?.let(::addPathSegment)
        }.build()

    private fun decodedName(href: String): String? = runCatching {
        val segment = URI(href).rawPath?.trimEnd('/')?.substringAfterLast('/') ?: return@runCatching null
        HttpUrl.Builder().scheme("http").host("local.invalid").addEncodedPathSegment(segment).build().pathSegments.last()
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun DavEntry.toAlbumMedia(parent: RemotePath): MediaItem? {
        if (isCollection) return null
        val mime = contentType?.lowercase()?.substringBefore(';') ?: return null
        val kind = when { mime.startsWith("image/") -> MediaKind.IMAGE; mime.startsWith("video/") -> MediaKind.VIDEO; else -> return null }
        val name = fileName ?: decodedName(href) ?: return null
        return MediaItem(
            remotePath = parent.child(decodedName(href) ?: name).getOrNull()?.value ?: return null,
            displayName = name,
            mimeType = mime,
            kind = kind,
            sizeBytes = contentLength.coerceAtLeast(0),
            etag = etag?.trim('"'),
            capturedAt = originalDateTime?.let(Instant::ofEpochSecond),
            modifiedAt = parseDate(lastModified),
            fileId = fileId?.toLongOrNull(),
            favorite = favorite,
        )
    }

    private fun DavEntry.toFolderMedia(parent: RemotePath): MediaItem? {
        if (isCollection) return null
        val mime = contentType?.lowercase()?.substringBefore(';') ?: return null
        val kind = when {
            mime.startsWith("image/") -> MediaKind.IMAGE
            mime.startsWith("video/") -> MediaKind.VIDEO
            else -> return null
        }
        val name = fileName ?: decodedName(href) ?: return null
        val path = parent.child(name).getOrNull()?.value ?: return null
        return MediaItem(
            remotePath = path,
            displayName = name,
            mimeType = mime,
            kind = kind,
            sizeBytes = contentLength.coerceAtLeast(0),
            etag = etag?.trim('"'),
            capturedAt = originalDateTime?.let(Instant::ofEpochSecond),
            modifiedAt = parseDate(lastModified),
            fileId = fileId?.toLongOrNull(),
            favorite = favorite,
        )
    }

    private fun DavEntry.toSearchMedia(session: UserSession): MediaItem? {
        if (isCollection) return null
        val mime = contentType?.lowercase()?.substringBefore(';') ?: return null
        val kind = when {
            mime.startsWith("image/") -> MediaKind.IMAGE
            mime.startsWith("video/") -> MediaKind.VIDEO
            else -> return null
        }
        val path = fileRelativePath(session, href) ?: return null
        val name = fileName ?: decodedName(href) ?: return null
        return MediaItem(
            remotePath = path,
            displayName = name,
            mimeType = mime,
            kind = kind,
            sizeBytes = contentLength.coerceAtLeast(0),
            etag = etag?.trim('"'),
            capturedAt = originalDateTime?.let(Instant::ofEpochSecond),
            modifiedAt = parseDate(lastModified),
            fileId = fileId?.toLongOrNull(),
            favorite = favorite,
        )
    }

    private fun fileRelativePath(session: UserSession, href: String): String? = runCatching {
        val segments = URI(href).rawPath?.split('/')?.filter(String::isNotBlank)?.map(::decodeSegment)
            ?: return@runCatching null
        val fullMarker = listOf("remote.php", "dav", "files", session.account.userId)
        val shortMarker = listOf("files", session.account.userId)
        val marker = if (segments.windowed(fullMarker.size).any { it == fullMarker }) fullMarker else shortMarker
        val start = segments.indices.firstOrNull { index ->
            index + marker.size <= segments.size && segments.subList(index, index + marker.size) == marker
        } ?: return@runCatching null
        val relative = segments.drop(start + marker.size)
        if (relative.isEmpty()) null else "/" + relative.joinToString("/")
    }.getOrNull()

    private fun decodeSegment(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

    private fun parseDate(value: String?): Instant = runCatching {
        ZonedDateTime.parse(requireNotNull(value), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }.getOrDefault(Instant.EPOCH)

    private fun failure(code: Int): AppResult.Failure = AppResult.Failure(when (code) {
        401 -> AppFailure.Unauthorized; 403 -> AppFailure.Forbidden; 404 -> AppFailure.Http(404)
        else -> AppFailure.Http(code)
    })

    private inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value)); is AppResult.Failure -> this
    }

    private fun <T> AppResult<List<T>>.successOrEmpty(): List<T> =
        (this as? AppResult.Success<List<T>>)?.value.orEmpty()

    private companion object {
        const val MAX_SEARCH_RESULTS = 5_000
        const val MAX_FALLBACK_FOLDERS = 256
        const val DEFAULT_UPLOAD_FOLDER = "/Photos"
        val XML = "application/xml; charset=utf-8".toMediaType()
        val TEXT_XML = "text/xml; charset=utf-8".toMediaType()
        val ALBUM_PROPS = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns"><d:prop><d:resourcetype/><d:getlastmodified/><nc:original-name/><nc:last-photo/><nc:nbItems/></d:prop></d:propfind>"""
        val ALBUM_MEDIA_PROPS = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/><d:getcontenttype/><d:getetag/><oc:fileid/><oc:favorite/><nc:file-name/><nc:metadata-photos-original_date_time/><nc:has-preview/></d:prop></d:propfind>"""
        val MEDIA_PROPS = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/><d:getcontenttype/><d:getetag/><oc:fileid/><oc:favorite/><nc:metadata-photos-original_date_time/><nc:has-preview/></d:prop></d:propfind>"""
        val TRASH_PROPS = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns"><d:prop><d:resourcetype/><d:getcontentlength/><nc:trashbin-filename/><nc:trashbin-original-location/><nc:trashbin-deletion-time/></d:prop></d:propfind>"""

        fun mediaSearchBody(username: String, limit: Int, offset: Int, favoriteOnly: Boolean = false): String {
            val safeUser = username.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val mimeConditions = listOf(
                "image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif",
                "video/mp4", "video/quicktime", "video/webm", "video/x-matroska",
            ).joinToString("") { mime ->
                "<d:eq><d:prop><d:getcontenttype/></d:prop><d:literal>$mime</d:literal></d:eq>"
            }
            val favoriteCondition = if (favoriteOnly) {
                "<d:eq><d:prop><oc:favorite/></d:prop><d:literal>1</d:literal></d:eq>"
            } else {
                ""
            }
            return """<?xml version="1.0" encoding="UTF-8"?>
                <d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns" xmlns:ns="https://github.com/icewind1991/SearchDAV/ns">
                  <d:basicsearch>
                    <d:select><d:prop><d:getcontentlength/><d:getcontenttype/><d:getetag/><d:getlastmodified/><d:resourcetype/><nc:metadata-photos-original_date_time/><nc:has-preview/><nc:realpath/><oc:fileid/><oc:favorite/></d:prop></d:select>
                    <d:from><d:scope><d:href>/files/$safeUser/</d:href><d:depth>infinity</d:depth></d:scope></d:from>
                    <d:where><d:and><d:or>$mimeConditions</d:or>$favoriteCondition</d:and></d:where>
                    <d:orderby><d:order><d:prop><nc:metadata-photos-original_date_time/></d:prop><d:descending/></d:order><d:order><d:prop><d:getlastmodified/></d:prop><d:descending/></d:order></d:orderby>
                    <d:limit><d:nresults>$limit</d:nresults><ns:firstresult>$offset</ns:firstresult></d:limit>
                  </d:basicsearch>
                </d:searchrequest>""".trimIndent()
        }
    }
}

@Serializable private data class ActivityEnvelope(val ocs: ActivityBody)
@Serializable private data class ActivityBody(val data: List<ActivityPayload> = emptyList())
@Serializable private data class ActivityPayload(
    @SerialName("activity_id") val activityId: Long,
    val datetime: String,
    val app: String,
    val type: String,
    val user: String? = null,
    val subject: String,
    val message: String? = null,
    val link: String? = null,
    @SerialName("object_name") val objectName: String? = null,
)
