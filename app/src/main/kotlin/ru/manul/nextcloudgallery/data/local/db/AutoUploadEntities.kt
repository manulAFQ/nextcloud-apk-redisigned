package ru.manul.nextcloudgallery.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "auto_upload_policies",
    primaryKeys = ["account_id"],
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["account_id"],
        childColumns = ["account_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class AutoUploadPolicyEntity(
    @ColumnInfo(name = "account_id") val accountId: String,
    val enabled: Boolean,
    @ColumnInfo(name = "include_camera") val includeCamera: Boolean,
    @ColumnInfo(name = "include_screenshots") val includeScreenshots: Boolean,
    @ColumnInfo(name = "include_videos") val includeVideos: Boolean,
    @ColumnInfo(name = "wifi_only") val wifiOnly: Boolean,
    @ColumnInfo(name = "required_ssid") val requiredSsid: String?,
    @ColumnInfo(name = "require_charging") val requireCharging: Boolean,
    @ColumnInfo(name = "minimum_battery_percent") val minimumBatteryPercent: Int,
    @ColumnInfo(name = "remote_base_path") val remoteBasePath: String,
    @ColumnInfo(name = "device_folder") val deviceFolder: String,
    @ColumnInfo(name = "selected_folders") val selectedFolders: String,
)

@Entity(
    tableName = "auto_upload_items",
    primaryKeys = ["account_id", "local_key"],
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["account_id"],
        childColumns = ["account_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["upload_job_id"], unique = true)],
)
data class AutoUploadItemEntity(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "local_key") val localKey: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val kind: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "captured_at_epoch_ms") val capturedAtEpochMs: Long,
    @ColumnInfo(name = "modified_at_epoch_seconds") val modifiedAtEpochSeconds: Long,
    val state: String,
    @ColumnInfo(name = "upload_job_id") val uploadJobId: String?,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)
