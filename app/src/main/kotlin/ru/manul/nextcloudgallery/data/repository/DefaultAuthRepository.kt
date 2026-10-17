package ru.manul.nextcloudgallery.data.repository

import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import ru.manul.nextcloudgallery.core.model.LoginChallenge
import ru.manul.nextcloudgallery.core.model.LoginPollResult
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppFailure
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.local.SessionStore
import ru.manul.nextcloudgallery.data.remote.AuthGateway
import ru.manul.nextcloudgallery.domain.repository.AuthRepository
import ru.manul.nextcloudgallery.domain.sync.SessionLifecycleObserver

class DefaultAuthRepository(
    private val gateway: AuthGateway,
    private val sessionStore: SessionStore,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    private val delayCall: suspend (Long) -> Unit = { delay(it) },
    private val sessionLifecycleObserver: SessionLifecycleObserver = SessionLifecycleObserver.None,
) : AuthRepository {
    override suspend fun startLogin(rawServerAddress: String): AppResult<LoginChallenge> {
        val address = ServerAddress.parse(rawServerAddress).getOrElse { error ->
            return AppResult.Failure(
                AppFailure.InvalidServerAddress(error.message ?: "Invalid server address"),
            )
        }
        return gateway.startLogin(address)
    }

    override suspend fun pollLogin(challenge: LoginChallenge): AppResult<LoginPollResult> {
        val pollResult = gateway.pollLogin(challenge)
        if (pollResult !is AppResult.Success || pollResult.value !is LoginPollResult.Authorized) {
            return pollResult
        }

        val saveResult = sessionStore.save(pollResult.value.session)
        return when (saveResult) {
            is AppResult.Success -> {
                runCatching { sessionLifecycleObserver.onSessionAvailable(pollResult.value.session) }
                pollResult
            }
            is AppResult.Failure -> saveResult
        }
    }

    override suspend fun currentSession(): AppResult<UserSession?> = sessionStore.load()

    override suspend fun awaitLogin(challenge: LoginChallenge): AppResult<UserSession> {
        val startedAt = TimeSource.Monotonic.markNow()
        var transientFailures = 0
        while (startedAt.elapsedNow().inWholeMilliseconds < maxWaitMillis) {
            when (val result = pollLogin(challenge)) {
                is AppResult.Failure -> {
                    val retryable = result.error == AppFailure.ServerUnavailable ||
                        result.error == AppFailure.NoNetwork ||
                        (result.error is AppFailure.Http && result.error.statusCode >= 500)
                    if (!retryable || ++transientFailures >= MAX_TRANSIENT_POLL_FAILURES) return result
                    delayCall(pollIntervalMillis)
                }
                is AppResult.Success -> when (val value = result.value) {
                    is LoginPollResult.Authorized -> return AppResult.Success(value.session)
                    LoginPollResult.Pending -> {
                        transientFailures = 0
                        delayCall(pollIntervalMillis)
                    }
                }
            }
        }
        return AppResult.Failure(AppFailure.LoginTimedOut)
    }

    override suspend fun logout(session: UserSession): AppResult<Unit> {
        val revokeResult = gateway.revokeAppPassword(session)
        val clearResult = sessionStore.clear()
        if (clearResult is AppResult.Success) {
            runCatching { sessionLifecycleObserver.onSessionRemoved(session.account.id) }
        }
        return when {
            clearResult is AppResult.Failure -> clearResult
            revokeResult is AppResult.Failure -> revokeResult
            else -> AppResult.Success(Unit)
        }
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 1_000L
        const val MAX_TRANSIENT_POLL_FAILURES = 15
        val DEFAULT_MAX_WAIT_MILLIS = 20.minutes.inWholeMilliseconds
    }
}
