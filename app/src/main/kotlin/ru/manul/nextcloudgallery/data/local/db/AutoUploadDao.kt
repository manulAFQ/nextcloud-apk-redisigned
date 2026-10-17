package ru.manul.nextcloudgallery.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.AutoUploadItemState
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate

@Dao
interface AutoUploadDao {
    @Query("SELECT * FROM auto_upload_policies WHERE account_id = :accountId LIMIT 1")
    suspend fun policy(accountId: String): AutoUploadPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePolicy(policy: AutoUploadPolicyEntity)

    @Query("DELETE FROM auto_upload_policies WHERE account_id = :accountId")
    suspend fun deletePolicy(accountId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClaim(item: AutoUploadItemEntity): Long

    @Query("DELETE FROM auto_upload_items WHERE account_id = :accountId AND state = 'CLAIMED' AND updated_at_epoch_ms < :before")
    suspend fun clearStaleClaims(accountId: String, before: Long)

    @Query("DELETE FROM auto_upload_items WHERE account_id = :accountId AND local_key = :localKey AND state = 'CLAIMED'")
    suspend fun releaseClaim(accountId: String, localKey: String)

    @Query("UPDATE auto_upload_items SET upload_job_id = :jobId, updated_at_epoch_ms = :now WHERE account_id = :accountId AND local_key = :localKey AND state = 'CLAIMED'")
    suspend fun linkJob(accountId: String, localKey: String, jobId: String, now: Long): Int

    @Query("UPDATE auto_upload_items SET state = 'QUEUED', updated_at_epoch_ms = :now WHERE upload_job_id = :jobId AND state = 'CLAIMED'")
    suspend fun confirmQueued(jobId: String, now: Long): Int

    @Query("UPDATE auto_upload_items SET state = 'UPLOADED', updated_at_epoch_ms = :now WHERE upload_job_id = :jobId")
    suspend fun markUploaded(jobId: String, now: Long)

    @Query("DELETE FROM auto_upload_items WHERE upload_job_id = :jobId")
    suspend fun removeForJob(jobId: String)
}

internal fun AutoUploadPolicyEntity.toDomain() = AutoUploadPolicy(
    accountId, enabled, includeCamera, includeScreenshots, includeVideos, wifiOnly,
    requiredSsid, requireCharging, minimumBatteryPercent, remoteBasePath, deviceFolder,
    selectedFolders.split(FOLDER_SEPARATOR).filter(String::isNotBlank).toSet(),
)

internal fun AutoUploadPolicy.toEntity() = AutoUploadPolicyEntity(
    accountId, enabled, includeCamera, includeScreenshots, includeVideos, wifiOnly,
    requiredSsid, requireCharging, minimumBatteryPercent, remoteBasePath, deviceFolder,
    selectedFolders.joinToString(FOLDER_SEPARATOR),
)

private const val FOLDER_SEPARATOR = "\u001F"

internal fun LocalMediaCandidate.toClaim(accountId: String, now: Long) = AutoUploadItemEntity(
    accountId = accountId,
    localKey = localKey,
    contentUri = contentUri,
    displayName = displayName,
    relativePath = relativePath,
    kind = kind.name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    capturedAtEpochMs = capturedAtEpochMs,
    modifiedAtEpochSeconds = modifiedAtEpochSeconds,
    state = AutoUploadItemState.CLAIMED.name,
    uploadJobId = null,
    updatedAtEpochMs = now,
)
