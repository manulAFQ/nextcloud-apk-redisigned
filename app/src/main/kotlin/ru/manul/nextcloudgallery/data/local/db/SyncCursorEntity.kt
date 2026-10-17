package ru.manul.nextcloudgallery.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "sync_cursors",
    primaryKeys = ["account_id", "remote_path"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SyncCursorEntity(
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "remote_path")
    val remotePath: String,
    @ColumnInfo(name = "directory_etag")
    val directoryEtag: String?,
    @ColumnInfo(name = "last_sync_epoch_ms")
    val lastSyncEpochMs: Long,
    @ColumnInfo(name = "sync_generation")
    val syncGeneration: Long,
)
