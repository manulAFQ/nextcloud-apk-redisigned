package ru.manul.nextcloudgallery.data.local

import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.result.AppResult

interface AutoUploadPolicyStore {
    suspend fun get(accountId: String): AppResult<AutoUploadPolicy?>
    suspend fun save(policy: AutoUploadPolicy): AppResult<Unit>
    suspend fun delete(accountId: String): AppResult<Unit>
}

interface AutoUploadRegistry {
    suspend fun clearStaleClaims(accountId: String, olderThanEpochMs: Long): AppResult<Unit>
    suspend fun claim(accountId: String, candidate: LocalMediaCandidate, nowEpochMs: Long): AppResult<Boolean>
    suspend fun linkJob(accountId: String, localKey: String, jobId: String, nowEpochMs: Long): AppResult<Unit>
    suspend fun confirmQueued(jobId: String, nowEpochMs: Long): AppResult<Unit>
    suspend fun releaseClaim(accountId: String, localKey: String): AppResult<Unit>
    suspend fun markUploadedForJob(jobId: String, nowEpochMs: Long): AppResult<Unit>
    suspend fun removeForJob(jobId: String): AppResult<Unit>

    data object None : AutoUploadRegistry {
        override suspend fun clearStaleClaims(accountId: String, olderThanEpochMs: Long) = AppResult.Success(Unit)
        override suspend fun claim(accountId: String, candidate: LocalMediaCandidate, nowEpochMs: Long) =
            AppResult.Success(true)
        override suspend fun linkJob(accountId: String, localKey: String, jobId: String, nowEpochMs: Long) =
            AppResult.Success(Unit)
        override suspend fun confirmQueued(jobId: String, nowEpochMs: Long) = AppResult.Success(Unit)
        override suspend fun releaseClaim(accountId: String, localKey: String) = AppResult.Success(Unit)
        override suspend fun markUploadedForJob(jobId: String, nowEpochMs: Long) = AppResult.Success(Unit)
        override suspend fun removeForJob(jobId: String) = AppResult.Success(Unit)
    }
}
