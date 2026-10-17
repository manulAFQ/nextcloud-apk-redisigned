package ru.manul.nextcloudgallery.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "media",
    primaryKeys = ["account_id", "remote_path"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["account_id", "parent_path", "sort_epoch_ms"]),
        Index(value = ["account_id", "etag"]),
    ],
)
data class MediaEntity(
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "parent_path")
    val parentPath: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    val kind: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    val etag: String?,
    @ColumnInfo(name = "captured_at_epoch_ms")
    val capturedAtEpochMs: Long?,
    @ColumnInfo(name = "modified_at_epoch_ms")
    val modifiedAtEpochMs: Long,
    @ColumnInfo(name = "sort_epoch_ms")
    val sortEpochMs: Long,
    @ColumnInfo(name = "has_preview")
    val hasPreview: Boolean,
    @ColumnInfo(name = "sync_generation")
    val syncGeneration: Long,
)
