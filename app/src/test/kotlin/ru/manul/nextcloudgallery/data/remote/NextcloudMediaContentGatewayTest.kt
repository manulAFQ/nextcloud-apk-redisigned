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
import ru.manul.nextcloudgallery.core.model.ByteRange
import ru.manul.nextcloudgallery.core.model.PreviewMode
import ru.manul.nextcloudgallery.core.model.RemoteMediaStream
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

class NextcloudMediaContentGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: NextcloudMediaContentGateway

    @Before
    fun prepare() {
        server = MockWebServer()
        server.start()
        gateway = NextcloudMediaContentGateway()
    }

    @After
    fun cleanUp() {
        server.close()
    }

    @Test
    fun `preview uses bounded nextcloud preview endpoint`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "image/jpeg")
                .body("preview-bytes")
                .build(),
        )

        val result = gateway.openPreview(
            session = session(),
            remotePath = "/Photos/Фото 1.jpg",
            width = 512,
            height = 512,
            mode = PreviewMode.COVER,
            version = "etag-1",
        )

        assertTrue(result is AppResult.Success)
        (result as AppResult.Success<RemoteMediaStream>).value.use { stream ->
            assertEquals("preview-bytes", stream.input.bufferedReader().readText())
        }
        val request = server.takeRequest()
        assertEquals("/index.php/core/preview.png", request.url.encodedPath)
        assertEquals("/Photos/Фото 1.jpg", request.url.queryParameter("file"))
        assertEquals("512", request.url.queryParameter("x"))
        assertEquals("cover", request.url.queryParameter("mode"))
        assertEquals("true", request.headers["X-NC-Preview"])
    }

    @Test
    fun `original forwards and validates byte range`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Range", "bytes 100-109/1000")
                .addHeader("Accept-Ranges", "bytes")
                .body("video-part")
                .build(),
        )

        val result = gateway.openOriginal(session(), "/Photos/video.mp4", ByteRange(100, 109))

        val stream = (result as AppResult.Success).value
        stream.use {
            assertEquals(100L, it.rangeStart)
            assertEquals(109L, it.rangeEndInclusive)
            assertEquals(1_000L, it.totalLength)
            assertTrue(it.acceptsByteRanges)
            assertEquals("video-part", it.input.bufferedReader().readText())
        }
        val request = server.takeRequest()
        assertEquals("bytes=100-109", request.headers["Range"])
        assertEquals("video.mp4", request.url.pathSegments.last())
    }

    @Test
    fun `ignored nonzero range is reported instead of returning wrong bytes`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "video/mp4")
                .body("complete-video")
                .build(),
        )

        val result = gateway.openOriginal(session(), "/Photos/video.mp4", ByteRange(100))

        assertEquals(AppFailure.RangeNotSupported, (result as AppResult.Failure).error)
    }

    @Test
    fun `ignored range from zero is also reported`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "video/mp4")
                .body("complete-video")
                .build(),
        )

        val result = gateway.openOriginal(session(), "/Photos/video.mp4", ByteRange(0, 99))

        assertEquals(AppFailure.RangeNotSupported, (result as AppResult.Failure).error)
    }

    @Test
    fun `partial response outside requested range is rejected`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Range", "bytes 100-115/1000")
                .body("wrong-video-part")
                .build(),
        )

        val result = gateway.openOriginal(session(), "/Photos/video.mp4", ByteRange(100, 109))

        assertEquals(AppFailure.InvalidServerResponse, (result as AppResult.Failure).error)
    }

    @Test
    fun `internally invalid content range is rejected`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Range", "bytes 100-199/150")
                .body("wrong-video-part")
                .build(),
        )

        val result = gateway.openOriginal(session(), "/Photos/video.mp4", ByteRange(100))

        assertEquals(AppFailure.InvalidServerResponse, (result as AppResult.Failure).error)
    }

    @Test
    fun `invalid preview size is rejected before network call`() = runTest {
        val result = gateway.openPreview(
            session(),
            "/Photos/photo.jpg",
            width = 8,
            height = 512,
            mode = PreviewMode.COVER,
            version = null,
        )

        assertEquals(AppFailure.InvalidPreviewRequest, (result as AppResult.Failure).error)
        assertEquals(0, server.requestCount)
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
