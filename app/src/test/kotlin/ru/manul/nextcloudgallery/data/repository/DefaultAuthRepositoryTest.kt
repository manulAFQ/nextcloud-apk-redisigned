package ru.manul.nextcloudgallery.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.manul.nextcloudgallery.core.model.Account
import ru.manul.nextcloudgallery.core.model.AppPassword
import ru.manul.nextcloudgallery.core.model.LoginChallenge
import ru.manul.nextcloudgallery.core.model.LoginPollResult
import ru.manul.nextcloudgallery.core.model.LoginPollToken
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.data.remote.AuthGateway
import ru.manul.nextcloudgallery.domain.sync.SessionLifecycleObserver

class DefaultAuthRepositoryTest {
    @Test
    fun `authorized session is saved`() = runTest {
        val session = session()
        val store = FakeSessionStore()
        val repository = DefaultAuthRepository(FakeAuthGateway(session), store)

        val result = repository.pollLogin(challenge())

        assertTrue(result is AppResult.Success)
        assertEquals(session, store.session)
    }

    @Test
    fun `authorized session starts sync lifecycle`() = runTest {
        val session = session()
        val observer = RecordingSessionObserver()
        val repository = DefaultAuthRepository(
            gateway = FakeAuthGateway(session),
            sessionStore = FakeSessionStore(),
            sessionLifecycleObserver = observer,
        )

        repository.pollLogin(challenge())

        assertEquals(session.account.id, observer.availableAccountId)
    }

    @Test
    fun `logout clears local session even if remote revoke fails`() = runTest {
        val session = session()
        val store = FakeSessionStore().apply { this.session = session }
        val gateway = FakeAuthGateway(session).apply { failRevoke = true }
        val observer = RecordingSessionObserver()
        val repository = DefaultAuthRepository(
            gateway = gateway,
            sessionStore = store,
            sessionLifecycleObserver = observer,
        )

        val result = repository.logout(session)

        assertTrue(result is AppResult.Failure)
        assertEquals(null, store.session)
        assertEquals(1, store.clearCalls)
        assertEquals(session.account.id, observer.removedAccountId)
    }

    @Test
    fun `await login keeps polling until authorized`() = runTest {
        val session = session()
        val store = FakeSessionStore()
        val gateway = FakeAuthGateway(session).apply { pendingPolls = 2 }
        var delays = 0
        val repository = DefaultAuthRepository(
            gateway = gateway,
            sessionStore = store,
            pollIntervalMillis = 1,
            maxWaitMillis = 1_000,
            delayCall = { delays += 1 },
        )

        val result = repository.awaitLogin(challenge())

        assertTrue(result is AppResult.Success)
        assertEquals(3, gateway.pollCalls)
        assertEquals(2, delays)
        assertEquals(session, store.session)
    }

    @Test
    fun `await login survives temporary local network failure`() = runTest {
        val session = session()
        val gateway = FakeAuthGateway(session).apply { failingPolls = 2 }
        val repository = DefaultAuthRepository(
            gateway = gateway,
            sessionStore = FakeSessionStore(),
            pollIntervalMillis = 1,
            maxWaitMillis = 1_000,
            delayCall = {},
        )

        val result = repository.awaitLogin(challenge())

        assertTrue(result is AppResult.Success)
        assertEquals(3, gateway.pollCalls)
    }

    private class FakeSessionStore : SessionStore {
        var session: UserSession? = null
        var clearCalls = 0

        override suspend fun save(session: UserSession): AppResult<Unit> {
            this.session = session
            return AppResult.Success(Unit)
        }

        override suspend fun load(): AppResult<UserSession?> = AppResult.Success(session)

        override suspend fun clear(): AppResult<Unit> {
            clearCalls += 1
            session = null
            return AppResult.Success(Unit)
        }
    }

    private class FakeAuthGateway(private val session: UserSession) : AuthGateway {
        var failRevoke = false
        var pendingPolls = 0
        var failingPolls = 0
        var pollCalls = 0

        override suspend fun startLogin(serverAddress: ServerAddress): AppResult<LoginChallenge> =
            AppResult.Success(challenge())

        override suspend fun pollLogin(challenge: LoginChallenge): AppResult<LoginPollResult> {
            pollCalls += 1
            return if (pollCalls <= failingPolls) {
                AppResult.Failure(AppFailure.ServerUnavailable)
            } else if (pollCalls <= failingPolls + pendingPolls) {
                AppResult.Success(LoginPollResult.Pending)
            } else {
                AppResult.Success(LoginPollResult.Authorized(session))
            }
        }

        override suspend fun revokeAppPassword(session: UserSession): AppResult<Unit> =
            if (failRevoke) {
                AppResult.Failure(AppFailure.ServerUnavailable)
            } else {
                AppResult.Success(Unit)
            }
    }

    private class RecordingSessionObserver : SessionLifecycleObserver {
        var availableAccountId: String? = null
        var removedAccountId: String? = null

        override fun onSessionAvailable(session: UserSession) {
            availableAccountId = session.account.id
        }

        override fun onSessionRemoved(accountId: String) {
            removedAccountId = accountId
        }
    }

    private companion object {
        fun address(): ServerAddress = ServerAddress.parse("http://10.0.2.2:8080").getOrThrow()

        fun session(): UserSession = UserSession(
            account = Account("id", address(), "user", null),
            appPassword = AppPassword.create("secret").getOrThrow(),
        )

        fun challenge(): LoginChallenge = LoginChallenge(
            serverAddress = address(),
            loginUrl = "http://10.0.2.2:8080/login/v2/flow/id",
            pollEndpoint = "http://10.0.2.2:8080/login/v2/poll",
            pollToken = LoginPollToken.create("poll-token").getOrThrow(),
        )
    }
}
