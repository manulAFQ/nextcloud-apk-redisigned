package ru.manul.nextcloudgallery.data.local.db

import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.core.model.MediaKind
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import java.time.Instant

internal fun Account.toEntity(
    active: Boolean,
    updatedAtEpochMs: Long,
): AccountEntity = AccountEntity(
    accountId = id,
    serverAddress = serverAddress.value,
    username = username,
    displayName = displayName,
    active = active,
    updatedAtEpochMs = updatedAtEpochMs,
)

internal fun AccountEntity.toDomain(): Account = Account(
    id = accountId,
    serverAddress = ServerAddress.parse(serverAddress).getOrThrow(),
    username = username,
    displayName = displayName,
)

internal fun MediaItem.toEntity(
    accountId: String,
    parentPath: String,
    syncGeneration: Long,
    hasPreview: Boolean = true,
): MediaEntity = MediaEntity(
    accountId = accountId,
    remotePath = remotePath,
    parentPath = parentPath,
    displayName = displayName,
    mimeType = mimeType,
    kind = kind.name,
    sizeBytes = sizeBytes,
    etag = etag,
    capturedAtEpochMs = capturedAt?.toEpochMilli(),
    modifiedAtEpochMs = modifiedAt.toEpochMilli(),
    sortEpochMs = (capturedAt ?: modifiedAt).toEpochMilli(),
    hasPreview = hasPreview,
    syncGeneration = syncGeneration,
)

internal fun MediaEntity.toDomain(): MediaItem = MediaItem(
    remotePath = remotePath,
    displayName = displayName,
    mimeType = mimeType,
    kind = MediaKind.valueOf(kind),
    sizeBytes = sizeBytes,
    etag = etag,
    capturedAt = capturedAtEpochMs?.let(Instant::ofEpochMilli),
    modifiedAt = Instant.ofEpochMilli(modifiedAtEpochMs),
)

internal fun UploadJob.toEntity(): UploadJobEntity = UploadJobEntity(
    jobId = id,
    accountId = accountId,
    localUri = localUri,
    remotePath = remotePath,
    sizeBytes = sizeBytes,
    transferredBytes = transferredBytes,
    state = state.name,
    attempt = attempt,
    failureMessage = failureMessage,
    nextAttemptEpochMs = nextAttemptEpochMs,
    destinationResolved = destinationResolved,
    resultEtag = resultEtag,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

internal fun UploadJobEntity.toDomain(): UploadJob = UploadJob(
    id = jobId,
    accountId = accountId,
    localUri = localUri,
    remotePath = remotePath,
    sizeBytes = sizeBytes,
    transferredBytes = transferredBytes,
    state = UploadState.valueOf(state),
    attempt = attempt,
    failureMessage = failureMessage,
    nextAttemptEpochMs = nextAttemptEpochMs,
    destinationResolved = destinationResolved,
    resultEtag = resultEtag,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)
