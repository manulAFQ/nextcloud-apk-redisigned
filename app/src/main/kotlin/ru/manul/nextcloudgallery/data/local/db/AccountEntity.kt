package ru.manul.nextcloudgallery.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "server_address")
    val serverAddress: String,
    val username: String,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    val active: Boolean,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
