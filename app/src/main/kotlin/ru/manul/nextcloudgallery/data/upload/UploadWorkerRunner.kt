package ru.manul.nextcloudgallery.data.upload

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.data.local.UploadQueueStore
import ru.manul.nextcloudgallery.data.local.UploadSource
import ru.manul.nextcloudgallery.data.local.AutoUploadRegistry
import ru.manul.nextcloudgallery.data.remote.UploadGateway

sealed interface UploadRunDecision {
    data class Success(val transferredBytes: Long) : UploadRunDecision
    data class Retry(val failure: AppFailure) : UploadRunDecision
    data class Failure(val failure: AppFailure) : UploadRunDecision
}

class UploadWorkerRunner(
    private val sessionStore: SessionStore,
    private val queueStore: UploadQueueStore,
    private val uploadSource: UploadSource,
    private val gateway: UploadGateway,
    private val autoUploadRegistry: AutoUploadRegistry = AutoUploadRegistry.None,
    private val clock: () -> Long = System::currentTimeMillis,
    private val progressIntervalMillis: Long = DEFAULT_PROGRESS_INTERVAL_MILLIS,
) {
    suspend fun run(
        jobId: String,
        expectedAccountId: String,
        reportProgress: suspend (transferredBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): UploadRunDecision {
        var job = when (val found = queueStore.get(jobId)) {
            is AppResult.Failure -> return found.error.toDecision()
            is AppResult.Success -> found.value
        } ?: return UploadRunDecision.Failure(AppFailure.UploadJobNotFound)

        if (job.accountId != expectedAccountId) {
            return UploadRunDecision.Failure(AppFailure.Forbidden)
        }
        if (job.state == UploadState.SUCCEEDED) return UploadRunDecision.Success(job.sizeBytes)
        if (job.state == UploadState.PAUSED) return UploadRunDecision.Success(job.transferredBytes)
        if (job.state == UploadState.FAILED) return UploadRunDecision.Failure(AppFailure.Unexpected("failed_job"))
        if ((job.nextAttemptEpochMs ?: 0) > clock()) {
            return UploadRunDecision.Retry(AppFailure.ServerUnavailable)
        }

        when (val marked = queueStore.markRunning(job.id, clock())) {
            is AppResult.Failure -> return marked.error.toDecision()
            is AppResult.Success -> if (!marked.value) {
                return UploadRunDecision.Success(job.transferredBytes)
            }
        }

        val session = when (val stored = sessionStore.load()) {
            is AppResult.Failure -> return finishFailure(job, stored.error, transferredBytes = 0)
            is AppResult.Success -> stored.value
        }
        if (session == null || session.account.id != job.accountId) {
            return finishFailure(job, AppFailure.Unauthorized, transferredBytes = 0)
        }

        if (!job.destinationResolved) {
            val resolvedPath = when (val resolved = gateway.resolveUniqueRemotePath(session, job.remotePath)) {
                is AppResult.Failure -> return finishFailure(job, resolved.error, transferredBytes = 0)
                is AppResult.Success -> resolved.value
            }
            when (val saved = queueStore.resolveDestination(job.id, resolvedPath, clock())) {
                is AppResult.Failure -> return finishFailure(job, saved.error, transferredBytes = 0)
                is AppResult.Success -> if (!saved.value) {
                    return UploadRunDecision.Failure(AppFailure.UploadJobNotFound)
                }
            }
            job = job.copy(remotePath = resolvedPath, destinationResolved = true)
        }

        val opened = when (val source = uploadSource.open(job.localUri)) {
            is AppResult.Failure -> {
                if (!source.error.isRetryable()) gateway.abort(session, job.id)
                return finishFailure(job, source.error, transferredBytes = 0)
            }
            is AppResult.Success -> source.value
        }
        val transferred = AtomicLong(0)
        return try {
            val result = opened.use {
                runUploadWithProgress(job, opened.mimeType, opened.input, transferred, reportProgress, session)
            }
            val finalBytes = transferred.get().coerceIn(0, job.sizeBytes)
            when (result) {
                is AppResult.Success -> {
                    when (val saved = queueStore.markSucceeded(
                        job.id,
                        job.sizeBytes,
                        result.value.etag,
                        clock(),
                    )) {
                        is AppResult.Failure -> saved.error.toDecision()
                        is AppResult.Success -> {
                            autoUploadRegistry.markUploadedForJob(job.id, clock())
                            UploadRunDecision.Success(job.sizeBytes)
                        }
                    }
                }
                is AppResult.Failure -> {
                    if (!result.error.isRetryable()) gateway.abort(session, job.id)
                    finishFailure(job, result.error, finalBytes)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            finishFailure(job, AppFailure.ServerUnavailable, transferred.get())
        }
    }

    private suspend fun runUploadWithProgress(
        job: UploadJob,
        mimeType: String?,
        input: java.io.InputStream,
        transferred: AtomicLong,
        reportProgress: suspend (Long, Long) -> Unit,
        session: ru.manul.nextcloudgallery.core.model.UserSession,
    ) = coroutineScope {
        val progressWriter = launch {
            var persisted = -1L
            while (isActive) {
                val current = transferred.get().coerceIn(0, job.sizeBytes)
                if (current != persisted) {
                    queueStore.updateProgress(job.id, current, clock())
                    reportProgress(current, job.sizeBytes)
                    persisted = current
                }
                delay(progressIntervalMillis)
            }
        }
        try {
            gateway.upload(
                session = session,
                uploadId = job.id,
                remotePath = job.remotePath,
                sizeBytes = job.sizeBytes,
                recoverCompletedUpload = job.attempt > 0,
                mimeType = mimeType,
                source = { input },
                onProgress = transferred::set,
            )
        } finally {
            progressWriter.cancelAndJoin()
            val finalBytes = transferred.get().coerceIn(0, job.sizeBytes)
            queueStore.updateProgress(job.id, finalBytes, clock())
            reportProgress(finalBytes, job.sizeBytes)
        }
    }

    private suspend fun finishFailure(
        job: UploadJob,
        failure: AppFailure,
        transferredBytes: Long,
    ): UploadRunDecision {
        val safeBytes = transferredBytes.coerceIn(0, job.sizeBytes)
        return if (failure.isRetryable()) {
            val nextAttempt = clock() + retryDelayMillis(job.attempt + 1)
            when (
                val saved = queueStore.markRetryable(
                    job.id,
                    safeBytes,
                    failure,
                    nextAttempt,
                    clock(),
                )
            ) {
                is AppResult.Failure -> saved.error.toDecision()
                is AppResult.Success -> if (saved.value) {
                    UploadRunDecision.Retry(failure)
                } else {
                    UploadRunDecision.Success(safeBytes)
                }
            }
        } else {
            when (val saved = queueStore.markFailed(job.id, safeBytes, failure, clock())) {
                is AppResult.Failure -> saved.error.toDecision()
                is AppResult.Success -> if (saved.value) {
                    UploadRunDecision.Failure(failure)
                } else {
                    UploadRunDecision.Success(safeBytes)
                }
            }
        }
    }

    private fun AppFailure.isRetryable(): Boolean = when (this) {
        AppFailure.NoNetwork,
        AppFailure.ServerUnavailable,
        AppFailure.SecureStorageUnavailable,
        AppFailure.LocalStorageUnavailable -> true
        is AppFailure.Http -> statusCode in RETRYABLE_HTTP_CODES || statusCode >= 500
        else -> false
    }

    private fun AppFailure.toDecision(): UploadRunDecision = if (isRetryable()) {
        UploadRunDecision.Retry(this)
    } else {
        UploadRunDecision.Failure(this)
    }

    private fun retryDelayMillis(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
        return (BASE_RETRY_MILLIS shl shift).coerceAtMost(MAX_RETRY_MILLIS)
    }

    private companion object {
        const val DEFAULT_PROGRESS_INTERVAL_MILLIS = 500L
        const val BASE_RETRY_MILLIS = 30_000L
        const val MAX_RETRY_MILLIS = 6L * 60 * 60 * 1_000
        const val MAX_BACKOFF_SHIFT = 9
        val RETRYABLE_HTTP_CODES = setOf(408, 425, 429)
    }
}
