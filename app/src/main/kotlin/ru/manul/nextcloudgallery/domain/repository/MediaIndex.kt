package ru.manul.nextcloudgallery.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.manul.nextcloudgallery.core.model.MediaItem

data class SyncCursor(
    val accountId: String,
    val remotePath: String,
    val directoryEtag: String?,
    val lastSyncEpochMs: Long,
    val syncGeneration: Long,
)

/** Local, non-secret metadata cache. The Nextcloud server remains the source of truth. */
interface MediaIndex {
    fun observeFolder(accountId: String, remotePath: String): Flow<List<MediaItem>>

    suspend fun page(
        accountId: String,
        remotePath: String,
        limit: Int,
        offset: Int,
    ): List<MediaItem>

    suspend fun cursor(accountId: String, remotePath: String): SyncCursor?

    suspend fun markFolderChecked(
        accountId: String,
        remotePath: String,
        directoryEtag: String,
        checkedAtEpochMs: Long,
    )

    suspend fun replaceFolder(
        accountId: String,
        remotePath: String,
        directoryEtag: String?,
        syncGeneration: Long,
        syncedAtEpochMs: Long,
        items: List<MediaItem>,
    )
}
