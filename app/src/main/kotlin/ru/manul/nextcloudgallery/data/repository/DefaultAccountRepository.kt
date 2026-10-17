package ru.manul.nextcloudgallery.data.repository

import ru.manul.nextcloudgallery.core.model.ServerCapabilities
import ru.manul.nextcloudgallery.core.model.UserProfile
import ru.manul.nextcloudgallery.core.model.UserSession
import ru.manul.nextcloudgallery.core.result.AppResult
import ru.manul.nextcloudgallery.data.remote.NextcloudGateway
import ru.manul.nextcloudgallery.domain.repository.AccountRepository

class DefaultAccountRepository(
    private val gateway: NextcloudGateway,
) : AccountRepository {
    override suspend fun capabilities(session: UserSession): AppResult<ServerCapabilities> =
        gateway.capabilities(session)

    override suspend fun profile(session: UserSession): AppResult<UserProfile> = gateway.profile(session)
}
