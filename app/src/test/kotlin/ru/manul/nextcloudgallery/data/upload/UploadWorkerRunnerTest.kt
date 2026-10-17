package ru.manul.nextcloudgallery.data.upload

import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadReceipt
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.OpenUploadSource
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.data.local.UploadQueueStore
import ru.manul.nextcloudgallery.data.local.UploadSource
import ru.manul.nextcloudgallery.data.local.UploadSourceInfo
import ru.manul.nextcloudgallery.data.remote.UploadGateway

class UploadWorkerRunnerTest {
    @Test
    fun `successful upload reaches succeeded state`() = runTest {
        val store = MemoryStore(job())
        val runner = runner(store, AppResult.Success(UploadReceipt("/Photos/photo.jpg", 7, "etag")))

        val result = runner.run("job", "account")

        assertTrue(result is UploadRunDecision.Success)
        assertEquals(UploadState.SUCCEEDED, store.current.state)
        assertEquals(7L, store.current.transferredBytes)
        assertEquals(1, store.current.attempt)
    }

    @Test
    fun `temporary network failure returns job to persistent queue`() = runTest {
        val store = MemoryStore(job())
        val runner = runner(store, AppResult.Failure(AppFailure.ServerUnavailable))

        val result = runner.run("job", "account")

        assertTrue(result is UploadRunDecision.Retry)
        assertEquals(UploadState.QUEUED, store.current.state)
        assertEquals(1, store.current.attempt)
        assertNotNull(store.current.nextAttemptEpochMs)
    }

    @Test
    fun `paused job does not open source or network`() = runTest {
        val store = MemoryStore(job().copy(state = UploadState.PAUSED))
        val source = FakeSource()
        val gateway = FakeGateway(AppResult.Success(UploadReceipt("/Photos/photo.jpg", 7, null)))
        val runner = UploadWorkerRunner(
            sessionStore = FakeSessionStore(session()),
            queueStore = store,
            uploadSource = source,
            gateway = gateway,
            progressIntervalMillis = 1,
        )

        val result = runner.run("job", "account")

        assertTrue(result is UploadRunDecision.Success)
        assertEquals(0, source.openCalls)
        assertEquals(0, gateway.calls)
    }

    private fun runner(store: MemoryStore, result: AppResult<UploadReceipt>) = UploadWorkerRunner(
        sessionStore = FakeSessionStore(session()),
        queueStore = store,
        uploadSource = FakeSource(),
        gateway = FakeGateway(result),
        clock = { 1_000L },
        progressIntervalMillis = 1,
    )

    private class FakeGateway(private val result: AppResult<UploadReceipt>) : UploadGateway {
        var calls = 0

        override suspend fun resolveUniqueRemotePath(
            session: UserSession,
            requestedRemotePath: String,
        ): AppResult<String> = AppResult.Success(requestedRemotePath)

        override suspend fun upload(
            session: UserSession,
            uploadId: String,
            remotePath: String,
            sizeBytes: Long,
            recoverCompletedUpload: Boolean,
            mimeType: String?,
            source: () -> java.io.InputStream,
            onProgress: (Long) -> Unit,
        ): AppResult<UploadReceipt> {
            calls += 1
            source().readBytes()
            onProgress(sizeBytes)
            return result
        }

        override suspend fun abort(session: UserSession, uploadId: String): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeSource : UploadSource {
        var openCalls = 0

        override fun inspect(localUri: String): AppResult<UploadSourceInfo> =
            AppResult.Success(UploadSourceInfo(7, "image/jpeg"))

        override fun open(localUri: String): AppResult<OpenUploadSource> {
            openCalls += 1
            val input = ByteArrayInputStream("content".toByteArray())
            return AppResult.Success(OpenUploadSource(input, "image/jpeg", input::close))
        }
    }

