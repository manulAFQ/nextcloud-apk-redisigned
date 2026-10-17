package ru.manul.nextcloudgallery.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import java.time.Instant

class EntityMappersTest {
    @Test
    fun `media entity uses captured time for stable sorting`() {
        val capturedAt = Instant.parse("2025-05-01T10:15:30Z")
        val modifiedAt = Instant.parse("2026-01-01T00:00:00Z")
        val item = media(capturedAt = capturedAt, modifiedAt = modifiedAt)

        val entity = item.toEntity("account-1", "/Photos", syncGeneration = 7)

        assertEquals(capturedAt.toEpochMilli(), entity.sortEpochMs)
        assertEquals(7, entity.syncGeneration)
        assertEquals(item, entity.toDomain())
    }

    @Test
    fun `media entity falls back to modified time`() {
        val modifiedAt = Instant.parse("2026-01-01T00:00:00Z")

        val entity = media(capturedAt = null, modifiedAt = modifiedAt)
            .toEntity("account-1", "/Photos", syncGeneration = 1)

        assertNull(entity.capturedAtEpochMs)
        assertEquals(modifiedAt.toEpochMilli(), entity.sortEpochMs)
    }

    @Test
    fun `upload job round trip keeps persistent state`() {
        val job = UploadJob(
            id = "job",
            accountId = "account",
            localUri = "content://media/photo",
            remotePath = "/Photos/photo.jpg",
            sizeBytes = 100,
            transferredBytes = 25,
            state = UploadState.QUEUED,
            attempt = 2,
            failureMessage = "ServerUnavailable",
            nextAttemptEpochMs = 5_000,
            destinationResolved = true,
            resultEtag = "result-etag",
            createdAtEpochMs = 1_000,
            updatedAtEpochMs = 2_000,
        )

        assertEquals(job, job.toEntity().toDomain())
    }

    private fun media(capturedAt: Instant?, modifiedAt: Instant) = MediaItem(
        remotePath = "/Photos/example.jpg",
        displayName = "example.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.IMAGE,
        sizeBytes = 42,
        etag = "etag-1",
        capturedAt = capturedAt,
        modifiedAt = modifiedAt,
    )
}
