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
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

class NextcloudSectionsGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: NextcloudSectionsGateway

    @Before fun setUp() { server = MockWebServer(); server.start(); gateway = NextcloudSectionsGateway() }
    @After fun tearDown() = server.close()

    @Test fun `loads real activity feed`() = runTest {
        server.enqueue(MockResponse.Builder().body(ACTIVITY).build())
        val result = gateway.activities(session(), 20)
        assertTrue(result is AppResult.Success)
        val event = (result as AppResult.Success).value.single()
        assertEquals(17L, event.id)
        assertEquals("Test user added photo.jpg", event.subject)
        val request = server.takeRequest()
        assertEquals("/ocs/v2.php/apps/activity/api/v2/activity", request.url.encodedPath)
        assertEquals("true", request.headers["OCS-APIRequest"])
    }

    @Test fun `lists albums and decodes names`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(ALBUMS).build())
        val result = gateway.albums(session())
        assertTrue(result is AppResult.Success)
        assertEquals("Семья", (result as AppResult.Success).value.single().name)
        assertEquals("/photos/testuser/albums/Семья", result.value.single().remotePath)
        assertEquals(3, result.value.single().itemCount)
        val request = server.takeRequest()
        assertEquals("/remote.php/dav/photos/testuser/albums/", request.url.encodedPath)
    }

    @Test fun `searches photos recursively across all nextcloud folders`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(MEDIA_SEARCH).build())
        val result = gateway.media(session()) as AppResult.Success
        val item = result.value.single()
        assertEquals("/Camera/October/photo.jpg", item.remotePath)
        assertEquals(42L, item.fileId)
        val request = server.takeRequest()
        assertEquals("SEARCH", request.method)
        assertEquals("/remote.php/dav/", request.url.encodedPath)
        assertTrue(request.headers["Content-Type"]!!.startsWith("text/xml"))
        val xml = request.body?.utf8().orEmpty()
        assertTrue(xml.contains("<d:depth>infinity</d:depth>"))
        assertTrue(xml.contains("/files/testuser/"))
        assertTrue(xml.contains("<d:where><d:and><d:or>"))
    }

    @Test fun `full search page merges newest file from upload folder`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(MEDIA_SEARCH).build())
        server.enqueue(MockResponse.Builder().code(207).body(PHOTOS_FOLDER).build())

        val result = gateway.media(session(), limit = 1) as AppResult.Success

        assertEquals("/Photos/new-upload.jpg", result.value.single().remotePath)
        server.takeRequest()
        val photosRequest = server.takeRequest()
        assertEquals("PROPFIND", photosRequest.method)
        assertEquals("/remote.php/dav/files/testuser/Photos/", photosRequest.url.encodedPath)
    }

    @Test fun `falls back to recursive dav listing when photos search is empty`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body("<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\"/>").build())
        server.enqueue(MockResponse.Builder().code(207).body(ROOT_FOLDER).build())
        server.enqueue(MockResponse.Builder().code(207).body(CAMERA_FOLDER).build())

        val result = gateway.media(session()) as AppResult.Success

        assertEquals("/Camera/photo.jpg", result.value.single().remotePath)
        assertEquals(listOf("SEARCH", "PROPFIND", "PROPFIND"), List(3) { server.takeRequest().method })
    }

    @Test fun `uses profile uid for oidc dav paths and parses subpath href`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(OIDC_MEDIA_SEARCH).build())

        val result = gateway.media(session(username = "user@example.test", userId = "admin")) as AppResult.Success

        assertEquals("/Photos/photo.jpg", result.value.single().remotePath)
        val request = server.takeRequest()
        assertTrue(request.body?.utf8().orEmpty().contains("<d:href>/files/admin/</d:href>"))
    }

    @Test fun `lists oidc albums using profile uid`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(OIDC_ALBUMS).build())

        val result = gateway.albums(session(username = "user@example.test", userId = "admin")) as AppResult.Success

        assertEquals(listOf("ргш", "рог", "шщ8"), result.value.map { it.name })
        assertEquals("/remote.php/dav/photos/admin/albums/", server.takeRequest().url.encodedPath)
    }

    @Test fun `creates photos album and adds existing media`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).build())
        val created = gateway.createAlbum(session(), "Семья") as AppResult.Success
        assertEquals("/photos/testuser/albums/Семья", created.value.remotePath)
        val mkcol = server.takeRequest()
        assertEquals("MKCOL", mkcol.method)

        server.enqueue(MockResponse.Builder().code(201).build())
        val media = ru.manul.nextcloudgallery.core.model.MediaItem(
            "/Camera/photo.jpg", "photo.jpg", "image/jpeg",
            ru.manul.nextcloudgallery.core.model.MediaKind.IMAGE, 12, "etag", null,
            java.time.Instant.EPOCH, 42,
        )
        assertTrue(gateway.addToAlbum(session(), created.value, listOf(media)) is AppResult.Success)
        val copy = server.takeRequest()
        assertEquals("COPY", copy.method)
        assertEquals("/remote.php/dav/files/testuser/Camera/photo.jpg", copy.url.encodedPath)
        assertTrue(copy.headers["Destination"]!!.contains("/remote.php/dav/photos/testuser/albums/"))
    }

    @Test fun `lists and restores trash item`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(TRASH).build())
        val trash = gateway.trash(session()) as AppResult.Success
        val item = trash.value.single()
        assertEquals("photo.jpg", item.displayName)
        assertEquals("Photos/photo.jpg", item.originalLocation)

        server.enqueue(MockResponse.Builder().code(201).build())
        assertTrue(gateway.restore(session(), item) is AppResult.Success)
        server.takeRequest()
        val move = server.takeRequest()
        assertEquals("MOVE", move.method)
        assertTrue(move.headers["Destination"]!!.endsWith("/restore/photo.jpg.d1792140000"))
    }

    @Test fun `deletes selected media through files dav`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        val first = media("/Photos/one.jpg", "one.jpg")
        val second = media("/Camera/two.jpg", "two.jpg")

        assertTrue(gateway.deleteMedia(session(), listOf(first, second)) is AppResult.Success)

        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("DELETE", firstRequest.method)
        assertEquals("/remote.php/dav/files/testuser/Photos/one.jpg", firstRequest.url.encodedPath)
        assertEquals("/remote.php/dav/files/testuser/Camera/two.jpg", secondRequest.url.encodedPath)
    }

    @Test fun `updates favorite through dav property`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body("<?xml version=\"1.0\"?><d:multistatus xmlns:d=\"DAV:\"><d:response><d:propstat><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>").build())

        assertTrue(gateway.setFavorite(session(), media("/Photos/one.jpg", "one.jpg"), true) is AppResult.Success)

        val request = server.takeRequest()
        assertEquals("PROPPATCH", request.method)
        assertTrue(request.body?.utf8().orEmpty().contains("<oc:favorite>1</oc:favorite>"))
    }

    @Test fun `loads requested five hundred item window`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(MEDIA_SEARCH).build())

        gateway.media(session(), limit = 500, offset = 1_000)

        val xml = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(xml.contains("<d:nresults>500</d:nresults>"))
        assertTrue(xml.contains("<ns:firstresult>1000</ns:firstresult>"))
    }

    @Test fun `favorites search filters favorite dav property`() = runTest {
        server.enqueue(MockResponse.Builder().code(207).body(MEDIA_SEARCH).build())

        gateway.favorites(session(), limit = 500)

        val xml = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(xml.contains("<oc:favorite/>"))
        assertTrue(xml.contains("<d:literal>1</d:literal>"))
    }

    private fun media(path: String, name: String) = ru.manul.nextcloudgallery.core.model.MediaItem(
        path, name, "image/jpeg", ru.manul.nextcloudgallery.core.model.MediaKind.IMAGE,
        12, "etag", null, java.time.Instant.EPOCH, 42,
    )

    private fun session(username: String = "testuser", userId: String = username) = UserSession(
        Account("account", ServerAddress.parse(server.url("/").toString()).getOrThrow(), username, null, userId),
        AppPassword.create("app-password").getOrThrow(),
    )

    private companion object {
        val ACTIVITY = """{"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK"},"data":[{"activity_id":17,"datetime":"2026-10-16T09:00:00+00:00","app":"files","type":"file_created","user":"testuser","subject":"Test user added photo.jpg","message":"","link":"https://cloud.test/f/1","object_name":"/Photos/photo.jpg"}]}}"""
        val ALBUMS = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns">
              <d:response><d:href>/remote.php/dav/photos/testuser/albums/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/photos/testuser/albums/%D0%A1%D0%B5%D0%BC%D1%8C%D1%8F/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getlastmodified>Fri, 16 Oct 2026 09:00:00 GMT</d:getlastmodified><nc:nbItems>3</nc:nbItems><nc:last-photo>42</nc:last-photo></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val MEDIA_SEARCH = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              <d:response><d:href>/remote.php/dav/files/testuser/Camera/October/photo.jpg</d:href><d:propstat><d:prop><d:getcontentlength>12</d:getcontentlength><d:getcontenttype>image/jpeg</d:getcontenttype><d:getetag>etag</d:getetag><d:getlastmodified>Fri, 16 Oct 2026 09:00:00 GMT</d:getlastmodified><oc:fileid>42</oc:fileid><nc:metadata-photos-original_date_time>1792140000</nc:metadata-photos-original_date_time></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val OIDC_MEDIA_SEARCH = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              <d:response><d:href>/files/remote.php/dav/files/admin/Photos/photo.jpg</d:href><d:propstat><d:prop><d:getcontentlength>12</d:getcontentlength><d:getcontenttype>image/jpeg</d:getcontenttype><d:getetag>etag</d:getetag><d:getlastmodified>Fri, 16 Oct 2026 09:00:00 GMT</d:getlastmodified><d:resourcetype/><nc:has-preview>true</nc:has-preview><oc:fileid>42</oc:fileid></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat><d:propstat><d:prop><nc:metadata-photos-original_date_time/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val OIDC_ALBUMS = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns">
              <d:response><d:href>/files/remote.php/dav/photos/admin/albums/</d:href><d:propstat><d:prop><nc:last-photo/><nc:nbItems/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>
              <d:response><d:href>/files/remote.php/dav/photos/admin/albums/%d1%80%d0%b3%d1%88/</d:href><d:propstat><d:prop><nc:last-photo>-1</nc:last-photo><nc:nbItems>0</nc:nbItems></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/files/remote.php/dav/photos/admin/albums/%d1%88%d1%898/</d:href><d:propstat><d:prop><nc:last-photo>-1</nc:last-photo><nc:nbItems>0</nc:nbItems></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/files/remote.php/dav/photos/admin/albums/%d1%80%d0%be%d0%b3/</d:href><d:propstat><d:prop><nc:last-photo>-1</nc:last-photo><nc:nbItems>0</nc:nbItems></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val ROOT_FOLDER = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/files/testuser/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/testuser/Camera/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val CAMERA_FOLDER = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response><d:href>/remote.php/dav/files/testuser/Camera/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/testuser/Camera/photo.jpg</d:href><d:propstat><d:prop><d:getcontenttype>image/jpeg</d:getcontenttype><d:getcontentlength>12</d:getcontentlength><d:getlastmodified>Fri, 16 Oct 2026 09:00:00 GMT</d:getlastmodified><oc:fileid>42</oc:fileid></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val PHOTOS_FOLDER = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response><d:href>/remote.php/dav/files/testuser/Photos/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/testuser/Photos/new-upload.jpg</d:href><d:propstat><d:prop><d:getcontenttype>image/jpeg</d:getcontenttype><d:getcontentlength>243800</d:getcontentlength><d:getlastmodified>Fri, 16 Oct 2026 12:15:00 GMT</d:getlastmodified><oc:fileid>572391</oc:fileid></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val TRASH = """
            <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns">
              <d:response><d:href>/remote.php/dav/trashbin/testuser/trash/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/trashbin/testuser/trash/photo.jpg.d1792140000</d:href><d:propstat><d:prop><d:getcontentlength>42</d:getcontentlength><nc:trashbin-filename>photo.jpg</nc:trashbin-filename><nc:trashbin-original-location>Photos/photo.jpg</nc:trashbin-original-location><nc:trashbin-deletion-time>1792140000</nc:trashbin-deletion-time></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
    }
}
