package ru.manul.nextcloudgallery.core.model

enum class UploadState { QUEUED, RUNNING, PAUSED, SUCCEEDED, FAILED }

data class UploadJob(
    val id: String,
    val accountId: String,
    val localUri: String,
    val remotePath: String,
    val sizeBytes: Long,
    val transferredBytes: Long,
    val state: UploadState,
    val attempt: Int,
    val failureMessage: String?,
    val nextAttemptEpochMs: Long?,
    val destinationResolved: Boolean,
    val resultEtag: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class UploadReceipt(
    val remotePath: String,
    val sizeBytes: Long,
    val etag: String?,
)
