package ru.manul.nextcloudgallery.data.autoupload

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.AutoUploadPolicy
import ru.manul.nextcloudgallery.core.model.LocalMediaCandidate
import ru.manul.nextcloudgallery.core.model.LocalMediaKind
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UploadJob
import ru.manul.nextcloudgallery.core.model.UploadState
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.AutoUploadPolicyStore
import ru.manul.nextcloudgallery.data.local.AutoUploadRegistry
import ru.manul.nextcloudgallery.data.local.LocalMediaSource
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.domain.repository.UploadQueueRepository

class AutoUploadRunnerTest {
    @Test
    fun `no device folders are synchronized by default`() = runTest {
        val uploads = FakeUploads()
        val result = runner(
            policy = policy().copy(selectedFolders = emptySet()),
            uploads = uploads,
            sourceResult = AppResult.Success(listOf(photo("camera-1", "DCIM/Camera/"))),
        ).run("account")

        assertEquals(0, (result as AutoUploadRunDecision.Success).enqueued)
        assertTrue(uploads.paths.isEmpty())
    }

    @Test
    fun `camera item is enqueued exactly once with dated destination`() = runTest {
        val registry = MemoryRegistry()
        val uploads = FakeUploads()
        val runner = runner(
            registry = registry,
            uploads = uploads,
            sourceResult = AppResult.Success(listOf(photo("camera-1", "DCIM/Camera/"))),
        )

        val first = runner.run("account")
        val second = runner.run("account")

        assertEquals(1, (first as AutoUploadRunDecision.Success).enqueued)
        assertEquals(0, (second as AutoUploadRunDecision.Success).enqueued)
        assertEquals(listOf("/Photos/Auto Upload/Pixel/2023/11/photo.jpg"), uploads.paths)
    }

    @Test
    fun `video is excluded while screenshot image is included`() = runTest {
        val uploads = FakeUploads()
        val policy = policy().copy(includeVideos = false)
        val items = listOf(
            photo("shot", "Pictures/Screenshots/"),
            photo("video", "DCIM/Camera/").copy(kind = LocalMediaKind.VIDEO, displayName = "clip.mp4"),
        )

        val result = runner(policy = policy, uploads = uploads, sourceResult = AppResult.Success(items)).run("account")

        assertEquals(1, (result as AutoUploadRunDecision.Success).enqueued)
        assertEquals(listOf("/Photos/Auto Upload/Pixel/2023/11/photo.jpg"), uploads.paths)
    }

    @Test
    fun `wrong wifi prevents MediaStore scan`() = runTest {
        val source = FakeSource(AppResult.Success(listOf(photo("camera-1", "DCIM/Camera/"))))
        val runner = runner(
            policy = policy().copy(requiredSsid = "Home"),
            source = source,
            conditions = DeviceConditions(true, "Guest", true, 100),
        )

        val result = runner.run("account")

        assertTrue(result is AutoUploadRunDecision.Retry)
        assertEquals(0, source.calls)
    }

    @Test
    fun `missing media permission is reported without queue mutation`() = runTest {
        val uploads = FakeUploads()
        val result = runner(
            uploads = uploads,
            sourceResult = AppResult.Failure(AppFailure.MediaPermissionRequired),
        ).run("account")

        assertEquals(
            AppFailure.MediaPermissionRequired,
            (result as AutoUploadRunDecision.Failure).failure,
        )
        assertTrue(uploads.paths.isEmpty())
    }

    private fun runner(
        policy: AutoUploadPolicy = policy(),
        registry: MemoryRegistry = MemoryRegistry(),
        uploads: FakeUploads = FakeUploads(),
        sourceResult: AppResult<List<LocalMediaCandidate>> = AppResult.Success(emptyList()),
        source: FakeSource = FakeSource(sourceResult),
        conditions: DeviceConditions = DeviceConditions(true, "Home", true, 100),
    ) = AutoUploadRunner(
        sessionStore = FakeSessionStore(session()),
        policyStore = FakePolicyStore(policy),
        registry = registry,
        mediaSource = source,
        environment = AutoUploadEnvironment { conditions },
        uploads = uploads,
        clock = { 1_700_000_000_000 },
    )

