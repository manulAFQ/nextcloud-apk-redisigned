package ru.manul.nextcloudgallery.core.model

data class ServerInfo(
    val installed: Boolean,
    val maintenance: Boolean,
    val needsDatabaseUpgrade: Boolean,
    val version: String,
    val productName: String,
)

data class ServerCapabilities(
    val chunkedUpload: Boolean,
    val previews: Boolean,
    val fileSharing: Boolean,
)

data class UserProfile(
    val userId: String,
    val displayName: String,
    val email: String?,
    val quota: UserQuota?,
)

data class UserQuota(
    val freeBytes: Long,
    val usedBytes: Long,
    val totalBytes: Long,
    val relativePercent: Double,
)
