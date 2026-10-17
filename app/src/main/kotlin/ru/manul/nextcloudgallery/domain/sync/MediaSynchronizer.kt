package ru.manul.nextcloudgallery.domain.sync

import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

sealed interface SyncOutcome {
    data class Updated(
        val itemCount: Int,
        val generation: Long,
        val directoryEtag: String?,
    ) : SyncOutcome

    data class Unchanged(
        val directoryEtag: String,
        val checkedAtEpochMs: Long,
    ) : SyncOutcome

    data object AlreadyRunning : SyncOutcome
}

interface MediaSynchronizer {
    suspend fun sync(
        session: UserSession,
        remotePath: String,
        force: Boolean = false,
    ): AppResult<SyncOutcome>
}
