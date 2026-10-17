package ru.manul.nextcloudgallery.data.remote

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
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.MediaFolderChange
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class WebDavMediaGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: WebDavMediaGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = WebDavMediaGateway()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `lists only image and video with metadata`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(207)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .body(MULTI_STATUS)
                .build(),
        )

        val result = gateway.listMedia(session(), "/Photos/Фото 2026")

        assertTrue(result is AppResult.Success)
        val media = (result as AppResult.Success).value
        assertEquals(2, media.size)
        assertEquals("clip.mp4", media[0].displayName)
        assertEquals(MediaKind.VIDEO, media[0].kind)
        assertEquals(2_048L, media[0].sizeBytes)
        assertEquals("video-etag", media[0].etag)
        assertEquals("/Photos/Фото 2026/clip.mp4", media[0].remotePath)
        assertEquals("Фото 1.jpg", media[1].displayName)
        assertEquals(MediaKind.IMAGE, media[1].kind)

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.headers["Depth"])
        assertEquals("Фото 2026", request.url.pathSegments.last())
    }

    @Test
    fun `rejects traversal before network call`() = runTest {
        val result = gateway.listMedia(session(), "/Photos/../secrets")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppFailure.InvalidRemotePath)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `unchanged directory etag avoids full metadata listing`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(207)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .body(FOLDER_STATUS)
                .build(),
        )

        val result = gateway.readMediaFolder(
            session = session(),
            remotePath = "/Photos/Фото 2026",
            previousDirectoryEtag = "folder-etag",
        )

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).value is MediaFolderChange.Unchanged)
        assertEquals(1, server.requestCount)
        assertEquals("0", server.takeRequest().headers["Depth"])
    }

    @Test
    fun `changed directory fetches complete snapshot`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(207)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .body(FOLDER_STATUS.replace("folder-etag", "new-folder-etag"))
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(207)
                .addHeader("Content-Type", "application/xml; charset=utf-8")
                .body(MULTI_STATUS.replace("folder-etag", "new-folder-etag"))
                .build(),
        )

        val result = gateway.readMediaFolder(
            session = session(),
            remotePath = "/Photos/Фото 2026",
            previousDirectoryEtag = "folder-etag",
        )

        assertTrue(result is AppResult.Success)
        val snapshot = (result as AppResult.Success).value as MediaFolderChange.Snapshot
        assertEquals("new-folder-etag", snapshot.directoryEtag)
        assertEquals(2, snapshot.items.size)
        assertEquals("0", server.takeRequest().headers["Depth"])
        assertEquals("1", server.takeRequest().headers["Depth"])
    }

    @Test
    fun `maps malformed XML to invalid response`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body("<not-closed>").build())

        val result = gateway.listMedia(session(), "/Photos")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppFailure.InvalidServerResponse)
    }

    private fun session(): UserSession = UserSession(
        account = Account(
            id = "account-id",
            serverAddress = ServerAddress.parse(server.url("/").toString()).getOrThrow(),
            username = "testuser",
            displayName = null,
        ),
        appPassword = AppPassword.create("test-app-password").getOrThrow(),
    )

    private companion object {
        val MULTI_STATUS = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/Photos/%D0%A4%D0%BE%D1%82%D0%BE%202026/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>
                  <d:getetag>&quot;folder-etag&quot;</d:getetag></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/Photos/%D0%A4%D0%BE%D1%82%D0%BE%202026/%D0%A4%D0%BE%D1%82%D0%BE%201.jpg</d:href>
                <d:propstat><d:prop><d:getcontenttype>image/jpeg</d:getcontenttype>
                  <d:getcontentlength>1024</d:getcontentlength>
                  <d:getlastmodified>Mon, 12 Oct 2026 18:00:00 GMT</d:getlastmodified>
                  <d:getetag>&quot;image-etag&quot;</d:getetag><nc:has-preview>true</nc:has-preview></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/Photos/%D0%A4%D0%BE%D1%82%D0%BE%202026/clip.mp4</d:href>
                <d:propstat><d:prop><d:getcontenttype>video/mp4</d:getcontenttype>
                  <d:getcontentlength>2048</d:getcontentlength>
                  <d:getlastmodified>Tue, 13 Oct 2026 19:00:00 GMT</d:getlastmodified>
                  <d:getetag>&quot;video-etag&quot;</d:getetag></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/testuser/Photos/%D0%A4%D0%BE%D1%82%D0%BE%202026/readme.txt</d:href>
                <d:propstat><d:prop><d:getcontenttype>text/plain</d:getcontenttype>
                  <d:getcontentlength>12</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val FOLDER_STATUS = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/testuser/Photos/%D0%A4%D0%BE%D1%82%D0%BE%202026/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>
                  <d:getetag>&quot;folder-etag&quot;</d:getetag></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }
}
