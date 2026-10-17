package ru.manul.nextcloudgallery.core.model

import java.time.Instant

enum class MediaKind { IMAGE, VIDEO }

data class MediaItem(
    val remotePath: String,
    val displayName: String,
    val mimeType: String,
    val kind: MediaKind,
    val sizeBytes: Long,
    val etag: String?,
    val capturedAt: Instant?,
    val modifiedAt: Instant,
    val fileId: Long? = null,
    val favorite: Boolean = false,
)
