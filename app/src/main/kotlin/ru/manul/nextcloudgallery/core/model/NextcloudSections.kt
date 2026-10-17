package ru.manul.nextcloudgallery.core.model

import java.time.Instant

data class ActivityEvent(
    val id: Long,
    val subject: String,
    val message: String?,
    val app: String,
    val type: String,
    val actor: String?,
    val objectName: String?,
    val link: String?,
    val occurredAt: Instant,
)

data class TrashItem(
    val id: String,
    val displayName: String,
    val originalLocation: String?,
    val sizeBytes: Long,
    val isFolder: Boolean,
    val deletedAt: Instant?,
)

data class RemoteAlbum(
    val name: String,
    val remotePath: String,
    val modifiedAt: Instant = Instant.EPOCH,
    val itemCount: Int = 0,
    val coverFileId: Long? = null,
)
