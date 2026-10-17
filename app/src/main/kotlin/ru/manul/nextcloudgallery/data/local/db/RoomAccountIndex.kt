package ru.manul.nextcloudgallery.data.local.db

import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.domain.repository.AccountIndex

class RoomAccountIndex(
    private val database: GalleryDatabase,
) : AccountIndex {
    override suspend fun activate(account: Account, updatedAtEpochMs: Long) {
        database.accountDao().activate(
            account.toEntity(
                active = true,
                updatedAtEpochMs = updatedAtEpochMs,
            ),
        )
    }
}
