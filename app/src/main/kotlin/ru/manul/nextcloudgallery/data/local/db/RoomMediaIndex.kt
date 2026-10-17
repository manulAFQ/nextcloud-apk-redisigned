package ru.manul.nextcloudgallery.data.local.db

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.manul.nextcloudgallery.core.model.MediaItem
import ru.manul.nextcloudgallery.domain.repository.MediaIndex
import ru.manul.nextcloudgallery.domain.repository.SyncCursor

class RoomMediaIndex(
    private val database: GalleryDatabase,
) : MediaIndex {
    override fun observeFolder(
        accountId: String,
        remotePath: String,
    ): Flow<List<MediaItem>> = database.mediaDao()
        .observeFolder(accountId, remotePath)
        .map { entities -> entities.map(MediaEntity::toDomain) }

    override suspend fun page(
        accountId: String,
        remotePath: String,
        limit: Int,
        offset: Int,
    ): List<MediaItem> {
        require(limit in 1..500) { "Page size must be between 1 and 500" }
        require(offset >= 0) { "Offset cannot be negative" }
        return database.mediaDao().page(accountId, remotePath, limit, offset).map(MediaEntity::toDomain)
    }

    override suspend fun cursor(accountId: String, remotePath: String): SyncCursor? =
        database.syncCursorDao().get(accountId, remotePath)?.let { entity ->
            SyncCursor(
                accountId = entity.accountId,
                remotePath = entity.remotePath,
                directoryEtag = entity.directoryEtag,
                lastSyncEpochMs = entity.lastSyncEpochMs,
                syncGeneration = entity.syncGeneration,
            )
        }

    override suspend fun markFolderChecked(
        accountId: String,
        remotePath: String,
        directoryEtag: String,
        checkedAtEpochMs: Long,
    ) {
        val current = checkNotNull(database.syncCursorDao().get(accountId, remotePath)) {
            "Cannot mark a folder without an existing sync cursor"
        }
        database.syncCursorDao().upsert(
            current.copy(
                directoryEtag = directoryEtag,
                lastSyncEpochMs = checkedAtEpochMs,
            ),
        )
    }

    override suspend fun replaceFolder(
        accountId: String,
        remotePath: String,
        directoryEtag: String?,
        syncGeneration: Long,
        syncedAtEpochMs: Long,
        items: List<MediaItem>,
    ) {
        val entities = items.map { item ->
            item.toEntity(
                accountId = accountId,
                parentPath = remotePath,
                syncGeneration = syncGeneration,
            )
        }
        database.withTransaction {
            database.mediaDao().upsertAll(entities)
            database.mediaDao().deleteMissingFromGeneration(accountId, remotePath, syncGeneration)
            database.syncCursorDao().upsert(
                SyncCursorEntity(
                    accountId = accountId,
                    remotePath = remotePath,
                    directoryEtag = directoryEtag,
                    lastSyncEpochMs = syncedAtEpochMs,
                    syncGeneration = syncGeneration,
                ),
            )
        }
    }
}
