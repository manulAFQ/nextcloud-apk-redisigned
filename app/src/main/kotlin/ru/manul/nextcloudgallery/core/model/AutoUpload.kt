package ru.manul.nextcloudgallery.core.model

data class AutoUploadPolicy(
    val accountId: String,
    val enabled: Boolean = false,
    val includeCamera: Boolean = false,
    val includeScreenshots: Boolean = false,
    val includeVideos: Boolean = false,
    val wifiOnly: Boolean = true,
    val requiredSsid: String? = null,
    val requireCharging: Boolean = false,
    val minimumBatteryPercent: Int = 20,
    val remoteBasePath: String = "/Photos/Auto Upload",
    val deviceFolder: String,
    val selectedFolders: Set<String> = emptySet(),
) {
    init {
        require(minimumBatteryPercent in 0..100)
        require(requiredSsid == null || requiredSsid.isNotBlank())
        require(deviceFolder.isNotBlank())
        require(RemotePath.parse(remoteBasePath).getOrNull()?.segments?.isNotEmpty() == true)
    }
}

enum class LocalMediaKind { IMAGE, VIDEO }

data class LocalMediaCandidate(
    val localKey: String,
    val contentUri: String,
    val displayName: String,
    val relativePath: String,
    val kind: LocalMediaKind,
    val mimeType: String?,
    val sizeBytes: Long,
    val capturedAtEpochMs: Long,
    val modifiedAtEpochSeconds: Long,
)

enum class AutoUploadItemState { CLAIMED, QUEUED, UPLOADED }
