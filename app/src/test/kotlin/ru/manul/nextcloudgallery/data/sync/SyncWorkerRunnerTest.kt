package ru.manul.nextcloudgallery.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.domain.sync.MediaSynchronizer
import ru.manul.nextcloudgallery.domain.sync.SyncOutcome

class SyncWorkerRunnerTest {
    @Test
    fun `missing or switched account finishes without remote request`() = runTest {
        val synchronizer = FakeSynchronizer(AppResult.Success(SyncOutcome.AlreadyRunning))
        val runner = SyncWorkerRunner(FakeSessionStore(session()), synchronizer)

        val result = runner.run("another-account", "/Photos", force = false)

        assertEquals(SyncRunDecision.Success(null), result)
        assertEquals(0, synchronizer.calls)
    }

    @Test
    fun `temporary network failure requests retry`() = runTest {
        val synchronizer = FakeSynchronizer(AppResult.Failure(AppFailure.ServerUnavailable))
        val runner = SyncWorkerRunner(FakeSessionStore(session()), synchronizer)

        val result = runner.run("account", "/Photos", force = false)

        assertTrue(result is SyncRunDecision.Retry)
    }

    @Test
    fun `authorization failure does not create retry loop`() = runTest {
        val synchronizer = FakeSynchronizer(AppResult.Failure(AppFailure.Unauthorized))
        val runner = SyncWorkerRunner(FakeSessionStore(session()), synchronizer)

        val result = runner.run("account", "/Photos", force = false)

        assertTrue(result is SyncRunDecision.Failure)
    }

    private class FakeSessionStore(
        private var session: UserSession?,
    ) : SessionStore {
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

    private class FakeSynchronizer(
        private val result: AppResult<SyncOutcome>,
    ) : MediaSynchronizer {
        var calls = 0

        override suspend fun sync(
            session: UserSession,
            remotePath: String,
            force: Boolean,
        ): AppResult<SyncOutcome> {
            calls += 1
            return result
        }
    }

    private companion object {
        fun session() = UserSession(
            account = Account(
                id = "account",
                serverAddress = ServerAddress.parse("http://10.0.2.2:8080").getOrThrow(),
                username = "user",
                displayName = null,
            ),
            appPassword = AppPassword.create("secret").getOrThrow(),
        )
    }
}
