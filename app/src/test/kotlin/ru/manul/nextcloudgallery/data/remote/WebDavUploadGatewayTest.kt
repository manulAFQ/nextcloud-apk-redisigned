package ru.manul.nextcloudgallery.data.remote

import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.ServerInfo
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class WebDavUploadGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: WebDavUploadGateway

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        gateway = WebDavUploadGateway()
    }

    @After
    fun cleanUp() {
        server.close()
    }

    @Test
    fun `streams content with overwrite protection and progress`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(405).build())
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("ETag", "\"uploaded-etag\"")
                .build(),
        )
        server.enqueue(davFile("/remote.php/dav/files/user/Photos/%D0%A4%D0%BE%D1%82%D0%BE%201.jpg", 7, "uploaded-etag"))
        val progress = mutableListOf<Long>()

        val result = gateway.upload(
            session = session(),
            uploadId = "job-1",
            remotePath = "/Photos/Фото 1.jpg",
            sizeBytes = 7,
            recoverCompletedUpload = false,
            mimeType = "image/jpeg",
            source = { ByteArrayInputStream("content".toByteArray()) },
            onProgress = progress::add,
        )

        assertEquals("uploaded-etag", (result as AppResult.Success).value.etag)
        assertEquals(7L, progress.last())
        assertEquals("PROPFIND", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("*", request.headers["If-None-Match"])
        assertEquals("image/jpeg", request.headers["Content-Type"])
        assertEquals("Фото 1.jpg", request.url.pathSegments.last())
        assertEquals("content", requireNotNull(request.body).utf8())
    }

    @Test
    fun `existing remote file is not overwritten`() = runTest {
        server.enqueue(davFile("/remote.php/dav/files/user/Photos/photo.jpg", 9, "old"))

        val result = gateway.upload(
            session(),
            "job-1",
            "/Photos/photo.jpg",
            1,
            false,
            "image/jpeg",
            source = { ByteArrayInputStream(byteArrayOf(1)) },
            onProgress = {},
        )

        assertEquals(AppFailure.RemoteFileAlreadyExists, (result as AppResult.Failure).error)
    }

    @Test
    fun `changed local source size fails without success`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(405).build())
        server.enqueue(MockResponse.Builder().code(201).build())

        val result = gateway.upload(
            session(),
            "job-1",
            "/Photos/photo.jpg",
            5,
            false,
            "image/jpeg",
            source = { ByteArrayInputStream("tiny".toByteArray()) },
            onProgress = {},
        )

        assertTrue(result is AppResult.Failure)
        assertEquals(AppFailure.LocalContentUnavailable, (result as AppResult.Failure).error)
    }

    @Test
    fun `final size mismatch fails integrity check`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(405).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(davFile("/remote.php/dav/files/user/Photos/photo.jpg", 2, "wrong"))

        val result = gateway.upload(
            session(), "job-1", "/Photos/photo.jpg", 1, false, "image/jpeg",
            source = { ByteArrayInputStream(byteArrayOf(1)) },
            onProgress = {},
        )

        assertEquals(AppFailure.UploadIntegrityMismatch, (result as AppResult.Failure).error)
    }

    @Test
    fun `unique name is selected without overwriting existing file`() = runTest {
        server.enqueue(davFile("/remote.php/dav/files/user/Photos/photo.jpg", 1, "old"))
        server.enqueue(MockResponse.Builder().code(404).build())

        val result = gateway.resolveUniqueRemotePath(session(), "/Photos/photo.jpg")

        assertEquals("/Photos/photo (1).jpg", (result as AppResult.Success).value)
        assertEquals("photo.jpg", server.takeRequest().url.pathSegments.last())
        assertEquals("photo (1).jpg", server.takeRequest().url.pathSegments.last())
    }

    @Test
    fun `temporary upload collection is deleted through user dav api`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertTrue(gateway.abort(session(), "job-1") is AppResult.Success)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/remote.php/dav/uploads/user/job-1", request.url.encodedPath)
    }

    @Test
    fun `chunked retry skips an uploaded chunk and assembles the rest`() = runTest {
        val chunkBytes = 5 * 1024 * 1024
        val content = ByteArray(chunkBytes + 3) { 1 }
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(405).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(davChunks("job-1", chunkBytes.toLong()))
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        server.enqueue(davFile("/remote.php/dav/files/user/Photos/photo.jpg", content.size.toLong(), "final"))
        gateway = WebDavUploadGateway(
            capabilitiesGateway = ChunkingGateway(),
            chunkThresholdBytes = 1,
            preferredChunkBytes = chunkBytes.toLong(),
        )

        val result = gateway.upload(
            session(),
            "job-1",
            "/Photos/photo.jpg",
            content.size.toLong(),
            true,
            "image/jpeg",
            source = { ByteArrayInputStream(content) },
            onProgress = {},
        )

        assertEquals("final", (result as AppResult.Success).value.etag)
        val requests = List(7) { server.takeRequest() }
        assertEquals(listOf("PROPFIND", "MKCOL", "MKCOL", "PROPFIND", "PUT", "MOVE", "PROPFIND"), requests.map { it.method })
        assertEquals("00002", requests[4].url.pathSegments.last())
        assertEquals(3L, requireNotNull(requests[4].body).size.toLong())
        assertEquals("F", requests[5].headers["Overwrite"])
        assertEquals(content.size.toString(), requests[5].headers["OC-Total-Length"])
    }

    private fun davFile(href: String, size: Long, etag: String) = MockResponse.Builder()
        .code(207)
        .addHeader("Content-Type", "application/xml")
        .body(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>$href</d:href><d:propstat><d:prop>
                <d:getcontentlength>$size</d:getcontentlength><d:getetag>"$etag"</d:getetag>
              </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
            """.trimIndent(),
        )
        .build()

    private fun davChunks(uploadId: String, firstChunkSize: Long) = MockResponse.Builder()
        .code(207)
        .addHeader("Content-Type", "application/xml")
        .body(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/uploads/user/$uploadId/</d:href><d:propstat><d:prop>
                <d:resourcetype><d:collection/></d:resourcetype>
              </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/uploads/user/$uploadId/00001</d:href><d:propstat><d:prop>
                <d:getcontentlength>$firstChunkSize</d:getcontentlength>
              </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
            """.trimIndent(),
        )
        .build()

    private class ChunkingGateway : NextcloudGateway {
        override suspend fun probe(address: ServerAddress): AppResult<ServerInfo> =
            AppResult.Failure(AppFailure.UnsupportedServer)

        override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
            AppResult.Success(ServerCapabilities(chunkedUpload = true, previews = true, fileSharing = false))

        override suspend fun listMedia(
            session: UserSession,
            remotePath: String,
        ): AppResult<List<MediaItem>> = AppResult.Failure(AppFailure.UnsupportedServer)
    }

    private fun session() = UserSession(
        account = Account(
            id = "account",
            serverAddress = ServerAddress.parse(server.url("/").toString()).getOrThrow(),
            username = "user",
            displayName = null,
        ),
        appPassword = AppPassword.create("app-password").getOrThrow(),
    )
}
