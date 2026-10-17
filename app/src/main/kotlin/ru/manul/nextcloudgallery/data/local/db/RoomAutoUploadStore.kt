package ru.manul.nextcloudgallery.data.local.db

import kotlinx.coroutines.CancellationException
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.AutoUploadPolicyStore
import ru.manul.nextcloudgallery.data.local.AutoUploadRegistry

class RoomAutoUploadStore(private val dao: AutoUploadDao) : AutoUploadPolicyStore, AutoUploadRegistry {
    override suspend fun get(accountId: String) = protect { dao.policy(accountId)?.toDomain() }
    override suspend fun save(policy: AutoUploadPolicy) = protect { dao.savePolicy(policy.toEntity()) }
    override suspend fun delete(accountId: String) = protect { dao.deletePolicy(accountId) }
    override suspend fun clearStaleClaims(accountId: String, olderThanEpochMs: Long) =
        protect { dao.clearStaleClaims(accountId, olderThanEpochMs) }
    override suspend fun claim(accountId: String, candidate: LocalMediaCandidate, nowEpochMs: Long) =
        protect { dao.insertClaim(candidate.toClaim(accountId, nowEpochMs)) != -1L }
    override suspend fun linkJob(accountId: String, localKey: String, jobId: String, nowEpochMs: Long) = protect {
        check(dao.linkJob(accountId, localKey, jobId, nowEpochMs) == 1)
    }
    override suspend fun confirmQueued(jobId: String, nowEpochMs: Long) = protect {
        check(dao.confirmQueued(jobId, nowEpochMs) == 1)
    }
    override suspend fun releaseClaim(accountId: String, localKey: String) =
        protect { dao.releaseClaim(accountId, localKey) }
    override suspend fun markUploadedForJob(jobId: String, nowEpochMs: Long) =
        protect { dao.markUploaded(jobId, nowEpochMs) }
    override suspend fun removeForJob(jobId: String) = protect { dao.removeForJob(jobId) }

    private suspend fun <T> protect(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AppResult.Failure(AppFailure.LocalStorageUnavailable)
    }
}
