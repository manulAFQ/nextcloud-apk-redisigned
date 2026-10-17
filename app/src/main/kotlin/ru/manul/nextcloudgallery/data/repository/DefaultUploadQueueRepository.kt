package ru.manul.nextcloudgallery.data.repository

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import ru.manul.nextcloudgallery.core.model.RemotePath
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.UploadQueueStore
import ru.manul.nextcloudgallery.data.local.UploadSource
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.data.local.AutoUploadRegistry
import ru.manul.nextcloudgallery.data.remote.UploadGateway
import ru.manul.nextcloudgallery.data.upload.UploadScheduler
import ru.manul.nextcloudgallery.domain.repository.AccountIndex
import ru.manul.nextcloudgallery.domain.repository.UploadQueueRepository

class DefaultUploadQueueRepository(
    private val store: UploadQueueStore,
    private val source: UploadSource,
    private val accountIndex: AccountIndex,
    private val scheduler: UploadScheduler,
    private val sessionStore: SessionStore,
    private val uploadGateway: UploadGateway,
    private val autoUploadRegistry: AutoUploadRegistry = AutoUploadRegistry.None,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : UploadQueueRepository {
    override fun observe(accountId: String): Flow<List<UploadJob>> = store.observe(accountId)

    override suspend fun enqueue(
        session: UserSession,
        localUri: String,
        remotePath: String,
        scheduleImmediately: Boolean,
        requestedJobId: String?,
    ): AppResult<UploadJob> {
        val path = RemotePath.parse(remotePath).getOrElse {
            return AppResult.Failure(AppFailure.InvalidRemotePath)
        }
        if (path.segments.isEmpty()) return AppResult.Failure(AppFailure.InvalidRemotePath)
        val sourceInfo = when (val inspected = source.inspect(localUri)) {
            is AppResult.Failure -> return inspected
            is AppResult.Success -> inspected.value
        }
        val now = clock()
        val job = UploadJob(
            id = requestedJobId ?: newId(),
            accountId = session.account.id,
            localUri = localUri,
            remotePath = path.value,
            sizeBytes = sourceInfo.sizeBytes,
            transferredBytes = 0,
            state = UploadState.QUEUED,
            attempt = 0,
            failureMessage = null,
            nextAttemptEpochMs = null,
            destinationResolved = false,
            resultEtag = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        return try {
            accountIndex.activate(session.account, now)
            when (val created = store.create(job)) {
                is AppResult.Failure -> created
                is AppResult.Success -> {
                    if (scheduleImmediately) scheduler.enqueue(job.id, job.accountId)
                    AppResult.Success(job)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppFailure.LocalStorageUnavailable)
        }
    }

    override suspend fun start(jobId: String): AppResult<Unit> = mutate(jobId) { job ->
        if (job.state != UploadState.QUEUED) return@mutate AppResult.Failure(AppFailure.Unexpected("job_not_queued"))
        scheduler.enqueue(job.id, job.accountId)
        AppResult.Success(Unit)
    }

    override suspend fun pause(jobId: String): AppResult<Unit> = mutate(jobId) { job ->
        when (val paused = store.pause(job.id, clock())) {
            is AppResult.Failure -> paused
            is AppResult.Success -> {
                if (paused.value || job.state == UploadState.PAUSED) scheduler.cancel(job.id)
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun resume(jobId: String): AppResult<Unit> = queueAgain(jobId)

    override suspend fun retry(jobId: String): AppResult<Unit> = queueAgain(jobId)

    override suspend fun remove(jobId: String): AppResult<Unit> = mutate(jobId) { job ->
        scheduler.cancel(job.id)
        val session = when (val loaded = sessionStore.load()) {
            is AppResult.Failure -> return@mutate loaded
            is AppResult.Success -> loaded.value
        }
        if (session == null || session.account.id != job.accountId) {
            return@mutate AppResult.Failure(AppFailure.Unauthorized)
        }
        when (val aborted = uploadGateway.abort(session, job.id)) {
            is AppResult.Failure -> aborted
            is AppResult.Success -> when (val deleted = store.delete(job.id)) {
                is AppResult.Failure -> deleted
                is AppResult.Success -> if (job.state == UploadState.SUCCEEDED) {
                    // Transfer history may be removed, but the durable deduplication
                    // record must remain or MediaStore would enqueue the file again.
                    AppResult.Success(Unit)
                } else {
                    autoUploadRegistry.removeForJob(job.id)
                }
            }
        }
    }

    override suspend fun restore(accountId: String): AppResult<Unit> {
        val now = clock()
        when (val recovered = store.recoverInterrupted(accountId, now)) {
            is AppResult.Failure -> return recovered
            is AppResult.Success -> Unit
        }
        return when (val jobs = store.runnable(accountId, now)) {
            is AppResult.Failure -> jobs
            is AppResult.Success -> {
                jobs.value.forEach { job ->
                    val delay = (job.nextAttemptEpochMs ?: now) - now
                    scheduler.enqueue(job.id, accountId, delay.coerceAtLeast(0))
                }
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun queueAgain(jobId: String): AppResult<Unit> = mutate(jobId) { job ->
        when (val queued = store.queueAgain(job.id, clock())) {
            is AppResult.Failure -> queued
            is AppResult.Success -> {
                if (queued.value || job.state == UploadState.QUEUED) {
                    scheduler.enqueue(job.id, job.accountId, replace = true)
                }
                AppResult.Success(Unit)
            }
        }
    }

    private suspend fun mutate(
        jobId: String,
        block: suspend (UploadJob) -> AppResult<Unit>,
    ): AppResult<Unit> = when (val found = store.get(jobId)) {
        is AppResult.Failure -> found
        is AppResult.Success -> found.value?.let { block(it) }
            ?: AppResult.Failure(AppFailure.UploadJobNotFound)
    }
}
