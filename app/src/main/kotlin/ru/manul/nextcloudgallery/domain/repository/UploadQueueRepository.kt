package ru.manul.nextcloudgallery.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface UploadQueueRepository {
    fun observe(accountId: String): Flow<List<UploadJob>>

    suspend fun enqueue(
        session: UserSession,
        localUri: String,
        remotePath: String,
        scheduleImmediately: Boolean = true,
        requestedJobId: String? = null,
    ): AppResult<UploadJob>

    suspend fun start(jobId: String): AppResult<Unit>

    suspend fun pause(jobId: String): AppResult<Unit>

    suspend fun resume(jobId: String): AppResult<Unit>

    suspend fun retry(jobId: String): AppResult<Unit>

    suspend fun remove(jobId: String): AppResult<Unit>

    suspend fun restore(accountId: String): AppResult<Unit>
}