    private class FakeSource(private val result: AppResult<List<LocalMediaCandidate>>) : LocalMediaSource {
        var calls = 0
        override fun scan(includeVideos: Boolean, limit: Int): AppResult<List<LocalMediaCandidate>> {
            calls += 1
            return when (result) {
                is AppResult.Failure -> result
                is AppResult.Success -> AppResult.Success(
                    if (includeVideos) result.value else result.value.filter { it.kind != LocalMediaKind.VIDEO },
                )
            }
        }
    }

    private class MemoryRegistry : AutoUploadRegistry {
        private val keys = mutableSetOf<String>()
        override suspend fun clearStaleClaims(accountId: String, olderThanEpochMs: Long) = AppResult.Success(Unit)
        override suspend fun claim(accountId: String, candidate: LocalMediaCandidate, nowEpochMs: Long) =
            AppResult.Success(keys.add(candidate.localKey))
        override suspend fun linkJob(accountId: String, localKey: String, jobId: String, nowEpochMs: Long) =
            AppResult.Success(Unit)
        override suspend fun confirmQueued(jobId: String, nowEpochMs: Long) = AppResult.Success(Unit)
        override suspend fun releaseClaim(accountId: String, localKey: String): AppResult<Unit> {
            keys.remove(localKey)
            return AppResult.Success(Unit)
        }
        override suspend fun markUploadedForJob(jobId: String, nowEpochMs: Long) = AppResult.Success(Unit)
        override suspend fun removeForJob(jobId: String) = AppResult.Success(Unit)
    }

    private class FakeUploads : UploadQueueRepository {
        val paths = mutableListOf<String>()
        override fun observe(accountId: String): Flow<List<UploadJob>> = emptyFlow()
        override suspend fun enqueue(
            session: UserSession,
            localUri: String,
            remotePath: String,
            scheduleImmediately: Boolean,
            requestedJobId: String?,
        ): AppResult<UploadJob> {
            paths += remotePath
            return AppResult.Success(job(requestedJobId ?: paths.size.toString(), localUri, remotePath))
        }
        override suspend fun start(jobId: String) = AppResult.Success(Unit)
        override suspend fun pause(jobId: String) = AppResult.Success(Unit)
        override suspend fun resume(jobId: String) = AppResult.Success(Unit)
        override suspend fun retry(jobId: String) = AppResult.Success(Unit)
        override suspend fun remove(jobId: String) = AppResult.Success(Unit)
        override suspend fun restore(accountId: String) = AppResult.Success(Unit)
    }

    private class FakePolicyStore(private val policy: AutoUploadPolicy?) : AutoUploadPolicyStore {
        override suspend fun get(accountId: String) = AppResult.Success(policy)
        override suspend fun save(policy: AutoUploadPolicy) = AppResult.Success(Unit)
        override suspend fun delete(accountId: String) = AppResult.Success(Unit)
    }

    private class FakeSessionStore(private var session: UserSession?) : SessionStore {
        override suspend fun save(session: UserSession): AppResult<Unit> { this.session = session; return AppResult.Success(Unit) }
        override suspend fun load() = AppResult.Success(session)
        override suspend fun clear(): AppResult<Unit> { session = null; return AppResult.Success(Unit) }
    }

    private companion object {
        fun policy() = AutoUploadPolicy(
            accountId = "account",
            enabled = true,
            deviceFolder = "Pixel",
            selectedFolders = setOf("DCIM/Camera/", "Pictures/Screenshots/"),
        )

        fun photo(key: String, path: String) = LocalMediaCandidate(
            localKey = key,
            contentUri = "content://media/external/images/media/1",
            displayName = "photo.jpg",
            relativePath = path,
            kind = LocalMediaKind.IMAGE,
            mimeType = "image/jpeg",
            sizeBytes = 10,
            capturedAtEpochMs = 1_700_000_000_000,
            modifiedAtEpochSeconds = 1_700_000_000,
        )

        fun session() = UserSession(
            Account("account", ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(), "user", null),
            AppPassword.create("secret").getOrThrow(),
        )

        fun job(id: String, uri: String, remote: String) = UploadJob(
            id, "account", uri, remote, 10, 0, UploadState.QUEUED, 0, null, null,
            destinationResolved = false, resultEtag = null, createdAtEpochMs = 1, updatedAtEpochMs = 1,
        )
    }
}
