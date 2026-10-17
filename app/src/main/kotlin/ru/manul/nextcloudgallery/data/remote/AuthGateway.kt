package ru.manul.nextcloudgallery.data.remote

import ru.manul.nextcloudgallery.core.model.LoginChallenge
import ru.manul.nextcloudgallery.core.model.LoginPollResult
import ru.manul.nextcloudgallery.core.model.ServerAddress
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult

interface AuthGateway {
    suspend fun startLogin(serverAddress: ServerAddress): AppResult<LoginChallenge>
    suspend fun pollLogin(challenge: LoginChallenge): AppResult<LoginPollResult>
    suspend fun revokeAppPassword(session: UserSession): AppResult<Unit>
}
