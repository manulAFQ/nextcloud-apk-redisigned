package ru.manul.nextcloudgallery.data.local

import kotlinx.coroutines.flow.Flow
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult

interface UploadQueueStore {
    fun observe(accountId: String): Flow<List<UploadJob>>

    suspend fun create(job: UploadJob): AppResult<Unit>

    suspend fun get(jobId: String): AppResult<UploadJob?>

    suspend fun markRunning(jobId: String, nowEpochMs: Long): AppResult<Boolean>

    suspend fun resolveDestination(jobId: String, remotePath: String, nowEpochMs: Long): AppResult<Boolean>

    suspend fun updateProgress(jobId: String, transferredBytes: Long, nowEpochMs: Long): AppResult<Boolean>

    suspend fun markSucceeded(
        jobId: String,
        transferredBytes: Long,
        resultEtag: String?,
        nowEpochMs: Long,
    ): AppResult<Boolean>

    suspend fun markRetryable(
        jobId: String,
        transferredBytes: Long,
        failure: AppFailure,
        nextAttemptEpochMs: Long,
        nowEpochMs: Long,
    ): AppResult<Boolean>

    suspend fun markFailed(
        jobId: String,
        transferredBytes: Long,
        failure: AppFailure,
        nowEpochMs: Long,
    ): AppResult<Boolean>

    suspend fun pause(jobId: String, nowEpochMs: Long): AppResult<Boolean>

    suspend fun queueAgain(jobId: String, nowEpochMs: Long): AppResult<Boolean>

    suspend fun recoverInterrupted(accountId: String, nowEpochMs: Long): AppResult<Unit>

    suspend fun runnable(accountId: String, nowEpochMs: Long): AppResult<List<UploadJob>>

    suspend fun delete(jobId: String): AppResult<Unit>
}
