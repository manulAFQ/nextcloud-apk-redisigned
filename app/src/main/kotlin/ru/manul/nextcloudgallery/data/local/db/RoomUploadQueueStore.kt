package ru.manul.nextcloudgallery.data.local.db

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.UploadQueueStore

class RoomUploadQueueStore(
    private val dao: UploadJobDao,
) : UploadQueueStore {
    override fun observe(accountId: String): Flow<List<UploadJob>> =
        dao.observeForAccount(accountId).map { jobs -> jobs.map(UploadJobEntity::toDomain) }

    override suspend fun create(job: UploadJob): AppResult<Unit> = protect {
        dao.upsert(job.toEntity())
    }

    override suspend fun get(jobId: String): AppResult<UploadJob?> = protect {
        dao.get(jobId)?.toDomain()
    }

    override suspend fun markRunning(jobId: String, nowEpochMs: Long): AppResult<Boolean> = protect {
        dao.markRunning(jobId, nowEpochMs) == 1
    }

    override suspend fun resolveDestination(
        jobId: String,
        remotePath: String,
        nowEpochMs: Long,
    ): AppResult<Boolean> = protect {
        dao.resolveDestination(jobId, remotePath, nowEpochMs) == 1
    }

    override suspend fun updateProgress(
        jobId: String,
        transferredBytes: Long,
        nowEpochMs: Long,
    ): AppResult<Boolean> = protect {
        dao.updateProgress(jobId, transferredBytes, nowEpochMs) == 1
    }

    override suspend fun markSucceeded(
        jobId: String,
        transferredBytes: Long,
        resultEtag: String?,
        nowEpochMs: Long,
    ): AppResult<Boolean> = protect {
        dao.markSucceeded(jobId, transferredBytes, resultEtag, nowEpochMs) == 1
    }

    override suspend fun markRetryable(
        jobId: String,
        transferredBytes: Long,
        failure: AppFailure,
        nextAttemptEpochMs: Long,
        nowEpochMs: Long,
    ): AppResult<Boolean> = protect {
        dao.markRetryable(
            jobId,
            transferredBytes,
            failure.safeCode(),
            nextAttemptEpochMs,
            nowEpochMs,
        ) == 1
    }

    override suspend fun markFailed(
        jobId: String,
        transferredBytes: Long,
        failure: AppFailure,
        nowEpochMs: Long,
    ): AppResult<Boolean> = protect {
        dao.markFailed(jobId, transferredBytes, failure.safeCode(), nowEpochMs) == 1
    }

    override suspend fun pause(jobId: String, nowEpochMs: Long): AppResult<Boolean> = protect {
        dao.pause(jobId, nowEpochMs) == 1
    }

    override suspend fun queueAgain(jobId: String, nowEpochMs: Long): AppResult<Boolean> = protect {
        dao.queueAgain(jobId, nowEpochMs) == 1
    }

    override suspend fun recoverInterrupted(accountId: String, nowEpochMs: Long): AppResult<Unit> = protect {
        dao.recoverInterrupted(
            accountId = accountId,
            staleBeforeEpochMs = nowEpochMs - STALE_RUNNING_MILLIS,
            updatedAtEpochMs = nowEpochMs,
        )
        Unit
    }

    override suspend fun runnable(accountId: String, nowEpochMs: Long): AppResult<List<UploadJob>> = protect {
        dao.findRestorable(
            accountId = accountId,
            states = listOf(UploadState.QUEUED.name, UploadState.RUNNING.name),
        ).map(UploadJobEntity::toDomain)
    }

    override suspend fun delete(jobId: String): AppResult<Unit> = protect {
        dao.delete(jobId)
    }

    private suspend fun <T> protect(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AppResult.Failure(AppFailure.LocalStorageUnavailable)
    }

    private fun AppFailure.safeCode(): String = when (this) {
        is AppFailure.Http -> "http_$statusCode"
        is AppFailure.InvalidServerAddress -> "invalid_server_address"
        is AppFailure.Unexpected -> "unexpected"
        else -> this::class.simpleName.orEmpty()
    }

    private companion object {
        const val STALE_RUNNING_MILLIS = 10L * 60 * 1_000
    }
}
