package ru.manul.nextcloudgallery.data.sync

import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.domain.sync.MediaSynchronizer
import ru.manul.nextcloudgallery.domain.sync.SyncOutcome

sealed interface SyncRunDecision {
    data class Success(val outcome: SyncOutcome?) : SyncRunDecision
    data class Retry(val failure: AppFailure) : SyncRunDecision
    data class Failure(val failure: AppFailure) : SyncRunDecision
}

class SyncWorkerRunner(
    private val sessionStore: SessionStore,
    private val synchronizer: MediaSynchronizer,
) {
    suspend fun run(
        expectedAccountId: String,
        remotePath: String,
        force: Boolean,
    ): SyncRunDecision {
        val session = when (val stored = sessionStore.load()) {
            is AppResult.Failure -> return stored.error.toDecision()
            is AppResult.Success -> stored.value
        } ?: return SyncRunDecision.Success(outcome = null)

        if (session.account.id != expectedAccountId) {
            return SyncRunDecision.Success(outcome = null)
        }

        return when (val result = synchronizer.sync(session, remotePath, force)) {
            is AppResult.Success -> SyncRunDecision.Success(result.value)
            is AppResult.Failure -> result.error.toDecision()
        }
    }

    private fun AppFailure.toDecision(): SyncRunDecision = when (this) {
        AppFailure.NoNetwork,
        AppFailure.ServerUnavailable,
        AppFailure.SecureStorageUnavailable,
        AppFailure.LocalStorageUnavailable -> SyncRunDecision.Retry(this)
        is AppFailure.Http -> if (statusCode in RETRYABLE_HTTP_CODES || statusCode >= 500) {
            SyncRunDecision.Retry(this)
        } else {
            SyncRunDecision.Failure(this)
        }
        else -> SyncRunDecision.Failure(this)
    }

    private companion object {
        val RETRYABLE_HTTP_CODES = setOf(408, 425, 429)
    }
}
