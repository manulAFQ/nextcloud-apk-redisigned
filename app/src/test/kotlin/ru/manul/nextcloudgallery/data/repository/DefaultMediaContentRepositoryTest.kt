package ru.manul.nextcloudgallery.data.repository

import java.io.ByteArrayInputStream
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.ByteRange
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.PreviewMode
import ru.manul.nextcloudgallery.core.model.RemoteMediaStream
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.DiskPreviewCache
import ru.manul.nextcloudgallery.data.remote.MediaContentGateway

class DefaultMediaContentRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `second preview request is served from disk cache`() = runTest {
        val gateway = RecordingGateway()
        val repository = DefaultMediaContentRepository(
            gateway = gateway,
            previewCache = DiskPreviewCache(
                directory = temporaryFolder.newFolder("previews"),
                maxCacheBytes = 1_024,
            ),
            maxPreviewBytes = 256,
        )

        val first = repository.preview(session(), item(), 512, 512, PreviewMode.COVER)
        val second = repository.preview(session(), item(), 512, 512, PreviewMode.COVER)

        assertEquals(1, gateway.previewCalls)
        val firstPreview = (first as AppResult.Success).value
        val secondPreview = (second as AppResult.Success).value
        assertFalse(firstPreview.fromCache)
        assertTrue(secondPreview.fromCache)
        assertEquals("preview", secondPreview.file.readText())
        assertEquals("etag-1", gateway.lastVersion)
    }

    private class RecordingGateway : MediaContentGateway {
        var previewCalls = 0
        var lastVersion: String? = null

        override suspend fun openPreview(
            session: UserSession,
            remotePath: String,
            width: Int,
            height: Int,
            mode: PreviewMode,
            version: String?,
        ): AppResult<RemoteMediaStream> {
            previewCalls += 1
            lastVersion = version
            return AppResult.Success(
                RemoteMediaStream(
                    input = ByteArrayInputStream("preview".toByteArray()),
                    contentType = "image/jpeg",
                    contentLength = 7,
                    etag = version,
                    rangeStart = null,
                    rangeEndInclusive = null,
                    totalLength = null,
                    acceptsByteRanges = false,
                    closeAction = {},
                ),
            )
        }

        override suspend fun openOriginal(
            session: UserSession,
            remotePath: String,
            range: ByteRange?,
        ): AppResult<RemoteMediaStream> = error("Not used")
    }

    private fun session() = UserSession(
        account = Account(
            id = "account",
            serverAddress = ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(),
            username = "user",
            displayName = null,
        ),
        appPassword = AppPassword.create("app-password").getOrThrow(),
    )

    private fun item() = MediaItem(
        remotePath = "/Photos/photo.jpg",
        displayName = "photo.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.IMAGE,
        sizeBytes = 1_024,
        etag = "etag-1",
        capturedAt = null,
        modifiedAt = Instant.parse("2026-10-16T10:00:00Z"),
    )
}
