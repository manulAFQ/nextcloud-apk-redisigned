package ru.manul.nextcloudgallery.domain.repository

import ru.manul.nextcloudgallery.core.model.LoginChallenge
import ru.manul.nextcloudgallery.core.model.LoginPollResult
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface AuthRepository {
    suspend fun startLogin(rawServerAddress: String): AppResult<LoginChallenge>
    suspend fun pollLogin(challenge: LoginChallenge): AppResult<LoginPollResult>
    suspend fun awaitLogin(challenge: LoginChallenge): AppResult<UserSession>
    suspend fun currentSession(): AppResult<UserSession?>
    suspend fun logout(session: UserSession): AppResult<Unit>
}
