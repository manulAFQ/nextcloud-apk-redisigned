package ru.manul.nextcloudgallery.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "upload_jobs",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["account_id", "state", "next_attempt_epoch_ms"]),
        Index(value = ["account_id", "remote_path"]),
    ],
)
data class UploadJobEntity(
    @PrimaryKey
    @ColumnInfo(name = "job_id")
    val jobId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "local_uri")
    val localUri: String,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "transferred_bytes")
    val transferredBytes: Long,
    val state: String,
    val attempt: Int,
    @ColumnInfo(name = "failure_message")
    val failureMessage: String?,
    @ColumnInfo(name = "next_attempt_epoch_ms")
    val nextAttemptEpochMs: Long?,
    @ColumnInfo(name = "destination_resolved", defaultValue = "0")
    val destinationResolved: Boolean,
    @ColumnInfo(name = "result_etag")
    val resultEtag: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