    private class FakeSessionStore(private var session: UserSession?) : SessionStore {
        override suspend fun save(session: UserSession): AppResult<Unit> {
            this.session = session
            return AppResult.Success(Unit)
        }

        override suspend fun load(): AppResult<UserSession?> = AppResult.Success(session)

        override suspend fun clear(): AppResult<Unit> {
            session = null
            return AppResult.Success(Unit)
        }
    }

    private class MemoryStore(initial: UploadJob) : UploadQueueStore {
        var current = initial
        private val flow = MutableStateFlow(listOf(initial))

        override fun observe(accountId: String): Flow<List<UploadJob>> = flow

        override suspend fun create(job: UploadJob) = success { current = job }

        override suspend fun get(jobId: String): AppResult<UploadJob?> = AppResult.Success(current)

        override suspend fun markRunning(jobId: String, nowEpochMs: Long) = successBoolean {
            current = current.copy(
                state = UploadState.RUNNING,
                transferredBytes = 0,
                attempt = current.attempt + 1,
                nextAttemptEpochMs = null,
            )
        }

        override suspend fun resolveDestination(jobId: String, remotePath: String, nowEpochMs: Long) =
            successBoolean { current = current.copy(remotePath = remotePath, destinationResolved = true) }

        override suspend fun updateProgress(jobId: String, transferredBytes: Long, nowEpochMs: Long) =
            successBoolean { current = current.copy(transferredBytes = transferredBytes) }

        override suspend fun markSucceeded(
            jobId: String,
            transferredBytes: Long,
            resultEtag: String?,
            nowEpochMs: Long,
        ) =
            successBoolean {
                current = current.copy(
                    state = UploadState.SUCCEEDED,
                    transferredBytes = transferredBytes,
                    resultEtag = resultEtag,
                )
            }

        override suspend fun markRetryable(
            jobId: String,
            transferredBytes: Long,
            failure: AppFailure,
            nextAttemptEpochMs: Long,
            nowEpochMs: Long,
        ) = successBoolean {
            current = current.copy(
                state = UploadState.QUEUED,
                transferredBytes = transferredBytes,
                nextAttemptEpochMs = nextAttemptEpochMs,
            )
        }

        override suspend fun markFailed(
            jobId: String,
            transferredBytes: Long,
            failure: AppFailure,
            nowEpochMs: Long,
        ) = successBoolean {
            current = current.copy(state = UploadState.FAILED, transferredBytes = transferredBytes)
        }

        override suspend fun pause(jobId: String, nowEpochMs: Long) = successBoolean {
            current = current.copy(state = UploadState.PAUSED)
        }

        override suspend fun queueAgain(jobId: String, nowEpochMs: Long) = successBoolean {
            current = current.copy(state = UploadState.QUEUED)
        }

        override suspend fun recoverInterrupted(accountId: String, nowEpochMs: Long) = success {}

        override suspend fun runnable(accountId: String, nowEpochMs: Long): AppResult<List<UploadJob>> =
            AppResult.Success(listOf(current))

        override suspend fun delete(jobId: String) = success {}

        private fun success(block: () -> Unit): AppResult<Unit> {
            block()
            flow.value = listOf(current)
            return AppResult.Success(Unit)
        }

        private fun successBoolean(block: () -> Unit): AppResult<Boolean> {
            block()
            flow.value = listOf(current)
            return AppResult.Success(true)
        }
    }

    private companion object {
        fun session() = UserSession(
            Account(
                "account",
                ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(),
                "user",
                null,
            ),
            AppPassword.create("secret").getOrThrow(),
        )

        fun job() = UploadJob(
            id = "job",
            accountId = "account",
            localUri = "content://media/photo",
            remotePath = "/Photos/photo.jpg",
            sizeBytes = 7,
            transferredBytes = 0,
            state = UploadState.QUEUED,
            attempt = 0,
            failureMessage = null,
            nextAttemptEpochMs = null,
            destinationResolved = false,
            resultEtag = null,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }
}
